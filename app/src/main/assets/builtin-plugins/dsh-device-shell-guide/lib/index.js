/** DSHA 设备能力引导：使用 dsh 0.1.5 的消息工厂与已提交事件判重。 */
import bridgePort from './bridge-port.cjs';
import * as fs from 'node:fs';
import { createUserMessage } from '@deepseek-ai/dsh-llm';
export const name = 'dsh-device-shell-guide';
const sectionName = 'dsh:device-shell-guide';
const guideVersion = '4';
const hostCommand = '/usr/local/bin/bx-host';

/**
 * 原生调用只在 bxroot 引擎真正开启时才写进提示：launcher 设了 BXROOT_AUTO_HOST=1，
 * 且 APK 把 bx-host 单文件 bind 到了 guest。proot/proroot 或旧 APK 下两者都不成立，
 * 不能让模型以为 getprop/bx-host 可用。每次组装重查，不缓存。
 */
export function nativeCallsActive(env = process.env, exists = fs.existsSync) {
  try { return env.BXROOT_AUTO_HOST === '1' && exists(hostCommand); } catch { return false; }
}

/** 原生调用开启时的通道顺序：原生优先，/app/* 与 adb-shell 退为后备。 */
function nativeChannelLines(port) {
  return [
  '■ 通道顺序（bxroot 原生调用已开启，原生优先）：',
  '  1) Ubuntu 工作区内的开发与文件操作使用普通工具；Android 设备文件写入必须走受保护的设备 shell。',
  '  2) 原生调用（首选）：要调用 Android 系统命令时直接在 Ubuntu 里运行，不经桥、不经 ADB。' +
  '     guest 里没有的命令自动落到 /system/bin；与 Ubuntu 同名的（uname、ls、ps）写 bx-host <命令> [参数] 或 /system/bin/ 绝对路径。\n' +
  '     本机实测可直接用（App 身份够）：getprop、toybox（uname/df/free/uptime/ps/top/date）、cat /proc/meminfo、' +
  '     logcat -d（只看本应用与系统摘要）、pm path <包名>、pm list features、pm get-install-location、pm list permissions、dumpsys -l。',
  '  3) 需要 shell/root 身份的命令：直接用设备 shell（adb-shell，自动选 root / Shizuku / ADB），本机 App 身份下必然被拒，' +
  '   不要先用原生调用试：\n' +
  '     - settings get/list（缺 INTERACT_ACROSS_USERS，连读都不行）；写入类 settings put 一律禁止；\n' +
  '     - dumpsys（除 -l）、pm list packages（缺 INTERACT_ACROSS_USERS）、pm list users、cmd package resolve-activity；\n' +
  '     - cat /proc/net/tcp、netstat/ifconfig 之外的内核视图（这些是文件权限，不是策略红线）。',
  '  4) 白名单外的命令（adb-shell 返回 [POLICY_BLOCKED]/126）：input、screencap、screenrecord、monkey、' +
  '   am start / cmd activity start-activity、am broadcast、cmd notification、dmesg、ifconfig、pm install/uninstall/clear/disable、' +
  '   settings put。别换写法硬试，按下面的替代走：\n' +
  '     - 启动界面、发广播：dsha-am start/broadcast（以 DSHA 身份发起，不需要 root）；\n' +
  '     - 点按、输入、读屏、截屏：/app/ui/*（无障碍）；\n' +
  '     - 仍需要上面这些原始命令：dsha-su（见下方“不受限设备 shell”）。\n' +
  '   分区/SELinux/挂载/刷机任何通道都不执行，dsha-su 也一样。',
  '  唯一允许的结束应用方式是 adb-shell 的 am force-stop <完整包名>（会先刷新应用清单并区分用户/系统应用）。',
  '  5) App 层接口 /app/*（127.0.0.1:' + port + '）：读屏、点按、通知、剪贴板、传感器、位置、导出等只有 App 能提供的能力，' +
  '     以及上面被策略禁止的命令的唯一替代。',
  '  原生调用以本 App 的 UID 运行，不提权。除上面那几类，其余命令正常用原生调用；万一被 Android 拒绝，改用 /app/* 或 adb-shell，' +
  '  说明原因，不要换写法反复硬试。',
  '  设备命令保护同样适用于原生调用：禁止项（分区、SELinux、settings put/setprop、挂载、卸载/清数据等）不得改用原生调用执行，' +
  '  [POLICY_BLOCKED] 也不能借此绕过。',
  '  Android 原生 shell 回到本 Ubuntu 会话："$BXROOT_ENTER" --cwd /root -- <程序> [参数]（退出码原样返回）；' +
  '  参数里的路径不自动改写，需要时用 "$BXROOT_ENTER" --to-host <guest路径> / --to-guest <宿主路径> 转换。' +
  '  bx-host 只接受 Android bionic 程序：给它 Ubuntu 程序返回 126，找不到返回 127。',
  '  例：查型号/版本/属性用 getprop；看内存用 free 或 cat /proc/meminfo；启动应用走 dsha-am 或 /app/launch；' +
  '  看电池/窗口/活动栈走 adb-shell 的 dumpsys；点按和截屏走 /app/ui/*。',
  ];
}

