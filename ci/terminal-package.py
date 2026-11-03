#!/usr/bin/env python3
"""记录已核验固定输入和终端测试包身份；仅写本地回执，不发布或安装。"""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "ym2025szz/DSHA-prerelase"
BRANCH = "Sunset-zhongdua-G"
WORKFLOW = ".github/workflows/terminal-regression.yml"
PRODUCTION_CERT = "e7e3a31a75946f2669194c972b3dd0c9aea3fc7c50a8b885d2dee710b22a53f5"


def sha(path):
    value = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "tools" / filename)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def identity(environment):
    commit = environment.get("GITHUB_SHA", "")
    if not re.fullmatch(r"[a-f0-9]{40}", commit) \
            or environment.get("GITHUB_REPOSITORY") != REPOSITORY \
            or environment.get("GITHUB_REF") != "refs/heads/" + BRANCH:
        raise ValueError("TERMINAL_BUILD_IDENTITY")
    for key in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_RUN_NUMBER"):
        if not re.fullmatch(r"[1-9][0-9]*", environment.get(key, "")):
            raise ValueError("TERMINAL_RUN_IDENTITY")
    return dict(repository=REPOSITORY, branch=BRANCH, workflow=WORKFLOW, commit=commit,
                runId=environment["GITHUB_RUN_ID"], runAttempt=environment["GITHUB_RUN_ATTEMPT"],
                runNumber=environment["GITHUB_RUN_NUMBER"])


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def input_receipt(output, environment):
    assets = module("terminal_ci_assets", "ci-assets.py")
    assets.verify_sources()
    archive_sha = environment.get("ASSET_SHA", "")
    archive_format = environment.get("ASSET_FORMAT", "")
    if not re.fullmatch(r"[a-f0-9]{64}", archive_sha) or archive_format not in ("bundle", "source-archive"):
        raise ValueError("TERMINAL_INPUT_IDENTITY")
    rows = [{"path": name, "bytes": (ROOT / name).stat().st_size, "sha256": sha(ROOT / name)}
            for name in sorted(assets.allowed())]
    value = dict(schema=1, **identity(environment), archiveSha256=archive_sha,
                 archiveFormat=archive_format, inputSource=environment.get("ASSET_SOURCE", "unknown"),
                 originalInputs=rows)
    write_json(output, value)
    print(json.dumps(value))


