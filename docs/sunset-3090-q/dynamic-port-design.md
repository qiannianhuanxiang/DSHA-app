# 动态设备桥端口设计

> 目标：让同机多个 DSHA 安装各自拥有可被本安装 guest 发现的设备桥端口，同时保持单实例行为、token 隔离和 fail-closed 策略不变。
>
> 状态：原设计与演进取舍保留在本文；2026-10-22 的代码收口及实际软件验证见 [CI 与动态端口修复](ci-fix-20261022.md)。设备矩阵尚未验收。命名以 [naming.md](naming.md) 为准。

## 1. 问题回顾

当前设备桥是"全机单例"：

- 原生 [HttpShellService.java:50](../../app/src/main/java/com/deepseekharness/app/HttpShellService.java#L50) 写死 `PORT = 3090`；
- guest [native-consent.cjs:50](../../app/src/main/assets/native-consent.cjs#L50)、`adb-shell.py`、内置插件全部写死 3090；
- 两个安装的 token 不同，但第二个安装的 guest 仍会打到第一个安装的桥上，得到 `200 {"result":"[UNAUTHORIZED]"}`，被 destructive-policy 归类为 `INVALID_REPLY`，所有工具 fail-closed。

完整现场见 [root-cause-two-instances.md](root-cause-two-instances.md)。

## 2. 目标与非目标

### 2.1 目标

| 编号 | 目标 |
|---|---|
| G1 | 同一设备上 N 个 DSHA 安装可同时运行，每个安装的 guest 只访问自己的桥 |
| G2 | 单实例行为不变：3090 空闲时仍绑定 3090，旧脚本/旧夹具照常工作 |
| G3 | guest 无需要写死端口：通过私有状态文件/环境变量发现实际端口，读不到才回退 3090 |
| G4 | 失败可解释：未授权、实例不匹配、端口变化、桥不可用分别是不同错误，不再一律 `INVALID_REPLY` |
| G5 | 不新增特权面：token 仍每安装独立；端口不是凭据；destructive-policy 仍 fail-closed |
| G6 | 可测试、可回滚：纯逻辑可单测；双实例有明确验收；回滚不迁移数据 |

### 2.2 非目标

- 不做跨安装共享桥、共享 token、跨安装代理。
- 不做局域网/远程桥；只监听 loopback。
- 不替代 recovery 运行时的独立 broker（见 §9）。
- 不解决同 UID 插件的信任问题；同 UID 插件本就能读 rootfs。
- 不改变 consent schema 的字段语义；只做加法与错误映射。
- 不为"两个同包名安装"设计；Android 本身不允许同包名双开。

## 3. 不变量（实现时不得破坏）

| 编号 | 不变量 |
|---|---|
| I1 | `.bridge_token` 每安装独立生成；不得复制、派生、从端口推导 |
| I2 | `.bridge_port` 是机器状态，不是凭据；权限 0600、NOFOLLOW、普通文件；不进备份/导出 |
| I3 | consent 仍要求 schema 1、`available=true`、UUID 字段、`runtimeInstanceId=<uuid>:<webGeneration>`；不得放宽 |
| I4 | destructive-policy 在无法验证 native consent 时仍然拒绝；端口/环境变量不能成为绕过手段 |
| I5 | 3090 优先；只有 `EADDRINUSE` 才回退；其他绑定错误直接失败，不静默换端口 |
| I6 | IPv4 `127.0.0.1` 是主监听；IPv6 `[::1]` 是附加监听，失败不致命 |
| I7 | 端口文件只在绑定成功后写入；停止/失败时删除；不得写入"预计端口" |
| I8 | guest 只接受 1-65535 的十进制端口，只连 127.0.0.1/::1；不得接受任意 host |
| I9 | 实例身份字段是加法；旧回复缺少该字段时按 legacy 接受，不能因此拒绝旧 App |
| I10 | 日志/状态/错误不包含 token；端口可以公开 |

## 4. 总体架构

```text
┌──────────────────────────── App A: com.dsh.client ────────────────────────────┐
│  HttpShellService（A 进程）                                                    │
│    bind 127.0.0.1:3090 成功 → 写 A rootfs 的 .dsh/.bridge_port = 3090          │
│    .dsh/.bridge_token = tokenA（A 自己生成）                                   │
│         ▲                                                                     │
│         │ tokenA + port 3090                                                   │
│  guest A（A 的 Ubuntu 容器）: native-consent / adb-shell / 插件                │
└───────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────── App B: dsh.android.client ────────────────────────┐
│  HttpShellService（B 进程）                                                    │
│    bind 3090 → EADDRINUSE → bind 3091 成功 → 写 B rootfs 的 .bridge_port=3091  │
│    .dsh/.bridge_token = tokenB（B 自己生成）                                   │
│         ▲                                                                     │
│         │ tokenB + port 3091                                                   │
│  guest B（B 的 Ubuntu 容器）: native-consent / adb-shell / 插件                │
└───────────────────────────────────────────────────────────────────────────────┘
```

关键点：

1. 两个 App 是不同 UID、不同 dataDir、不同 rootfs；`.bridge_port` 天然隔离。
2. `127.0.0.1` 仍是全机共享，但端口不同，listener 不再冲突。
3. 每个 guest 只读**自己 rootfs** 的 `.bridge_port` 和 `.bridge_token`，不会跨安装。
4. 端口文件只是"发现"，真正的授权仍是 token + consent。

## 5. 端口选择

### 5.1 候选与算法

- 首选：`3090`
- 回退：`3091`、`3092`、…、`3099`（共 10 个）
- 选择顺序固定，先到先得；不随机、不哈希，便于现场诊断。
- 跳过 `3080`（dsh Web 默认）与 `3081`（LAN 代理）；候选范围本身不含它们。

伪代码：

```text
for port in 3090..3099:
    socket = new ServerSocket()
    socket.setReuseAddress(true)        // 允许快速重启，不做 SO_REUSEPORT
    try:
        socket.bind(127.0.0.1, port)
        bound = port
        break
    catch BindException e:
        close(socket)
        last = e
        continue
if bound < 0:
    fail("设备桥端口 3090-3099 均被占用: " + last)
else:
    write .bridge_port = bound (0600, NOFOLLOW, atomic)
    write .bridge_status = "ok port=" + bound
    publish listener
```

### 5.2 为什么是 10 个、为什么在 3090 附近

- 10 个足够覆盖开发机上的正式包 + 1-2 个测试包；再多说明环境异常，应显式报错。
- 低于 Linux 临时端口段（32768-60999），减少与出站连接抢端口。
- 靠近 3090，帮助文本、运维习惯和旧脚本迁移成本最低。
- 不选 `SO_REUSEPORT`：内核会把连接分给任意 listener，token 归属不可控，等于把两个安装的桥混在一起。

### 5.3 绑定失败分类

| 错误 | 处理 |
|---|---|
| `EADDRINUSE` | 尝试下一个候选；全部占用则失败 |
| `EACCES` / 地址不可用 / 其他 `IOException` | 立即失败，不重试；写 `.bridge_status` |
| `LIFECYCLE` 已被新 run 取代 | 关闭 socket，不写状态（避免旧线程覆盖新实例） |

### 5.4 IPv6

IPv4 成功后再尝试 `[::1]:port`。IPv6 失败只记日志（`BRIDGE_IPV6_UNAVAILABLE`），不影响 IPv4 主通道，也不换端口。

## 6. 原生实现

### 6.1 新增纯逻辑

`app/src/main/java/com/deepseekharness/app/util/BridgePortPolicy.java`

- `PRIMARY_PORT = 3090`
- `ATTEMPTS = 10`
- `candidates()` 返回 `3090..3099`
- `valid(int)`：1-65535，排除 3080/3081
- `rangeLabel()`：`3090-3099`
- 无 Android import，配 `BridgePortPolicyTest`

### 6.2 HttpShellService

- 新增 `private volatile int bridgePort = PORT`，`public static int port()`（未就绪返回 0）。
- `bindAndServe` 按 §5.1 循环绑定；成功后设置 `bridgePort`，再 publish。
- `noteBindOk(int port)`：清空 `bindError`，写 `.bridge_port` 与 `.bridge_status=ok port=<port>`。
- `noteBindError`：写失败原因，删除 `.bridge_port`；日志不再写"3090 桥"，改为"设备桥"。
- `finishRun`：正常停止或失败时删除 `.bridge_port`（仅当当前 run 仍是 activeRun）。
- `appVersion()` 增加 `BRIDGE_PORT=<port>` 行；保留 `BRIDGE_PROTOCOL=3`。
- `appHelp()` 返回前把文本里的 `3090` 替换为实际端口（帮助示例必须可用）。
- `isReady()` 语义不变：只认 IPv4 主 listener。
- `PORT` 常量保留，作为首选端口和旧调用方的兼容锚点。

### 6.3 端口文件读写

`app/src/main/java/com/deepseekharness/app/util/BridgeCredentialFiles.java`

- `publishPort(BackupFileSystem fs, File file, int port)`
  - 端口 1-65535；
  - 父目录必须是 DIRECTORY；
  - 原子写、`mode 0600`、读回校验；
  - 不追加换行（guest 读取时 trim 也兼容）。
- `readPort(BackupFileSystem fs, File file)`
  - `stat` 必须是 FILE；
  - 最多 5 字节；
  - 正则 `[0-9]{1,5}`，数值 1-65535；
  - 失败返回 -1，不抛给调用方。

端口文件路径：`CredentialPaths.BRIDGE_PORT`（新常量，值为 `.bridge_port`），与 token/status 同目录。

### 6.4 凭据/备份/导出

- `credential-paths.json` 增加 `"bridgePort": ".bridge_port"`；`externalMachine.exact` 与 `prefixes` 增加 `.bridge_port`、`.bridge_port.`；`backupMachine.additionalExact` 增加 `.bridge_port`。
- 运行 `python3 tools/generate-credential-paths.py` 重新生成 `CredentialPathRules.java`。
- `backup_rules.xml` / `data_extraction_rules.xml` 增加 `.bridge_port` 排除项。
- `/app/export`、`/app/readfile` 的凭据护栏已经覆盖 `.dsh` 路径；端口文件不需要额外拒绝，但不能被备份/导出。

### 6.5 实例身份字段（本轮不新增）

本轮用"每安装独立端口 + 每安装 token"区分实例，**不**在 consent payload 里新增 `applicationId` 字段：

- 端口由本安装 rootfs 的 `.bridge_port` 提供，token 由本安装 rootfs 的 `.bridge_token` 提供；guest 只读自己的文件，天然隔离。
- 请求打到别的实例时，token 必然不匹配，SDK 映射为 `NATIVE_CONSENT_UNAUTHORIZED`。
- 不在 payload 里加字段，避免旧/新 schema 组合、测试夹具和描述符的额外变更；后续如需更强的"实例自报身份"，再单独评审加法字段。

## 7. guest 侧发现

### 7.1 解析顺序

1. `/root/.dsh/.bridge_port`：`lstat` 普通文件、非符号链接、size 1-5、mode 0600、内容 `[0-9]{1,5}`、1-65535。
2. `process.env.DSHA_BRIDGE_PORT`：同样校验。
3. `3090`：legacy 回退。

文件优先于环境变量：文件由本安装原生在绑定成功后原子写入，是权威来源；环境变量用于诊断/兼容。

不在进程启动时缓存；每次请求重新解析，保证桥重启换端口后无需重启 guest。

### 7.2 消费者清单

| 消费者 | 位置 | 改动 |
|---|---|---|
| consent SDK | `app/src/main/assets/native-consent.cjs` | 读端口；`readConnection` 加 `port`；`bridgeFetch` 校验 `expectedConnectionPort` |
| fetch 兼容层 | `app/src/main/assets/bridge-token-compat.cjs` | 允许实际端口，不再只认 3090 |
| 设备 shell | `app/src/main/assets/adb-shell.py` | 3 处 URL 改为解析端口 |
| 设备 shell 策略 | `app/src/main/assets/device-shell-policy.py` | 若引用 3090 一并更新（当前未发现） |
| computer use | `builtin-plugins/dsh-computer-use-android/lib/server.cjs` | 基础 URL 动态 |
| 虚拟屏 | `builtin-plugins/dsh-tool-vscreen/lib/server.cjs` | URL/origin/port 动态 |
| 状态悬浮条 | `builtin-plugins/dsh-status-overlay/lib/index.js` | 2 处 URL 动态 |
| 任务通知 | `builtin-plugins/dsh-task-notifier/lib/index.js` | URL 动态 |
| 设备 shell 指南 | `builtin-plugins/dsh-device-shell-guide/lib/index.js` | 文本用 `DSHA_BRIDGE_PORT` 占位 |
| 插件能力扫描 | `app/src/main/assets/plugin-capabilities.cjs` | 识别动态端口范围，避免把合法引用报成未知 |
| 帮助 | `HttpShellService.appHelp()` | 运行时替换实际端口 |

所有 guest 代码只连 `127.0.0.1`（或 `::1`），不得把端口文件里的值拼到非 loopback host。

### 7.3 JS/Python 解析器约定

```js
function bridgePort() {
  try {
    const p = '/root/.dsh/.bridge_port';
    const st = fs.lstatSync(p);
    if (!st.isFile() || st.isSymbolicLink() || st.size < 1 || st.size > 5 || (st.mode & 0o077) !== 0) throw 0;
    const fd = fs.openSync(p, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
    const raw = fs.readFileSync(fd, 'ascii').trim();
    fs.closeSync(fd);
    if (!/^[0-9]{1,5}$/.test(raw)) throw 0;
    const n = Number(raw);
    if (n >= 1 && n <= 65535) return n;
  } catch {}
  const env = Number(process.env.DSHA_BRIDGE_PORT);
  if (Number.isInteger(env) && env >= 1 && env <= 65535) return env;
  return 3090;
}
```

Python 侧用 `os.lstat` + `os.open(..., os.O_NOFOLLOW)`，同样校验 0600 和数字范围；失败回退 3090。

## 8. consent 身份与错误语义

### 8.1 连接事实

`readConnection()` 返回：

```json
{ "applicationId": "dsh.android.client", "generation": "<sha256(token)>", "port": 3091 }
```

`applicationId` 是 SDK 自报的诊断字段（可用 `DSHA_BRIDGE_APPLICATION_ID` 覆盖），本轮不作为 native 侧校验依据；真正的实例区分是端口 + token。`bridgeFetch(route, init)` 允许的 init 增加 `expectedConnectionPort`；请求前重新读端口，若与期望不符，拒绝并报 `NATIVE_CONSENT_PORT_CHANGED`。

### 8.2 回复校验（native-consent.cjs）

在现有 schema 校验基础上：

1. 如果回复是字符串 `[UNAUTHORIZED]`（旧桥 200 业务回复）→ `NATIVE_CONSENT_UNAUTHORIZED`，不再走 `JSON.parse` 失败路径。
2. HTTP 非 2xx → `HTTP_ERROR`（保留状态码）。
3. 连接拒绝/超时 → `NATIVE_CONSENT_UNAVAILABLE` / `TIMEOUT`。
4. 端口在读取/请求期间变化 → `NATIVE_CONSENT_PORT_CHANGED`。
5. 其他 schema 失败仍为 `INVALID_REPLY`。

### 8.3 destructive-policy

- `globalConsent` 传给 `bridgeFetch` 的期望端口来自 `connection.port`；
- 事后核对 SDK 捕获的 `applicationId`、`generation`、`port` 是否与本次读取一致（自洽校验，不做 native 字段比对）；
- 其它 fail-closed 语义不变；
- 错误文案区分"未授权/端口变化/桥不可用"，便于用户和开发组定位。

### 8.4 为什么不直接把未授权改成 401

历史协议里 `/app/*` 和 `/exec` 都用 200 + `[UNAUTHORIZED]` 文本，很多旧脚本/测试按这个格式解析。本分支先保持 200，在 SDK 层把该文本映射为明确错误；等插件/脚本全部升级后，再评估对 consent 路由单独返回 401 的协议版本。这个取舍写在这里，避免后来者误以为忘了改。

## 9. 生命周期与顺序

1. Web 启动 → `HttpShellService` 绑定端口 → 写 `.bridge_port` / `.bridge_status`。
2. guest 启动可能早于绑定完成：工具调用时解析端口；文件不存在就回退 3090，若仍连不上则报 `NATIVE_CONSENT_UNAVAILABLE`，下一次调用重试。
3. 桥重启可能换端口：原生原子覆盖 `.bridge_port`；guest 下次调用重新读取。
4. 桥停止/失败：删除 `.bridge_port`；guest 回退 3090 会连接失败，错误明确为"桥不可用"，而不是授权格式错误。
5. App 更新/覆盖安装：rootfs 与运行时资产按现有事务更新；端口文件重新写入。
6. 备份恢复：`.bridge_port` 被排除；恢复后由原生重写。
7. 维护/强杀：不依赖端口文件做身份；token 与 consent 仍是主校验。

## 10. 安全分析

| 威胁 | 影响 | 缓解 |
|---|---|---|
| 同 UID 插件伪造 `.bridge_port` 指向别的端口 | 请求打到错误服务 | token 仍在请求头；对方无 token 会 401；端口变化检查；同 UID 本就可读 rootfs，不新增权限 |
| 端口文件符号链接 | 读到攻击者文件 | 原生原子写、0600；guest `lstat` + `O_NOFOLLOW` + 普通文件校验 |
| 其他 App 读端口/token | 接管桥 | Android app sandbox 隔离 dataDir；token 0600；端口不是凭据 |
| 端口扫描/未授权请求 | 信息泄露/DoS | 只监听 loopback；token 门控；有界队列；未授权不执行命令 |
| 占用 3091-3099 阻止绑定 | 桥不可用 | 有界范围 + 明确失败状态；不静默降级；用户可看到冲突 |
| 复制另一安装 token | 跨安装冒充 | 明确禁止；token 每安装独立；端口与 token 不匹配会被拒绝 |
| 环境变量注入 `DSHA_BRIDGE_PORT` | 重定向 | 文件优先；env 同样校验范围；token 仍是主校验 |
| 旧 guest 回退 3090 | 连错实例 | 旧/新资产随 App 打包，不混用；跨版本手工混用不在支持范围 |

**安全边界声明**：动态端口解决的是可用性/冲突，不是隔离边界。桥仍然运行在 App UID 下，同 UID 插件和终端本来就能读 token；不要把它写成"安全沙箱"。

## 11. 兼容性

| 场景 | 行为 |
|---|---|
| 单实例，3090 空闲 | 与基线完全一致；`/app/version` 只多一行 `BRIDGE_PORT` |
| 单实例，3090 被非 DSHA 程序占用 | 回退 3091；guest 通过文件发现；旧脚本若硬编码 3090 会失败（文档提示用 `/app/version`） |
| 旧 App + 新 App（当前场景） | 旧 3090；新 3091；各自独立；不再串扰 |
| 旧 native + 新 guest | 没有 `.bridge_port` → 回退 3090；旧桥可用；consent 按既有 schema 接受 |
| 新 native + 旧 guest | 旧 guest 只认 3090；若新桥回退到 3091 会失败；不支持跨版本混用资产 |
| 备份/恢复 | `.bridge_port` 排除；不会把 A 的端口带到 B |
| recovery 运行时 | 使用独立的 `DSHA_RECOVERY_BROKER_PORT` 环境变量和 `/v1/consent`，不读主桥端口；不受本设计影响 |
| 桥协议 | 保持 3；新增 `/app/version` 行和 consent 字段是加法；不改变既有端点语义 |

## 12. 失败模式与用户可见信息

| 情况 | 原生状态 | guest 行为 | 用户看到 |
|---|---|---|---|
| 3090 绑定成功 | `ok port=3090` | 正常 | 工具正常 |
| 3090 被占，3091 成功 | `ok port=3091` | 读文件用 3091 | 工具正常；`/app/version` 显示 3091 |
| 3090-3099 全被占 | `fail 端口 3090-3099 均被占用` | 失败 `NATIVE_CONSENT_UNAVAILABLE` | "设备桥不可用，请关闭占用实例" |
| 端口文件缺失 | 正常桥 | 回退 3090；若不对则不可用 | 诊断提示检查 `.bridge_status` |
| 端口文件损坏/符号链接 | 正常桥 | 忽略并回退 | 同上；日志有 `BRIDGE_PORT_FILE_INVALID` |
| 另一个实例的桥应答 | 正常 | `NATIVE_CONSENT_UNAUTHORIZED` | "桥拒绝了当前安装的 token" |
| 桥重启换端口 | 覆盖文件 | 下次调用重新读取 | 无感或一次重试 |
| 桥崩溃 | 文件可能残留 | 连接拒绝 → `NATIVE_CONSENT_UNAVAILABLE` | "设备桥未运行" |
| Web 端口与回退端口冲突 | 桥仍在 | Web 启动失败/换端口 | 按现有 Web 端口提示处理 |

## 13. 替代方案与取舍

### 13.1 每安装 Unix domain socket（长期更优）

- 优点：不占 TCP 端口，天然按目录隔离，权限 0600；
- 缺点：需要 proot 额外 bind、guest 的 HTTP 客户端要支持 UDS、旧脚本/帮助全部要改；
- 结论：作为长期方向，本分支不实现，文档保留。

### 13.2 单端口多路复用（按 token 路由）

- 一个 listener 接收所有安装请求，按 token 分发；
- 缺点：安全关键、token 枚举面、实现复杂、故障域合并；
- 结论：不采用。

### 13.3 强制单实例（卸载旧包）

- 零代码；但用户明确要并存/迁移期双开；
- 结论：只作为运维兜底，不作为产品方案。

### 13.4 SO_REUSEPORT

- 内核随机分发连接，token 归属不可控；
- 结论：禁止。

### 13.5 复用已有的 BridgeConnectionDescriptor

仓库里已有 [BridgeConnectionDescriptor.java](../../app/src/main/java/com/deepseekharness/app/bridge/BridgeConnectionDescriptor.java)，带 port/token/HMAC 握手，但它是未接线的实验设计，要求端口不等于 3090，且需要额外的 proot bind 和 launcher 协议。本分支不启用它，避免把动态端口和新的握手协议一起引入；保留为未来"强身份握手"的参考。

## 14. 发布、回滚与开关

- **只在 Sunset-3090-Q 分支**：不合并、不发布；测试包独立包名/签名。
- **行为开关**：不新增用户可见开关；3090 优先保证默认行为。需要强制固定端口做对照实验时，可在测试构建中把候选缩成 `[3090]`（仅调试用途，不提交）。
- **回滚**：revert 代码即可；旧代码忽略 `.bridge_port`，仍按 3090 运行；不需要数据迁移。残留文件无害，可在维护脚本中删除。
- **数据**：不涉及用户数据格式；`.bridge_port` 已排除备份。

## 15. 测试与验收

### 15.1 单元/宿主

- `BridgePortPolicyTest`：候选顺序、范围、非法值。
- `BridgeCredentialFilesTest`（或现有测试扩展）：端口发布/读取/权限/回退。
- `test-native-consent-transport.mjs`：端口解析、`readConnection.port`、`expectedConnectionPort`、`[UNAUTHORIZED]` 映射。
- `test-destructive-global-consent.mjs`：port 事实变化、实例不匹配、fail-closed 保持。
- `test-destructive-full-access.mjs`、`test-recovery-native-consent.mjs`：回归不破坏。
- Android 单测：`NativeConsentProjectionTest`、凭据路径测试、描述符输入测试。

### 15.2 设备

见 [regression-and-risk.md](regression-and-risk.md) 的完整清单，核心是：

- 单实例 3090；
- 双实例 3090 + 3091；
- 三实例 3090/3091/3092；
- 停 A 后 B 仍工作；
- 重启 B 换端口后 guest 自动发现；
- 错 token / 错实例 / 端口变化错误可区分；
- 备份/导出不含 `.bridge_port`。

## 16. 未决问题

1. consent 路由是否在下一个协议版本改为 401（当前保留 200 + 映射）。
2. Web 端口策略是否显式避开 3090-3099（当前只避开 3081/3090；残余风险已记录）。
3. 是否把 Unix socket 作为下一阶段主方案。
4. 是否需要在 App UI 里直接展示"桥端口冲突"（当前只写 `.bridge_status` 与聊天错误）。
5. 测试包签名是否改为长期固定的测试 keystore（当前 CI 可生成/缓存）。

这些问题不影响本分支第一版实现；在评审时逐项决定。
