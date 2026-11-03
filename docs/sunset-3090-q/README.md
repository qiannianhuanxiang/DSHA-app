# Sunset-3090-Q 分支

> **一句话**：把设备桥从"全机写死 3090 的单例"改成"每个 DSHA 安装可发现自己的端口"，让两个安装（`com.dsh.client` 与 `dsh.android.client` / 测试包）可以并存，且第二个实例不再把所有工具误判为"原生授权失败"。
>
> 这是**个人测试性分支**：不打 tag、不发布 Release、不更新公开仓库；测试包使用独立包名和测试签名，仅供本机/CI 测试。

## 状态

| 项 | 值 |
|---|---|
| 分支 | `Sunset-3090-Q` |
| 基线 | `e057379256161f42a28a2adcccef4ea4f5e7dcf1`（0.2.1-rc.2-20261022.0243） |
| 当前阶段 | 已同步主线 `8fad934`（2100），保留动态桥及测试身份；修复应急 SDK 端口协议兼容与跨平台归档排序。本地两版关联测试各 168 项通过；完整范围、原始失败与设备边界见 [本轮同步记录](main-sync-20261023.md) |
| 唯一命名口径 | [naming.md](naming.md) |
| 归属 | SunsetRNE（个人测试分支），不是团队发布分支 |

> 实现进度会更新在本文末尾的"变更记录"。文档中标注"计划/将"的条目，在代码落地前不代表已实现。

## 范围

**做：**

1. 原生设备桥端口不再写死：首选 `3090`，占用时回退 `3091-3099`，成功后把实际端口写入 rootfs 的 `.bridge_port`。
2. guest 侧统一发现端口：`native-consent.cjs`、`adb-shell.py`、`bridge-token-compat.cjs`、内置 computer-use / vscreen / status-overlay / task-notifier 等消费者。
3. 授权链路的实例身份与错误语义：错 token / 错实例 / 端口变化分别可区分，不再一律 `INVALID_REPLY`；destructive-policy 保持 fail-closed。
4. `/app/version`、`/app/help`、`.bridge_status` 暴露实际端口，便于诊断。
5. 凭据/备份/导出排除 `.bridge_port`（机器状态，不进 v5、不导出）。
6. 测试包身份与 CI：`dsh.android.client.SunsetRNE`、测试签名、APK artifact、Node 24 兼容的 GitHub Actions。
7. 回归线与风险控制文档，供其他开发者评审时直接使用。

**不做：**

- 不改变正式包 `dsh.android.client` / `com.dsh.client` 的身份、签名和发布流程。
- 不把动态端口"修复"成跨实例共享 token；token 永远每安装独立。
- 不引入远程桥、不做局域网暴露、不绕过 `DeviceShellPolicy` 或 destructive-policy。
- 保留恢复（recovery）运行时的独立生命周期、broker 和凭据；仅补齐其 SDK 与公共 ToolRuntime 的端口身份协议兼容。
- 不把本分支的测试签名、包名、CI 产物带入正式发布。

## 文档索引

| 文档 | 用途 |
|---|---|
| [main-sync-20261023.md](main-sync-20261023.md) | 2100 主线同步、应急 SDK 兼容、归档去重及本轮验证边界 |
| [naming.md](naming.md) | 分支/包名/签名/CI/运行时命名的唯一口径 |
| [root-cause-two-instances.md](root-cause-two-instances.md) | 为什么两个 DSHA 并存会不断报错：现场证据、失败链、设计前提 |
| [dynamic-port-design.md](dynamic-port-design.md) | 动态端口完整设计：目标、协议、端口选择、guest 发现、安全、失败模式、替代方案、回滚 |
| [regression-and-risk.md](regression-and-risk.md) | 回归线、测试矩阵、风险登记、双实例验证清单、给其他开发者的注意事项 |
| [ci-test-package.md](ci-test-package.md) | CI 流水线、测试签名、APK 产物、2026-02-26 GitHub Actions Node 20 变更 |

## 验收标准

代码落地后，至少满足：

1. **单实例不回归**：只有正式包运行时，桥仍绑定 `3090`，`/app/version` 报 `BRIDGE_PORT=3090`，既有工具/审计/单测通过。
2. **双实例可共存**：先起 A（3090），再起 B（自动 3091），两者 `.bridge_status` 都是 `ok port=...`；各自 guest 只访问自己的端口；A/B 的工具互不串扰。
3. **错误可解释**：B 的 token 访问 A 时，原生桥以 HTTP 200 加 `[UNAUTHORIZED]` 拒绝，公共策略映射为 `DSHA_NATIVE_CONSENT_UNAUTHORIZED`，不落到 `INVALID_REPLY`。
4. **回退兼容**：临时删除 `.bridge_port` 后，guest 回退 `3090`（旧行为）；恢复文件后无需重启 Web 即可继续。
5. **状态文件安全**：`.bridge_port` 为 `0600` 普通文件、不可符号链接、内容仅 1-5 位数字；不进入备份/导出。
6. **CI 可复现**：测试包 `applicationId=dsh.android.client.SunsetRNE`、`debuggable=false`、签名证书 ≠ E7E3，artifact 含 APK、SHA256SUMS、证书和 build-info。
7. **不发布**：无 tag、无 Release、无公开上传；workflow 只有 `contents: read`。

## 已知限制

- 完整 APK 的大型输入不在 Git 中。默认使用仓库固定的 2100 私有完整源码附件；自定义 URL/SHA 必须成对提供（见 [CI 契约](ci-test-package.md)）。下载或摘要校验失败会使 APK job 失败，不再静默跳过。独立源码模式最多因缺失输入跳过 5 项外部夹具；完整测试必须实际执行它们。
- 动态端口只解决"同机多安装"的端口冲突，不解决同 UID 插件的信任问题；token 与 destructive-policy 仍是主防线。
- 端口的首选/回退范围（3090-3099）低于临时端口段，但仍可能与用户手工配置的 Web 端口冲突；这是残余风险，测试矩阵会覆盖，长期方案见设计文档的 Unix socket 备选。
- 测试签名优先使用 `SUNSET_TEST_KEYSTORE_B64`，其次复用 cache，缺失才生成；cache 失效后仍可能换证书，跨 run 覆盖安装不能只依赖缓存。

## 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-23 | 同步主线 2100；固定输入转为已核验新附件；修复应急端口授权兼容与跨平台打包排序，补 CI 必跑回归。详情见[同步记录](main-sync-20261023.md) |
| 2026-10-22 | 建立分支；先落文档：命名、根因、动态端口设计、回归/风险、CI 契约 |
| 2026-10-22 | 实现原生 3090-3099 端口回退、`.bridge_port` 发布、guest 发现、consent 错误映射与凭据/备份排除；补纯逻辑/文件/宿主测试 |
| 2026-10-22 | 添加 `Sunset-3090-Q` CI、`dsh.android.client.SunsetRNE` 测试身份、测试签名与非压缩 APK artifact；现有 workflow 升级到 Node 24 Actions |
| 2026-10-22 | 推送 `origin/Sunset-3090-Q`（79e7a30）；本地 12 项分支宿主回归、描述符/凭据/文档检查通过；ARM64 容器无法运行 x86_64 AIDL，完整 APK 由 GitHub x86 runner 或固定输入包验证 |
