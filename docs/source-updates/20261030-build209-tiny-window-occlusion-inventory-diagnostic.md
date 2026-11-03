# build209：系统微窗遮挡误判（#212）与结束应用清单诊断增强（#210 附带）

本轮基于 build208（`0.2.1-rc.3-20261029.0741-dsh0.2.1-rc.3`）源码。源码版本为
`0.2.1-rc.3-20261030.0006-dsh0.2.1-rc.3` / build209；本轮未构建、安装或发布 APK，
未操作手机。

## #212：1×1 系统字形窗触发 TARGET_WINDOW_OCCLUDED

Xiaomi REDMI K90 Ultra / Android 17 / HyperOS 实测：`/app/ui/screenshot` 恒返回
`TARGET_WINDOW_OCCLUDED`、从不签发 `observationId`，坐标 tap/swipe 全部被拒；
`dump` 与按文字点按正常。遮挡列表里有多个 1×1 的系统窗（com.miui.notes 的
DISPLAY_OVERLAY 与 APPLICATION_OVERLAY、HyperDuo glyph 的 STATUS_BAR_SUB_PANEL）、
MAGNIFICATION_OVERLAY 边缘手势条与底部防误触条。这些窗在屏幕上不可见，
但 `MainScreenWindowPolicy.region` 对「边界有效、与目标相交、非边缘装饰」的
窗口一律计为遮挡，1×1 字形窗正好落进该分支——整台设备的坐标手势因此报废。

修复：恰好 1×1 的窗口不参与遮挡判定，也不进入排除表（它们不构成任何可用面积
的变化）。issue 建议过 ≤4px²，但既有契约（MainScreenObservationTest）锁定
「2×2 的内部覆盖窗即使像素一致也必须拒绝坐标授权」，两者取交集后豁免收紧到
1×1——issue 实测的三个触发源（notes 两枚、HyperDuo glyph）全部是 1×1。
更大的系统装饰窗（MAGNIFICATION_OVERLAY 边缘条、底部防误触条等）本轮不动：
无障碍 API 无法区分「系统装饰」与「真实的非标准类型遮挡」，放宽它们需要窗口
uid 或层级的额外证据。

测试：`MainScreenWindowPolicyTest` 新增两例——两种 1×1 布局（含屏幕角落）
不遮挡、不进排除表、手势可用；2×1 与 2×2 内部覆盖仍按遮挡处理，
与 MainScreenObservationTest 的既有契约一致。

## #210 附带：结束应用的清单解析错误不带定位信息

vivo V2307A 实测 `am force-stop com.ss.android.ugc.aweme` 被
`[POLICY_BLOCKED] 应用清单格式无法确认` 拦截，而同一时刻 `/app/apps` 能正常
返回该包名——清单解析对每一行零容错，但没有设备输出样本，无法确定 ROM 的
`pm list packages -U` 实际格式变体，盲改正则有放行风险。

修复：解析失败时错误信息附带首条意外行（经 `SensitiveData.redact` 脱敏、
80 字符截断）并区分用户/系统清单；i18n 词条「应用清单格式无法确认」保持
（十语翻译继续生效），诊断详情作为数据附加。放行/拒绝语义完全不变——仍然
fail-closed，只是下次用户反馈能直接带出 ROM 的实际输出格式。

测试：`DeviceAppPolicyTest` 新增一例——用户/系统两侧各构造一条意外行，
断言错误消息标注清单侧并携带该行。

## 验证范围与限制

- Windows 宿主、source-only 模式：`MainScreenWindowPolicyTest`、
  `DeviceAppPolicyTest`、`ScreenTargetTest` 定向通过；`check-source-hygiene.py`
  与十语 changelog 门禁通过；全量 Standard Debug 单测见提交前运行记录
  （已知本地环境固有失败与 build208 一致）。
- #212 的豁免是纯逻辑窗口几何判定；「真机 HyperOS 上坐标手势恢复」依赖该 ROM
  窗口清单的实际行为，设备验收继续暂停，留待恢复后确认。
- #210 附带只增强诊断，不改放行语义；实际格式变体待用户按新错误信息反馈。
- 未构建、安装或发布 APK；未操作手机。
