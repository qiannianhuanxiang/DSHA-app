# CI 测试包与 GitHub Actions 变更

> 本文件定义 `Sunset-3090-Q` 的 CI 契约：测试包身份、测试签名、APK 产物、固定输入包，以及 2026 年 GitHub Actions 的两项相关变更。
>
> 命名以 [naming.md](naming.md) 为准；分支只用于测试，**不打 tag、不创建 Release、不公开上传**。

## 1. 2026 年 GitHub Actions 相关变更

### 1.1 非压缩 artifact（2026-02-26）

GitHub 于 2026-02-26 支持上传/下载**非 zip** artifact（changelog 关键词：`2026-02-26-github-actions-now-supports-uploading-and-downloading-non-zipped-artifacts`）。此前 `actions/upload-artifact` 会把上传内容自动打成一个 zip；现在可以在 `upload-artifact` 上使用 `archive: false`，让 APK 以**.apk 原文件**形式存储和下载，不再被二次压缩成 `.zip`。

对本分支的意义：

- APK 本身已是压缩包，再 zip 一次既浪费时间又让下载后多一步解压；
- 测试同学需要直接拿到 `.apk` 安装，不能拿到 `artifact.zip` 里再套一个 `apk`；
- 因此 APK 使用 `archive: false` 单文件上传；`SHA256SUMS`、测试证书、`build-info.json`、签名结果及完整测试摘要另用 `archive: true` 归档上传。固定版本 action 的非压缩模式仅接受一个文件，且使用实际文件名作为 artifact 名。

### 1.2 Node 20 退出（2026-09-23）

GitHub 已从 2026-09-23 起移除 Node 20，JavaScript action 统一运行在 Node 24；旧的 `actions/checkout@v4`、`actions/setup-node@v4`、`actions/upload-artifact@v4` 等 Node 20 action 会直接失败。

本仓库现有 `ci-fast.yml` / `ci-package.yml` 仍 pin 在 v4（Node 20）。**2026-10-22 之后这些 workflow 已经不能可靠运行**，因此本分支同时把它们升级到 Node 24 兼容版本：

| action | 新 pin | 版本 |
|---|---|---|
| `actions/checkout` | `3d3c42e5aac5ba805825da76410c181273ba90b1` | v7.0.1 |
| `actions/setup-java` | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` | v6.1.0 |
| `actions/setup-node` | `949feb2413d6458794dcd2491c4babbbce0c15c1` | v7.1.0 |
| `actions/upload-artifact` | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` | v7.0.2 |
| `actions/cache` | `55cc8345863c7cc4c66a329aec7e433d2d1c52a9` | v6.1.0 |

> 版本/提交以 2026-10-22 的 `git ls-remote` 为准；升级时重新核对 SHA，不直接改用浮动 tag。

### 1.3 通用 `Source checks` 不在本分支重复跑

`ci-fast.yml` 的 push 触发器已排除 `Sunset-3090-Q`。原因：该工作流的完整 `--stage fast`
依赖固定输入包（`test-asset-deployment`）、上游夹具（`test-mobile-server-patches`）和
原生 toolchain/raw runtime（`test-vscreen-protocol`、`test-permission-full-access`、
`test-bundled-dsh-version`），在 2026-10-22 的基线上本来就不是全绿；另外
`test-ui-boundaries154`、`test-runtime-session`、`test-core-boundaries` 等既有失败
与本分支无关。本分支由 `sunset-3090-q.yml` 的 curated source 门槛负责；
`ci-fast` 的完整回归留给拥有固定输入的正式 CI/发布流程。

### 1.4 仍然保留的约束

- `ubuntu-24.04` 托管 runner；Java 17、Node 24、Python 3.12 由 `tools/ci-toolchain.py` 从 `ci/toolchain.lock.json` 读取。
- 所有 action 继续按 SHA 固定，注释版本号；不使用 `@main`、`@latest`。
- workflow 只有 `contents: read`，不请求写权限，不创建 Release/tag。
- 不使用 `ACTIONS_ALLOW_USE_UNSECURE_NODE_VERSION` 之类的回退开关。

