import argparse
import base64
import copy
import hashlib
import importlib.util
import json
import os
import re
import shlex
import subprocess
import sys
from pathlib import Path
import tempfile
import textwrap
import unittest
from unittest import mock
import xml.etree.ElementTree as ET
import zipfile

spec=importlib.util.spec_from_file_location('ci_package',Path(__file__).with_name('verify-ci-package.py'))
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
toolchain_spec=importlib.util.spec_from_file_location('ci_toolchain',Path(__file__).with_name('ci-toolchain.py'))
toolchain=importlib.util.module_from_spec(toolchain_spec);toolchain_spec.loader.exec_module(toolchain)


class ToolchainBinding(unittest.TestCase):
    def test_current_source_matches_then_ndk_or_sdk_drift_is_rejected(self):
        self.assertEqual('17',toolchain.read()['java'])
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            for name in ('ci/toolchain.lock.json','app/build.gradle','build.gradle',
                         'gradle/wrapper/gradle-wrapper.properties'):
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True)
                path.write_bytes((toolchain.ROOT/name).read_bytes())
            toolchain.read(root)
            app=root/'app/build.gradle'
            original=app.read_text(encoding='utf-8')
            app.write_text(original.replace("ndkVersion '26.3.11579264'","ndkVersion '27.0.0'"),encoding='utf-8')
            with self.assertRaisesRegex(ValueError,'TOOLCHAIN_DRIFT'):toolchain.read(root)
            app.write_text(original.replace('targetSdk 37','targetSdk 36'),encoding='utf-8')
            with self.assertRaisesRegex(ValueError,'TARGET_SDK'):toolchain.read(root)

