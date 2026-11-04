# 回归线与风险控制

> 本文件是 Sunset-3090-Q 的测试门禁和风险登记。任何"工具能用了"的口头结论都不算通过；必须按下面的矩阵留下可复核的结果。
>
> 只在本分支/测试包上执行；不要在正式生产数据上做双实例、强杀、覆盖安装等破坏性操作。

## 1. 回归原则

1. **单实例不回归优先于双实例新功能**：3090 空闲时必须与基线一致。
2. **失败也要可解释**：预期失败（端口占用、错 token、桥停止）必须返回明确错误，而不是笼统超时。
3. **不改数据格式**：动态端口只新增 `.bridge_port` 机器状态，不迁移用户数据、不改备份格式。
4. **证据分离**：原始输出、失败复现、修复后复跑分开保存；不合并成"一次全绿"。
5. **不把宿主测试冒充真机**：双实例必须在本机/目标设备上实际启动两个包验证。
6. **不记录敏感值**：token、API key、用户内容不进公开日志；端口、包名、错误码可以记录。

## 2. 测试分层

| 层 | 范围 | 入口 | 通过标准 |
|---|---|---|---|
| 纯逻辑单测 | 端口候选/校验、凭据路径、consent projection | `./gradlew :app:testStandardDebugUnitTest --tests '*BridgePortPolicyTest' ...` | 零失败/错误 |
| 宿主脚本 | native-consent 传输、destructive policy、recovery | `python3 tools/run-host-tests.py --stage fast` | 全部通过；新增端口用例 |
| Android 单测 | 现有 Standard/Low 选定类 | `./gradlew :app:testStandardDebugUnitTest :app:testLowDebugUnitTest -x :app:prepareStandardAssets -x :app:prepareRecoveryAssets` | 零失败/错误；跳过项单独记录 |
| 静态检查 | 凭据生成、描述符输入、格式、链接 | `python3 tools/generate-credential-paths.py --check` 等 | 与源码一致 |
| 设备单实例 | 正式包/测试包单独运行 | 见 §4.1 | 桥 3090、工具正常、`/app/version` 含端口 |
| 设备双实例 | 两个包同时运行 | 见 §4.2 | 端口不同、互不串扰、错 token 可解释 |
| 设备三实例 | 正式 + 两个测试包（如可安装） | 见 §4.3 | 3090/3091/3092；第四个失败有明确状态 |
| CI 测试包 | 包名/签名/可调试/产物 | 见 [ci-test-package.md](ci-test-package.md) | 包名 `.SunsetRNE`、签名 ≠ E7E3、`debuggable=false`、artifact 完整 |

## 3. 必跑回归清单（代码落地后）

### 3.1 纯逻辑/宿主

```bash
# 凭据路径生成一致性
python3 tools/generate-credential-paths.py --check

# 运行时输入契约/描述符源码检查
python3 tools/test-runtime-descriptor-inputs.py
python3 tools/verify-runtime-descriptor-source.py

# 宿主快速回归（含 native-consent/destructive/recovery）
python3 tools/run-host-tests.py --stage fast
```

新增/修改的用例至少覆盖：

- `BridgePortPolicy.candidates()` 顺序为 3090..3099，`valid()` 拒绝 0/65536/3081。
- `.bridge_port` 发布后读回一致；0644/符号链接/非数字/超长内容被拒绝。
- `native-consent.cjs`：
  - 无文件/无 env → 3090；
  - 文件 3091 → 3091；
  - env 3092 但文件 3091 → 文件优先；
  - 文件损坏 → env 或 3090；
  - 读连接返回 `port`；
  - `[UNAUTHORIZED]` → `DSHA_NATIVE_CONSENT_UNAUTHORIZED`，不再 `INVALID_REPLY`；
  - `expectedConnectionPort` 与当前不符 → `DSHA_NATIVE_CONSENT_PORT_CHANGED`。
- `destructive-policy`：consent 不可用仍拒绝；port 事实变化使排队请求失效；不得因端口存在而放行。
- `recovery-native-consent`：不受主桥端口影响，仍用 `DSHA_RECOVERY_BROKER_PORT`。

### 3.2 Android 单测

```bash
./gradlew :app:testStandardDebugUnitTest :app:testLowDebugUnitTest   -x :app:prepareStandardAssets -x :app:prepareRecoveryAssets   --no-daemon --max-workers=2
```

要求：

