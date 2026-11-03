# 任务状态上岛（Android 16 实时活动）

核验日期：2026-10-27。参考基线：build205 源码（`0.2.1-rc.3-20261027.1312`）。

本页说明 DSHA 如何把「任务正在执行」的阶段性状态显示在系统状态栏胶囊 / 灵动岛 / 流体云上，
以及它为什么不需要 root、不需要白名单，什么情况下会静默退回普通通知。

## 1. 能力与边界

| 项 | 事实 |
|---|---|
| 依赖的系统接口 | Android 16（API 36）起的 Live Updates（promoted ongoing） |
| 需要 root / LSPosed | **不需要** |
| 需要厂商白名单 | **不需要**（小米官方大岛 `miui.focus.param` 才需要，本功能不依赖它） |
| 需要的权限 | `POST_PROMOTED_NOTIFICATIONS`（`normal\|appops`，装机即授予，用户可在系统设置关闭） |
| 胶囊显示内容 | 图标 + 一行短文本（约 7 字符），**不含进度条** |
| 失败行为 | 任何异常都返回原通知，功能退化为普通通知，不影响任务本身 |

支持的平台：原生 Android 16+、小米澎湃 OS 3.0.300+/4、ColorOS 16、OriginOS 6，以及后续跟进同一接口的厂商。
更老系统与鸿蒙只显示普通通知 —— 这是系统能力差异，不是开关失效。

## 2. 两代 ROM 的判据是相反的

| 版本 | 提升判据 |
|---|---|
| Android 16 GA（36.0，如 ColorOS 16） | 旧模型：需要 `android.colorized=true` |
| Android 16 QPR1+（36.1 / Android 17，如 HyperOS 4） | 新模型：需要请求位 `android.requestPromotedOngoing=true`，且 **colorized 必须为 false** |

因此实现按「系统是否提供 `canPostPromotedNotifications()`」分成两支，不能只写一支。
平台侧的 `setRequestPromotedOngoing` 是 36.1 才有的方法，在 36.0 上调用会抛
`NoSuchMethodError`，所以这里统一走 **extra**（官方文档明确允许用它替代 setter）。

## 3. 改动清单

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/deepseekharness/app/IslandSupport.java` | 新增。能力查询、两代 ROM 分支、通知增强、系统设置入口；纯展示层 |
| `app/src/main/java/com/deepseekharness/app/util/IslandText.java` | 新增。纯逻辑：短文本裁剪、岛位选举（无 Android import，配单测） |
| `app/src/main/java/com/deepseekharness/app/AgentTaskNotifications.java` | 渲染时接入选举与增强；计时器模式 |
| `app/src/main/java/com/deepseekharness/app/ui/IslandFragment.java` | 新增。设置页「任务状态上岛」 |
| `app/src/main/java/com/deepseekharness/app/ui/SettingsFragment.java` | 入口标签 |
| `app/src/main/java/com/deepseekharness/app/core/ConfigStore.java` | 两个偏好读写 |
| `app/src/main/java/com/deepseekharness/app/util/Constants.java` | 两个偏好键名 |
| `app/src/main/AndroidManifest.xml` | `POST_PROMOTED_NOTIFICATIONS` |
| `app/build.gradle` | `dsha.islandEdition` 独立包名开关 + `BuildConfig.ISLAND_EDITION` |
| `app/src/test/java/com/deepseekharness/app/util/IslandTextTest.java` | 新增。选举与裁剪的单测 |

## 4. 岛位选举

系统按包名只显示一个胶囊（SystemUI `filterByPackage`），多个活跃任务同时请求提升会互相顶掉，
表现为胶囊文字来回跳。因此每次渲染先选一个代表：

```
等待确认 (5) > 正在停止 (4) > 执行工具 (3) > 生成回复 (2) > 其他运行中 (1) > 不参与 (0)
```

不参与的条件：非活跃、状态 `unknown`、用户已划掉。同优先级取更早开始的任务，保证文字稳定。
只有被选中的那条通知会被增强，其余保持普通通知。

## 5. 偏好字段

| 键 | 默认 | 含义 |
|---|---|---|
| `task_island` | `true` | 任务状态上岛总开关 |
| `task_island_chronometer` | `false` | 用系统计时器显示耗时（省掉 1Hz 重绘，但胶囊不再显示阶段文字） |

短文本与计时器互斥：官方胶囊内容优先级是 `shortCriticalText > MetricStyle > when(计时器)`，
写了短文本就会盖掉计时器，所以计时器模式下不写短文本。

## 6. 独立版包名

`-Pdsha.islandEdition=true`（或环境变量 `DSHA_ISLAND_EDITION=1`）时：
`applicationIdSuffix '.island'`、`versionNameSuffix '-island'`，包名变为 `dsh.android.client.island`。
应用私有目录、偏好、插件与运行环境随之隔离，可与正式 DSHA 并存安装。
默认构建（不带该参数）完全不受影响。

## 7. 验证范围

已做：宿主单测（`IslandTextTest`）、源码整洁与文档链接检查、Standard Debug 编译。
未做：真机安装与胶囊显示验收（本轮不操作手机）。因此「在真机上确实出现胶囊」仍属未验证，
不能从编译通过推导为设备通过。