class PackageBinding(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.source=self.root/'source';self.source.mkdir()
        self.reports=self.root/'stability-acceptance'/'018412c5-321b-4710-9bcb-a64fbec61aa1'
        self.reports.mkdir(parents=True)
        file=self.source/'app/build.gradle';file.parent.mkdir();file.write_text('verified source')
        snapshot=self.reports/'source-sha256.json'
        snapshot.write_text(json.dumps({'app/build.gradle':gate.digest(file)}))
        self.commands=[]
        for name in sorted(gate.REQUIRED_CHECKS):
            log=self.reports/(name+'.log');log.write_text(name+' passed\n')
            self.commands.append(dict(name=name,exitCode=0,sha256=gate.digest(log)))
        rows=[]
        for flavor in ('standard','low'):
            apk=self.root/f'app-{flavor}-release-unsigned.apk';apk.write_bytes(flavor.encode())
            rows.append(dict(flavor=flavor,sha256=gate.digest(apk),certificateSha256=None))
        self.receipt=dict(verificationSchema=2,status='PASS_UNSIGNED_PACKAGE',commit='abc',dirty=False,
                          sourceSnapshot=dict(sha256=gate.digest(snapshot)),apks=rows,
                          commands=self.commands,
                          junit={flavor:dict(tests=2,skipped=0,failures=0,errors=0)
                                 for flavor in ('Standard','Low')})
    def validate(self):return gate.validate_receipt(self.receipt,self.root,'abc',self.source,
                                                  {'app/build.gradle'},set(), expected_flavors=('standard','low'))
    def test_exact_pair_is_selected(self):self.assertEqual(2,len(self.validate()))
    def test_standard_scope_keeps_gates_and_rejects_missing_expected_flavor(self):
        self.receipt['apks'] = [row for row in self.receipt['apks'] if row['flavor'] == 'standard']
        self.receipt['commands'] = [row for row in self.commands if row['name'] != 'low-elf']
        self.receipt['junit'].pop('Low')
        selected = gate.validate_receipt(self.receipt, self.root, 'abc', self.source,
                                         {'app/build.gradle'}, set())
        self.assertEqual([self.root / 'app-standard-release-unsigned.apk'], selected)
        with self.assertRaisesRegex(ValueError, 'PACKAGE_FLAVORS'):
            self.validate()
        self.receipt['commands'] = [row for row in self.receipt['commands'] if row['name'] != 'standard-elf']
        with self.assertRaisesRegex(ValueError, 'PACKAGE_CHECK_INCOMPLETE'):
            gate.validate_receipt(self.receipt, self.root, 'abc', self.source,
                                  {'app/build.gradle'}, set())
    def test_actual_uuid_report_layout_is_selected_and_duplicate_receipts_rejected(self):
        manifest=self.reports/'manifest.json';manifest.write_text(json.dumps(self.receipt))
        self.assertEqual(manifest,gate.select_manifest(self.root))
        duplicate=self.root/'stability-acceptance'/'older'/'manifest.json'
        duplicate.parent.mkdir();duplicate.write_text('{}')
        with self.assertRaisesRegex(ValueError,'MANIFEST_COUNT'):gate.select_manifest(self.root)
    def test_apk_tamper_and_failed_gate_are_rejected(self):
        (self.root/'app-low-release-unsigned.apk').write_bytes(b'tampered')
        with self.assertRaisesRegex(ValueError,'APK_CHANGED'):self.validate()
        self.receipt['commands'][0]['exitCode']=1
        with self.assertRaisesRegex(ValueError,'FAILED_CHECK'):self.validate()
    def test_source_change_and_commit_mismatch_are_rejected(self):
        (self.source/'app/build.gradle').write_text('new source')
        with self.assertRaisesRegex(ValueError,'SOURCE_CHANGED'):self.validate()
        self.receipt['commit']='other'
        with self.assertRaisesRegex(ValueError,'RECEIPT_IDENTITY'):self.validate()
    def test_missing_named_gate_changed_log_and_unexecuted_tests_are_rejected(self):
        self.receipt['commands'].pop()
        with self.assertRaisesRegex(ValueError,'CHECK_INCOMPLETE'):self.validate()
        self.receipt['commands']=[dict(name=name,exitCode=0,
                                      sha256=gate.digest(self.reports/(name+'.log')))
                                  for name in sorted(gate.REQUIRED_CHECKS)]
        (self.reports/'apk-assets.log').write_text('unverified replacement')
        with self.assertRaisesRegex(ValueError,'CHECK_CHANGED'):self.validate()
        self.receipt['commands']=[dict(name=name,exitCode=0,
                                      sha256=gate.digest(self.reports/(name+'.log')))
                                  for name in sorted(gate.REQUIRED_CHECKS)]
        self.receipt['junit']['Low']['skipped']=2
        with self.assertRaisesRegex(ValueError,'JUNIT_INCOMPLETE'):self.validate()
    def test_omitted_deleted_and_unsafe_source_cannot_be_hidden(self):
        snapshot=self.reports/'source-sha256.json'
        for captured,pattern in [({'missing.java':'a'*64},'SOURCE_INCOMPLETE'),
                                 ({'app/build.gradle':gate.digest(self.source/'app/build.gradle'),
                                   'tools/removed.py':'a'*64},'SOURCE_MISSING'),
                                 ({'app/build.gradle':gate.digest(self.source/'app/build.gradle'),
                                   'C:/outside.txt':'a'*64},'SOURCE_PATH')]:
            snapshot.write_text(json.dumps(captured))
            self.receipt['sourceSnapshot']['sha256']=gate.digest(snapshot)
            with self.subTest(captured=captured),self.assertRaisesRegex(ValueError,pattern):self.validate()
    def test_foreign_failed_or_wrong_workflow_run_is_rejected(self):
        run=dict(repository=dict(full_name='DSH-APP/DSHA'),head_repository=dict(full_name='DSH-APP/DSHA'),
                 head_sha='abc',status='completed',conclusion='success',event='workflow_dispatch',
                 path='.github/workflows/ci-package.yml')
        gate.validate_run(run,'DSH-APP/DSHA','abc')
        for key,value in [('head_sha','other'),('conclusion','failure'),('path','.github/workflows/other.yml'),('event','pull_request')]:
            other=copy.deepcopy(run);other[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):gate.validate_run(other,'DSH-APP/DSHA','abc')

class SunsetPatchRegressions(unittest.TestCase):
    def setUp(self):
        self.root = Path(__file__).resolve().parents[1]
        workflow = (self.root / '.github/workflows/sunset-3090-q.yml').read_text(encoding='utf-8')
        self.job = workflow.split('\n  apk:\n', 1)[1]

    def step(self, name):
        marker = '      - name: ' + name + '\n'
        self.assertEqual(self.job.count(marker), 1)
        return marker + self.job.split(marker, 1)[1].split('\n      - ', 1)[0]

    def test_runtime_regressions_run_after_fixture_before_full_unit_tests(self):
        step = self.step('Verify runtime patch upgrades and managed recovery consent')
        start = self.job.index(step)
        self.assertLess(self.job.index('run: python3 tools/prepare-test-runtime.py'), start)
        self.assertLess(start, self.job.index('Complete Standard unit tests with fixed inputs'))
        self.assertIn('actions/setup-java@', self.job[:start])
        self.assertIn('actions/setup-node@', self.job[:start])
        self.assertNotRegex(step, r'\n        (?:if|continue-on-error):')
        command = shlex.split(step.split('        run: |\n', 1)[1].replace('\\\n', ''))
        self.assertEqual(command[:2], ['python3', 'tools/run-host-tests.py'])
        parser = argparse.ArgumentParser()
        parser.add_argument('--stage', required=True)
        parser.add_argument('--jdk', required=True)
        parser.add_argument('--playwright', required=True)
        parser.add_argument('--browsers-path', required=True)
        parser.add_argument('--output', required=True)
        parser.add_argument('--only', action='append', required=True)
        selected = parser.parse_args(command[2:])
        self.assertEqual(selected.stage, 'runtime')
        self.assertEqual(selected.jdk, '$JAVA_HOME')
        self.assertEqual(selected.playwright, '$GITHUB_WORKSPACE/tools/ci-browser/node_modules/playwright')
        self.assertEqual(selected.browsers_path, '$RUNNER_TEMP/dsha-plugin-browsers')
        self.assertIn('npm ci --prefix tools/ci-browser --ignore-scripts', self.job[:start])
        self.assertIn('node tools/ci-browser/node_modules/playwright/cli.js install --with-deps chromium', self.job[:start])
        browser = json.loads((self.root / 'tools/ci-browser/package.json').read_text())
        browser_lock = json.loads((self.root / 'tools/ci-browser/package-lock.json').read_text())
        self.assertEqual(browser['dependencies']['playwright'], '1.64.1')
        self.assertEqual(browser_lock['packages']['node_modules/playwright']['version'], browser['dependencies']['playwright'])
        self.assertRegex(browser_lock['packages']['node_modules/playwright']['integrity'], r'^sha512-')
        self.assertEqual(selected.output, 'app/build/host-tests/sunset-patch-chains')
        expected = {
            'test-plugin-management-patch-chain.py': {'raw', 'jdk17'},
            'test-language-patch-chain.py': {'raw', 'jdk17', 'current_archive'},
            'test-ui-language.mjs': {'node24', 'raw'},
            'test-prompt-language.mjs': {'node24', 'raw'},
            'test-app-activity.mjs': {'node24'},
            'test-plugin-runtime-evidence.mjs': {'node24', 'raw'},
            'test-plugin-discovery.py': {'python39', 'node24'},
            'test-plugin-review.py': {'python39'},
            'test-plugin-management-probe.mjs': {'node24', 'raw'},
            'test-session-interaction-patch-upgrade.py': {'raw', 'jdk17'},
            'test-preset-pending-patch-upgrade.py': {'raw', 'jdk17'},
            'test-recovery-native-consent.mjs': {'node24', 'managed'},
            'test-mobile-server-compression-carrier.mjs': {'node24', 'raw'},
        }
        self.assertEqual(set(selected.only), set(expected))
        self.assertEqual(len(selected.only), len(expected))
        manifest = json.loads((self.root / 'tools/host-tests.manifest.json').read_text(encoding='utf-8'))
        entries = {row['path']: row for row in manifest['tests']}
        for name in selected.only:
            with self.subTest(test=name):
                row = entries['tools/' + name]
                self.assertEqual(row['stage'], selected.stage)
                self.assertTrue(expected[name] <= set(row['requires']))
                # 现有 job 只准备这些依赖；新增浏览器等要求必须另行配置。
                self.assertTrue(set(row['requires']) <= {'python39', 'node24', 'raw', 'managed', 'jdk17', 'native_host', 'browser', 'current_archive'})
                if name == 'test-recovery-native-consent.mjs':
                    # 该入口依赖此变量执行真实 ToolRuntime，不能只跑 SDK 传输分支。
                    self.assertEqual(row['environment'].get('DSHA_TEST_RUNTIME'), '{managed}')

    def test_full_input_gate_rejects_missing_or_skipped_external_cases(self):
        step = self.step('Verify all seven Standard external-fixture tests executed')
        script = textwrap.dedent(step.split("          python3 - <<'PY'\n", 1)[1].rsplit('          PY', 1)[0])
        prefix = 'com.deepseekharness.app.'
        required = [
            (prefix + 'runtime.AdbWheelBundleTest', 'realBundleRebuildsOnlyOmittedEntriesAndPreservesRestoredWheel'),
            *[(prefix + 'runtime.RuntimeToolsRegistryAndroidTest', name) for name in (
                'completeProductionChainAppliesAllSpecializedStagesAndIsRepeatable',
                'lanRetirementRejectsModuleDriftAndPreservesOriginalBytes',
                'lanRetirementRejectsUnknownVersionAndContractDrift',
                'oldLanExactReplacementAndIdempotenceRemainStrict',
                'previousRuntimeGenerationCannotReceiveCurrentOverlayInPlace')],
            (prefix + 'util.ManagedPatchRegistryIntegrationTest', 'everyActiveModuleRepairsAsAWholeAndRetiredUiNeverRuns'),
        ]
        low = (prefix + 'vscreen.VirtualScreenLowBoundaryTest', 'lowFlavorRemainsUnsupportedEvenOnAModernAndroidVersion')
        def execute(rows):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                reports = root / 'app/build/test-results/testStandardDebugUnitTest'
                reports.mkdir(parents=True)
                suite = ET.Element('testsuite', tests=str(len(rows)), failures='0', errors='0',
                                   skipped=str(sum(status == 'skipped' for _, _, status in rows)))
                for owner, method, status in rows:
                    case = ET.SubElement(suite, 'testcase', classname=owner, name=method)
                    if status:
                        ET.SubElement(case, status)
                ET.ElementTree(suite).write(reports / 'TEST-fixture.xml')
                run = subprocess.run([sys.executable, '-c', script], cwd=root,
                                     env={**os.environ, 'GITHUB_STEP_SUMMARY': str(root / 'summary.md')},
                                     capture_output=True, text=True)
                output = root / 'out/full-unit-tests.json'
                return run, json.loads(output.read_text()) if output.exists() else None
        passing = [(owner, name, '') for owner, name in required] + [(*low, 'skipped')]
        run, report = execute(passing)
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(report['flavors']['Standard']['requiredFixtureTestsPassed'], 7)
        self.assertEqual(report['flavors']['Standard']['skipped'], 1)
        for target in required:
            for status in ('missing', 'skipped'):
                with self.subTest(target=target, status=status):
                    rows = [row for row in passing if row[:2] != target]
                    if status == 'skipped':
                        rows.append((*target, 'skipped'))
                    run, report = execute(rows)
                    self.assertNotEqual(run.returncode, 0)
                    self.assertIsNone(report)
        run, report = execute(passing + [(prefix + 'plugins.PluginRegistryTest', 'unexpected', 'skipped')])
        self.assertNotEqual(run.returncode, 0)
        self.assertIn('Unexpected full-input skips', run.stderr)
        self.assertIsNone(report)
        run, report = execute(passing + [(*required[-1], '')])
        self.assertNotEqual(run.returncode, 0)
        self.assertIsNone(report)

    def test_every_ci_entry_uses_standard_scope(self):
        for name in ('sunset-3090-q.yml', 'ci-fast.yml'):
            workflow = (self.root / '.github/workflows' / name).read_text(encoding='utf-8')
            self.assertIn(':app:testStandardDebugUnitTest', workflow)
            self.assertNotRegex(workflow, r':app:(?:test|lint|assemble)Low')
            self.assertNotIn('("Standard", "Low")', workflow)
            self.assertNotIn('tools/remote', workflow)
            self.assertNotIn('dsha-connect', workflow)
        package = (self.root / '.github/workflows/ci-package.yml').read_text(encoding='utf-8')
        self.assertIn('--stage package --flavors standard', package)
        self.assertNotIn('app/build/outputs/apk/*/', package)
        manifest = json.loads((self.root / 'tools/host-tests.manifest.json').read_text(encoding='utf-8'))
        low = next(row for row in manifest['tests'] if row['path'] == 'tools/test-low-preview-capability.mjs')
        self.assertEqual(low['stage'], 'history')

    def test_branch_stability_runner_refuses_low_scope_before_execution(self):
        result = subprocess.run([sys.executable, str(self.root / 'tools/verify-stability.py'),
                                 '--stage', 'software', '--flavors', 'both'],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn('invalid choice', result.stderr)

    def test_patch_chain_logs_are_retained_even_when_the_gate_fails(self):
        step = self.step('Upload full unit and host test reports')
        self.assertIn('\n        if: always()\n', step)
        self.assertIn('actions/upload-artifact@', step)
        self.assertIn('name: ${{ needs.source.outputs.tests_name }}', step)
        paths = step.split('          path: |\n', 1)[1].splitlines()
        self.assertEqual({path.strip() for path in paths if path.startswith('            ')},
                         {'app/build/test-results', 'app/build/host-tests/sunset-patch-chains',
                           'app/build/reports/lint-results-standardRelease.*'})


class TerminalTestScope(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('terminal_scope', Path(__file__).with_name('verify-terminal-test-scope.py'))
        cls.gate = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.gate)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.rows = [(owner, name, '') for owner, name in self.gate.REQUIRED_FIXTURES]
        self.rows += [(owner, 'fixtureIndependentTerminalCase', '') for owner in self.gate.REQUIRED_TERMINAL_CLASSES]
        self.rows += [(*next(iter(self.gate.ALLOWED_SKIPS)), 'skipped')]

    def write(self, rows, override=None):
        counts = dict(tests=len(rows), failures=sum(row[2] == 'failure' for row in rows),
                      errors=sum(row[2] == 'error' for row in rows),
                      skipped=sum(row[2] == 'skipped' for row in rows))
        if override:
            counts.update(override)
        suite = ET.Element('testsuite', **{key: str(value) for key, value in counts.items()})
        for owner, name, status in rows:
            case = ET.SubElement(suite, 'testcase', classname=owner, name=name)
            if status:
                ET.SubElement(case, status)
        ET.ElementTree(suite).write(self.root / 'TEST-fixtures.xml')

    def test_all_seven_fixtures_and_terminal_classes_execute_once(self):
        self.write(self.rows)
        result = self.gate.verify(self.root)
        counts = result['flavors']['Standard']
        self.assertEqual(counts['requiredFixtureTestsPassed'], 7)
        self.assertEqual(counts['requiredTerminalClassesPassed'], len(self.gate.REQUIRED_TERMINAL_CLASSES))
        self.assertEqual(counts['skipped'], 1)
        self.assertEqual(result['mode'], 'full')
        self.assertEqual(result['selection'], 'unfiltered')

    def test_missing_skipped_failed_or_duplicated_fixture_is_rejected(self):
        for owner, name in self.gate.REQUIRED_FIXTURES:
            for status in ('missing', 'skipped', 'failure', 'error', 'duplicate'):
                with self.subTest(owner=owner, name=name, status=status):
                    rows = [row for row in self.rows if row[:2] != (owner, name)]
                    if status == 'duplicate':
                        rows += [(owner, name, ''), (owner, name, '')]
                    elif status != 'missing':
                        rows.append((owner, name, status))
                    self.write(rows)
                    with self.assertRaises(ValueError):
                        self.gate.verify(self.root)

    def test_missing_terminal_class_or_unknown_skip_is_rejected(self):
        for owner in self.gate.REQUIRED_TERMINAL_CLASSES:
            with self.subTest(owner=owner):
                self.write([row for row in self.rows if row[0] != owner])
                with self.assertRaisesRegex(ValueError, 'TERMINAL_REQUIRED_CLASS'):
                    self.gate.verify(self.root)
        self.write(self.rows + [('unknown.Class', 'unexpectedSkip', 'skipped')])
        with self.assertRaisesRegex(ValueError, 'UNEXPECTED_SKIP'):
            self.gate.verify(self.root)

    def test_missing_reports_and_forged_counts_or_hidden_failures_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'JUNIT_MISSING'):
            self.gate.verify(self.root)
        for override in ({'tests': 9999}, {'skipped': 0}, {'failures': 1}):
            self.write(self.rows, override)
            with self.assertRaisesRegex(ValueError, 'JUNIT_COUNTS'):
                self.gate.verify(self.root)
        self.write(self.rows + [('other.Class', 'hiddenFailure', 'failure')], {'failures': 0})
        with self.assertRaisesRegex(ValueError, 'JUNIT_COUNTS'):
            self.gate.verify(self.root)


class TerminalPackageIdentity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('terminal_package', Path(__file__).resolve().parents[1] / 'ci/terminal-package.py')
        cls.gate = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.gate)

    def test_exact_branch_commit_and_attempt_are_required(self):
        env = dict(GITHUB_SHA='a' * 40, GITHUB_REPOSITORY='ym2025szz/DSHA-prerelase',
                   GITHUB_REF='refs/heads/Sunset-zhongdua-G', GITHUB_RUN_ID='123',
                   GITHUB_RUN_ATTEMPT='2', GITHUB_RUN_NUMBER='8')
        self.assertEqual(self.gate.identity(env)['runAttempt'], '2')
        for key, value in (('GITHUB_SHA', 'short'), ('GITHUB_REF', 'refs/heads/Sunset-3090-Q'),
                           ('GITHUB_REPOSITORY', 'other/repo'), ('GITHUB_RUN_ATTEMPT', '0')):
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, 'IDENTITY'):
                self.gate.identity({**env, key: value})

    def test_package_version_abi_sdk_and_non_debuggable_match_current_source(self):
        gradle = ('applicationId "dsh.android.client"\n'
                  "applicationIdSuffix '.SunsetRNE2'\n"
                  'versionCode 197\nversionName "current"\n')
        badging = ("package: name='dsh.android.client.SunsetRNE2' versionCode='197' versionName='current-SunsetTest'\n"
                   "sdkVersion:'30'\ntargetSdkVersion:'37'\nnative-code: 'arm64-v8a'\n")
        self.assertEqual(self.gate.validate_badging(badging, gradle)['versionCode'], 197)
        # aapt2 版本间最低 API 的键名不同；两种拼写都必须精确绑定 30，target 仍精确 37。
        for spelling in ("sdkVersion:'30'", "minSdkVersion:'30'"):
            parsed = self.gate.validate_badging(
                badging.replace("sdkVersion:'30'", spelling), gradle)
            self.assertEqual((parsed['minSdk'], parsed['targetSdk']), (30, 37))
        for wrong in (badging.replace("versionCode='197'", "versionCode='196'"),
                      badging.replace(".SunsetRNE2", ".SunsetRNE"),
                      badging.replace('current-SunsetTest', 'current'),
                      badging.replace('arm64-v8a', 'x86_64'),
                      badging.replace("sdkVersion:'30'", "sdkVersion:'23'"),
                      badging.replace("sdkVersion:'30'", "minSdkVersion:'23'"),
                      badging.replace("targetSdkVersion:'37'", "targetSdkVersion:'36'"),
                      badging.replace("sdkVersion:'30'\n", ""),
                      badging + 'application-debuggable\n'):
            with self.subTest(badging=wrong), self.assertRaises(ValueError):
                self.gate.validate_badging(wrong, gradle)
        # 后缀缺失、重复或非法时不得猜测测试包身份。
        for bad_gradle in (gradle.replace("applicationIdSuffix '.SunsetRNE2'\n", ''),
                           gradle + "applicationIdSuffix '.OtherRNE'\n",
                           gradle.replace('.SunsetRNE2', '.2'),
                           gradle.replace('.SunsetRNE2', '.Bad-Suffix')):
            with self.subTest(gradle=bad_gradle), self.assertRaises(ValueError):
                self.gate.validate_badging(badging, bad_gradle)

    def test_public_certificate_matches_apk_and_production_signature_is_rejected(self):
        der = b'public certificate fixture'
        digest = hashlib.sha256(der).hexdigest()
        pem = '-----BEGIN CERTIFICATE-----\n' + base64.b64encode(der).decode() + '\n-----END CERTIFICATE-----\n'
        signature = 'Signer #1 certificate SHA-256 digest: ' + digest + '\n'
        self.assertEqual(self.gate.validate_certificate(signature, pem), digest)
        with self.assertRaisesRegex(ValueError, 'PRODUCTION_CERTIFICATE'):
            self.gate.validate_certificate(signature.replace(digest, self.gate.PRODUCTION_CERT), pem)
        with self.assertRaisesRegex(ValueError, 'MISMATCH'):
            self.gate.validate_certificate(signature.replace(digest, 'a' * 64), pem)
        with self.assertRaisesRegex(ValueError, 'APK_CERTIFICATE'):
            self.gate.validate_certificate(signature + signature, pem)


