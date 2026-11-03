# Sunset-3090-Q 命名与标识约定

> 本文是 Sunset-3090-Q 分支内唯一的命名口径。代码、CI、APK 文件名、文档和提交信息出现冲突时，以本文为准。
>
> 状态：已定稿（2026-10-22，Asia/Shanghai）。仅适用于 `Sunset-3090-Q` 分支，不得自动带入 `codex/prerelease-190`、main 或任何发布分支。

## 1. 分支与基线

| 项 | 值 |
|---|---|
| 分支名 | `Sunset-3090-Q` |
| 大小写 | 必须保留 **S** 与 **Q** 大写；不要写成 `sunset-3090-q`、`Sunset-3090-q` |
| 基线提交 | `e057379256161f42a28a2adcccef4ea4f5e7dcf1`（0.2.1-rc.2-20261022.0243） |
| 仓库 | `ym2025szz/DSHA-prerelase`（私有维护仓库） |
| 远端 | `git@github.com:ym2025szz/DSHA-prerelase.git` |
| 用途 | 个人测试性分支：验证动态设备桥端口、修复双实例冲突、产出 CI 测试包 |
| 禁止 | 打 tag、创建 GitHub Release、公开上传、PR 到公开仓库、部署网站 |

命名里的 `3090` 指当前被写死的设备桥端口，`Q` 是本分支的区分后缀；二者都不是版本号，不参与 dsh / APK 版本计算。

## 2. 测试包身份

| 项 | 值 |
|---|---|
| applicationId | `dsh.android.client.SunsetRNE` |
| 大小写 | `.SunsetRNE` 必须原样保留（S、R、N、E 大写） |
| 与正式包关系 | 与 `dsh.android.client` 数据、权限、签名隔离；不覆盖安装、不共享数据 |
| versionCode | `190`（与基线一致，不递增；测试包不冒充新正式版本） |
| versionName（Standard） | `0.2.1-rc.2-20261022.0243-dsh0.2.1-rc.2-SunsetTest` |
| versionName（Low，如构建） | `0.2.1-rc.2-20261022.0243-dsh0.2.1-rc.2low-SunsetTest` |
| 应用显示名 | 保持现有名称；靠包名与版本名识别测试身份，避免额外资源差异 |
| 可调试性 | `debuggable=false`（测试签名不等于 debug 包） |
| 安装方式 | 只能覆盖安装**同包名、同测试签名**的旧测试包；不能覆盖 E7E3 正式包 |

包名后缀是测试身份的第一标识。任何发布脚本、Release 清单、官网下载页都不得出现 `SunsetRNE` 包名。

## 3. 测试签名

| 项 | 值 |
|---|---|
| 用途 | 仅签 CI 测试包；不签增量更新清单、不签正式 APK |
| keystore 文件 | `sunset-test.jks`（PKCS12） |
| alias | `sunset-test` |
| DN | `CN=DSHA Sunset Test (NOT FOR RELEASE), OU=Sunset-3090-Q, O=SunsetRNE, C=CN` |
| 密钥 | RSA 3072，有效期 3650 天 |
| 默认测试口令 | `sunset-test-only`（仅测试；不是秘密，不用于任何生产系统） |
| 证书 SHA-256 | 每次生成都不同；CI 必须打印并上传证书；**必须不等于** E7E3：`e7e3a31a75946f2669194c972b3dd0c9aea3fc7c50a8b885d2dee710b22a53f5` |
| 私钥保管 | 优先使用 CI secret `SUNSET_TEST_KEYSTORE_B64`；未配置时由 CI 临时生成并缓存/上传测试 keystore。不得提交到 Git |

正式发布证书 E7E3 只属于正式包；测试包一旦用 E7E3 签名，就视为命名违规，CI 必须失败。

## 4. CI 命名

| 项 | 值 |
|---|---|
| workflow 文件 | `.github/workflows/sunset-3090-q.yml` |
| workflow 显示名 | `Sunset-3090-Q test package` |
| job：源码与回归 | `source` |
| job：测试包 | `apk` |
| APK 文件名及 artifact 名 | `dsha-sunset-<short_sha>.apk`，short_sha 为提交前 8 位，共 24 字符 |
| 验证附件 artifact | `sunset-evidence` |
| 完整测试报告 artifact | `sunset-tests` |
| 源码测试报告 artifact | `sunset-source` |
| 名称限制 | 最多 32 个 ASCII 字符；只使用小写字母、数字、点和短横线 |
| 随附文件 | `SHA256SUMS`、`sunset-test-cert.pem`、`build-info.json` |
| 触发 | 推送到 `Sunset-3090-Q`；`workflow_dispatch` 可手动提供固定输入包 URL/SHA |
| 保留期 | 14 天（测试产物，不长期归档） |
| 权限 | `contents: read`；不请求 `contents: write`、不创建 Release、不打 tag |

