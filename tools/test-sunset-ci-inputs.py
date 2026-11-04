#!/usr/bin/env python3
"""验证 CI 输入必须成对选择，不能因缺配置而跳过 APK。"""
import importlib.util
import json
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location("sunset_inputs", Path(__file__).with_name("sunset-ci-inputs.py"))
inputs = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inputs)


class SunsetInputsTests(unittest.TestCase):
    def test_empty_configuration_uses_pinned_private_source_archive(self):
        result = inputs.resolve({})
        lock = json.loads((inputs.ROOT / "ci/sunset-inputs.lock.json").read_text(encoding="utf-8"))
        self.assertEqual(lock["schema"], 1)
        self.assertEqual(lock["release"], "v0.2.1-rc.3-20261025.2000")
        self.assertEqual(lock["asset"], "DSHA-prerelase-0.2.1-rc.3-20261025.2000-source.zip")
        for key in ("url", "sha256", "format"):
            self.assertEqual(result[key], lock[key])
        self.assertEqual(result["source"], "source-lock")
        self.assertEqual(result["format"], "source-archive")
        self.assertEqual(result["url"], "https://api.github.com/repos/ym2025szz/DSHA-prerelase/releases/assets/673911569")
        self.assertEqual(result["sha256"], "b10df2d3b3eeccfd5aee61aba1c8ed116ad1e92a5258f2831f7fcd4df61f6241")

    def test_dispatch_pair_wins_as_one_unit(self):
        result = inputs.resolve({"SUNSET_DISPATCH_URL": "https://example.invalid/new.zip",
                                 "SUNSET_DISPATCH_SHA": "a" * 64,
                                 "SUNSET_REPOSITORY_URL": "https://example.invalid/old.zip",
                                 "SUNSET_REPOSITORY_SHA": "b" * 64})
        self.assertEqual(result["source"], "workflow-dispatch")
        self.assertEqual(result["sha256"], "a" * 64)
        self.assertEqual(result["format"], "bundle")

    def test_partial_pair_fails_even_when_fallback_exists(self):
        for prefix in ("SUNSET_DISPATCH", "SUNSET_REPOSITORY"):
            for suffix, value in (("URL", "https://example.invalid/inputs.zip"), ("SHA", "a" * 64)):
                with self.subTest(prefix=prefix, suffix=suffix):
                    env = {prefix + "_" + suffix: value}
                    if prefix == "SUNSET_DISPATCH":
                        env.update(SUNSET_REPOSITORY_URL="https://example.invalid/fallback.zip",
                                   SUNSET_REPOSITORY_SHA="b" * 64)
                    with self.assertRaisesRegex(ValueError, "SUNSET_INPUT_PAIR_REQUIRED"):
                        inputs.resolve(env)

    def test_repository_pair_and_explicit_format_are_used(self):
        result = inputs.resolve({"SUNSET_REPOSITORY_URL": "https://example.invalid/source.zip",
                                 "SUNSET_REPOSITORY_SHA": "b" * 64,
                                 "SUNSET_REPOSITORY_FORMAT": "source-archive"})
        self.assertEqual(result["source"], "repository-variables")
        self.assertEqual(result["format"], "source-archive")

    def test_invalid_inputs_never_reach_github_output(self):
        base = {"SUNSET_DISPATCH_URL": "https://example.invalid/inputs.zip", "SUNSET_DISPATCH_SHA": "a" * 64}
        for key, value in (("URL", "http://example.invalid/inputs.zip"),
                           ("URL", "https://user:secret@example.invalid/inputs.zip"),
                           ("URL", "https://example.invalid/inputs.zip\nextra=1"),
                           ("SHA", "wrong"), ("FORMAT", "guess")):
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                inputs.resolve(dict(base, **{"SUNSET_DISPATCH_" + key: value}))


if __name__ == "__main__":
    unittest.main()