- `BridgePortPolicyTest` 覆盖候选顺序/范围/保留端口。
- `BridgeCredentialFilesTest` 覆盖端口文件发布、读回、非法值/目录/权限状态。
- `CredentialPathsTest`、`DataRootPolicyTest` 覆盖 `.bridge_port` 的机器状态/排除语义。
- 本版不新增 native consent 的 `applicationId` 字段，因此不要求对应的 projection 测试。
- 描述符相关测试在固定输入包恢复后跑 `prepare-runtime-descriptor.py --check`。

## 4. 设备验收清单

### 4.1 单实例（必过）

1. 只启动 A（正式包或测试包）。
2. 确认桥绑定 3090：
   ```bash
   cat /root/.dsh/.bridge_status          # ok port=3090
   cat /root/.dsh/.bridge_port            # 3090（无换行）
   stat -c '%a %F' /root/.dsh/.bridge_port  # 600 regular file
   curl -s -H @/root/.dsh/.bridge_headers http://127.0.0.1:3090/app/version
   ```
3. 调用至少 3 类工具：`/app/device`、`/app/ui/dump`、一次文件读写。
4. 确认 `/app/version` 含 `BRIDGE_PORT=3090`，帮助示例端口正确。
5. 停止 DSH，确认 `.bridge_port` 被删除、`.bridge_status=stopped`。

### 4.2 双实例（核心验收）

前置：两个包已安装，A 先启动并占 3090。

1. 启动 B（另一个包）。
2. 确认 B 的桥状态：
   ```bash
   # 在 B 的容器/rootfs 内
   cat /root/.dsh/.bridge_status          # ok port=3091（或 3092...）
   cat /root/.dsh/.bridge_port
   ```
3. 确认 A、B 端口不同，且各自的 token 只对本端口有效：
   ```bash
   # 用 A 的 token 访问 A 端口 → 正常
   # 用 A 的 token 访问 B 端口 → [UNAUTHORIZED] 或 401
   # 用 B 的 token 访问 A 端口 → [UNAUTHORIZED] 或 401
   ```
4. 在 A、B 各跑一次工具调用（设备信息 + 截屏/树 + bash），确认：
   - 都成功；
   - 没有 `INVALID_REPLY`；
   - 日志里端口与各自 `.bridge_port` 一致。
5. 停止 A，确认 B 仍正常；B 的桥不因 A 退出而换端口（除非 B 自己重启）。
6. 重启 B，确认可能换端口后 guest 自动发现，工具仍正常。

### 4.3 三实例/端口耗尽

1. 安装第三个测试包（如可改包名）或临时用同一测试包不同用户空间；启动后应得到 3092。
2. 人为占满 3090-3099（测试环境），启动下一个实例：
   - `.bridge_status` 明确写 `fail ... 3090-3099`；
   - 无 `.bridge_port`；
   - 工具报 `NATIVE_CONSENT_UNAVAILABLE`，不是 `INVALID_REPLY`。
3. 释放一个端口后重启实例，确认恢复。

### 4.4 故障与恢复

| 场景 | 操作 | 预期 |
|---|---|---|
| 端口文件缺失 | 删除 `.bridge_port`，调用工具 | guest 回退 3090；若桥不在 3090 则明确不可用；桥重启后恢复 |
| 端口文件损坏 | 写入 `abc`/超长/0644 | 拒绝该发现值，回退环境端口或 3090；授权仍由 token/consent 验证，不保证有独立解析日志 |
| 端口文件符号链接 | 指向其他文件 | 被拒绝；不读取目标 |
| token 错误 | 用另一安装 token | `UNAUTHORIZED`/401，不执行命令 |
| 桥崩溃 | 强杀 App/桥进程 | 工具报"桥不可用"；重启后恢复 |
| Web 端口冲突 | 把 Web 配到 3091 且桥回退到 3091 | 按现有 Web 端口策略提示；记录残余风险 |
| 备份/导出 | 导出数据/备份 | 不含 `.bridge_port`、`.bridge_token`、`.bridge_headers` |
| 覆盖安装测试包 | 同测试签名覆盖 | UID/数据保持；桥重新绑定；端口文件重建 |

### 4.5 证据保存

- 保存 `/app/version`、`.bridge_status`、`.bridge_port` 的**脱敏**输出（端口可留，token 不留）。
- 保存工具调用成功/失败的错误码，不只保存最终回答。
- 保存 A/B 两个包的 versionName、applicationId、签名证书 SHA-256（测试证书可公开）。
- 失败复现与修复后复跑分开记录；不要把一次通过写成"完整矩阵通过"。

## 5. 风险登记