const nativeCommandProbe = '/root/dsh-bin/dsha-am';

/** 已装原生能力命令（termux-* / dsha-am / dsha-su）时才提示；旧环境没有这些入口不能让模型去调用。 */
export function nativeCommandsInstalled(exists = fs.existsSync) {
  try { return exists(nativeCommandProbe); } catch { return false; }
}

function nativeCommandLines() {
  return [
  '■ 原生能力命令（PATH 里直接可用，底层是本机 3090 桥，不需要自己拼 curl）：',
  '  - Termux:API 风格：termux-clipboard-get / termux-clipboard-set <文本>、termux-notification -t 标题 -c 内容、' +
  'termux-toast、termux-vibrate -d 毫秒、termux-torch on|off、termux-open-url <链接>、termux-share <文件|文本>、' +
  'termux-location、termux-sensor -l / -s 名称、termux-battery-status、termux-dialog -t 问题 -v 选项1,选项2、termux-info。' +
  '返回 DISABLED / NO_PERMISSION 时照原话告诉用户去哪开，不要重试。',
  '  - dsha-am start -n 包名/.类 [-a ACTION] [-d 链接] [--es 键 值] | dsha-am broadcast -p 包名 -a ACTION：' +
  '以 DSHA 自身身份发 Intent（不需要 root/ADB），按设备能力授权中的屏幕确认策略询问用户；只支持 start/broadcast，' +
  '-d 只收 http(s)/geo/tel/mailto/market。用户拒绝返回 [ERR] USER_REJECTED，不要换通道重发。',
  '  - dsha-su <命令> 或 dsha-su -c "<脚本>"：不受限制的设备 shell，经已授权 root（优先）或 Shizuku 执行，' +
  '支持管道、重定向、脚本以及 input/screencap/am/settings put 等白名单外命令。默认关闭：返回“不受限制的设备 shell 未开启”时，' +
  '请用户到 设置 → 设备能力授权 打开“不受限制的设备 shell”，不要改用其它通道绕过。分区、SELinux、挂载、刷机仍被拒绝；' +
  '会改动系统或其它应用数据的命令先向用户说明再执行。ADB 通道不提供不受限执行。',
  ];
}
const languages = Object.freeze({ zh: 'Simplified Chinese', 'zh-Hant': 'Traditional Chinese', en: 'English',
  ru: 'Russian', ja: 'Japanese', ko: 'Korean', ar: 'Arabic', pt: 'Portuguese', la: 'Latin', fr: 'French' });
const supported = value => typeof value === 'string' && Object.hasOwn(languages, value);
const fallbackLanguage = () => supported(process.env.DSHA_UI_LANGUAGE) ? process.env.DSHA_UI_LANGUAGE : undefined;