class TerminalPackageReceipt(unittest.TestCase):
    def test_receipt_binds_bytes_inputs_tests_and_attempt_then_rejects_tampering(self):
        spec = importlib.util.spec_from_file_location('terminal_receipt_test', Path(__file__).resolve().parents[1] / 'ci/terminal-package.py')
        helper = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(helper)
        scope_spec = importlib.util.spec_from_file_location('terminal_receipt_scope', Path(__file__).with_name('verify-terminal-test-scope.py'))
        scope = importlib.util.module_from_spec(scope_spec)
        scope_spec.loader.exec_module(scope)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'out'
            out.mkdir()
            (root / 'app/src/main/assets').mkdir(parents=True)
            (root / 'app/build.gradle').write_text(
                'applicationId "dsh.android.client"\n'
                "applicationIdSuffix '.SunsetRNE2'\n"
                'versionCode 197\nversionName "current"\n')
            (root / 'app/src/main/assets/runtime-descriptor.json').write_text(json.dumps({'runtimeId': 'b' * 64}))
            (root / 'app/src/main/assets/dsh-runtime.inputs.json').write_text(json.dumps({'archive_sha256': 'c' * 64}))
            reports = root / 'app/build/test-results/testStandardDebugUnitTest'
            reports.mkdir(parents=True)
            rows = [*scope.REQUIRED_FIXTURES, *((owner, 'testCase') for owner in scope.REQUIRED_TERMINAL_CLASSES)]
            suite = ET.Element('testsuite', tests=str(len(rows)), failures='0', errors='0', skipped='0')
            for owner, name in rows:
                ET.SubElement(suite, 'testcase', classname=owner, name=name)
            ET.ElementTree(suite).write(reports / 'TEST-all.xml')
            tests = scope.verify(reports)
            (out / 'full-unit-tests.json').write_text(json.dumps(tests))
            env = dict(GITHUB_SHA='a' * 40, GITHUB_REPOSITORY='ym2025szz/DSHA-prerelase',
                       GITHUB_REF='refs/heads/Sunset-zhongdua-G', GITHUB_RUN_ID='123',
                       GITHUB_RUN_ATTEMPT='2', GITHUB_RUN_NUMBER='8', SIGNING_KEY_SOURCE='ephemeral')
            inputs = dict(**helper.identity(env), archiveSha256='d' * 64, archiveFormat='source-archive')
            (out / 'fixed-inputs.json').write_text(json.dumps(inputs))
            (out / 'apk-badging.txt').write_text("package: name='dsh.android.client.SunsetRNE2' versionCode='197' versionName='current-SunsetTest'\nsdkVersion:'30'\ntargetSdkVersion:'37'\nnative-code: 'arm64-v8a'\n")
            der = b'public certificate fixture'
            cert = hashlib.sha256(der).hexdigest()
            (out / 'apk-signature.txt').write_text('Signer #1 certificate SHA-256 digest: ' + cert + '\n')
            (out / 'terminal-test-cert.pem').write_text('-----BEGIN CERTIFICATE-----\n' + base64.b64encode(der).decode() + '\n-----END CERTIFICATE-----\n')
            apk = out / 'dsha-terminal-aaaaaaaa.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('AndroidManifest.xml', b'test manifest')
            with mock.patch.object(helper, 'ROOT', root), mock.patch.object(helper, 'module', return_value=scope), mock.patch('builtins.print'):
                helper.package_receipt(apk, out, env)
                receipt = json.loads((out / 'build-info.json').read_text())
                self.assertEqual(receipt['runAttempt'], '2')
                self.assertEqual(receipt['apkSha256'], helper.sha(apk))
                self.assertEqual(receipt['certificateSha256'], cert)
                self.assertEqual(len((out / 'SHA256SUMS').read_text().splitlines()), 5)
                inputs['runAttempt'] = '1'
                (out / 'fixed-inputs.json').write_text(json.dumps(inputs))
                with self.assertRaisesRegex(ValueError, 'INPUT_RECEIPT'):
                    helper.package_receipt(apk, out, env)
                inputs['runAttempt'] = '2'
                (out / 'fixed-inputs.json').write_text(json.dumps(inputs))
                tests['mode'] = 'source-only'
                (out / 'full-unit-tests.json').write_text(json.dumps(tests))
                with self.assertRaisesRegex(ValueError, 'TEST_RECEIPT'):
                    helper.package_receipt(apk, out, env)
                with zipfile.ZipFile(apk, 'a') as archive:
                    archive.writestr('assets/forbidden.jks', b'not a real key')
                with self.assertRaisesRegex(ValueError, 'APK_PRIVATE_KEY'):
                    helper.package_receipt(apk, out, env)


