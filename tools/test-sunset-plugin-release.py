#!/usr/bin/env python3
"""纯临时 Git 仓库回归：历史、提交/运行绑定与只创建一次的预发布合同。"""
import copy
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("sunset_plugin_release", Path(__file__).with_name("sunset-plugin-release.py"))
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class HistoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="sunset-plugin-history-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "repo"
        self.root.mkdir()
        self.git_env = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
                            GIT_TERMINAL_PROMPT="0", GIT_AUTHOR_NAME="Sunset fixture",
                            GIT_AUTHOR_EMAIL="fixture@example.invalid", GIT_COMMITTER_NAME="Sunset fixture",
                            GIT_COMMITTER_EMAIL="fixture@example.invalid")
        self.git("init", "-q", "--initial-branch=" + release.BRANCH)
        (self.root / "tracked.txt").write_text("fixture\n", encoding="utf-8")
        self.git("add", "tracked.txt")
        self.base = self.commit("chore: 历史基线", "2020-01-01T00:00:00+00:00")
        self.git("tag", "baseline")
        self.first = self.commit("refactor: 插件职责拆分\n\n保留未知文件。", "2023-01-01T00:00:00+00:00")
        self.tip = self.commit("test: 完整回归", "2021-01-01T00:00:00+00:00")
        self.git("update-ref", release.BRANCH_REF, self.tip)
        self.env = dict(GITHUB_REPOSITORY=release.REPOSITORY, SUNSET_REPOSITORY_PRIVATE="true",
                        GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_REF="refs/heads/" + release.BRANCH,
                        GITHUB_WORKFLOW_REF=f"{release.REPOSITORY}/{release.WORKFLOW}@refs/heads/{release.BRANCH}",
                        GITHUB_WORKFLOW_SHA=self.tip, GITHUB_SHA=self.tip, GITHUB_RUN_ID="12345",
                        GITHUB_RUN_ATTEMPT="1")

    def git(self, *args, date=None, cwd=None):
        environment = dict(self.git_env)
        if date:
            environment.update(GIT_AUTHOR_DATE=date, GIT_COMMITTER_DATE=date)
        return subprocess.check_output(["git", "-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false",
                                        "-c", "core.hooksPath=" + os.devnull, *args],
                                       cwd=cwd or self.root, env=environment, text=True,
                                       encoding="utf-8", stderr=subprocess.PIPE).strip()

    def commit(self, message, date="2022-01-01T00:00:00+00:00"):
        self.git("commit", "-q", "--allow-empty", "-m", message, date=date)
        return self.git("rev-parse", "HEAD")

    def select_tip(self, tip):
        self.tip = tip
        self.env.update(GITHUB_SHA=tip, GITHUB_WORKFLOW_SHA=tip)
        self.git("update-ref", release.BRANCH_REF, tip)

    def plan(self, base="baseline", base_sha=None, env=None):
        return release.prepare(self.root, env or self.env, base, base_sha if base_sha is not None else self.base)

    def needs(self):
        outputs = dict(commit=self.tip, run_id=self.env["GITHUB_RUN_ID"], run_attempt=self.env["GITHUB_RUN_ATTEMPT"])
        return dict(source=dict(result="success", outputs=dict(outputs, history_base_sha=self.base)),
                    apk=dict(result="success", outputs=dict(outputs, apk_artifact_id="9876", evidence_artifact_id="9877")))

    def report(self):
        return release.history(self.root, self.plan(), self.needs())

    def test_tag_and_full_sha_resolve_to_same_baseline_without_mutation(self):
        before = self.git("show-ref")
        for base in ("baseline", "refs/tags/baseline", self.base):
            with self.subTest(base=base):
                plan = self.plan(base)
                self.assertEqual(plan["commit"], self.tip)
                self.assertEqual(plan["baseline"]["commit"], self.base)
        self.assertEqual(self.git("show-ref"), before)
        self.assertEqual(self.git("status", "--porcelain"), "")

    def test_annotated_tag_peels_to_pinned_commit(self):
        self.git("tag", "-a", "annotated-base", self.base, "-m", "基线")
        self.assertEqual(self.plan("annotated-base")["baseline"]["commit"], self.base)

    def test_missing_baseline_fails_without_fallback(self):
        for base in ("missing-baseline", "0" * 40):
            with self.subTest(base=base), self.assertRaisesRegex(ValueError, "COMMIT_NOT_FOUND"):
                self.plan(base)

    def test_branch_short_sha_revision_and_shell_text_are_not_baselines(self):
        for base in (release.BRANCH, "HEAD", "HEAD~1", self.base[:12], "refs/heads/" + release.BRANCH,
                     "refs/remotes/origin/" + release.BRANCH, "baseline\nnext=value", "--all", "$(touch nope)"):
            with self.subTest(base=base), self.assertRaises(ValueError):
                self.plan(base)
        self.assertFalse((self.root / "nope").exists())

    def test_missing_or_short_pin_is_rejected(self):
        for value in ("", self.base[:12], self.base.upper()):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "BASE_FULL_SHA_REQUIRED"):
                self.plan(base_sha=value)

    def test_moved_baseline_tag_is_rejected(self):
        self.git("tag", "-f", "baseline", self.first)
        with self.assertRaisesRegex(ValueError, "BASE_SHA_MISMATCH"):
            self.plan()

    def test_non_ancestor_baseline_is_rejected(self):
        self.git("checkout", "-q", "--orphan", "unrelated")
        unrelated = self.commit("chore: 不相关历史")
        self.git("tag", "foreign-base")
        self.git("checkout", "-q", release.BRANCH)
        with self.assertRaisesRegex(ValueError, "BASE_NOT_ANCESTOR"):
            self.plan("foreign-base", unrelated)

    def test_checked_out_commit_must_match_full_github_sha(self):
        for value in (self.tip[:12], self.first, self.tip[:12] + "0" * 28):
            env = dict(self.env, GITHUB_SHA=value, GITHUB_WORKFLOW_SHA=value)
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "FULL_COMMIT_REQUIRED|HEAD_COMMIT_MISMATCH"):
                self.plan(env=env)

    def test_current_commit_must_remain_on_sunset_branch(self):
        self.git("update-ref", release.BRANCH_REF, self.base)
        with self.assertRaisesRegex(ValueError, "COMMIT_NOT_ON_BRANCH"):
            self.plan()

    def test_branch_advancing_does_not_change_verified_release_target(self):
        newer = self.commit("chore: 尚未验证的新提交")
        self.git("update-ref", release.BRANCH_REF, newer)
        self.git("checkout", "-q", "--detach", self.tip)
        plan = self.plan()
        self.assertEqual(plan["commit"], self.tip)
        self.assertEqual(plan["branchTip"], newer)
        self.assertNotIn(newer, [row["commit"] for row in release.history(self.root, plan, self.needs())["commits"]])

    def test_changed_tracked_source_is_rejected(self):
        (self.root / "tracked.txt").write_text("unverified edit\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "TRACKED_SOURCE_CHANGED"):
            self.plan()

    def test_shallow_history_is_rejected(self):
        shallow = Path(self.temp.name) / "shallow"
        self.git("clone", "-q", "--depth=1", self.root.as_uri(), str(shallow))
        with self.assertRaisesRegex(ValueError, "FULL_HISTORY_REQUIRED"):
            release.prepare(shallow, self.env, "baseline", self.base)

    def test_empty_history_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "EMPTY_HISTORY"):
            self.plan(self.tip, self.tip)

    def test_existing_target_tag_is_never_reused_or_moved(self):
        tag = self.plan()["tag"]
        for target in (self.base, self.tip):
            self.git("tag", "-f", tag, target)
            with self.subTest(target=target), self.assertRaisesRegex(ValueError, "TAG_EXISTS"):
                self.plan()
            self.assertEqual(self.git("rev-parse", tag), target)

    def test_run_and_attempt_make_new_tags(self):
        first = self.plan()["tag"]
        second = self.plan(env=dict(self.env, GITHUB_RUN_ID="12346"))["tag"]
        third = self.plan(env=dict(self.env, GITHUB_RUN_ATTEMPT="2"))["tag"]
        self.assertEqual(len({first, second, third}), 3)
        self.assertRegex(first, r"^sunset-plugin-[a-f0-9]{12}-r12345-a1$")

    def test_wrong_repository_event_branch_workflow_and_run_are_rejected(self):
        for key, value in (("GITHUB_REPOSITORY", "DSH-APP/DSHA"), ("SUNSET_REPOSITORY_PRIVATE", "false"),
                           ("GITHUB_EVENT_NAME", "push"), ("GITHUB_REF", "refs/tags/baseline"),
                           ("GITHUB_WORKFLOW_REF", "foreign"), ("GITHUB_WORKFLOW_SHA", self.first),
                           ("GITHUB_RUN_ID", "0"), ("GITHUB_RUN_ATTEMPT", "1\nextra=value")):
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.plan(env=dict(self.env, **{key: value}))

    def test_both_jobs_must_succeed_without_skips_or_cancellation(self):
        for name in ("source", "apk"):
            for status in ("failure", "cancelled", "skipped", ""):
                needs = self.needs()
                needs[name]["result"] = status
                with self.subTest(name=name, status=status), self.assertRaisesRegex(ValueError, "JOB_NOT_SUCCESS"):
                    release.history(self.root, self.plan(), needs)
        for needs in (None, {}, {"source": self.needs()["source"]}, dict(self.needs(), foreign={})):
            with self.subTest(needs=needs), self.assertRaisesRegex(ValueError, "REQUIRED_JOBS"):
                release.history(self.root, self.plan(), needs)

    def test_each_job_must_bind_full_sha_same_run_and_same_attempt(self):
        for name in ("source", "apk"):
            for key, value in (("commit", self.tip[:12]), ("commit", self.tip[:12] + "0" * 28),
                               ("commit", self.first), ("run_id", "54321"), ("run_attempt", "2")):
                needs = self.needs()
                needs[name]["outputs"][key] = value
                with self.subTest(name=name, key=key, value=value), self.assertRaisesRegex(ValueError, "JOB_IDENTITY"):
                    release.history(self.root, self.plan(), needs)

    def test_missing_or_foreign_baseline_receipt_is_rejected(self):
        for base in ("", self.first):
            needs = self.needs()
            needs["source"]["outputs"]["history_base_sha"] = base
            with self.subTest(base=base), self.assertRaisesRegex(ValueError, "SOURCE_BASE_MISMATCH"):
                release.history(self.root, self.plan(), needs)

    def test_missing_ambiguous_or_invalid_artifact_id_is_rejected(self):
        for value in ("", "0", "9877", "123/../../other", 9876):
            needs = self.needs()
            needs["apk"]["outputs"]["apk_artifact_id"] = value
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "ARTIFACT_ID"):
                release.history(self.root, self.plan(), needs)

    def test_history_is_old_to_new_by_ancestry_not_commit_clock(self):
        report = self.report()
        self.assertEqual([row["commit"] for row in report["commits"]], [self.first, self.tip])
        self.assertIn("保留未知文件。", report["commits"][0]["message"])
        self.assertEqual(report["commits"][1]["parents"], [self.first])
        self.assertEqual(report["artifacts"]["apk"]["url"],
                         f"https://github.com/{release.REPOSITORY}/actions/runs/12345/artifacts/9876")

    def test_merged_side_history_and_merge_commit_are_not_lost(self):
        previous_tip = self.tip
        self.git("checkout", "-q", "-b", "side", self.base)
        side = self.commit("feat: 支线插件能力")
        self.git("checkout", "-q", release.BRANCH)
        self.git("merge", "-q", "--no-ff", "side", "-m", "merge: 保留两个父提交")
        merge = self.git("rev-parse", "HEAD")
        self.select_tip(merge)
        report = self.report()
        commits = [row["commit"] for row in report["commits"]]
        self.assertEqual(set(commits), {self.first, previous_tip, side, merge})
        self.assertEqual(report["commits"][-1]["parents"], [previous_tip, side])
        self.assertEqual(len(commits), 4)
        self.assertEqual(commits[-1], merge)
        for row in report["commits"]:
            for parent in row["parents"]:
                if parent in commits:
                    self.assertLess(commits.index(parent), commits.index(row["commit"]))

    def test_large_history_preserves_full_attachments_and_fences_message(self):
        report = self.report()
        message = "fix: 数据正文\n```\n@someone\n" + "完整历史" * 30000
        report["commits"][0]["message"] = message
        full, body = release.render(report)
        self.assertIn("````\n" + message + "\n````", full)
        self.assertIn("history.md / history.json", body)
        self.assertLess(len(body.encode("utf-8")), 100000)
        self.assertIn(self.tip, body)
        output = Path(self.temp.name) / "history"
        release.write_history(output, report)
        self.assertEqual(json.loads((output / "history.json").read_text(encoding="utf-8"))["commits"][0]["message"], message)
        self.assertEqual((output / "history.md").read_text(encoding="utf-8"), full)

    def test_existing_output_and_unknown_files_are_preserved(self):
        output = Path(self.temp.name) / "unknown"
        output.mkdir()
        original = output / "history.json"
        original.write_text("unknown owner", encoding="utf-8")
        with self.assertRaises(FileExistsError):
            release.write_history(output, self.report())
        self.assertEqual(original.read_text(encoding="utf-8"), "unknown owner")
        self.assertEqual([path.name for path in output.iterdir()], ["history.json"])

    def publication(self):
        report = self.report()
        output = Path(self.temp.name) / "publication"
        release.write_history(output, report)
        tag_response = dict(ref="refs/tags/" + report["tag"], object=dict(type="commit", sha=self.tip))
        release_response = dict(tag_name=report["tag"], prerelease=True, draft=False,
                                assets=[dict(name=name, state="uploaded", size=(output / name).stat().st_size)
                                        for name in ("history.md", "history.json")])
        return report, output, tag_response, release_response

    def test_publish_creates_only_exact_tag_and_new_prerelease_then_checks_both(self):
        report, output, tag, published = self.publication()
        execute = mock.Mock(side_effect=[json.dumps(tag), "created", json.dumps(tag), json.dumps(published)])
        url = release.publish(report, output, execute)
        self.assertEqual(execute.call_count, 4)
        args, payload = execute.call_args_list[0].args
        self.assertEqual(args, ["api", "--hostname", "github.com", "--method", "POST",
                                f"repos/{release.REPOSITORY}/git/refs", "--input", "-"])
        self.assertEqual(json.loads(payload), dict(ref="refs/tags/" + report["tag"], sha=self.tip))
        create = execute.call_args_list[1].args[0]
        self.assertEqual(create[:3], ["release", "create", report["tag"]])
        self.assertIn("--verify-tag", create)
        self.assertIn("--prerelease", create)
        self.assertIn("--latest=false", create)
        self.assertEqual(create[create.index("--repo") + 1], "github.com/" + release.REPOSITORY)
        self.assertEqual(create[create.index("--target") + 1], self.tip)
        self.assertEqual(create[-2:], [str(output / "history.md"), str(output / "history.json")])
        self.assertNotIn("--clobber", create)
        self.assertEqual(url, f"https://github.com/{release.REPOSITORY}/releases/tag/{report['tag']}")

    def test_remote_tag_race_or_unknown_create_is_not_replayed(self):
        report, output, tag, _ = self.publication()
        for results in ([ValueError("SUNSET_TAG_EXISTS")],
                        [json.dumps(tag), ValueError("SUNSET_REMOTE_OUTCOME_UNKNOWN")]):
            execute = mock.Mock(side_effect=results)
            with self.subTest(count=len(results)), self.assertRaises(ValueError):
                release.publish(report, output, execute)
            self.assertEqual(execute.call_count, len(results))
            self.assertTrue((output / "history.json").is_file())

    def test_wrong_remote_commit_or_missing_history_asset_fails(self):
        report, output, tag, published = self.publication()
        wrong_tag = copy.deepcopy(tag)
        wrong_tag["object"]["sha"] = self.first
        execute = mock.Mock(return_value=json.dumps(wrong_tag))
        with self.assertRaisesRegex(ValueError, "CREATED_TAG_MISMATCH"):
            release.publish(report, output, execute)
        self.assertEqual(execute.call_count, 1)
        for missing in (dict(published, assets=[]), dict(published, prerelease=False)):
            execute = mock.Mock(side_effect=[json.dumps(tag), "created", json.dumps(tag), json.dumps(missing)])
            with self.subTest(missing=missing), self.assertRaisesRegex(ValueError, "ASSETS_INCOMPLETE|RELEASE_MISMATCH"):
                release.publish(report, output, execute)
            self.assertEqual(execute.call_count, 4)


class WorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (Path(__file__).resolve().parents[1] / release.WORKFLOW).read_text(encoding="utf-8")
        cls.source = cls.workflow.split("\n  source:\n", 1)[1].split("\n  apk:\n", 1)[0]
        cls.apk = cls.workflow.split("\n  apk:\n", 1)[1].split("\n  publish:\n", 1)[0]
        cls.publish = cls.workflow.split("\n  publish:\n", 1)[1]

    def test_publish_is_opt_in_and_only_job_with_write_permission(self):
        inputs = self.workflow.split("permissions:\n", 1)[0]
        self.assertIn("branches: [Sunset-3090-Q]", inputs)
        self.assertNotIn("tags:", inputs)
        self.assertRegex(inputs, r"publish_history:\n(?:[^\n]*\n)*?        type: boolean\n        default: false")
        self.assertIn("history_base:", inputs)
        self.assertIn("history_base_sha:", inputs)
        self.assertIn("permissions:\n  contents: read\n", self.workflow)
        self.assertEqual(self.workflow.count("contents: write"), 1)
        self.assertIn("permissions:\n      contents: write\n", self.publish)
        self.assertIn("needs: [source, apk]", self.publish)
        self.assertIn("needs.source.result == 'success' && needs.apk.result == 'success'", self.publish)
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.publish_history", self.publish)
        self.assertIn("github.event.repository.private", self.publish)
        self.assertIn("github.ref == 'refs/heads/Sunset-3090-Q'", self.publish)
        self.assertNotIn("continue-on-error:", self.workflow)

    def test_every_checkout_is_exact_commit_and_all_actions_stay_sha_pinned(self):
        for job in (self.source, self.apk, self.publish):
            self.assertIn("ref: ${{ github.sha }}", job)
            self.assertIn("persist-credentials: false", job)
        self.assertIn("fetch-depth: 0", self.source)
        self.assertIn("fetch-depth: 0", self.publish)
        for action in re.findall(r"uses:\s*([^\s#]+)", self.workflow):
            self.assertRegex(action, r"^[\w.-]+/[\w.-]+@[a-f0-9]{40}$")
        self.assertIn("SUNSET_NEEDS: ${{ toJSON(needs) }}", self.publish)
        self.assertIn("--base-sha \"$HISTORY_BASE_SHA\"", self.publish)
        self.assertIn("--only test-sunset-plugin-release.py", self.source)
        self.assertIn("github.run_id || github.ref", self.workflow)

    def test_existing_standard_full_input_and_signing_gates_remain_required(self):
        self.assertIn("-Pdsha.sourceOnlyTests=true", self.source)
        for gate in ("-Pdsha.sourceOnlyTests=false", "Verify all seven Standard external-fixture tests executed",
                     ":app:lintStandardRelease", ":app:assembleStandardRelease", "Sign with test key and verify identity",
                     "test \"$package\" = \"dsh.android.client.SunsetRNE\"",
                     "test \"$cert\" != \"e7e3a31a75946f2669194c972b3dd0c9aea3fc7c50a8b885d2dee710b22a53f5\"",
                     "python3 tools/verify-dsh-upgrade-apk.py", "python3 tools/verify-recovery-apk.py"):
            with self.subTest(gate=gate):
                self.assertIn(gate, self.apk)
        self.assertNotRegex(self.workflow, r":app:(?:test|lint|assemble)Low")
        self.assertNotIn("--generate-notes", self.publish)


if __name__ == "__main__":
    unittest.main()