/** Current native preference, queried anew for each assembly; no installation/profile/user files are rewritten. */
export async function selectedLanguage(signal) {
  let fd;
  try {
    if (signal?.aborted) return fallbackLanguage();
    fd = fs.openSync('/root/.dsh/.bridge_token', fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW | fs.constants.O_NONBLOCK);
    const info = fs.fstatSync(fd);
    if (!info.isFile() || info.size < 1 || info.size > 129 || (info.mode & 0o077) !== 0) throw Error('LANGUAGE_TOKEN_FILE');
    const bytes = Buffer.alloc(130), size = fs.readSync(fd, bytes, 0, bytes.length, 0);
    const token = bytes.subarray(0, size).toString('latin1').trim();
    if (size !== info.size || !/^[A-Za-z0-9_-]{1,128}$/.test(token)) throw Error('LANGUAGE_TOKEN_INVALID');
    fs.closeSync(fd); fd = undefined;
    const port = bridgePort();
    const timeout = AbortSignal.timeout(1500);
    const response = await fetch(`http://127.0.0.1:${port}/app/version`, {
      headers: { 'X-Token': token }, redirect: 'error',
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
    });
    if (!response.ok || bridgePort() !== port) throw Error('LANGUAGE_BRIDGE_UNAVAILABLE');
    const reader = response.body?.getReader();
    if (!reader) throw Error('LANGUAGE_BRIDGE_RESPONSE');
    const parts = []; let length = 0;
    try {
      for (;;) {
        const part = await reader.read();
        if (part.done) break;
        length += part.value.byteLength;
        if (length > 8192) throw Error('LANGUAGE_BRIDGE_LIMIT');
        parts.push(Buffer.from(part.value));
      }
    } finally { await reader.cancel().catch(() => {}); }
    const reply = JSON.parse(Buffer.concat(parts, length).toString('utf8'));
    const value = typeof reply.result === 'string' ? /^UI_LANGUAGE=([^\r\n]+)$/m.exec(reply.result)?.[1] : undefined;
    if (supported(value)) return value;
  } catch { /* Old/offline bridges retain the captured launch preference; user instructions still take priority. */ }
  finally { if (fd !== undefined) try { fs.closeSync(fd); } catch {} }
  return fallbackLanguage();
}

export function languageInstruction(language) {
  return (supported(language)
    ? `DSHA current response language: ${language} (${languages[language]}). Use this language for replies and explanations by default.`
    : 'Use the language requested by the user, otherwise follow the language of the current conversation.')
    + ' An explicit user request, custom prompt or user-owned AGENTS.md language instruction takes priority. '
    + 'This current DSHA default supersedes earlier DSHA built-in language defaults, including old Chinese-only guide snapshots; '
    + 'it does not replace user instructions. Keep quoted text, code, paths and tool identifiers in their original form.';
}

/**
 * 端口必须在每次组装提示时重读：Web 可能先于设备桥启动，桥重启也可能换端口。
 * 在模块加载时缓存会把双实例场景永久写成另一个安装的 3090。
 */
