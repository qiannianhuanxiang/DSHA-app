"""检查源码行尾、显式依赖身份与工作流钉住；不扫私有数据或历史日志。"""
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
names = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode("utf-8").split("\0")
errors = []
byte_pinned = {'app/src/main/assets/ubuntu-tools.manifest.json', 'app/src/main/assets/builtin-plugins/dsh-auto-review/package.json', 'app/src/main/assets/builtin-plugins/dsh-device-shell-guide/package.json', 'app/src/main/assets/lan-settings-upstream-contract.json', 'app/src/main/assets/builtin-plugins/dsh-web-mobile/lib/index.js', 'app/src/main/assets/builtin-plugins/dsh-computer-use-android/package.json', 'app/src/main/assets/ubuntu-tools.layout', 'app/src/main/assets/startup-mcp-timing-patch.json', 'app/src/main/assets/managed-runtime-inputs.json'}
byte_pinned.add("tools/mobile-server/index.js")
byte_pinned.add("app/src/main/assets/plugin-manager-policy-patch.json")
byte_pinned.add("app/src/main/assets/plugin-manager.py")
history_fixture_pins = {
    "tools/fixtures/session-interaction-v3/session-interaction-patch.json": "37f53270df43f737ec7c1d38d55cbf7e30e0cd8f21418ca8831b1582da590b37",
    "tools/fixtures/session-interaction-v3/web-integration/session-interaction.js": "df67935e0ab81e23b494119cb7b4c97e2263cd6d01a6cf9069ca6004e6e3a790",
    "tools/fixtures/rc2-upgrade-source/dsh-client-ui-workspace.js": "52765aacb6c7f96327da5d8709d8cbb30dd2957d4744a42da04c18e07fe264e5",
    "tools/fixtures/rc2-upgrade-source/dsh-client-ui-agent-preset.js": "6ecdf173de66fce620e588f10ed1e3b624725febab059fb867beefef9ae4ddb8",
}
# These frozen language sources retain their reviewed bytes. Runtime generators
# use the signed descriptor; native catalogues and notes have explicit CI pins.
language_generator_pins = {
    "tools/fixtures/language-v1-patch.json",
    "tools/i18n/web-translations/ar.json",
    "tools/i18n/web-translations/fr.json",
    "tools/i18n/web-translations/ja.json",
    "tools/i18n/web-translations/ko.json",
    "tools/i18n/web-translations/la.json",
    "tools/i18n/web-translations/pt.json",
    "tools/i18n/web-translations/ru.json",
    "tools/i18n/web-translations/zh-Hant.json",
}
language_source_pins = {
    "docs/releases/build197-changes.ar.md": "a96ff6d4f58886938ada1197aa758da75a19ef378c2347830128ca71eda00030",
    "docs/releases/build197-changes.la.md": "24a72be4658c4874920e25f430bbbfdd912ce7d6deb74b428dd9cd32daabcaaa",
    "docs/releases/build197-changes.pt.md": "9c84cad387a0496aa7a0affa9c8a2ee6617ff5d747236e1ebcce84723cbf8500",
    "tools/i18n/translations-extra/ar.json": "1298ab9b86a2c575137f740eb234e03f3a3b07a8566880420e18ffef873907d2",
    "tools/i18n/translations-extra/fr.json": "1ccdfccf91a7dc1bb885d06d319ae2dcdd5bf746f71e4c44f5de3d73971e5e4d",
    "tools/i18n/translations-extra/ja.json": "4f236e46f4d051ce7ccb86b81b2ce5302fff5cd066aba7be58e4813215343ab4",
    "tools/i18n/translations-extra/ko.json": "42ff3b77af01452c6390511571eaf5448d07efa50fb01e9c1a74f2fbf351eb7d",
    "tools/i18n/translations-extra/pt.json": "2e55c7217e4fed29b93ef53b3a6faed5d2500e1c2310eeedddd9aa291aeafc83",
    "tools/i18n/translations-extra/ru.json": "c48df397e00d4368c0759246b780e47a7cdf04ee0e9cd825a2014e38a98d0c70",
    "tools/i18n/translations/ar.json": "a446080d2b55ecb07ab6df197e4db4aee5f8ad1f46f4c2033fd865729581f27c",
    "tools/i18n/translations/fr.json": "1b2349094a7bddb0cd1bc3d9d34cc1667c850a4b87bd9c008e21b6fa37d14d9e",
    "tools/i18n/translations/ja.json": "2537d0501eab0e67069a746c54883aaee9ba1e7fc46ef075c8b4abd2b3007346",
    "tools/i18n/translations/ko.json": "9ffbd753ee86869255411fa5138f141e64d5287992ea842cde173d1c3fc73c67",
    "tools/i18n/translations/pt.json": "4112972c1d6364a1647b17ab60db866a59489b9e51e5966e81c7d91947d02bd5",
    "tools/i18n/translations/ru.json": "21144819f6611c9decd255cddc7436b9092688d59a9beeef8129488f992444dc",
}
descriptor = json.loads((ROOT / "app/src/main/assets/runtime-descriptor.json").read_text(encoding="utf-8"))
binary = {".so", ".bin", ".jar", ".aar", ".gz", ".tgz", ".zip", ".png", ".jpg", ".webp", ".pdf", ".keystore"}
for name in names:
    path = ROOT / name
    if not path.is_file() or path.suffix in binary or name.startswith('tools/history/') or name in byte_pinned or name in history_fixture_pins or name in language_generator_pins or name in language_source_pins:
        continue
    value = path.read_bytes()
    if b"\0" not in value and b"\r\n" in value:
        errors.append("CRLF: " + name)