### 1.5 Linux 依赖校验与本地构建边界

- `gradle/verification-metadata.xml` 必须同时包含 aapt2 的 Windows 与 Linux classifier 校验；只保留 Windows 条目会让 Linux runner 在 `compileStandard...NavigationResources` 处以 `Dependency verification failed` 失败。2026-10-22 已补 `aapt2-9.1.1-14792394-linux.jar` 的 SHA-256。
- 没有固定输入包时可以做源码/单测编译，但不能产出可运行 APK。工作区里从已安装 APK 提取的 `offline-rootfs.bin`、`dsh-runtime.bin` 是已应用补丁的运行产物，不能反向当作源输入；`prepare-standard-assets.py` 会以 dsh 补丁/依赖锁不一致拒绝。
- aarch64 主机上 Google 的 build-tools/aapt2 是 x86_64；`build.sh` 只内置 AIDL shim，未内置 aapt2 仿真。arm64 本地完整打包需要额外的 qemu/aapt2 适配，这不属于 CI 契约；CI 的 x86_64 runner 直接使用 Google aapt2。

## 2. 测试包身份

| 项 | 值 |
|---|---|
| applicationId | `dsh.android.client.SunsetRNE` |
| versionCode | `190` |
| versionName | `0.2.1-rc.2-20261022.2100-dsh0.2.1-rc.2-SunsetTest`（Standard） |
| 可调试 | `false` |
| 签名 | 测试 keystore，证书 SHA-256 ≠ E7E3 |
| 安装 | 只覆盖同包名、同测试签名的旧测试包；不覆盖正式包 |

构建开关：

```bash
./gradlew :app:assembleStandardRelease \
  -Pdsha.sunsetTest=true \
  -Pdsha.unsignedPackage=true
```

- `dsha.sunsetTest=true`：applicationId 加 `.SunsetRNE`，versionName 加 `-SunsetTest`。
- `dsha.unsignedPackage=true`：跳过 E7E3 正式签名，产出 unsigned APK；随后用测试 keystore 签名。
- 不传这两个参数时，行为与基线一致，不会生成测试包身份。

## 3. 测试签名

### 3.1 keystore

| 项 | 值 |
|---|---|
| 文件 | `sunset-test.jks`（PKCS12） |
| alias | `sunset-test` |
| DN | `CN=DSHA Sunset Test (NOT FOR RELEASE), OU=Sunset-3090-Q, O=SunsetRNE, C=CN` |
| 算法 | RSA 3072，3650 天 |
| 口令 | `sunset-test-only`（测试口令，不是秘密；CI 环境变量 `SUNSET_TEST_STORE_PASSWORD` 可覆盖） |
| 证书 | 上传 `sunset-test-cert.pem`；CI 打印 SHA-256 |

### 3.2 来源

优先级：

1. GitHub Actions secret `SUNSET_TEST_KEYSTORE_B64`（base64 的 PKCS12）—— 需要跨 run 覆盖安装时配置；
2. `actions/cache` 缓存的 `sunset-test.jks`（cache key 固定，7 天保留期内可复用）；
3. 本次 run 用 `keytool` 临时生成。

无论来源，CI 都必须：

```bash
apksigner verify --print-certs app-signed.apk
# 断言证书 SHA-256 != e7e3a31a75946f2669194c972b3dd0c9aea3fc7c50a8b885d2dee710b22a53f5
```

测试私钥**不提交 Git**；临时 keystore 可作为 artifact 上传（14 天），仅供本人复用，不得用于任何正式产物。

## 4. Workflow 设计

文件：`.github/workflows/sunset-3090-q.yml`

```yaml
name: Sunset-3090-Q test package
on:
  push:
    branches: [Sunset-3090-Q]
  workflow_dispatch:
    inputs:
      asset_bundle_url: { required: false, type: string }
      asset_bundle_sha256: { required: false, type: string }
permissions:
  contents: read
concurrency:
  group: sunset-3090-q-REF
  cancel-in-progress: true
```