export function promptText(language = fallbackLanguage(), native = nativeCallsActive(), commands = nativeCommandsInstalled()) {
  const port = bridgePort();
  return [
  '【设备操作能力 · DSHA】你正运行在用户 Android 手机的容器里，可以干预这台实体手机。',
  languageInstruction(language),
  '用户在当前普通对话中明确要求操控这台实体手机时，先调用 mcp__dsha-android__android_control({action:"begin"})，再使用已内置的 android_get_state、android_screenshot、android_node 或 /app/launch 等能力执行；即使只启动应用也先 begin。真实会话和轮次由工具传输层自动绑定，不由模型猜测或传入，不新建聊天。不要只回复操作教程，也不要要求用户去工作区打开助手。普通讨论、引用、翻译或未明确要求执行时不要调用手机控制；不能靠关键词自动决定用户意图。',
  'begin 后原生显示当前对话的任务状态；首次实际读屏前会隐藏该任务窗口，保持隐藏到任务结束或用户暂停，避免遮挡目标界面。需要观察时先 android_get_state 或 android_screenshot；如果输入前窗口可见状态改变，取新观察后再明确决定下一步，不暗重试。已成功 begin 的手机任务完成或放弃时，在收尾中调用一次 android_control({action:"finish"})，关闭本轮执行界面和控制权，不取消模型对话；finish 后本轮不再重新 begin。UNKNOWN/PARTIAL 不自动重放，先核对实际状态，收尾不代表未知动作已成功或已退出。',
  '系统权限、悬浮显示或无障碍未授权时，按工具返回的原因引导用户完成必要授权；当前对话、原生执行状态、确认策略和系统授权各自生效，不伪造授权或自动批准。',
  '只有 curl 可用时，明确手机操作先 /app/ui/task-begin，完成时 /app/ui/task-end；两者和所有 /app/ui/* 请求都从实际工具环境传 taskSessionId=$DSH_PHONE_SESSION_ID、turnId=$DSH_PHONE_TURN_ID，不由模型提供身份。begin 只建立当前对话任务，不能替代权限或观察；end 完成停窗，不取消模型。',

  ...(native ? nativeChannelLines(port) : [
  '■ 三条通道，按这个顺序选：',
  '  1) Ubuntu 工作区内的开发与文件操作使用普通工具；Android 设备文件写入必须走受保护的设备 shell。',
  '  2) App 层接口 /app/*（走 127.0.0.1:' + port + '，零配置，不需要 ADB，也不需要 Shizuku）',
  '  3) 设备 shell（adb-shell 命令自动选择已授权的 root、Shizuku 或 ADB）—— 查询设备日志、普通设备文件操作和结束用户应用。',
  '  例：查设备状态用 /app/device 而不是 dumpsys battery；启动应用用 /app/launch 而不是 am start。',
  ]),

  '■ 屏幕目标先按用户要求区分：',
  '  - 实体主屏（display 0）使用 mcp__dsha-android__* 或 /app/ui/*；这些接口不会转到虚拟屏。',
  '  - 用户要求操作独立虚拟屏时，使用 mcp__dsha-vscreen__*。先 android_vscreen_status；已有或待清理的 Core 不得重新 create。' +
  '    保留状态返回的 coreInstance，再显式 see 或 tree 观察该屏，不从实体主屏截图推断虚拟屏内容。',
  '  - 虚拟屏读图用 see；可读控件用 tree 返回的 nodeId 做 node(click/focus/set_text)，' +
  '    已聚焦输入框可用 editor(get/edit/submit)。中文和动态页面输入优先选择已观察的 editable 节点或 editor；节点操作不靠旧像素坐标。',
  '  - 观察、节点或输入框身份必须来自同一 coreInstance/generation；坐标动作仍须最新观察帧，锚定操作遵守工具返回的原观察与一次性规则。' +
  '    结果 UNKNOWN 或执行后目标变化时不得自动重放，也不得切换到另一块屏幕兜底；工具或权限不可用时说明实际原因。',

  '■ 完整端点清单（读屏 / 点按 / 输入 / 截屏 / 通知 / 剪贴板 / 传感器 / 导出文件 …）：',
  '  # Credentials are sent by header file; do not place them in URLs or argv.',
  '  curl -s -H @/root/.dsh/.bridge_headers "http://127.0.0.1:' + port + '/app/help"',
  '  → 要用设备能力时查这一次，里面有每个端点的参数和写法。',
  '    清单刻意没写在这里 —— 它有十几 KB，写进提示词就是每一轮都替你付一次上下文。',
  '  ⚠ /app/help 与 /app/version 只有较新的 App 才有（老版本会把未知路径当 shell 命令处理，',
  '    回给你的东西不像清单）。那种情况按下面这份最小清单用，别反复重试 /app/help：',
  '    设备信息 /app/device · 应用列表 /app/apps?q= · 启动应用 /app/launch?pkg=',
  '    读屏 /app/ui/dump · 按文字点按 /app/ui/tap?text= · 输入 /app/ui/input?text=',
  '    滑动 /app/ui/swipe · 截屏 /app/ui/screenshot · 按键 /app/ui/key',
  '    通知 /app/notify?title=&text= · 提问 /app/ask?q=&options=a|b · Toast /app/toast?text=',
  '    剪贴板 /app/clip（写入加 ?text=） · 分享 /app/share?text= · 开链接 /app/open?url=',
  '    震动 /app/vibrate?ms= · 导出文件 /app/export?path= · 读 sdcard 文件 /app/readfile?path=',
  '    位置 /app/location · 传感器 /app/sensors 与 /app/sensor?type= · 手电 /app/torch?on=1',

  '■ 硬约束（几条，都别违）：',
  '  - 短信是独立敏感能力：用 adb-shell 执行 content query --uri content://sms，默认逐次确认。' +
  '    持续自动化需用户在设置 → 设备能力授权中开启短信读取，可随时撤销；仅当前 Android 用户，' +
  '    不允许发送、修改或删除短信。DSHA 授权不能替代 Android 权限，拒绝时不要绕过；',
  '  - 设备命令保护始终开启：允许读取任何目录（仍受 Android 权限限制）；根目录和系统目录、' +
  '    DCIM、Pictures、Android/data、Android/obb 及其子目录只读。普通 Download 文件操作允许；',
  '  - 禁止块设备/分区、SELinux 修改、settings put/setprop、挂载、刷机、卸载/清应用数据，未知命令直接拦截；',
  '  - 结束应用前自动刷新全部应用并分用户/系统两组；普通用户应用直接结束，系统应用与关键进程禁止结束。' +
  '    使用完整包名或明确正数 PID，禁止模糊匹配与整组杀进程；',
  '  - [POLICY_BLOCKED] 表示不执行，不能靠 Root、旧确认开关、环境变量放行。' +
  '    不可用普通 shell、Python/Node、裸 ADB 或 UI 操作绕过设备策略；',
  '  - 接口回 DISABLED / NO_PERMISSION、或 ADB 连不上时，照原话告诉用户去哪里开，' +
  '    不要反复重试同一条 —— 重试不会让开关自己变；',
  '  - 屏幕操作的节奏：实体主屏每次点按或输入之后先 /app/ui/dump；虚拟屏操作后重新 see 或 tree，再决定下一步。' +
  '    别凭记忆连点，界面可能已经变了；',
  '  - 设备 shell 优先使用用户已在「设置 → 设备能力授权」开启且经 root 管理器授权的 su，可直接执行，无需 ADB 配对；' +
  '    否则使用已授权的 Shizuku，再尝试已启用的 ADB。用 id 核验实际身份，不假设一定是 uid=2000 或 root。' +
  '    --su 只在明确需要 root 时使用，仍须原生授权，不能绕过任何设备保护；',
  '  - [EXECUTION_UNKNOWN] 表示命令可能已执行。先检查实际状态，不得切换通道或自动重放；',
  '  - 不要用 /root/dsh-bin/adb 或裸 adb 命令 —— 那是守卫包装脚本，会失败；',
  ...(commands ? nativeCommandLines() : []),
  ].join('\n');
}