for path in (ROOT / ".github/workflows").glob("*.yml"):
    for selected in re.findall(r"uses:\s*([^\s#]+)", path.read_text(encoding="utf-8")):
        if not re.fullmatch(r"[\w.-]+/[\w.-]+@[0-9a-f]{40}", selected):
            errors.append("UNPINNED_ACTION: " + str(path.relative_to(ROOT)))
if "mavenLocal()" in (ROOT / "settings.gradle").read_text(encoding="utf-8"):
    errors.append("UNCONTROLLED_MAVEN_LOCAL")
for path in (ROOT / "app/src/main/java").rglob("*.java"):
    if path.name != "Ids.java" and "[a-f0-9]{8}-" in path.read_text(encoding="utf-8"):
        errors.append("DUPLICATE_UUID_PATTERN: " + path.relative_to(ROOT).as_posix())
history_paths = ["tools/history/_byte-policy." + ext for ext in ("py", "java", "md", "txt", "sh", "xml", "json", "cjs")]
history_paths.append("tools/history/LICENSE")
history_paths.extend(sorted(byte_pinned))
history_paths.extend(sorted(history_fixture_pins))
history_paths.extend(sorted(language_generator_pins))
history_paths.extend(sorted(language_source_pins))
for name, expected in history_fixture_pins.items():
    path = ROOT / name
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        errors.append("HISTORICAL_FIXTURE_BYTES: " + name)
attributes = subprocess.check_output(
    ["git", "check-attr", "-z", "text", "eol", "--", *history_paths], cwd=ROOT
).rstrip(b"\0").split(b"\0")
for at in range(0, len(attributes), 3):
    if attributes[at + 2] != b"unset":
        errors.append("HISTORY_BYTE_NORMALIZATION: " + attributes[at].decode("utf-8"))
for name in sorted(byte_pinned):
    expected = (descriptor["launcherInputs"].get("generator:" + name) if name == "tools/mobile-server/index.js"
                else descriptor["inputs"].get(name.removeprefix("app/src/main/assets/")))
    if not re.fullmatch("[a-f0-9]{64}", expected or "") or hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != expected:
        errors.append("PINNED_BYTE_DIGEST: " + name)
language_pins = dict(language_source_pins)
for name in language_generator_pins:
    language_pins[name] = descriptor["launcherInputs"].get("generator:" + name)
for name, expected in sorted(language_pins.items()):
    path = ROOT / name
    if not re.fullmatch("[a-f0-9]{64}", expected or "") or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        errors.append("PINNED_LANGUAGE_BYTES: " + name)
toolchain = json.loads((ROOT / "ci/toolchain.lock.json").read_text(encoding="utf-8"))
wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
if "gradle-" + toolchain["gradle"] + "-" not in wrapper or "distributionSha256Sum=" not in wrapper:
    errors.append("GRADLE_IDENTITY")
if errors:
    raise SystemExit("\n".join(errors))
print("PASS tracked text LF or verified exact managed byte pins, pinned Actions, explicit Maven/Gradle identities, shared UUID validation and history byte policy")