### 4.1 `source` job

1. checkout（`persist-credentials: false`）
2. `python3 tools/ci-toolchain.py --github-output`
3. setup-java（Temurin 17）、setup-node（24）
4. 源码契约检查：

```bash
python3 tools/prepare-runtime-tools.py --check
python3 tools/verify-runtime-descriptor-source.py
python3 tools/generate-third-party-notices.py --check
python3 tools/check-third-party-notices.py
python3 tools/generate-builtin-plugins.py --verify
python3 tools/generate-credential-paths.py --check
python3 tools/prepare-semver-reference.py
python3 tools/format-java-tree.py --check
python3 tools/run-host-tests.py --check-manifest
python3 tools/check-source-hygiene.py
python3 tools/check-ui-i18n.py
python3 tools/check-document-links.py
```

5. 分支宿主回归：只跑与设备桥/consent/shell 直接相关、且不依赖离线输入包或上游夹具的用例：

   ```bash
   python3 tools/run-host-tests.py --stage fast --jdk "$JAVA_HOME" \
     --only test-native-consent-transport.mjs \
     --only test-bridge-token-compat.mjs \
     --only test-bridge-token-transport.py \
     --only test-destructive-guard.mjs \
     --only test-destructive-full-access.mjs \
     --only test-vscreen-anchored.mjs \
     --only test-device-shell-policy.py \
     --only test-credential-paths.py \
     --only test-runtime-descriptor-inputs.py \
     --only test-runtime-descriptor-source.py \
     --only test-plugin-capabilities.py \
     --only test-architecture-boundaries.py
   ```

   完整 `--stage fast` 里的 `test-asset-deployment`、`test-mobile-server-patches` 需要
   离线输入包/上游夹具，`test-dns-compat` 依赖宿主 DNS；这些由 `ci-fast`/package 流程或
   固定输入包覆盖，不在本分支的推送门禁里伪造通过。
6. Android 源码模式单测（不生成离线包）：

```bash
sdkmanager "platforms;android-37.0" "build-tools;36.0.0"
./gradlew :app:testStandardDebugUnitTest :app:testLowDebugUnitTest \
  -Pdsha.sourceOnlyTests=true \
  -x :app:prepareStandardAssets -x :app:prepareRecoveryAssets \
  --no-daemon --max-workers=2
```

该模式仅允许真实 wheel 归档的 1 项、managed runtime 夹具的 4 项在输入不存在时跳过；存在但损坏、摘要或版本不匹配仍然失败。默认 `dsha.sourceOnlyTests=false`，缺失输入直接失败。报告同时列出 tests/failures/errors/skipped，不称完整资产测试通过。

7. 上传原始测试结果（默认 zip）及任务摘要。

超时：50 分钟。

### 4.2 `apk` job

源码门禁通过后必须执行；固定输入获取或校验失败时任务失败，不再静默跳过 APK。

输入包来源优先级：

1. `workflow_dispatch` 的 `asset_bundle_url` + `asset_bundle_sha256`；
2. 仓库变量 `SUNSET_ASSET_BUNDLE_URL` + `SUNSET_ASSET_BUNDLE_SHA256`。
3. 两者均未配置时，使用 [固定输入锁](../../ci/sunset-inputs.lock.json) 指定的既有私有完整源码附件。URL 固定到 Release asset ID，整个 ZIP 的 SHA-256 固定；仅提取 11 个白名单输入，每个二进制仍按当前源码 pin 验证，不覆盖或执行归档中的源码。

URL/SHA 必须在同一来源成对提供，半对配置立即失败，不单独回退。自定义归档默认为严格的 `bundle`；完整源码归档需显式选择 `asset_archive_format=source-archive`，仓库变量对应 `SUNSET_ASSET_ARCHIVE_FORMAT`。私有 GitHub Release API 下载只在该步骤使用 `GITHUB_TOKEN`，重定向后不携带认证头。

步骤：

1. checkout、toolchain、setup-java/Node（同上）。
2. 下载/校验固定输入包：