export function apply(ctx) {
  let systemActive = false;
  const guided = new WeakMap();
  const inspected = new WeakSet();
  const renderedGuides = new WeakMap();
  const isGuide = event => event?.type === 'user/message'
    && event.data?.source?.kind === 'plugin' && event.data.source.plugin === name;
  // 作用域注入不会阻塞极简配置的启动；标准配置只走系统提示，避免双份引导。
  ctx.inject(['systemPrompt'], promptCtx => {
    promptCtx.systemPrompt.section({ name: sectionName, order: 150, text: context => {
      const text = promptText();
      if (context && typeof context === 'object') renderedGuides.set(context, text);
      return text;
    } });
    promptCtx.effect(() => { systemActive = true; return () => { systemActive = false; }; });
  });
  // The rc3 section text callback is synchronous. Its official assembly waterfall is async,
  // so only this owned section is refreshed there; complete/custom prompts remain authoritative.
  ctx.on('system-prompt/assemble', async (assembly, context, next) => {
    const ownedText = context && typeof context === 'object' ? renderedGuides.get(context) : undefined;
    if (context && typeof context === 'object') renderedGuides.delete(context);
    const value = await next();
    if (ownedText === undefined || !value.sections?.some(section => section.name === sectionName && section.text === ownedText)) return value;
    const language = await selectedLanguage(context?.signal);
    return { ...value, sections: value.sections.map(section => section.name === sectionName && section.text === ownedText
      ? { ...section, text: promptText(language) } : section) };
  });
  const eventKey = event => isGuide(event) ? event.data.source.sections?.find(section =>
    section.name?.startsWith(`${name}:v${guideVersion}:`))?.name : undefined;
  ctx.on('session/event', (session, event) => { const key = eventKey(event); if (key) guided.set(session, key); });
  ctx.on('agent/pre-step', async ({ agent, step, signal }, next) => {
    const decision = await next();
    if (systemActive || decision.kind !== 'enter' || signal?.aborted || step !== 1) return decision;
    const session = agent?.session;
    if (!session || !Array.isArray(decision.messages) || decision.messages.length === 0) return decision;
    if (!inspected.has(session)) {
      inspected.add(session);
      // 恢复/分叉的历史也参与判重；不修改已有 V3 事件或消息身份。
      for (const event of session.snapshotEvents?.() ?? []) {
        const key = eventKey(event); if (key) guided.set(session, key);
      }
    }
    const language = await selectedLanguage(signal);
    if (signal?.aborted) return decision;
    // 原生调用开关也进判重键：同一会话换到/换出 bxroot 时要重新给一次引导。
    const key = `${name}:v${guideVersion}:${language ?? 'conversation'}${nativeCallsActive() ? ':native' : ''}${nativeCommandsInstalled() ? ':commands' : ''}`;
    if (guided.get(session) === key) return decision;
    const text = promptText(language);
    const guide = createUserMessage({
      content: [{ type: 'text', text }],
      source: { kind: 'plugin', plugin: name, form: 'snapshot', sections: [{ name: key, text }] },
    });
    // 后续插件仍可能拒绝步骤，因此仅在 session/event 确认写入后标记已注入。
    return { ...decision, messages: [guide, ...decision.messages] };
  }, { prepend: true });
}