class TerminalWorkflowContract(unittest.TestCase):
    def setUp(self):
        self.root = Path(__file__).resolve().parents[1]
        self.workflow = (self.root / '.github/workflows/terminal-regression.yml').read_text()

    def step(self, name):
        marker = '      - name: ' + name + '\n'
        self.assertEqual(self.workflow.count(marker), 1)
        return marker + self.workflow.split(marker, 1)[1].split('\n      - ', 1)[0]

    def test_push_binding_standard_only_and_read_only_permissions(self):
        self.assertIn('branches: [Sunset-zhongdua-G]', self.workflow)
        self.assertIn("github.ref == 'refs/heads/Sunset-zhongdua-G'", self.workflow)
        self.assertEqual(re.findall(r'contents:\s*(\w+)', self.workflow), ['read'])
        self.assertNotRegex(self.workflow, r':app:(?:test|lint|assemble)Low')
        self.assertNotIn('publish_history', self.workflow)
        self.assertNotIn('gh release', self.workflow)
        self.assertNotRegex(self.workflow, r'(?m)^  publish:')
        self.assertEqual(self.workflow.count('persist-credentials: false'), 2)
        self.assertEqual(self.workflow.count('ref: ${{ github.sha }}'), 2)
        fast = (self.root / '.github/workflows/ci-fast.yml').read_text()
        self.assertIn('      - Sunset-zhongdua-G', fast.split('  pull_request:', 1)[0])

    def test_current_inputs_precede_generation_and_full_unfiltered_tests(self):
        order = ['Restore and record exact original build197 inputs',
                 'Rebuild current managed runtime and descriptor',
                 'Prepare current verified raw and managed fixtures',
                 'Verify runtime patch upgrades and managed recovery consent',
                 'Complete Standard unit tests with fixed inputs',
                 'Verify seven fixtures and terminal test execution',
                 'Lint Standard release', 'Build unsigned Standard test package',
                 'Sign test APK and verify exact identity and contents']
        locations = [self.workflow.index('      - name: ' + name) for name in order]
        self.assertEqual(locations, sorted(locations))
        for name in order:
            self.assertNotRegex(self.step(name), r'\n        (?:if|continue-on-error):')
        unit = self.step('Complete Standard unit tests with fixed inputs')
        self.assertIn('-Pdsha.sourceOnlyTests=false', unit)
        self.assertIn('--rerun-tasks', unit)
        self.assertNotIn('--tests', unit)
        self.assertNotIn(' -x ', unit)
        self.assertNotIn('SUNSET_REPOSITORY_URL:', self.workflow)

    def test_private_keys_never_enter_cache_or_artifact_and_cleanup_always_runs(self):
        self.assertNotIn('actions/cache@', self.workflow)
        for step in self.workflow.split('\n      - '):
            if 'actions/upload-artifact@' in step:
                paths = step.split('path: ', 1)[1].split('if-no-files-found:', 1)[0]
                self.assertNotRegex(paths, r'(?i)\.(?:jks|keystore|p12|pfx)\b')
                self.assertNotIn('RUNNER_TEMP', paths)
                self.assertNotIn('out/*', paths)
        cleanup = self.step('Remove test private key')
        self.assertIn('if: always()', cleanup)
        self.assertIn('expected.resolve().parent != parent', cleanup)
        self.assertIn('expected.unlink(missing_ok=True)', cleanup)
        self.assertIn('::add-mask::', self.workflow)
        self.assertIn('--ks-pass env:TERMINAL_TEST_STORE_PASSWORD', self.workflow)

    def test_single_apk_and_failure_reports_have_distinct_uploads(self):
        apk = self.step('Upload signed APK without archiving')
        self.assertIn('archive: false', apk)
        self.assertIn('path: ${{ steps.sign.outputs.apk }}', apk)
        self.assertNotIn('          name:', apk)
        for name in ('Upload source reports even on failure', 'Upload test reports even on failure'):
            self.assertIn('if: always()', self.step(name))
        evidence = self.step('Upload public package evidence')
        self.assertIn('out/fixed-inputs.json', evidence)
        self.assertIn('out/full-unit-tests.json', evidence)
        self.assertIn('out/terminal-test-cert.pem', evidence)
        self.assertIn('out/build-info.json', evidence)


if __name__=='__main__':unittest.main()