```bash
python3 tools/ci-assets.py --url "$URL" --sha256 "$SHA"
```

该脚本先校验整个 ZIP 的固定 SHA，再暂存白名单输入并核对全部源码 pin，成功后才写入目标；普通 bundle 必须恰好 11 个成员，显式 source-archive 只选择其中白名单成员。缺失、重复、链接、路径歧义或摘要不符均失败。

3. 安装 Android SDK/NDK：

```bash
sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "ndk;26.3.11579264"
```

4. 原始输入通过 pin 核验后，从当前 npm lock 重建 DSH 归档，并刷新实际运行时描述符，再准备夹具。这样本分支的授权补丁更新不会继续使用旧归档 recipe 元数据；旧输入包和新生成运行时的 SHA 在 build-info 中分别记录。执行两版完整单测（无测试筛选、无资产任务排除），并逐项确认上述 5 项两版均执行且无跳过：

```bash
python3 tools/prepare-dsh-runtime.py
python3 tools/prepare-backup-assets.py --write
python3 tools/prepare-runtime-descriptor.py --write
python3 tools/verify-runtime-descriptor-source.py
python3 tools/prepare-test-runtime.py
./gradlew :app:testStandardDebugUnitTest :app:testLowDebugUnitTest \
  -Pdsha.sunsetTest=true -Pdsha.sourceOnlyTests=false --no-daemon --max-workers=2
```

完整测试通过后构建 unsigned 测试包：

```bash
./gradlew :app:assembleStandardRelease \
  -Pdsha.sunsetTest=true -Pdsha.unsignedPackage=true \
  --no-daemon
```

5. 测试签名：

- 解析 `SUNSET_TEST_KEYSTORE_B64` → `sunset-test.jks`；
- 否则从 cache 恢复；
- 否则 `keytool` 生成；
- `apksigner sign --ks sunset-test.jks --ks-key-alias sunset-test ...`

6. 校验身份：

```bash
aapt2 dump badging app-signed.apk | grep "package: name='dsh.android.client.SunsetRNE'"
apksigner verify --print-certs app-signed.apk
# debuggable=false；证书 != E7E3；versionName 含 -SunsetTest
```

7. 生成 `SHA256SUMS`、`build-info.json`（commit、run id、versionName、证书 SHA-256、端口设计版本）。
8. 分别上传 APK 原文件和验证附件：

```yaml
- uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
  with:
    archive: false
    retention-days: 14
    path: ${{ steps.sign.outputs.apk }}
- uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
  with:
    name: sunset-evidence
    archive: true
    retention-days: 14
    path: |
      out/SHA256SUMS
      out/sunset-test-cert.pem
      out/build-info.json
      out/apk-signature.txt
      out/full-unit-tests.json
```

说明：`archive: false` 是 2026-02-26 的非压缩 artifact 能力；下载得到的就是 `.apk`，不是 `.zip`。

9. 未配置 secret 时上传本轮实际使用的缓存或新生成测试 keystore（单文件，14 天；仅供测试包复用）。

超时：120 分钟。

### 4.3 为什么 APK job 需要输入包

`offline-rootfs.bin`、`dsh-runtime.bin`、`ubuntu-tools.bin` 等固定输入合计约 738 MB，不在 Git 中（GitHub 单文件 100 MB 限制，且仓库明确不提交大生成归档）。没有输入包时：

- `source` job 运行源码检查、宿主回归及明确标注跳过数的源码模式单测；
- `apk` job 使用源码锁中的现有私有输入归档；下载失败或输入不符则明确失败；
- 不生成假的/残缺 APK。

覆盖固定默认输入（可选，必须同时提供 URL 和 SHA）：

- 仓库 Settings → Variables：`SUNSET_ASSET_BUNDLE_URL`、`SUNSET_ASSET_BUNDLE_SHA256`；
- 手动 `workflow_dispatch` 时填两个输入。

### 4.4 真实 CI 暴露的队列测试竞态

