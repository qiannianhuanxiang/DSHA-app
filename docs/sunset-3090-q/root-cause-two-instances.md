# 为什么两个 DSHA 同时存在会不断报错

> 本文记录 2026-10-22 本机双实例故障的现场、代码链路和设计前提。它是 [dynamic-port-design.md](dynamic-port-design.md) 的问题输入，不是事后猜测。
>
> 涉及设备：OnePlus PJD110 / Android 16 (SDK 36)；旧包 `com.dsh.client`（0.2.1-rc.2-20261021.2107），新包 `dsh.android.client`（0.2.1-rc.2-20261022.0243）。

## 1. 现象

新版 `dsh.android.client` 的会话里，模型调用任何工具都返回：

```text
Error: DSHA_DESTRUCTIVE_BLOCKED:
DSHA_NATIVE_CONSENT_UNAVAILABLE:
INVALID_REPLY; current native consent could not be verified.
```

被拒的调用包括 `bash`、`read_image`、子智能体、截屏/触控/控件树和文件读写。Web 页面、模型流式输出、会话保存都正常；只有设备工具链整体不可用。旧包 `com.dsh.client` 的工具不受影响。

## 2. 现场时间线

| 时间（Asia/Shanghai） | 事件 | 证据 |
|---|---|---|
| 10-21 23:30 | 旧包 `com.dsh.client` 安装并启动 | `dumpsys package` firstInstallTime |
| 10-21 23:31 | 旧包设备桥绑定 3090 成功 | 旧 rootfs `.dsh/.bridge_status` = `ok port=3090` |
| 10-22 11:01 | 新包 `dsh.android.client` 安装（独立 applicationId） | `dumpsys package` firstInstallTime |
| 10-22 11:02 | 新包设备桥绑定失败 | 新 rootfs `.bridge_status` = `fail 设备桥端口 3090 被占用…EADDRINUSE` |
| 10-22 11:27–11:38 | 新版会话所有工具被拒 | 会话截图与 `DSHA_NATIVE_CONSENT_UNAVAILABLE: INVALID_REPLY` |

补充事实：

- 两个包各自运行独立的 Ubuntu 容器和独立 DSH Web（旧 39141、新 34495），Web 端口是动态的，所以两边都能聊天。
- 两个包各自生成独立的 `.bridge_token`（只比对哈希，内容不同），没有共享。
- 新版 guest 的 `native-consent.cjs` 固定访问 `127.0.0.1:3090`；实际应答的是旧包桥。
- 用新版自己的 token 访问 3090，实测返回 `HTTP 200 {"result":"[UNAUTHORIZED]"}`；用旧版 token 返回正常 consent JSON。

## 3. 失败链（逐步）

```text
新版 guest（dsh.android.client 容器）
  │  读自己的 /root/.dsh/.bridge_token（B）
  ▼
native-consent.cjs 固定请求 http://127.0.0.1:3090/app/native-policy
  │  X-Token: B
  ▼
旧版 HttpShellService（com.dsh.client 进程，持有 3090）
  │  tokenMatch(B) == false
  ▼
HTTP/1.1 200 OK
{"result":"[UNAUTHORIZED]"}          ← 历史协议：未授权也返回 200
  ▼
destructive-policy globalConsent()
  │  response.ok == true
  │  envelope.result 是字符串
  │  JSON.parse("[UNAUTHORIZED]") 抛错
  ▼
stage = INVALID_REPLY
  ▼
availableConsent() 抛
DSHA_DESTRUCTIVE_BLOCKED: DSHA_NATIVE_CONSENT_UNAVAILABLE: INVALID_REPLY
  ▼
prepareOperation() 对每个工具都执行上述检查 → 每个工具都被拒
```

对应代码：