| 编号 | 风险 | 可能性 | 影响 | 缓解 | 残余 |
|---|---|---|---|---|---|
| R1 | 3090-3099 被其他程序占用 | 低 | 桥不可用 | 有界扫描 + 明确状态；文档提示 | 需要用户释放端口 |
| R2 | 用户把 Web 端口配到回退区间 | 低 | Web 启动失败/换端口 | WebPortPolicy 现有冲突处理；文档提示 | 未自动避让 3091-3099 |
| R3 | guest 读到过期 `.bridge_port` | 中 | 一次连接失败 | 原生停止/失败时删除；guest 每次重读；错误可解释 | 强杀时可能残留到下次启动 |
| R4 | 同 UID 插件伪造端口文件 | 低 | 请求打到错误端口 | token + 端口变化检查；同 UID 本就可信 | 无法防恶意同 UID 代码 |
| R5 | 旧 guest 硬编码 3090 | 中（跨版本混用） | 新桥回退到 3091 时旧 guest 失败 | 资产随 App 打包；文档禁止混用 | 手工替换资产时仍可能 |
| R6 | 错 token 返回 200 被误判 | 中 | 错误码不直观 | SDK 映射 `[UNAUTHORIZED]`；后续评估 401 | 旧桥仍返回 200 |
| R7 | 测试签名每次不同 | 中 | 跨 run 不能覆盖安装 | 配置 `SUNSET_TEST_KEYSTORE_B64` 或保留 artifact keystore | 未配置时需卸载重装 |
| R8 | CI 无固定输入包 | 高（默认） | 无 APK artifact | 文档说明 `SUNSET_ASSET_BUNDLE_URL/SHA256`；source 检查仍跑 | 需要手工提供 bundle |
| R9 | 误把测试包当正式包 | 低 | 签名/包名混淆 | `.SunsetRNE` + `-SunsetTest` + 测试证书校验 + 文档禁止 | 无 |
| R10 | 描述符手工更新遗漏 | 中 | CI package 失败/运行时身份不一致 | 用脚本重算所有存在文件哈希；固定输入包恢复后跑 `--check` | 本机无 bundle 时无法完整校验 |
| R11 | 动态端口被写成"安全隔离" | 低 | 误导评审 | 文档/注释明确"不是沙箱" | 无 |
| R12 | 回滚后残留 `.bridge_port` | 低 | 旧代码忽略；无害 | 文档说明可删除；不参与身份 | 无 |

## 6. 给其他开发者的注意事项

1. **不要直接把本分支合进 main**。这是个人测试分支；动态端口、测试包身份、CI 都需要单独评审。
2. **不要复用 E7E3**。测试包必须用测试签名；CI 会校验证书哈希不等于 E7E3。
3. **不要复制 token**。跨安装复制 token 会让两个实例互相冒充，违反 I1；遇到工具不可用先看 `.bridge_status` 和端口。
4. **不要在生产数据上验证双实例**。测试包独立包名/数据；正式包只做只读检查。
5. **不要硬编码 3090**。新代码统一用 `.bridge_port`/\`DSHA_BRIDGE_PORT` 解析；帮助文本用运行时替换。
6. **不要把端口文件当凭据**。端口可公开；token 永远 0600、不导出、不进备份。
7. **改 guest 资产后必须更新描述符**。`runtime-descriptor.json` 与 `managed-runtime-inputs.json` 是受管身份；CI 的 `prepare-runtime-descriptor.py --check` 会拒绝漂移。
8. **APK CI 需要固定输入包**。不要把 `offline-rootfs.bin` 等大文件提交进 Git；用 `ci-assets.py` 的 URL/SHA 流程。
9. **GitHub Actions 2026-02-26 后不要用 Node 20 action**。见 [ci-test-package.md](ci-test-package.md)。
10. **报告证据要脱敏**。不要上传 token、API key、用户聊天、设备截图原图；只保留错误码、端口、版本、哈希。

## 7. 合并/发布门禁

本分支永远不满足"发布"条件；仅当以下全部满足时，才考虑把动态端口部分**单独提取**给正式分支评审：

- 单实例/双实例/三实例设备验收通过；
- 宿主 + Android 单测零失败/错误；
- 描述符在固定输入包下 `--check` 通过；
- 安全评审确认 I1-I10 未被破坏；
- 文档写明残余风险（Web 端口、旧 guest、测试签名）；
- 不包含测试包身份、CI 测试签名、本分支专用命名。

在此之前，`Sunset-3090-Q` 只用于本机/CI 测试，不打 tag、不发布。
