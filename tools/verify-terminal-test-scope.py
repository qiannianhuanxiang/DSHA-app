#!/usr/bin/env python3
"""核验 Standard 全量 XML、七项真实外部夹具及本轮终端类；不把跳过写成通过。"""
import argparse
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PREFIX = "com.deepseekharness.app."
REQUIRED_FIXTURES = (
    (PREFIX + "runtime.AdbWheelBundleTest",
     "realBundleRebuildsOnlyOmittedEntriesAndPreservesRestoredWheel"),
    *[(PREFIX + "runtime.RuntimeToolsRegistryAndroidTest", name) for name in (
        "completeProductionChainAppliesAllSpecializedStagesAndIsRepeatable",
        "lanRetirementRejectsModuleDriftAndPreservesOriginalBytes",
        "lanRetirementRejectsUnknownVersionAndContractDrift",
        "oldLanExactReplacementAndIdempotenceRemainStrict",
        "previousRuntimeGenerationCannotReceiveCurrentOverlayInPlace")],
    (PREFIX + "util.ManagedPatchRegistryIntegrationTest",
     "everyActiveModuleRepairsAsAWholeAndRetiredUiNeverRuns"),
)
# 这是 Standard 中显式不适用的边界用例，不是运行 Low flavor。
ALLOWED_SKIPS = frozenset({
    (PREFIX + "vscreen.VirtualScreenLowBoundaryTest",
     "lowFlavorRemainsUnsupportedEvenOnAModernAndroidVersion"),
})
REQUIRED_TERMINAL_CLASSES = (
    PREFIX + "core.TerminalSessionOwnerTest",
    PREFIX + "core.TerminalRuntimeProtectionTest",
    PREFIX + "core.TerminalPtyThreadContractTest",
    PREFIX + "runtime.TerminalRuntimeTest",
    PREFIX + "util.TerminalSessionTest",
    PREFIX + "util.TerminalReadyTest",
    PREFIX + "util.TerminalTabsTest",
    PREFIX + "util.TerminalWorkspacePolicyTest",
    PREFIX + "util.TerminalInputTest",
    PREFIX + "ui.TerminalInputContractTest",
    PREFIX + "ui.TerminalNavigationTest",
    PREFIX + "ui.TerminalFloatingMenuTest",
    PREFIX + "ui.TerminalActivityLifecycleTest",
)


def verify(reports, terminal_classes=REQUIRED_TERMINAL_CLASSES):
    files = sorted(Path(reports).glob("TEST-*.xml"))
    if not files:
        raise ValueError("TERMINAL_JUNIT_MISSING")
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    cases = {}
    skipped = []
    for path in files:
        if path.is_symlink() or not path.is_file():
            raise ValueError("TERMINAL_JUNIT_NOT_REGULAR")
        suite = ET.parse(path).getroot()
        if suite.tag != "testsuite":
            raise ValueError("TERMINAL_JUNIT_ROOT")
        rows = suite.findall("testcase")
        actual = {"tests": len(rows), **{
            field: sum(row.find(tag) is not None for row in rows)
            for field, tag in (("failures", "failure"), ("errors", "error"),
                               ("skipped", "skipped"))}}
        try:
            declared = {key: int(suite.attrib[key]) for key in counts}
        except (KeyError, ValueError) as error:
            raise ValueError("TERMINAL_JUNIT_COUNTS") from error
        if actual != declared:
            raise ValueError("TERMINAL_JUNIT_COUNTS: " + path.name)
        for key in counts:
            counts[key] += actual[key]
        for row in rows:
            identity = (row.get("classname"), row.get("name"))
            if not all(identity) or identity in cases:
                raise ValueError("TERMINAL_JUNIT_DUPLICATE_OR_UNNAMED")
            cases[identity] = row
            if row.find("skipped") is not None:
                skipped.append(identity)
    if not counts["tests"] or counts["failures"] or counts["errors"]:
        raise ValueError("TERMINAL_JUNIT_FAILED: " + json.dumps(counts))
    unexpected = set(skipped) - ALLOWED_SKIPS
    if unexpected:
        raise ValueError("TERMINAL_JUNIT_UNEXPECTED_SKIP: " + repr(sorted(unexpected)))
    fixture_rows = []
    for identity in REQUIRED_FIXTURES:
        row = cases.get(identity)
        if row is None or any(row.find(tag) is not None
                              for tag in ("skipped", "failure", "error")):
            raise ValueError("TERMINAL_REQUIRED_FIXTURE: " + ".".join(identity))
        fixture_rows.append(dict(classname=identity[0], name=identity[1], status="passed"))
    terminal_rows = []
    for owner in terminal_classes:
        methods = sorted(name for (classname, name), row in cases.items() if classname == owner)
        if not methods:
            raise ValueError("TERMINAL_REQUIRED_CLASS: " + owner)
        if any((owner, name) in skipped for name in methods):
            raise ValueError("TERMINAL_REQUIRED_CLASS_SKIPPED: " + owner)
        terminal_rows.append(dict(classname=owner, tests=len(methods), methods=methods))
    counts["requiredFixtureTestsPassed"] = len(fixture_rows)
    counts["requiredTerminalClassesPassed"] = len(terminal_rows)
    return dict(schema=1, mode="full", selection="unfiltered", flavors={"Standard": counts},
                requiredFixtures=fixture_rows, requiredTerminalClasses=terminal_rows,
                allowedPlatformSkips=[dict(classname=owner, name=name) for owner, name in skipped])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reports", type=Path,
                        default=ROOT / "app/build/test-results/testStandardDebugUnitTest")
    parser.add_argument("--output", type=Path, default=ROOT / "out/full-unit-tests.json")
    args = parser.parse_args()
    result = verify(args.reports)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write("### Standard 全量完整输入测试\n\n")
            summary.write("- " + ", ".join(f"{key}={value}" for key, value
                                           in result["flavors"]["Standard"].items()) + "\n")
            summary.write("七项外部夹具逐方法通过，终端类逐类执行；平台不适用跳过单列，未运行 Low。\n")


if __name__ == "__main__":
    main()
