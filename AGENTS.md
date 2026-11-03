# AGENTS.md

<!-- merge-worktree-rules-20261027 -->
> **2026-10-27 本轮合并的当前约束**：本轮在 F 盘隔离工作树 `F:/DSHA_RESTART/source/build198-sunset-20261027` 处理源码，后续工作树和构建输出继续放 F 盘；保留 `F:/DSHA_RESTART/source/build198` 中用户未提交的路径说明及 C 盘活动 UI 原型。用户已暂停手机验收，须等用户明确恢复后才能进行；不自动构建、安装或发布 APK，也不擅自操作设备或改动授权。只处理和验证 Standard，不运行或安装 Low，保留既有 Low 交付原字节。build198 已撤除整套电脑连接，本次合并不得恢复电脑连接及其入口；手机控制和既有本机/LAN 能力仍按用户要求保留。
>
> 下方双方版本、构建、CI、设备与路径记录均属于各自对应的历史提交、候选和时点；不能作为本次合并后的测试、设备验收或发布结论。历史文本中的“当前”、手机状态和工作路径按当时阅读；最终源码身份、依赖及验证范围以本次合并后实际检查为准。基线文档曾记录移动插件 3.2.1 / 上游 `10e58ce0c4ed424c7fbd9c91d48db06c6061b4e4`，Sunset 分支文档记录 3.2.2 / 上游 `4c798ecc554009e401dc1f166b73c314cdf5273f`，这两条仅为分支历史。

> **本轮源码身份**：`0.2.1-rc.3-20261027.1149-dsh0.2.1-rc.3` / build204，基于 build198 合并 Sunset-3090-Q 的 `5d694b65f5ceca9ccbd3d854a2b1efbacd53c159`。虚拟屏 MCP 的 launch 接受可选 `generation`，预检后只向原生 launch 发送 `coreInstance` 和 `package`；预检不构成原子代次围栏，取消或未知结果不能自动重放。tap/node 继续使用原观察代次。本轮未安装或发布 build204，手机验收仍暂停。

**build198 基线历史（路径和设备状态均为当时记录）**

<!-- build198-software-candidate -->
> **当前源码：build198软件候选** — `0.2.1-rc.3-20261025.2100-dsh0.2.1-rc.3` /Standard /dsh.android.client /E7E3非调试。真实合入Sunset19提交，修复授权切换隐藏暂停，撤下整套电脑连接，统一顶部安全区并解耦活动服务等待。91类806项Standard测试零失败/错误，7项Windows软链权限skip已由真实POSIX44项补验覆盖；Release Lint0错误/致命279警告。用户暂停手机验收，手机仍197；本条不证明198装机或发布。[范围与失败记录](docs/releases/v0.2.1-rc.3-build198-20261025.md)。实际源 `C:/Users/18768/.codex/worktrees/build198-from197/DSHA_RESTART`，不构建旧F173/S197。下方旧当前条目按历史阅读。

**Sunset 分支历史（以下证据仅适用于各自对应的提交、候选和时点）**