完整版本、完整提交 SHA、运行编号和证书信息保存在 `build-info.json`，不重复拼入下载名称。artifact 在每个 workflow run 内独立，因此验证附件和报告可以使用固定短名。APK 使用 `archive:false`，其 artifact 名来自实际文件名；只改 upload 的 `name` 不能缩短 APK 名。任务摘要提供 APK 和校验附件的直接下载链接。

完整构建输入默认使用 [固定输入锁](../../ci/sunset-inputs.lock.json) 指定的私有源码归档；URL/SHA 可成对覆盖，源码门禁通过后必须执行 APK 构建，详见 [CI 测试包文档](ci-test-package.md)。

## 5. 运行时命名

| 项 | 值 |
|---|---|
| 首选端口 | `3090`（保持兼容） |
| 回退范围 | `3091-3099`（共 10 个候选） |
| 端口文件 | rootfs 内 `/root/.dsh/.bridge_port`，内容为十进制端口，无换行；权限 `0600`、NOFOLLOW、普通文件 |
| 环境变量 | `DSHA_BRIDGE_PORT`（可选覆盖/诊断；文件优先） |
| 状态文件 | `/root/.dsh/.bridge_status`，成功为 `ok port=<port>`，失败为 `fail <原因>` |
| 版本端点 | `/app/version` 增加 `BRIDGE_PORT=<port>`；保留 `BRIDGE_PROTOCOL=3` |
| 新错误码 | `DSHA_NATIVE_CONSENT_UNAUTHORIZED`、`DSHA_NATIVE_CONSENT_PORT_CHANGED` |
| 兼容回退 | 读不到端口文件/环境变量时，guest 回退 `3090`（旧 App、旧夹具、恢复环境） |

端口文件不是凭据，但也属于机器状态：不得导出、不得进入用户备份、不得随 v5 备份恢复。

## 6. 构建开关

| 开关 | 作用 |
|---|---|
| `-Pdsha.sunsetTest=true` 或环境变量 `DSHA_SUNSET_TEST=1` | 启用 `.SunsetRNE` 包名后缀、`-SunsetTest` 版本名后缀、测试包身份校验 |
| `-Pdsha.unsignedPackage=true` | 跳过 E7E3 正式签名，产出 unsigned APK；CI 再用测试 keystore 签名 |
| 默认（不传） | 行为与基线完全一致，不产生测试包身份 |

测试构建不得使用 `assembleStandardRelease` 的默认签名路径（它会要求 E7E3）；CI 固定使用 unsigned + `apksigner` 测试签名。

## 7. 文档命名

| 文件 | 内容 |
|---|---|
| `docs/sunset-3090-q/README.md` | 分支章程、范围、状态、文档索引 |
| `docs/sunset-3090-q/naming.md` | 本文，命名唯一口径 |
| `docs/sunset-3090-q/root-cause-two-instances.md` | 双实例为什么冲突、现场证据与失败链 |
| `docs/sunset-3090-q/dynamic-port-design.md` | 动态端口完整设计、协议、风险与替代方案 |
| `docs/sunset-3090-q/regression-and-risk.md` | 回归线、测试矩阵、风险登记、注意事项 |
| `docs/sunset-3090-q/ci-test-package.md` | CI、测试签名、产物与 GitHub Actions 2026-02-26 变更 |

## 8. 明确禁止

1. 不打 tag、不创建 Release、不上传公开仓库、不更新官网/下载页。
2. 不用 E7E3 签测试包，不把测试签名用于任何正式产物。
3. 不复制另一个安装的 `.bridge_token` 来"让工具先跑起来"。
4. 不在正式生产数据上做双实例、强杀、覆盖安装等破坏性验证。
5. 不把本分支文档、CI、开关直接合并进 main；需要时单独评审。
6. 不把 `.bridge_port` 当凭据或导出文件；端口可公开，token 永远不可。