def validate_badging(text, gradle):
    # aapt2 不同版本对最低 API 的打印键名不一致（sdkVersion / minSdkVersion）；
    # 两者都只接受精确 30，且必须同时有精确 targetSdkVersion 37，不降低约束。
    text = text.replace("\r\n", "\n")
    source = {}
    for field, expression in (("packageName", r'applicationId\s+"([^"]+)"'),
                              ("versionName", r'versionName\s+"([^"]+)"'),
                              ("versionCode", r'versionCode\s+(\d+)')):
        matches = re.findall(expression, gradle)
        if len(matches) != 1:
            raise ValueError("TERMINAL_SOURCE_VERSION")
        source[field] = matches[0]
    # 测试包后缀必须从当前 Gradle 身份读取：正式包名保持不变，Sunset 构建靠唯一后缀独立共存。
    suffixes = set(re.findall(r"applicationIdSuffix\s+'([^']+)'", gradle))
    if len(suffixes) != 1:
        raise ValueError("TERMINAL_SOURCE_PACKAGE_SUFFIX")
    suffix = suffixes.pop()
    if not re.fullmatch(r"\.[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*", suffix):
        raise ValueError("TERMINAL_SOURCE_PACKAGE_SUFFIX")
    expected = {**source, "packageName": source["packageName"] + suffix,
                "versionName": source["versionName"] + "-SunsetTest"}
    package = re.findall(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", text, re.M)
    if len(package) != 1 or package[0] != (expected["packageName"], expected["versionCode"], expected["versionName"]):
        raise ValueError("TERMINAL_APK_VERSION")
    if re.search(r"^application-debuggable(?:\s|$)", text, re.M):
        raise ValueError("TERMINAL_APK_DEBUGGABLE")
    if re.findall(r"^native-code:\s*(.+)$", text, re.M) != ["'arm64-v8a'"]:
        raise ValueError("TERMINAL_APK_ABI")
    minimum = re.findall(r"^(?:minSdkVersion|sdkVersion):'([^']*)'$", text, re.M)
    target = re.findall(r"^targetSdkVersion:'([^']*)'$", text, re.M)
    if minimum != ["30"] or target != ["37"]:
        observed = [line for line in text.splitlines() if "dkVersion" in line][:8]
        raise ValueError("TERMINAL_APK_SDK: " + " | ".join(observed))
    return {**expected, "versionCode": int(expected["versionCode"]), "debuggable": False,
            "abis": ["arm64-v8a"], "minSdk": 30, "targetSdk": 37,
            "badgingMinSdkKey": "minSdkVersion" if "minSdkVersion:'30'" in text else "sdkVersion"}


def validate_certificate(signature, pem):
    digests = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([a-fA-F0-9]+)$", signature, re.M)
    if len(digests) != 1 or not re.fullmatch(r"[a-fA-F0-9]{64}", digests[0]):
        raise ValueError("TERMINAL_APK_CERTIFICATE")
    cert = digests[0].lower()
    if cert == PRODUCTION_CERT:
        raise ValueError("TERMINAL_PRODUCTION_CERTIFICATE_FORBIDDEN")
    matches = re.findall(r"-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\s]+)-----END CERTIFICATE-----", pem)
    if len(matches) != 1:
        raise ValueError("TERMINAL_PUBLIC_CERTIFICATE")
    der = base64.b64decode(re.sub(r"\s", "", matches[0]), validate=True)
    if hashlib.sha256(der).hexdigest() != cert:
        raise ValueError("TERMINAL_PUBLIC_CERTIFICATE_MISMATCH")
    return cert


def package_receipt(apk, out, environment):
    build = identity(environment)
    if apk.name != "dsha-terminal-" + build["commit"][:8] + ".apk" or apk.is_symlink() or not apk.is_file():
        raise ValueError("TERMINAL_APK_FILENAME")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("TERMINAL_APK_DUPLICATE_MEMBER")
        if any(name.lower().endswith((".jks", ".keystore", ".p12", ".pfx")) for name in names):
            raise ValueError("TERMINAL_APK_PRIVATE_KEY")
    package = validate_badging((out / "apk-badging.txt").read_text(),
                               (ROOT / "app/build.gradle").read_text())
    cert = validate_certificate((out / "apk-signature.txt").read_text(),
                                (out / "terminal-test-cert.pem").read_text())
    tests = json.loads((out / "full-unit-tests.json").read_text())
    scope = module("terminal_test_scope", "verify-terminal-test-scope.py")
    # 再从本次 XML 核验，不仅相信先前保存的 JSON 摘要。
    if tests != scope.verify(ROOT / "app/build/test-results/testStandardDebugUnitTest"):
        raise ValueError("TERMINAL_TEST_RECEIPT")
    inputs = json.loads((out / "fixed-inputs.json").read_text())
    if any(inputs.get(key) != value for key, value in build.items()):
        raise ValueError("TERMINAL_INPUT_RECEIPT")
    value = dict(schema=1, kind="terminal-regression-test-apk", **build, **package,
                 flavors=["standard"], apkFile=apk.name, apkBytes=apk.stat().st_size,
                 apkSha256=sha(apk), certificateSha256=cert, signingKeySource=environment.get("SIGNING_KEY_SOURCE"),
                 assetBundleSha256=inputs["archiveSha256"], inputArchiveFormat=inputs["archiveFormat"],
                 fixedInputsSha256=sha(out / "fixed-inputs.json"),
                 runtimeId=json.loads((ROOT / "app/src/main/assets/runtime-descriptor.json").read_text())["runtimeId"],
                 rebuiltRuntimeSha256=json.loads((ROOT / "app/src/main/assets/dsh-runtime.inputs.json").read_text())["archive_sha256"],
                 unitTests=tests, note="TEST ONLY - NOT FOR RELEASE; no installation performed")
    write_json(out / "build-info.json", value)
    files = [apk, out / "terminal-test-cert.pem", out / "build-info.json",
             out / "fixed-inputs.json", out / "full-unit-tests.json"]
    (out / "SHA256SUMS").write_text("".join(f"{sha(path)}  {path.name}\n" for path in files), encoding="utf-8")
    print(json.dumps(value, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("inputs", "package"))
    parser.add_argument("--out", type=Path, default=ROOT / "out")
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    if args.mode == "inputs":
        input_receipt(args.out / "fixed-inputs.json", os.environ)
    else:
        if args.apk is None:
            parser.error("package requires --apk")
        package_receipt(args.apk, args.out, os.environ)


if __name__ == "__main__":
    main()