<!-- build202-current -->
> **2026-10-26 build202（Sunset-3090-Q，UI v5 原生试点 + 全屏终端整合）**：`0.2.1-rc.3-20261026.2115-dsh0.2.1-rc.3` / 202 / Standard / 测试包 `dsh.android.client.SunsetRNE` / 非调试 / CI 临时证书（非 E7E3）。合并 `Sunset-zhongdua-G`（merge commit `6a0cf18`）保留点击即拉起独立全屏 `TerminalActivity`、会话保护、手机 7+7 / 平板 8+8 附加键、侧栏三向态与横屏压缩；新增 v5 原生外壳试点：顶栏品牌/页面语境、手机悬浮胶囊导航、平板 80dp 左栏、启动主页活动条/三格真实状态/日志复制跟随导出，插件与设置沿用真实功能。最终 commit `11d2f348…`，[run 40342031367](https://github.com/ym2025szz/DSHA-prerelase/actions/runs/40342031367)（run 39）Source-only 与 Signed APK 两个 job 成功：全量 Standard **2788 tests / 0 fail / 0 error / 1 allowed skip**、7 项固定夹具实跑、Lint 0 error/0 fatal；APK 435,467,169 bytes，SHA `71f4159fbe9ae0b7beaa9448d1aca83185ef652e69d2d8dde374c63f6a309b27`，临时证书 `4dfb75c2…c20077`。失败尝试 run 40334979040（固定输入描述符不一致）与 run 40340264326（UpdateVersionPageTest 旧断言）保留，修正见 commit `c289b4d` / `11d2f34`。未安装、未操作手机；真机渲染、点击、TalkBack、字体缩放与旋转未验收。报告见 [本轮报告](docs/releases/v0.2.1-rc.3-build202-ui-v5-20261026.md)，回执 `/root/DSHA/deliveries/ui-v5-20261026/delivery.json`。

<!-- ui-replacement-20261027 -->
> **2026-10-27 build202 UI 替换二批（同 versionCode 202，Sunset-3090-Q）**：commit `4ecfff0`（UI 主体 `fc3998d`），[run 40371493536](https://github.com/ym2025szz/DSHA-prerelase/actions/runs/40371493536)（run 41 / attempt 2）source 与 APK 两个 job 成功。新增 `shell_page.xml` 页面组件层与 `bg_shell_*`，通用 surface/card/text/line 对齐 v5；设置、插件、运行配置、安装与环境、设备授权、数据与备份、更新、诊断、欢迎/首启、关于、环境恢复与 CardPage 内页统一 v5 标题/分组卡片/行/输入/按钮。终端修复侧栏新建/结束会话与“回到输入”首击被触摸焦点吞掉的问题（移除 `focusableInTouchMode`，收起侧栏后同步恢复输入焦点），并按用户要求删除侧栏白天/黑夜/系统主题分段；保留点击「终端」拉起独立全屏 `TerminalActivity`、返回不结束会话。全量 Standard **2790 tests / 0 fail / 0 error / 1 allowed skip**，7 项固定夹具实跑，格式门禁与 Release Lint 通过；APK 435,475,663 bytes，SHA `a72d02516c2511d3dfe2c45e5d887d3688e8dfeb03f0379e791f4e0f2dbaec45`，同测试证书 `4dfb75c2…c20077`（非 E7E3），手机副本 `/storage/emulated/0/Download/DSHA/dsha-ui-v5-replacement-4ecfff0c-SunsetRNE.apk`。失败尝试 run 40369592143（新增焦点回归测试发现延后一帧不生效，已改同步）与 run 40371493536 首轮 `LanPortPolicyTest` 端口竞态（重跑通过）保留。未安装、未操作手机；真机渲染、点击、TalkBack、字体缩放与旋转未验收。报告见 [本轮报告](docs/releases/v0.2.1-rc.3-build202-ui-replacement-20261027.md)，回执 `/root/DSHA/deliveries/ui-v5-replacement-20261027/delivery.json`。

> **2026-10-24 Sunset-3090-Q 分支 CI 范围**：用户取消本分支全部 Low 测试。当前所有工作流仅运行 Standard 的源码/完整输入 JUnit、Lint 与打包；宿主执行器也拒绝 Low，专用 Low 预览用例保留历史。Sunset 构建使用 rc.3 固定源码输入、`dsh.android.client.SunsetRNE` 测试签名身份，不与 E7E3 正式包混同。历史双版本回执只用于兼容读取，不视为本轮 Low 测试。
<!-- build198-native-landscape-current -->
> **2026-10-26 build198 后续（终端侧栏横屏压缩）**：commit `8d7e4b620c0f1df36c4ecebc0a27a20d5dbd4b2b`，[run 40306054023](https://github.com/ym2025szz/DSHA-prerelase/actions/runs/40306054023) 两个 job 成功；2551 tests / 0 fail / 0 error / 1 allowed skip，Lint 0 error/350 warnings；APK 307,898,702 bytes，SHA `af91812eae766d889b134d60d0525d701884f55c3406153dc7675dd3d68485a2`，临时证书 `5c58107033ce4ccb4edcfb04389b3a593b9ab48520244726f2d68147771ecc7a`（非 E7E3，需先卸载旧 SunsetRNE2 测试包）。横屏侧栏改为三列两行、44dp/10.5sp 动作、短标签+完整无障碍名、单行摘要、隐藏副标题/脚注并收紧主题/头部；竖屏与超高窗口恢复原两列三行。用户已覆盖更新并提供新截图：横屏三列两行/短标签生效、底栏约由侧栏 1/2 降到 1/3，竖屏保持完整标签；设备上是正式包 `dsh.android.client`，测试包 `SunsetRNE2` 未安装。报告见 docs/releases/v0.2.1-rc.3-build198-terminal-landscape-20261026.md，回执 `/root/DSHA/deliveries/native-terminal-landscape-20261026/delivery.json`。上一批原生主题包见下行。

<!-- build198-native-theme-current -->
> **2026-10-26 build198 后续（终端侧栏三向态、图标化与 2×2 底栏）**：feature commit `1be3f6ab91f25e50ae844cf1a1ca01e775d20df3`，格式清单修订 `fb95eeafd18cbbb2ade15887fc796295970309eb`，[run 40294484304](https://github.com/ym2025szz/DSHA-prerelase/actions/runs/40294484304) 两个 job 成功；2550 tests / 0 fail / 0 error / 1 allowed skip，Lint 0 error/350 warnings；APK 307,898,702 bytes，SHA `8815e6517c52297039156be21c66290a90629e238b6dda07213d55a6ca976d24`，临时证书 `8bd5e1e985a5fa443f047c20f9de1fee11f229505dbb2187202233e4a91d4124`（非 E7E3，不保证覆盖上一测试包）。终端侧栏/悬浮球改为浅蓝三向态、11 个新矢量、单行会话、三向态开关、2×2 主操作+输入辅助六动作、系统栏规则与软危险结束全部；手机 7+7 / 平板 8+8 附加键沿用。未安装、未操作手机；报告见 docs/releases/v0.2.1-rc.3-build198-native-terminal-theme-20261026.md，回执 `/root/DSHA/deliveries/native-terminal-theme-20261026/delivery.json`。首次 run 40294086317 因新测试未进格式清单失败，已保留。

<!-- build198-current -->
> **2026-10-26 build198（个人测试分支，CI 已产包）**：`0.2.1-rc.3-20261026.0644-dsh0.2.1-rc.3` /198 /Standard /Sunset 独立测试包 `dsh.android.client.SunsetRNE2`（正式包仍 `dsh.android.client`）/ 非调试 / CI 临时测试证书。终端悬浮球与工作台“菜单”改为会话侧栏（极简与窄工作台抽屉、≥840dp 内联），终端内核和运行保护沿用 build197。最终 commit `7eaf914ba46b3be3f0c84b87a82701487fbc6bf3` 的 [terminal-regression run 40248037705](https://github.com/ym2025szz/DSHA-prerelase/actions/runs/40248037705) 成功：source contracts + signed Standard test APK；2519 tests / 0 fail / 0 error / 1 allowed skip；APK 307,831,518 bytes，SHA `dad7d25983f1f7a783d130c0bcad08ad9f53cca7c77fa3d5079b614bd2c231d1`，证书 SHA `e962ccabeb1bcc58fd55cf2fdfc46f208b68e8ba9e7b75ee1b81efc4b6ef2599`（非 E7E3）。前两次运行失败（iOS-only API、无障碍/说明断言及无关 LAN 端口竞态）已修正并保留。未安装、未操作手机、未做设备验收；本地回执 `/root/DSHA/deliveries/terminal-20261026/delivery.json`，报告见 docs/releases/v0.2.1-rc.3-build198-terminal-sidebar-20261026.md。下方 build197 身份为历史。
> **2026-10-26 build198 后续源修订（同 versionCode 的 CI 测试包）**：commit `f8117b67948eb4c0691a3dbe236193d9966a39ec`，[run 40262205574](https://github.com/ym2025szz/DSHA-prerelase/actions/runs/40262205574) 成功；2526 tests / 0 fail / 0 error / 1 allowed skip，Lint 0 error/346 warnings。新增终端侧栏平板形态重判定（极简宽屏打开时内联）、深色侧栏与短窗密集底栏、宽屏设置/说明和“结束全部”逐项核验入口。APK 307,852,222 bytes，SHA `d72ef9f6f0581d54f27b24100273c46fcd95a4a28066e17992052c3a5950348b`，临时证书 `6b4d668b99f03b5d685d168c9dcc1901177197761df09094a02c0c0291fd10e1`（非 E7E3，不保证覆盖上一测试包）。未安装、未操作手机；报告见 docs/releases/v0.2.1-rc.3-build198-terminal-tablet-20261026.md，回执 `/root/DSHA/deliveries/terminal-tablet-20261026/delivery.json`。

<!-- build197-current -->
> **2026-10-25 build197**：`0.2.1-rc.3-20261025.2000-dsh0.2.1-rc.3` /197 /Standard /dsh.android.client /E7E3非调试；常规APK307703892字节，SHA `5cd635ab68ea61e74822e5d25a0b7ddc66ce21d03ea4412faa045c070a90f244`。单最近任务/主页返回、LAN状态、当前对话手机工具、十语及动态主提示，修复IME观察取消、旧poll和gated恢复空白。76类505项Standard零失败错误skip，Lint0错/致命337警告，17命令含9APK门禁，1168份当前格式证据。最终Android13同签名覆盖/实算SHA、当前会话手机任务确认→COMPLETED（空WebView语义节点边界保留）→finishOK、三次Main返回重进同task1680、一张recents、正常停止及原权限恢复；此前候选Settings树/阿语RTL/十语/法语证据独立标注，确认超时不删。正式/独立应急仍rc3共用36c3归档字节而运行根独立，Ubuntu10不变。仅固定私有ym2025szz/DSHA-prerelase，实际发布与完整源码/六资产commit/SHA见外部artifacts/deliveries/build197-navigation-language-20261025.json；不跑Low、不写公开DSHA/网站。实际源仍C:/Users/18768/.codex/worktrees/build190-from170/DSHA_RESTART，不构建F盘旧173；报告docs/releases/v0.2.1-rc.3-build197-20261025.md。官方中继未部署；手机5GB/新长时/WAN/全矩阵未验。下方旧当前条目按历史阅读。

<!-- build196-current -->
> **2026-10-24 build196**：`0.2.1-rc.3-20261024.1835-dsh0.2.1-rc.3` /196 /Standard /`dsh.android.client` /E7E3非调试，常规APK名；最终R7候选302430394字节，SHA `f3e053866dbf410edfc02587d64c9589ce9ac3c702f5bdef751b1f501c708d57`。正式与独立应急仍rc3、Ubuntu10；启动准备共用检查，保留事务/退出证明。修复助手真实会话/跨轮任务绑定、IME和固定输入区、物理屏android_node与自有悬浮窗识别、分享/另存为前台分流、双安装端口；首页移除后台任务入口，电脑连接从首页进入。最终45类279项Standard JUnit零失败/错误/skip、Release Lint0错/0致命/303警告、17条所选命令（其中9条APK检查）通过。R1真机发现两helper部署遗漏、R2发现编辑焦点、R4发现长记录输入及MCP缺语义工具，R6完成真实模型/语义控制/文件流程；R7仅完整标签模板修正，另验覆盖启动/原测试会话/分享/正常停止，最终1157份格式覆盖为1137+17+2+1。各SHA设备证据分开保留；不称首轮全绿。Android16同签名正常停止覆盖、真实已配置模型与文件/控制的有限范围见 docs/releases/v0.2.1-rc.3-build196-20261024.md，最终装机与交付以 artifacts/deliveries/build196-startup-usability-20261024.json 为准。只私有 DSHA-prerelase，不构建或验收Low，不写公开DSHA/网站；实际源码仍 C:/Users/18768/.codex/worktrees/build190-from170/DSHA_RESTART，禁止构建F盘旧173。官方中继未部署、真实PC/WAN及手机5GB/长时矩阵未完成，不扩写为全功能全设备验证。下方旧当前版本按历史阅读。

<!-- build195-current -->
> **2026-10-24 build195 当前软件交付**：`0.2.1-rc.3-20261024.0645-dsh0.2.1-rc.3` /code195 /Standard /`dsh.android.client` /E7E3非调试；常规APK名不变，302375411字节，SHA `acd8056283e95006b6dbc89f4b5d6f2a90d97fd3f20a1d90cd8160e87145556a`。新增独立电脑工作区/配对/连接网关、物理屏任务归属与悬浮助手；正式与独立应急DSH均rc3，共用归档字节，运行根独立，Ubuntu10不变。23类129项所选JUnit零失败/错误/skip、Release Lint0错/0致命/303警告及精确APK9门禁通过；宿主真实5GB上传/下载及强制中继分别留证。源与包仅交付私有DSHA-prerelase，发布成功及commit/ZIP摘要以外部回执`artifacts/deliveries/build195-remote-assistant-20261024.json`为准。官方中继未部署，缺SSH登录信息，不填虚构端点。用户明确本轮不做真机验收：未安装195，无194→195设备覆盖、手机完整远程/控制/语音/SAF/异网结论。不运行Low、不改其原件、不写公开DSHA/网站。实际源仍C:/Users/18768/.codex/worktrees/build190-from170/DSHA_RESTART，不构建F盘旧173。报告docs/releases/v0.2.1-rc.3-build195-20261024.md；插件规范仅docs/plugin-contract-v0.1.md参考提案。


<!-- build194-current -->
> 当前build194：`0.2.1-rc.3-20261024.0345-dsh0.2.1-rc.3` /Standard /dsh.android.client /E7E3 /非调试，rc3常规文件名。修复下载gzip重复处理导致prepare空响应，以及Android随包CA批量解析0张导致证书验证失败（逐PEM恢复原121根，原CA字节不变）。新runtimeId沿既有自动维护事务落地，禁止运行中热覆写。版本说明绑定code/version及双语raw；无匹配/404/410正常无更新，真实TLS/网络仍为未确认，不以无新版掩盖。17类98项StandardJUnit、9host、ReleaseLint0错/致命/256警告、9精确APK门禁；设备范围见报告。仅私有目标https://github.com/ym2025szz/DSHA-prerelase/releases/tag/v0.2.1-rc.3-20261024.0345，外部回执artifacts/deliveries/build194-update-download-20261024.json；Low及公开DSHA/网站不动。实际源仍C:/Users/18768/.codex/worktrees/build190-from170/DSHA_RESTART，不构建F盘旧检出。[本轮报告](docs/releases/v0.2.1-rc.3-build194-update-download-20261024.md)。


<!-- current-rc3-identity -->
> 2026-10-24 当前rc.3正式身份更正：`0.2.1-rc.3-20261024.0120-dsh0.2.1-rc.3` /code193 /Standard /dsh.android.client /E7E3 /非调试。当前APK和目录必须为release/0.2.1-rc.3/dsha-0.2.1-rc.3.apk；正式和独立应急DSH都是rc3。6处DSH peers已统一rc3，DSH归档d77261bf及Java/原生字节不变；本次Standard assemble/Release Lint0错/致命/255警告、6精确APK门禁。旧217JUnit/13host及实际192链仅按逐字节相同输入引用，不称本次重跑；手机未验。旧rc2命名193作为历史保留，不能再作为当前下载名。私有目标 https://github.com/ym2025szz/DSHA-prerelase/releases/tag/v0.2.1-rc.3-20261024.0120 ，最终外部回执artifacts/deliveries/build193-rc3-version-align-20261024.0120.json；不构建Low、不写公开DSHA/网站，实际源仍C:/Users/18768/.codex/worktrees/build190-from170/DSHA_RESTART。[当前报告](docs/releases/v0.2.1-rc.3-build193-version-align-20261024.md)。

<!-- build193-rc3-software -->
> 2026-10-24 build193软件检查：`0.2.1-rc.2-20261023.2047-dsh0.2.1-rc.3` /193 /非调试Standard /dsh.android.client /E7E3 /常规dsha-0.2.1-rc.2.apk；APK SHA `e85159a8b57b65584969d3eeb61f8ee140d94d3a369837c62ac6f451fa496f13`。正式DSH精确rc3，独立应急也固定rc3/d77261bf，包内同字节去重；Ubuntu10、引擎与包名不改。无障碍调度/epoch/截图回调、pnpm V3及旧日志/optional bundle观察器修复。仅Standard 37类217项JUnit、13host、Release Lint0错/致命/255警告及8精确APK门禁；初次夹具选择失败与修正补跑均保留。未做193手机验收、原vivo16或新5GB/重启续传/全矩阵，不声明旧exit1已证因。新APK/完整源码仅私有预发布目标，最终发布commit/服务器SHA/ZIP回执见artifacts/deliveries/build193-issue183-plugin-env-20261023.json。实际源码仍C:/Users/18768/.codex/worktrees/build190-from170/DSHA_RESTART，不构建F盘旧173，不跑Low、不写公开DSHA/网站。[报告](docs/releases/v0.2.1-rc.2-build193-issue183-plugin-env-20261023.md)。

<!-- build192-files:begin -->
> **2026-10-23 build192 文件传输修订（本地已验证，仅私有交付范围）**：`0.2.1-rc.2-20261023.1836-dsh0.2.1-rc.2` / code192 / `dsh.android.client` / E7E3 / 非调试Standard，常规文件名不变；292,301,320字节，SHA `f1b332bc087e999bf9bd8954a70c9c7ad869d0a04193933d446854f0e31fbf3c`。单文件十进制5,000,000,000字节、原生下载记录/续传、键盘最小避让与搜索脱敏前置诊断。Standard所选20类102项JUnit，失败/错误/skip 0/0/0；16host，Lint错误/致命0、警告254，精确APK7门禁；仅Android13有限设备操作，范围见本轮报告实际记录。5GB宿主往返不算手机验收；手机5GB/重启续传/Android16/realme与转发搜索恢复未验。R1拒绝和后续修正保留，不称一路全绿。仅私有仓库交APK/完整源码；最终commit/ZIP自身SHA/发布状态由外部回执核验，不公开DSHA/网站、不跑Low。本条覆盖下方旧当前版本说明。[本轮报告](docs/releases/v0.2.1-rc.2-build192-files-20261023.md)。
<!-- build192-files:end -->

> **2026-10-23 build192 本轮执行约束**：用户明确只要 Standard；检查、单测、Lint、构建和发布均只运行标准版，不运行 Low 任务。版本码更新为192，保持 `dsh.android.client` / E7E3 / 非调试及常规文件名。文件上下传单文件上限统一5,000,000,000字节；新增下载/相册/另存为/分享、持久记录与断点续传，空白首页键盘最小避让，搜索请求头脱敏诊断。本条仅记录实施要求，验收与发布状态以本轮最终回执为准；只向既有 `ym2025szz/DSHA-prerelase` 私有仓库交付 APK 与完整源码。

> **2026-10-23 build191 Sunset合并（本地已验证，仅私有交付）**：保持code191，诊断名 `0.2.1-rc.2-20261023.1640-dsh0.2.1-rc.2`，正式 `dsh.android.client`/E7E3/非调试Standard/同名APK；292,218,461字节、SHA `ba5fd1e19c5043fe000f02a3ffb56f125939a11b87bad0988d1677c73334cb26`。保留154c04c/9364bba两个parent及新增16项历史，不回退上一191修订。两版所选44类各242项JUnit零失败/错误/skip、每版5完整外部夹具实跑、18host（含真实managed应急五工具）、Lint零error/fatal且warning248/161、Standard assemble、APK9项与实际APK账号2模块Java链通过。source-only允许的缺夹具skip未用于该完整输入验收；SunsetRNE测试CI未启用，不操作设备、不新增Low包。CI输入隔离恢复11项，9归档核当前pin、2inputsJSON来源与当前recipe重建分开；DSH归档与1500字节一致，不重复算缩包收益。原Win夹具/旧JUnit计数断言失败保留，test-only纠正后全量重跑，不重打APK。仅[本轮私有Release](https://github.com/ym2025szz/DSHA-prerelase/releases/tag/v0.2.1-rc.2-20261023.1640)交付；远端/源码ZIP自身SHA由Release与外部回执证明。[本轮范围](docs/releases/v0.2.1-rc.2-build191-sunset-merge-20261023.md)覆盖下方旧当前说明，旧1500/分支设备证据不转记。

> **2026-10-23 build191 启动修订（本地已验证，仅私有交付）**：`0.2.1-rc.2-20261023.1500-dsh0.2.1-rc.2` /191 / `dsh.android.client` /E7E3非调试Standard；常规文件名 `dsha-0.2.1-rc.2.apk`，292,204,984字节，SHA `cbbce4efd2103ef4323a04788385dbe13ff02d94c7880537aa2bb0c7ee28a58f`。两版所选34类各182项JUnit零失败/错误/skip，Release Lint零error/fatal、warning248/161，Standard正式assemble、精确APK9项、实际APK两账号模块Java链与host12项及最终夹具复核通过；原始失败与纠正保留。删除截图DSH权限档位及旧值生效，屏幕确认和系统能力授权保留；CAS轻入口/FD扫描仍完整扫描，不信根mtime/旧成功标记，账号同凭据在途合并及启动/运行诊断分离见[本轮报告](docs/releases/v0.2.1-rc.2-build191-startup-20261023.md)。未验新APK设备，不转记旧2100或Sunset/Android16结果、不声称19.4秒样本已解决。仅[本轮私有Release](https://github.com/ym2025szz/DSHA-prerelase/releases/tag/v0.2.1-rc.2-20261023.1500)交付新APK及完整源码；实际远端与源码ZIP自身SHA由该Release及独立外部回执证明，不上传公开DSHA或网站；Low172及历史原件保留。

> **2026-10-22 插件速度维护（当前2100）**：`0.2.1-rc.2-20261022.2100-dsh0.2.1-rc.2` / code190 / `dsh.android.client` / E7E3非调试Standard，292,198,739字节，SHA `d62f17f23a7949f0f25285ec7afa44cb35de24a6bc91887ba4600023d18ffee0`。独立management查询保留原完整list合同；两worker仍完整校验，V2保留旧policy实际幂等锚点。2020候选真机被PATCH_CHAIN_MISMATCH拒绝，未交付；修正后真实Java六模块、旧版升级和精确APK门禁通过。两版44类各332项零失败/错误/skip、17宿主及APK7通过；Lint警告248/161保留，失败原件与test-only依赖修正分开记录。停止覆盖58文件首启前逐字节相同，手机proot工作台与有效插件启停已验；开关反馈到确认约3.10–3.39秒，页面三轮1.41–2.47秒波动，不能证明普遍整页提速。PR119的既有生产布局修复保留并补22项回归，1817像素记录仍有3帧局部标题异常，不能宣称全部PiP问题消失。默认画中画关闭，用户临时验收后已还原。公开DSHA上传仍暂停，仅私有维护交付。[本轮报告](docs/releases/v0.2.1-rc.2-build190-ui-performance-20261022.md)。

> **2026-10-22 UI与息屏维护（1817历史）**：`0.2.1-rc.2-20261022.1817-dsh0.2.1-rc.2` / code190 / `dsh.android.client` / E7E3非调试Standard，默认proot、可选proroot。APK 292,189,033字节，SHA `8460ee70f5f6946132372737cfc90f097e00e89dc519973af5eb783f833ac60e`。插件配置锁序、重复刷新和无关诊断等待已修；会话单击切换、侧栏按压闪屏与预设pending布局/旧V1升级已修。可见用户启动在当前帧申请FGS，真实promotion后准入，Stop/超时/服务丢失按精确票据及代次收尾，不后台任意提升或重放业务。两版关联42类各310项零失败/错误/skip、host14和APK6门禁通过；Lint零错误/致命、警告248/161。Low API24误用与测试SDK重载编译失败原件均保留，经过修正重跑，非一次连续全绿。最终包正常停止覆盖安装，58个受检查用户文件在首启前逐字节一致；双运行时点击后立即息屏建立后端与CPU锁，不能外推自然Doze/OEM或整机功耗。完整最终手机范围见本轮报告。Root递归READ子链接缺口及旧内部入口锁链未扩称修复；Low172原字节不动。公开DSHA上传/PR/发布仍暂停，仅私有维护交付。[本轮报告](docs/releases/v0.2.1-rc.2-build190-ui-performance-20261022.md) · [实现与回滚](docs/ui-performance-20261022.md)。

> **2026-10-22 性能维护**：`0.2.1-rc.2-20261022.0243-dsh0.2.1-rc.2` / code190 / `dsh.android.client` / E7E3非调试Standard。当前身份与默认proot/可选proroot覆盖旧规则。同台隔离完整初始化65.128→42.772秒，生产温启动proot三轮12.8/12.5/12.3秒、proroot6.8/5.2/5.4秒（有限顺序/缓存样本，不外推普遍百分比）。精确APK292,159,963字节、SHA `d77bf985a5f9ec49be0667625894bcd16c5f3a23df2210a837527af8a172a947`；两版选定JUnit各1,143项零失败/错误，为分段结果，skip3/76、Lint警告249/162保留。已做真实同签名停止覆盖、冷流程、双引擎Web、三次后台返回及独立应急正常启动/停止；无真实模型对话或新长时/功耗对照。升级trial的proroot126后回退proot不算proroot成功；Root递归READ子链接缺口未修复且已披露。公开DSHA上传/PR/发布仍暂停，仅私有维护交付；Low172不变。[本轮报告](docs/releases/v0.2.1-rc.2-build190-performance-20261022.md) · [调用链与回滚](docs/performance-startup-20261022.md)。

> 当前发布身份：**`dsh.android.client`**；本轮code191，E7E3与非调试要求保持，最终诊断版本及状态以顶部build191记录为准，覆盖下方历史包名、版本和发布规则。与旧 `com.dsh.client` 的数据独立，通过备份导出/恢复迁移。当前授权仅向既有私有维护仓库交付本轮APK及完整源码；公开 DSH-APP/DSHA 的上传、PR 与发布继续暂停。[包名变更历史](docs/releases/v0.2.1-rc.2-build190-package-rename-20261021.md)。


> F13 `0.2.1-rc.2-20261021.2107-dsh0.2.1-rc.2` / code190：修复目录服务依赖组合、附件resolver退休及后台临时身份失败后的恢复。Standard选定150类/952项JUnit（3跳过），Low选定150类/952项（76跳过），两版零失败/错误；Release Lint零错误、Standard/Low警告249/162，八项实际APK检查通过。原始失败、纠正及负控分开保留，不称一次连续全绿；无设备/ADB、真实账号/模型或新长时验收。[关联Release入口](https://github.com/ym2025szz/DSHA-prerelase/releases/tag/v0.2.1-rc.2-20261021.2107) · [范围与证据](docs/releases/v0.2.1-rc.2-build190-fix13-20261021.md)。实际远端可得性由Release与独立回执核验。

已发布F12保留原身份：[原记录](docs/releases/v0.2.1-rc.2-build190-fix12-20261021.md) · [原Release](https://github.com/ym2025szz/DSHA-prerelase/releases/tag/v0.2.1-rc.2-20261021.1413)；F11仍为未上传本地检查点。

> build190 本轮用户决定（2026-10-19）：以已发布的 build170 / `v0.2.0-rc2`（8362bba747fcf84c19e436c3dbb594a0fc388a67）为源码基座，定向迁入 build187（ac7bf8eda8b46a22f6412b17ec3574bf895b1876）的全局授权、虚拟屏、任务通知等功能。保留生产旧数据、宿主 v5 备份和迁移链，不引入实验版 fresh-only 数据/备份架构。引擎恢复 `proot + proroot`，首次安装与未显式选择时默认稳定 `proot`，显式 `proroot` 可选；不迁入 bxroot。正式身份保持 `com.dsh.client` / E7E3 / 非调试，versionCode 190，基础版本及下载文件版本 `0.2.1-rc.2`，内置官方 DSH `0.2.1-rc.2`，Ubuntu base10 不变。危险的完整格式化生产入口与擦除后端仍退休，外置工作区不能清理或重放旧事务写删。标准版优先，历史 Low172 与既有正式交付原件保留。总资产、描述符、签名打包及交付由主代理统一完成；下面 build170 数量与版本按基座历史阅读。

DSHA 的 AI / 新贡献者入口。本文让你不扫全库就能上手 —— 读它之前先读 [README.md](README.md)。

**F13当前入口**：`0.2.1-rc.2-20261021.2107-dsh0.2.1-rc.2` /code190。F13冻结源码2215成员/200篇Markdown，Java adoption 1029项。分发/格式证明与JUnit执行计数分开。固定输入包738,101,893 bytes，SHA-256 `da3a87e9782469136f08d37143582fec845af1a69406b9a534f8cb7f68249ed8`，原始输入直接展开到项目路径；不是完整源码ZIP的大小或摘要。本批软件与APK见[F13记录](docs/releases/v0.2.1-rc.2-build190-fix13-20261021.md)。UNKNOWN保留原DOM等待同身份核验，不授ready；旧票不复活、业务不重发；guest与launcher停止仍分证。

**仓库事实速览**：build170基座/code190，Java17/:app/arm64，DSH0.2.1-rc.2/base10，proot默认+proroot可选。固定包内启动入口及受管dsh包装继续保留。键盘动画期间不逐帧setPadding，原生有界帧通道用当前完整身份校验，网页只合成移动聚焦composer，末帧提交布局，失焦/分离/PiP清理。mobile3.2.2固定main4c798ec，保留安全服务端差异，不恢复上游撤回的#236。OneClickBackup复用NativeBackupJobs安全停止/v5/tar.gz/读回验证；Standard写Download/DSHA，Low旧系统首次选目录；不扫外置工作区、不默认导出原生密钥、不自动重启或重放。源码冻结、测试原始失败与补跑、精确APK资产及最终上传回执分开保存，不把浏览器机制回归写成手机FPS。完整源码ZIP直接展开固定输入；不能从F盘旧173构建。

维护入口见[文档总索引](docs/documentation-index.md)、[项目总手册](docs/project-architecture-handbook.md)、[权限](docs/device-permissions-architecture.md)、[环境/数据/插件](docs/runtime-data-plugin-architecture.md)、[运行引擎](docs/runtime-engines.md)、[端口](docs/ports-and-networking.md)、[DNS](docs/dns-configuration.md)、[虚拟屏](docs/virtual-screen-architecture.md)、[ADR0003](docs/adr/0003-virtual-screen-lifecycle-and-input.md)及[来源依据](docs/references-and-provenance.md)。

**build170基座历史策略**：移动 UI 3.0.5；第三方插件检查后自动提交/启用，允许依赖生命周期脚本和 pnpmfile；新导出为无密码 tar.gz，内部自动副本及旧加密原件读取保留。两版各45项关联检查和 Android13 非破坏性正式包验收范围见[历史发布说明](docs/releases/v0.2.0-rc2-notes.md)与[build170](docs/releases/v0.2.21-build170-20261009.md)。移动UI等当前值以上方本轮决定为准，下方目录数量为历史索引。

---

## 目录地图（build170历史数量；当前职责导航）

```
app/src/main/java/com/deepseekharness/app/
├── *.java（根包，27 个）      设备与服务层：无障碍、桥、LAN、悬浮条、Shizuku/Root、PTY
├── ui/      （69 个）         界面：MainActivity + 各 Fragment / Activity / 对话框
├── core/    （27 个）         编排：HarnessController、维护屏障、备份任务、诊断
├── runtime/ （18 个）         容器：ProotBootstrap、ContainerRuntime、WebProcessManager、装机
├── backup/  （62 个）         数据：v5 加密备份、恢复、迁移、格式化、自动备份（最大的包）
├── vscreen/ （10 个）         独立虚拟屏：通道 / 预览 / 输入 / 无障碍桥
├── recovery/(4 个)            独立应急 DSH：与正式环境完全分开的生命周期
├── bridge/  （3 个）          AppBridge 契约、AdbBridge、LAN 访问
├── data/    （3 个）          KeyVault（Keystore）、便携设置、导出
└── util/    （118 个）        纯逻辑：不 import 任何 Android API，必须配单测
```

### 根包（`com.deepseekharness.app`）——设备与系统服务

| 类 | 职责 |
|---|---|
| `HttpShellService` | **3090 桥本体**（约 105 KB，最大的类）：`/exec` 与 `/app/*` 共 24 个端点，token 门控 |
| `DshaAccessibilityService` | 无障碍：`uiDump / uiTap / uiTapText / uiInput / uiKey / uiSwipe / uiScreenshot` + 虚拟屏转发 |
| `DeviceBridgeService` | 设备通道总管：ADB / Shizuku / Root 三通道选择与保活 |
| `DeviceShellExecutor` / `DeviceAppInventory` / `DeviceSense` | 设备命令执行（白名单在 util）、应用清单、传感器 / 位置 / 手电 |
| `LanProxyService` / `LanAuth` | 局域网代理 `:3081`（token fail-closed）与鉴权 |
| `OverlayController` / `OverlayLines` | 流式悬浮条（歌词式 AI 输出 + 命令批准） |
| `HarnessService` / `WebPreviewActivity` | 前台服务 + 看门狗；Web 内嵌预览（WebView / Gecko） |
| `PtySession` | Termux terminal-emulator JNI 包装（真 PTY 终端） |
| `ShizukuShell` / `RootShell` / `ShellService` / `AdbKeepAliveReceiver` / `BootReceiver` | 通道实现与开机 / 保活接收器 |
| `DshaDocumentsProvider` | 文件管理器访问私有目录（MT 管理器等） |
| `DeviceShellPolicy` / `DeviceShellExecutor` | 受管设备命令解析与执行前守卫；批准不覆盖显式拒绝，Root 也受此策略检查 |
| `BridgeAskDialog` / `ConfirmReceiver` | 原生业务提问与安全确认通知回执；业务选择不由全局确认代答 |
| `DshaApp` / `UpdateDownloadService` | Application 入口（系统语言锁存等）；应用内更新下载 |

Root 维护以 [权限架构](docs/device-permissions-architecture.md) 与 [固定上游源码核查范围](docs/references-and-provenance.md) 为准：九个 su 候选及 metadata 检查不等于管理器识别或全版本兼容；65 秒只限制结果收集。2026-10-22 对照 `140c7ba` 的审查发现递归 READ 尚未核验子链接目标，短信保护存在实现覆盖缺口，不能写成“仅有文档问题”或“完整防绕过”；未修状态、回归与设备证据须分别记录。历史 `DangerShellGuard` 已不在生产调用链。

### 分层归属（改代码前先看这里）

| 层 | 关键类 | 职责 |
|---|---|---|
| `ui/` | MainActivity / WelcomeActivity / Launch·Config·Settings·Plugin·Workspace Fragment / WebPreviewActivity | 界面与启动门禁 |
| `core/` | `HarnessController`（约 57 KB，编排核心） | 启动 / 停止 dsh、鉴权链接捕获；**不要再往里塞新职责**，新逻辑写成独立协作者 |
| `core/` | `ConfigStore` | 配置唯一读写入口（端口 / 模型 / workdir / API key） |
| `core/` | `InstallRepository` / `MaintenanceCoordinator` / `RuntimeTasks` / `StartupDiagnostics` | 安装状态、维护屏障、运行任务锁、启动诊断 |
| `runtime/` | `ProotBootstrap` / `ContainerRuntime` / `WebProcessManager` | proot 命令组装；运行时选择 + BINDS 挂载清单；停止 Web（哨兵 + pid + 出生身份核验） |
| `runtime/` | `InstallPipeline` / `ManagedRuntimeAssets` / `RuntimeTrial` | 六步安装编排、受管资产、更新试运行 |
| `backup/` | `NativeBackupJobs` / `BackupArchive` / `HostDataTransaction` / `AutomaticBackups` / `FactoryReset` | v5 加密备份 / 恢复 / 数据事务 / 自动计划 / 格式化 |
| `recovery/` | `RecoveryController` / `RecoveryRuntime` / `RecoveryRepairBroker` | 独立应急 DSH，不经正式环境的迁移与启动锁 |
| `vscreen/` | `VirtualScreenManager` / `VirtualScreenAccessibility` / `VirtualScreenPreviewView` | App 持有认证连接与心跳；输入必须带最新帧号 |
| `bridge/` | `AppBridge`（契约）/ `AdbBridge`（无线配对 TLS1.3-PSK + SPAKE2）/ `LocalNetworkAccess` | 桥接缝与设备通道 |
| `data/` | `KeyVault` | Keystore AES/GCM 加密 API key |
| `util/` | `Constants` / `ShellQuote` / `Query` / `WebProcSel` / `DeviceShellPolicy` / `HttpProtocol` / `BackupScope` 等 118 个 | 纯逻辑，全包零 Android import，**必须配单测** |

---

## 测试布局（build170历史快照）

下列数量仅描述基座历史分布；F13实际选择类/XML、失败/错误/跳过与最终软件结果以[F13记录](docs/releases/v0.2.1-rc.2-build190-fix13-20261021.md)及统一回执为准，不从静态文件数推导执行数。

`app/src/test/java/com/deepseekharness/app/` 共 172 个测试类，分布：`util/` 112、`backup/` 45、`core/` 6、`runtime/` 6、`vscreen/` 1、`ui/` 1、根包 1（`LanAuthTest`）。

```bash
bash build.sh :app:testStandardDebugUnitTest   # 全量单测
```

锁定的不变式（重构时绝不能改坏）：

- `ShellQuote`：POSIX 单引号转义，恶意值不能逃逸。
- `Query`：逐参数名匹配（`indexOf(key+"=")` 会被后缀劫持，已修）；「参数为空」≠「参数不存在」。
- `BackupScope`：部分备份绝不叫 `DSHA-backup-*`（否则老版本当全量恢复会清掉配置与插件）；`dshPaths` 与 `mergeSubdirs` 一一对应。
- `WebProcSel`：认得出 dsh 进程、**绝不误杀 proot/proroot**（杀到容器启动器 = 环境连 App 一起带走）。
- `DeviceShellPolicy`：白名单默认拒绝，只接受可完整识别的单条 argv，不执行用户提供的 shell 程序。
- `HttpProtocol` / `SocketDispatch`：头部字节与总截止独立上限；LAN 连接登记、短请求 / 长流 / 反向 pump 独立有界。

---

## 安全模型（设计围绕这些边界）

先读 [docs/security-model.md](docs/security-model.md)。要点：

- **设备命令白名单默认开启**（`util/DeviceShellPolicy`）：格式化 / 分区 / SELinux / 系统属性写入直接拒绝；不认识的命令不放行；拦截返回 `[POLICY_BLOCKED]` + 退出码 126，没有逐条「仍然允许」按钮。Root / Shizuku / ADB 三通道同一套策略。build211 起用户可在设备能力授权中显式开启「不受限制的设备 shell」（默认关闭、可撤销、仅 root/Shizuku、硬禁止项仍拒绝，见 [安全模型](docs/security-model.md)）；不要把它扩展到 ADB 或用其它入口绕过开关。
- **桥的凭据护栏**（`util/BridgePathPolicy`）：`/app/export`、`/app/readfile` 拒绝 `.dsh` / `.ssh` / `.android` 等凭据区；字符串判据 + `getCanonicalPath()` 双重核验，堵软链接绕法。
- **最小权限默认**：所有敏感能力（ADB、Root、短信、传感器、位置、悬浮窗、LAN）默认关闭、可撤销；最小配置下 DSHA 照常跑 dsh。
- **备份凭据排除**：桥 token 整文件排除；`.credentials.yaml` 字段级剔除本机密钥、保留用户 API key。
- **这不是内核沙箱**：容器、插件、终端都在 App 的 Android UID 下运行。白名单保护随包入口，不约束任意自写代码 —— 别在文档里宣称「安全隔离」。
- 已知弱点（含 `danger-full-access`、历史明文备份、签名密钥待轮换）直接写在安全模型文档里，别删。

---

## 技术约束（围绕这些设计）

### 版本与构建

- **当前身份以本轮冻结回执及app/build.gradle为准**：code190、E7E3用于正式APK；0.1.7-rc2/147及A3F4均为历史记录，不作为本轮构建或签名输入。最新软件交付范围见上方决定。
- `standard`：minSdk 30，系统 WebView；`low`：minSdk 23，内置 GeckoView 143。构建任务 `assembleStandardRelease` / `assembleLowRelease`。兼容版 proot / loader / 终端 JNI 以 API 23 重编（`tools/build-low-proot.py`、`tools/termux-jni/`），**不要把标准版 proot 当 Android 6 可执行文件**。
- 离线 rootfs（`assets/offline-rootfs.bin`）**不提交**，由准备脚本生成；APK 用 `offline-rootfs.layout=split-runtime-v1` 标记 Ubuntu 与 `dsh-runtime.bin` 分包，构建核验两份摘要。
- 新 dsh 依赖由 `tools/dsh-runtime/package-lock.json` 锁定；`tools/prepare-runtime-descriptor.py` 生成独立受管契约描述，APK 版本仅作诊断来源。相同基础版本更新受管树，个人数据保持原位；**不因 UI-only APK 更新替换环境，不因普通 dsh 更新递增 Ubuntu 基础环境版本**。
- 离线 curl / git / 证书由 `tools/ubuntu-tools/packages.lock.json` 锁定（`tools/prepare-ubuntu-tools.py` 生成 `ubuntu-tools.bin`）；网页 ES 兼容依赖锁在 `tools/web-compat/`（`node tools/prepare-web-compat.mjs`）。生成文件不提交，构建核验摘要。
- 中英文界面：文案目录 `tools/i18n/messages.json`，构建生成 Java 文案字典；偏好 `system`（默认）/ `zh` / `en`；**系统语言必须在进程最早时刻锁存**（`SystemLanguage.initialize()`，早于任何 `Locale.setDefault`），否则「跟随系统」切一次就自我锁死。语言切换只重建界面，不停终端或 Web；用户输入、命令原文不做自动替换。
- 旧 WebView 的 `AbortSignal.any/timeout` 与 `crypto.randomUUID` 补齐：兼容脚本须进入受管 HTML 的应用脚本之前，并覆盖文档起始与 Worker。

### 数据与备份

- **自动备份默认开启**（2026-09-17 要求，覆盖更早的禁用规则）：`AutomaticBackups` 支持每日指定时间、1–168 小时间隔、停止后三种模式（`util/AutomaticBackupPolicy` 有单测）；到期仍在运行则延后，不强停 Web / 终端；保留最近 3 份已验证自动副本，手动与未知记录不自动删除。
- 用户导出 v5 使用无需密码的 `DSHA-data-v5-<UUID>.tar.gz`，封装 DSHADATA 完整性载荷；内部自动副本仍加密，旧加密原件读取仍需原密码并完整认证。原生 API Key 默认排除；先完整性/认证核验再预检，只写私有编号槽位。本机授权状态不能从用户归档生效；受管脚本、可执行配置和未知插件先隔离。
- 数据格式不能由 APK 或 dsh 版本号推导：`DataFormatEvidence` 对未经完整检查的数据保留 unknown；发件人声称的已验证格式不能成为删除原件的依据。
- 覆盖更新闲时清理可再生缓存及多余副本：保留至少两份健康且字节符合计划的副本，修改过或含额外文件的原件不删除；新重建通过核验后建立 `retired-proof.json` 再清理 `previous-linux`。
- 完整格式化的生产入口与擦除后端已退休，不恢复旧清理流程。外置工作区维护只断开私有引用，不遍历、覆盖或删除其内容；合法缓存清理仍须受控私有目标与 NOFOLLOW 核验。
- 覆盖安装用原包名、同签名、非调试正式包，做非破坏性检查；不在正式数据上运行强杀、删 Bash、坏插件等故障注入。

### 运行时与更新

- 新受管更新使用 `ManagedRuntimeTransaction` / `HostDataTransaction`；真实隔离 `RuntimeTrial` 核验静态资产 / 原生模块、实际端口 / 鉴权、存储和会话写后重开、网页内核与后端握手及 guest 退出后才能提交。回执保存在 rootfs 外并绑定文件摘要；至少保留直接健康前代。
- `RuntimeTrial` 必须记录实际 `runtimeMode`。所选 proroot 在鉴权前明确退出且无插件故障时，可按 `WebRuntimeFallback` 仅重试一次 proot；**不能把 proot 成功报告成 proroot 原生成功**。静态检查先选 proot，慢启动不触发重试。
- `RuntimeTrialRecords` 只轮换已确认关闭的现场：保留最近 3 条成功和 5 条失败；未关闭或标记异常的目录不得自动删除，也不能以累计次数设永久上限。
- 就绪检查必须解析实际 `/bin/bash` 的 ELF 和加载器路径；可执行位用 lstat（受 Android W^X 影响的 canExecute 不可靠）。损坏系统不得反复执行配置安全启动，应进入环境修复。
- 当前插件策略允许未锁定依赖解析、生命周期脚本与 pnpmfile；链接/本地包/更新通过格式、路径、实际依赖与摘要及事务检查后自动提交启用。用户主动禁用与安全模式意图保持，不恢复旧原生审阅门槛。`plugin-transactions.py` 记录候选与原件，未提交日志继续进入维护门禁。
- 系统插件与用户插件分层：系统插件源码不进用户备份，由当前 APK 按精确受管证明重建；`.disabled` 与旧实体冲突时禁用意图优先。
- `NARB_DISABLE_NATIVE_CACHE=1` 让原生扩展直接从随包目录加载（默认缓存在 `--link2symlink` 下首次悬空）。

### Web / 桥 / 终端

- 主Web从当前受管dsh包装和固定包内入口启动，加载共享RuntimeTools环境。默认自动端口`--port 0`，成功鉴权后记录实际端口；显式手动配置按当前选择策略处理。完整环境与入口见[运行机制](docs/runtime-data-plugin-architecture.md)，不复制旧固定3080命令。proot从nativeLibraryDir执行，不能移到filesDir绕过Android W^X。
- `WebPortPolicy.choose`在启动前选择固定/本机备用/动态端口；备用记忆仅本机，不跨设备迁移。当前生产未接入`retryBindRace`，不得把helper及单测写成已实现的绑定竞态自动重试。鉴权、保活与LAN使用实际端口，不按端口猜PID终止，见[端口专页](docs/ports-and-networking.md)。
- Web 启动等待鉴权不设强制终止时限（60 秒仅提示）；停止与维护必须共用 `WebProcessManager` 的 PID 身份 / 出生身份核验。
- 3090 桥协议版本做特性检测（`/app/version`，`BRIDGE_PROTOCOL=2`）；插件判版本用 `>=` 而不是 `==`。命令查询串预算不得缩到低于 8192 字符需求。
- LAN 正文由方法、状态码和明确传输边界决定；只有真正验证过的 101 WebSocket 握手可切隧道；不把 hop-by-hop 头移除后继续发送不匹配的编码体。
- PTY 出生身份在原生 fork/exec 握手中登记：子进程自读 stat，父进程确认后才 exec；不得退回 Java 启动后单次读取。PTY 与简易终端独立标签和会话，跨切页 / 旋转 / 语言切换保持进程；关闭单个标签必须核验该会话退出后再移除。终端会话 ID 永久递增，显示编号独立复用最小空缺。

### 应急 / 虚拟屏 / 设备

- **独立应急 DSH**：`recovery/` 不经过正式环境的迁移、数据绑定及全局启动锁；归档由 `tools/recovery-runtime/lock.json` 单独锁定；应急 Profile 仅有五个受控修复工具。不能用「原生安全 Profile」替代独立运行根。`RecoveryRepairBroker` 的 HTTP 接口不能确认写入：原生确认绑定候选、源摘要及数据代次，再经停止屏障和宿主事务。
- 应急资产去重：`recovery-asset-locations.json` 仅映射物理位置；仅当正式归档 SHA 与独立应急锁完全相同才共用包内字节，解压运行根仍独立，不读取正式 rootfs 作为救援依赖。
- **虚拟屏**（Standard，API30+）：App持有受管Core。raw坐标输入要求最新已观察帧；semantic节点/editor与anchored动作各有独立身份和目标复核，不强制像素静止、不静默互相fallback。UNKNOWN不重放，详见[架构](docs/virtual-screen-architecture.md)和[ADR0003](docs/adr/0003-virtual-screen-lifecycle-and-input.md)。
- **麦克风**：清单同时声明 `RECORD_AUDIO` 与 `MODIFY_AUDIO_SETTINGS`；由 `BrowserMicrophone` 按网页请求申请，不能在普通启动时预授权；只放行当前本机页面的纯音频请求，摄像头 / 屏幕音频拒绝；旧回调不能批准新页。
- **设备验收经验**：`uiautomator dump` 会抑制其他无障碍服务；截图授权绑定 DSH generation，停止 / 断连 / 撤销后失效；截图存应用私有 Pictures/DSHA，无需所有文件访问；PiP 可能盖住底部控件，自动化必须核对实际可见区域与屏幕方向；截图 / 读屏不可将敏感值写入公开取证文件。
- Shizuku 必须注册 `rikka.shizuku.ShizukuProvider`；标准版 13.1.5 / 兼容版 12.2.0（不用 overrideLibrary 掩盖 minSdk 24）。回调严格核对管理器 UID、Binder 描述符、单次请求与超时。
- 内置移动插件 `dsh-web-mobile` 为 3.2.2，固定上游 main `4c798ecc554009e401dc1f166b73c314cdf5273f`；`tools/apply-mobile-client-patches.mjs` 管理本地差异，不恢复上游已撤回的视口改动；上游已接管的 rc1 导航补丁不要重新套回。

---

## 启动契约（不可破坏）

- `welcomed == false` → `WelcomeActivity` → 点开始 → `MainActivity`；`MainActivity` 进入前校验 `welcomed`，否则永远回 Welcome。
- 正常运行检查 `.offline-extracted` 与完整环境身份。维护页允许进入受限主界面查看配置和日志；**此入口不能伪造就绪标记**，终端、Web 与插件执行仍须通过环境门禁。
- 启动失败自动进入原生 `StartupRecoveryActivity`；等待鉴权本身不是故障。最近五次脱敏启动记录独立保存在私有目录。
- 兼容重试仅在 proroot 已确认退出、鉴权前且无明确插件故障时进行一次，**不能由超时触发**。
- 配置快照由宿主 `ConfigurationSnapshots` 执行：正文逐文件存于 rootfs 外，保留三份健康与三份修复前快照；旧日志已提交后不得覆盖后来修改的配置。
- DocumentsProvider 保留 `root` 和 `linux/ubuntu/...` 的既有不透明文档 ID；guest 绝对软链接按 rootfs 解析，删除链接不递归目标；写文件先验证已打开的文件描述符再截断。
- 短信查询属于独立敏感能力：只允许当前 Android 用户的严格 `content query`；原生预授权默认关闭、可撤销、不从系统备份恢复。

---

## 已知 trap（搬自原版，骨架已按此设计）

- **停止靠 pid 文件 + 出生身份，不靠端口反查**：`/proc/net/tcp` 非 root 读不到（静默空），`/proc` 有 hidepid。启动时 `echo $$ > /root/.dsha-web.pid` 再 `exec node`；停止先写哨兵 `/root/.dsha-stopped`（看门狗见到就退出，否则「秒复活」）。旧 PID 复用按内核 `kill(pid,0)` + 出生身份二次核验，不得按裸 PID / 端口 / 名称强杀。
- **app 私有目录禁 `link(2)`**（SELinux）：proot 必须带 `--link2symlink`；`PROOT_L2S_DIR` 在 rootfs 内的 `.l2s` 必须绑定到相同宿主绝对路径，否则 dpkg 安装对硬链接 chown/stat 会报文件不存在。
- **两把签名钥匙各管一件事**：线上 APK 用历史发布证书（E7E3），增量更新清单用独立 keystore，绝不混用。
- Android 的 `FileInputStream(FileDescriptor)` 不接管传入描述符：宿主备份通过 `ParcelFileDescriptor` 自动关闭流明确转移所有权。
- 数据维护先关闭 PTY 与简易终端再取 `RuntimeTasks` 屏障；proroot 终端按本次独立会话核验并回收 guest，不能只结束启动器就释放工作锁。
- 维护成功文案不能代替就绪检查；未就绪时留在原生页，不自动跳回循环。维护页忙状态不使用插件查询 / 终端等通用锁冒充数据维护。

---

## 交付与验收

- 已发布F12保留[原记录](docs/releases/v0.2.1-rc.2-build190-fix12-20261021.md)与[原Release](https://github.com/ym2025szz/DSHA-prerelase/releases/tag/v0.2.1-rc.2-20261021.1413)；当前F13 `0.2.1-rc.2-20261021.2107-dsh0.2.1-rc.2`继续使用既有私有维护仓库。最终软件/APK见[本批记录](docs/releases/v0.2.1-rc.2-build190-fix13-20261021.md)；源码/附件/远端状态由独立回执核验，旧包不能代替本批证明。
- 所有设备操作已暂停，不安装、启动、关闭或对话；软件检查核对实际包名、code190、E7E3及`debuggable=false`，不以检查通过授权操作手机。
- Git保留构建必需、有notice/锁的小型JNI/vendor/解释器预编译依赖及27个必要历史构建源码、唯一website夹具；不宣称所有vendor native可从源码重现。大生成归档不入Git；Release完整源码ZIP额外含ci-assets.py逐SHA核对的原始大构建输入，配置工具链后按recipe构建，不需独立bundle，不能从APK逆恢复全部输入。6个审计JSON原路径白名单见[CONTRIBUTING](CONTRIBUTING.md)。私钥、用户数据、设备图/日志和历史现场不上传；ZIP自身摘要只写Release正文，避免自引用。
- 验收范围如实声明：不把本机验证扩写成 Android 6/7、16 KiB 或全部外部模型服务矩阵完成；Standard/Low 与虚拟屏能力按实际设备证据声明。
- 本轮只新增预发布范围与软件证据，历史fix2/fix3保持原样；不更新原仓库、原Release或官网清单，不部署网站。

---

## 编码约定

- 注释与 UI 串用中文；提交信息用中文 + `type:` 前缀说明原因。
- 每个协作者单一职责；纯逻辑抽到 `util/` 并配测试；不改历史 SharedPreferences 键名。
- 匹配现有风格：try/catch 包住有风险操作、优雅降级、失败 toast 给用户。
- 原生按钮和选项统一居中与字体边距；普通卡片、主按钮及文字状态共用主题资源，不用独立渐变或硬编码颜色制造同类框色差。布局修订跑 `LayoutAuditInstrumentation` 的 style 中英文验收（日夜、短屏、1.3 倍字体）。
- 应用弹窗统一 `DshaDialogBuilder`，自定义内容必须能在短屏和大字体下滚动；应用状态经 `UiStateText` 按显示边界重新渲染，不缓存语言。
- DNS 默认 `auto`：Node 双栈 `lookup` 仅在 `EAI_AGAIN` / `EAI_FAIL` 时重试一次 IPv4，不重放 HTTP 请求，不降级显式 IPv6；配置页另有 `ipv4`（glibc `no-aaaa`）与 `native`。Web、插件、shell 与 PTY 共用 `RuntimeTools` 的预加载环境。

---

## 文档与流程参考

| 文档 | 内容 |
|---|---|
| [BUILD.md](BUILD.md) | 环境（JDK 17 / SDK 37 / NDK 26 / Python 3.9+）、构建、签名与发布交付流程 |
| [docs/security-model.md](docs/security-model.md) | 安全模型与已知弱点 |
| [docs/plugins.md](docs/plugins.md) | 插件安装、打包契约与验证范围 |
| [docs/android-low.md](docs/android-low.md) | Low 版兼容差异（API 23 重编、desugaring、老系统权限） |
| [docs/releases/](docs/releases/) | 各版本发布说明与真机验收记录（新版本验收写这里） |
| [CHANGELOG.md](CHANGELOG.md) | 完整更新记录 |


## build190 fix1 历史补充（2026-10-19，所列有限验收仅属当时APK）

fix1当时本地日期版versionName **`0.2.1-rc.2-20261019.2352-dsh0.2.1-rc.2`**，versionCode190、`com.dsh.client`和下载文件名 `dsha-0.2.1-rc.2.apk` 不变。正式APK SHA-256 `5b1d8f5d6f71c7527f48d336013c74c6a2bf9e4f8811df6d416337deaac1ac38`，314,189,001 bytes；`fix1-dated-version-final` 91.39秒通过，两版各511项零失败/错误（Standard508pass/3skip、Low481pass/30skip），Lint0error、253/165warning，runtimeId仍为 `fa4759062b5cc65fea8bdff170fc4554083555f598d42596f57ad023d0442da8`。15:53:39 UTC正常停止后精确同签名覆盖并原名早交付，安装APK摘要匹配、UID/首次安装时间保持，f744入history。

本包15:55:04 UTC实际DSH就绪/proroot/0.2.1-rc.2/环境READY，日志 **7.7秒后端鉴权就绪、无fallback**；15:55:29 UTC同一既有验收会话可读。

真实DeepSeek第六轮先status STOPPED/cleanupfalse，再空portrait create（1,330ms）→同core active status→精确close（964ms），原始工具详情确认oktrue、EXITED、displayReleaseAcknowledgedtrue、STOPPED、cleanupPendingfalse。请求720×1280但接口仅orientation，实际1008×1792，不计精确分辨率通过。模型未调用其他app/input/screenshot/个人读取工具。

本包16:00:51 UTC关modal后Back→Main、同会话再入；16:02:53 Home实际PiP，16:06:11真实物理启动器→Main+原PiP，16:07:51原同任务同会话恢复全屏。小窗正常Stop后16:10:17旧Web任务移除、DSH未运行/环境READY、自有UID仅宿主、HarnessService不存在、独立DeviceBridge保留；16:11:48 proroot3.2秒后端鉴权就绪、无fallback，16:12:57以不同新任务恢复同会话，全屏1440×3200/PiPfalse，无旧小屏残留。

两轮网页ready日志（含人工停留）分别46.0秒/96.1秒，与后端鉴权7.7秒/3.2秒分开，不作全程启动耗时。

16:18:49 UTC临时ON撤回OFF/CACHE_VERIFIED/rev3；16:21:19 DSH正常停止，无Web/Harness、仅host，独立DeviceBridge保留；16:21:31停止同SHA覆盖，UID/首次安装时间保持，16:24:09新host进程读回OFF/NONE/rev3，仍无Web/Harness。用户随后切到其他应用即停止操作，16:25该屏排除为DSHA验收判据；没有新导出最后诊断。仅本机Standard/WebView所列有限路径已验。

源码zip预定 `artifacts/source/build190-fix1/dsha-build190-fix1-source.zip`，最终回执预定 `artifacts/deliveries/build190-fix1.json`，本次文档冻结时待生成核验，不声称已存在。旧SHA结果不转记本版。

此前已交付f744为 `0.2.1-rc.2 / 190`、E7E3、非调试，SHA-256 `f744bbb21b5df4c5eee3e72ee8321bfb5994d852c08b98d65b9b82d65c9c3738`，314,188,808 bytes。其两版JUnit各511项：Standard508通过/3skip、Low481通过/30skip、零失败及错误；Lint0error、253/165warning；actualAPK验证通过，75 installs/147 assets/371 launchers，runtimeId `fa4759062b5cc65fea8bdff170fc4554083555f598d42596f57ad023d0442da8`。这些仅为f744历史。该包精确同签名覆盖与早交付已完成；获准真实重启及boot变化核对后，实际Main为环境READY/Web未运行，全局ON/NONE/revision2可重开、原生ADB连接验证。MCP完整功能未通过，不扩大为全部设备验收；日期版已安装并有独立proroot就绪及空虚拟屏创建/关闭实测，其所列有限路径已完成，长期/完整矩阵未覆盖。源码zip与最终回执待生成核验，未上传GitHub、网站或Release。

- 本轮已修版本/注册表一致性、rc1迁移来源、wrapper兼容、BrowserOrigin私有根/官方空草稿、Harness退休、proroot入口/shim、授权XML尾LF、混合任务导航和MCP错误映射。生产数据、同代网页、未知退出保护及独立DeviceBridge保留；授权37、导航41、原生工具链21等宿主边界见[fix1报告](docs/releases/v0.2.1-rc.2-build190-fix1-20261019.md)。
- 历史692：Android13有限OFF修复；Android16已复现9ee Back/启动器故障，692实际READY、proroot6.1秒无fallback、Back/Main/物理启动器/同会话全屏有限通过。原生恢复OFF/revision1后，同SHA覆盖重开13:10:31 UTC读回ON/NONE/revision2；真实只读模型轨迹printf、/app/device、/app/plugins（163/0/0）有结果，授权失败和CLI缺profile后的修正保留。MCP status为NOT_RUNNING，ask-user人答FAIL并提交；create随后JOURNAL_UNAVAILABLE，不能算虚拟屏通过。
- f316修NioInstallationIdentity的FileStore拒绝，保留NOFOLLOW/CREATE_NEW/Unix0600与原身份；11项定向通过。实际第五轮已越journal，却ADB_CONNECT_FAILED→START_UNKNOWN、cleanupRequired=true、无core身份；旧屏障保留。后续原生ADB连接验证不能补成前次未执行证明；约21:20本地用户并行配对freeform→全屏片段不作单一因果归因。
- f744四类NOT_SENT修复仅承认helper实际退出、输出完整及精确有效未commit票的原子撤销；commit/撤销同锁，已commit/丢回执/失配/过期/截断/异常仍UNKNOWN。manager仍核对epoch、状态与journal提交，不清f316旧UNKNOWN、不自动重放。代理定向131项（1skip）零失败/错误及Python8/29项通过，后两项当次输出OK但未保存原始日志；独立四类复核21/21，设备效果待验。
- 旧UNKNOWN无core身份时，主负责人先正常停止DSH并确认无自有guest，取得用户**仅本次明确重启许可**后约15:03 UTC真实重启且确认boot变化，再覆盖f744。不能将其变成自动重启或清旧屏障的通用授权。f744最新有限Main/授权/ADB记录不代表MCP完整功能通过；当前日期版已有有限导航/停止换代、OFF跨进程及最终停止证明，未新增最后诊断导出；692局部结果和9ee115.042秒默认proot冷测各属旧SHA。Low190未验设备，未新增长时、全模型或全部ROM通过结论。本地交付以 artifacts/deliveries/build190-fix1.json 的实际生成及摘要核验为准；不发布GitHub。


<!-- merged-sunset-ci-history -->
> **2026-10-24 Sunset-3090-Q 分支 CI 范围**：用户取消本分支全部 Low 测试。当前所有工作流仅运行 Standard 的源码/完整输入 JUnit、Lint 与打包；宿主执行器也拒绝 Low，专用 Low 预览用例保留历史。Sunset 构建使用 rc.3 固定源码输入、`dsh.android.client.SunsetRNE` 测试签名身份，不与 E7E3 正式包混同。历史双版本回执只用于兼容读取，不视为本轮 Low 测试。
> 本段只描述分支历史；本轮正式 E7E3 Standard 构建不启用 SunsetRNE 或额外测试 APK。