- [HttpShellService.java:50](../../app/src/main/java/com/deepseekharness/app/HttpShellService.java#L50)：`public static final int PORT = 3090;`
- [HttpShellService.java:751](../../app/src/main/java/com/deepseekharness/app/HttpShellService.java#L751)：未授权时 `result = "[UNAUTHORIZED]"`，仍按 200 业务回复写出。
- [native-consent.cjs:50](../../app/src/main/assets/native-consent.cjs#L50)：`port: 3090` 写死。
- [destructive-policy/index.js:32-35](../../app/src/main/assets/destructive-policy/index.js#L32-L35)：`stage = 'INVALID_REPLY'` 后直接 `JSON.parse(envelope.result)`。
- [destructive-policy/index.js:140-149](../../app/src/main/assets/destructive-policy/index.js#L140-L149)：`prepareOperation` 每次调用都先做 consent 校验。
- [包名迁移记录:12](../releases/v0.2.1-rc.2-build190-package-rename-20261021.md#L12)：固定设备桥端口未改，并排安装不代表两套设备桥能同时占用同一端口。

## 4. 为什么 Web 能同时跑，桥不能

| 维度 | Web | 设备桥 |
|---|---|---|
| 端口 | 动态分配（旧 39141、新 34495） | 写死 3090 |
| 归属 | 每个 DSH 进程自己的 HTTP 服务 | 每个 App 进程内的 `HttpShellService` |
| 鉴权 | Web 自己的 token/会话 | 每安装独立的 `.bridge_token` |
| 冲突后果 | 端口被占时 WebPortPolicy 换端口/提示 | `bind()` 失败，整个设备工具链不可用 |

Android 的 loopback 是全机共享的；不像 Web 端口可以动态选，设备桥的端口是 guest 侧硬编码的契约，所以第二个实例没有"换一个端口再告诉 guest"的通道。

## 5. 为什么是"不断报错"而不是启动时报一次

1. 新包桥启动失败后只写一次 `.bridge_status`；Web 继续运行，模型继续工作。
2. 每个工具调用都走 `prepareOperation` → `globalConsent()`，没有"本会话已确认桥不可用"的缓存。
3. 旧包桥一直持有 3090，新包桥没有重绑机会；只要旧包不退，新包每次请求都会打到旧包。
4. 旧包对错 token 固定返回 200 + `[UNAUTHORIZED]`，错误被归类为"回复格式无效"，而不是"令牌/实例不对"，所以没有可恢复分支，也没有退避。
5. destructive-policy 是 fail-closed：无法验证 native consent 时，拒绝是设计行为；它没有责任区分"桥被别的实例占用"和"本机策略损坏"。

结果就是：模型每尝试一个工具，就重复撞同一堵墙；用户看到的是"所有工具都被系统拦截"。

## 6. 为什么当年会这么设计

build170 时代的隐含前提是"全机只有一个 DSHA"：

- 固定 3090 让 `curl`、帮助文档、插件、手写脚本都简单；
- token 门控和 destructive-policy 只需要面对一个宿主；
- 没有"实例身份"字段，因为不需要区分两个安装。

包名拆成 `com.dsh.client` 与 `dsh.android.client` 后，两个安装可以并存，这个前提失效了。迁移文档已经写了"并排安装不代表两套设备桥可同时占用同一端口"，但当时没有把"并存"当作正常场景，也没有给 guest 一条发现端口的路径。

## 7. 结论：四个前提同时失效

1. **端口单例**：`127.0.0.1:3090` 只能有一个 listener；Android 不给每个 app 独立网络命名空间。
2. **端口写死**：原生、SDK、shell、内置插件、帮助文本全部硬编码 3090，没有发现机制。
3. **身份缺失**：只有 token，没有 applicationId/实例标识；错 token 的回复还是 200 业务 JSON。
4. **失败放大**：destructive-policy 逐工具 fail-closed，且每次都重查，把一次桥冲突放大成"所有工具全挂"。

这不是"版本工具链爆炸"，而是"**单例服务 + 多实例客户端**"的架构冲突；包名拆分只是让两个实例第一次可以同时存在，从而暴露了它。

## 8. 最小复现/验证（只读，不修改生产数据）

在任一安装容器内：

```bash
# 1. 看本安装桥状态
cat /root/.dsh/.bridge_status
cat /root/.dsh/.bridge_token | wc -c

# 2. 用本安装 token 访问 3090
TOKEN=$(cat /root/.dsh/.bridge_token)
curl -sS -H "X-Token: $TOKEN" http://127.0.0.1:3090/app/native-policy

# 3. 用另一个安装的 token 访问 3090（只读；不复制 token 文件）
#    期望：{"result":"[UNAUTHORIZED]"}
```

双实例共存的验收命令见 [regression-and-risk.md](regression-and-risk.md)。

## 9. 修复方向

见 [dynamic-port-design.md](dynamic-port-design.md)：动态端口 + guest 发现 + 实例身份 + 可解释错误；测试包与 CI 见 [ci-test-package.md](ci-test-package.md)。