运行 `39753314515` 的源码单测实际执行 2046 项、1 失败、7 跳过；失败为 `ScreenOwnershipTest.queueCapacityRejectsBeforePlatformWork`，预期 `SCREEN_QUEUE_FULL` 而实际 `SCREEN_QUEUE_TIMEOUT`。旧测试的 `current` 回调会在容量检查和执行前各调用一次，却两次都递减入队计数，可能在只有 15 个名额被占用时就检查溢出。

修复将每个请求停在取得容量后的首轮检查，主线程确认 16 个独立请求均持有容量后再验证第 17 个被拒绝，最后显式释放屏障。生产队列不变。JDK17 整类 14 项、容量用例连续 100 轮通过；隔离负控将容量误改成 17 时测试确实失败。原失败记录保留，后续全量结果以新提交的实际 CI 为准。

## 5. 产物契约

| 文件 | 说明 |
|---|---|
| `dsha-sunset-SHORT_SHA.apk` | 测试 APK |
| `SHA256SUMS` | 对 APK/证书的 SHA-256 |
| `sunset-test-cert.pem` | 测试证书（公开材料） |
| `build-info.json` | commit、run、包名、versionName、证书 SHA-256、输入包 SHA-256、构建时间 |
| `sunset-test.jks` | 未配置 secret 时上传缓存或新生成的测试私钥，14 天，禁止用于生产 |
| `full-unit-tests.json` | 两版完整测试实际计数及 5 项真实夹具逐项执行证明 |

APK artifact 名使用实际短文件名 `dsha-sunset-<8位提交号>.apk`；验证附件名为 `sunset-evidence`，完整与源码测试报告分别为 `sunset-tests`、`sunset-source`。名称最多 32 个 ASCII 字符，CI 在构建前检查。完整版本、完整 SHA 和运行身份保存在 `build-info.json`，其中 `apkFile` 记录实际文件名；任务摘要提供直接下载链接。

## 6. 验证命令（下载 artifact 后）

```bash
sha256sum -c SHA256SUMS
aapt2 dump badging *.apk | head -5
apksigner verify --print-certs *.apk
# 确认：
# - package name = dsh.android.client.SunsetRNE
# - versionName 含 -SunsetTest
# - application-debuggable 不出现
# - 证书 SHA-256 != e7e3a31a...
```

## 7. 安全与合规

- 只使用测试签名；E7E3 只属于正式包。
- 不创建 tag/Release；workflow 不请求 `contents: write`。
- 输入包 URL 必须是 HTTPS 且带固定 SHA-256；不使用可变分支 URL。
- 不在日志打印 token/API key/用户数据；APK 中不包含测试 keystore。
- 测试 keystore 14 天过期，证书主题明确测试用途；不得用于任何正式分发。
- 工作流成功和附件读回可证明测试包完成构建、签名、内容校验与上传；不代表真机安装运行或正式发布。

## 8. 本地等价命令（无 CI 时）

```bash
# 需要固定输入包已展开到 app/src/main/assets/
./gradlew :app:assembleStandardRelease \
  -Pdsha.sunsetTest=true -Pdsha.unsignedPackage=true --no-daemon

keytool -genkeypair -keystore sunset-test.jks -storetype PKCS12 \
  -alias sunset-test -keyalg RSA -keysize 3072 -validity 3650 \
  -dname "CN=DSHA Sunset Test (NOT FOR RELEASE), OU=Sunset-3090-Q, O=SunsetRNE, C=CN" \
  -storepass sunset-test-only -keypass sunset-test-only

apksigner sign --ks sunset-test.jks --ks-key-alias sunset-test \
  --ks-pass pass:sunset-test-only --key-pass pass:sunset-test-only \
  --out dsha-sunset.apk app/build/outputs/apk/standard/release/*-unsigned.apk

apksigner verify --print-certs dsha-sunset.apk
```

## 9. 回滚

- 删除/禁用 `sunset-3090-q.yml` 即停止测试包流水线；
- 代码回滚不影响正式包；
- 已上传的测试 artifact 14 天后自动过期；测试 keystore 如泄露只影响测试包，不涉及 E7E3；
- 不删除历史正式 Release/资产。
