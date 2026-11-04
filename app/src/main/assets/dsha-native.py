#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DSHA 原生能力命令：Termux:API 风格包装、以 DSHA 身份发 Intent、不受限设备 shell。

所有命令只是 3090 桥（/app/*、/device/execute）的薄封装，不新增任何权限：
- termux-*   转调 /app/* 能力（剪贴板、通知、电量、定位、传感器……）；
- dsha-am    以 DSHA 自己的包名启动界面/发广播（/app/intent），不需要 root；
- dsha-su    用户在设置里开启“不受限制的设备 shell”后，经已授权 root/Shizuku 执行任意命令。

调用名决定行为（argv[0] 的 basename），由 /root/dsh-bin 下的同名包装脚本分发。
"""
import json
import os
import re
import stat
import sys
import urllib.error
import urllib.parse
import urllib.request

TOKEN_FILE = '/root/.dsh/.bridge_token'
PORT_FILE = '/root/.dsh/.bridge_port'
DEFAULT_PORT = 3090


class BridgeError(Exception):
    def __init__(self, message, code=1):
        super().__init__(message)
        self.code = code


def bridge_port():
    try:
        info = os.lstat(PORT_FILE)
        if stat.S_ISREG(info.st_mode) and 1 <= info.st_size <= 5 and (info.st_mode & 0o077) == 0:
            fd = os.open(PORT_FILE, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            try:
                raw = os.read(fd, 6)
            finally:
                os.close(fd)
            if re.fullmatch(rb'[0-9]{1,5}', raw) and 1 <= int(raw) <= 65535:
                return int(raw)
    except OSError:
        pass
    raw = os.environ.get('DSHA_BRIDGE_PORT', '')
    if re.fullmatch(r'[0-9]{1,5}', raw) and 1 <= int(raw) <= 65535:
        return int(raw)
    return DEFAULT_PORT


def token():
    try:
        with open(TOKEN_FILE, encoding='ascii') as source:
            value = source.read(200).strip()
    except (OSError, UnicodeDecodeError) as error:
        raise BridgeError('设备桥未准备好，请打开 DSHA 后重试（%s）' % type(error).__name__, 124) from error
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', value):
        raise BridgeError('设备桥凭据无效，请重启 DSHA', 124)
    return value


def call(route, params=None, timeout=60):
    """GET 桥端点，返回 result 字符串；网络错误与未授权分开报告。"""
    query = urllib.parse.urlencode(params or {})
    url = 'http://127.0.0.1:%d%s%s' % (bridge_port(), route, ('?' + query) if query else '')
    request = urllib.request.Request(url, headers={'X-Token': token()})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(request, timeout=timeout) as response:
            body = response.read(4 * 1024 * 1024)
    except urllib.error.URLError as error:
        raise BridgeError('无法连接 DSHA 设备桥：%s' % getattr(error, 'reason', error), 124) from error
    try:
        result = json.loads(body.decode('utf-8'))['result']
    except (ValueError, KeyError, TypeError) as error:
        raise BridgeError('设备桥响应格式无法识别', 125) from error
    if result == '[UNAUTHORIZED]':
        raise BridgeError('设备桥拒绝了凭据，请重启 DSHA', 124)
    return result


MIN_APP_CODE = 211  # /app/intent 与不受限设备 shell 自 build211 起提供


def require_app_code(feature):
    """旧 DSHA 不认识新端点：/app/intent 回 [NO_CMD]，/device/execute 会静默忽略 unrestricted
    而按白名单执行。先查版本，避免把“没执行”或“降级执行”当成功。"""
    info = call('/app/version', timeout=10)
    match = re.search(r'^APP_CODE=(\d+)$', info, re.M)
    if not match or int(match.group(1)) < MIN_APP_CODE:
        raise BridgeError('%s 需要 DSHA build%d 或更新（当前 %s），请先更新 DSHA' % (
            feature, MIN_APP_CODE, match.group(1) if match else '未知'), 1)


def emit(text):
    if text:
        sys.stdout.write(text if text.endswith('\n') else text + '\n')


def fail_code(result):
    """把桥的文字状态折成退出码：拒绝/错误非零，便于脚本判断。"""
    head = result.lstrip()[:40]
    if head.startswith(('[POLICY_BLOCKED]', 'FORBIDDEN')):
        return 126
    if head.startswith(('DISABLED', 'NO_PERMISSION', '[ERR]', 'ERROR', 'NOT_FOUND', 'NO_', '[NO_CMD]', '[APP_BACKGROUND]')):
        return 1
    if head.startswith(('[RESULT_UNKNOWN]', '[EXECUTION_UNKNOWN]')):
        return 125
    return 0


def simple(route, params=None):
    result = call(route, params)
    emit(result)
    return fail_code(result)


def options(argv, spec):
    """极小的 -x 值 解析器：spec 映射选项到参数名；剩余位置参数原样返回。"""
    values, rest, i = {}, [], 0
    while i < len(argv):
        arg = argv[i]
        if arg in spec:
            if i + 1 >= len(argv):
                raise BridgeError('%s 缺少参数' % arg, 2)
            values[spec[arg]] = argv[i + 1]
            i += 2
        elif arg in ('-h', '--help'):
            raise BridgeError('help', 0)
        else:
            rest.append(arg)
            i += 1
    return values, rest


def read_stdin_if_piped():
    if sys.stdin is None or sys.stdin.isatty():
        return ''
    return sys.stdin.read(64 * 1024)


# ---- Termux:API 风格 -------------------------------------------------------

def termux_clipboard_get(argv):
    result = call('/app/clip')
    if fail_code(result) == 0:
        sys.stdout.write(result)
        return 0
    emit(result)
    return fail_code(result)


def termux_clipboard_set(argv):
    text = ' '.join(argv) if argv else read_stdin_if_piped()
    if not text:
        raise BridgeError('用法：termux-clipboard-set <文本>  或  echo 文本 | termux-clipboard-set', 2)
    return simple('/app/clip', {'text': text})


def termux_notification(argv):
    values, rest = options(argv, {'-t': 'title', '--title': 'title', '-c': 'text', '--content': 'text'})
    text = values.get('text') or ' '.join(rest) or read_stdin_if_piped()
    params = {'text': text}
    if values.get('title'):
        params['title'] = values['title']
    return simple('/app/notify', params)


def termux_toast(argv):
    text = ' '.join(a for a in argv if not a.startswith('-')) or read_stdin_if_piped()
    return simple('/app/toast', {'text': text})


def termux_vibrate(argv):
    values, _ = options(argv, {'-d': 'ms'})
    return simple('/app/vibrate', {'ms': values.get('ms', '300')})


def termux_torch(argv):
    state = argv[0] if argv else 'on'
    if state not in ('on', 'off'):
        raise BridgeError('用法：termux-torch on|off', 2)
    return simple('/app/torch', {'on': '1' if state == 'on' else '0'})


def termux_open_url(argv):
    if len(argv) != 1:
        raise BridgeError('用法：termux-open-url <http(s)/geo/tel/mailto/market 链接>', 2)
    return simple('/app/open', {'url': argv[0]})


def termux_share(argv):
    values, rest = options(argv, {'-a': 'action', '-c': 'type', '-t': 'title'})
    if rest and os.path.isfile(rest[0]):
        return simple('/app/share', {'path': os.path.realpath(rest[0])})
    text = ' '.join(rest) or read_stdin_if_piped()
    if not text:
        raise BridgeError('用法：termux-share <文件或文本>', 2)
    return simple('/app/share', {'text': text})


def termux_location(argv):
    values, _ = options(argv, {'-p': 'provider', '-r': 'request'})
    params = {'fresh': '1'} if values.get('request') in ('once', 'updates') or not values.get('request') else {}
    return simple('/app/location', params)


def termux_sensor(argv):
    values, rest = options(argv, {'-s': 'name', '-n': 'count', '-d': 'delay'})
    if '-l' in rest or '--list' in rest or not values.get('name'):
        return simple('/app/sensors')
    return simple('/app/sensor', {'name': values['name']})


def termux_battery_status(argv):
    result = call('/app/device')
    if fail_code(result) != 0:
        emit(result)
        return fail_code(result)
    # /app/device 是面向人类的文本；尽量抽出电量行，抽不出就整段给出，不编造数值。
    lines = [line for line in result.splitlines() if re.search(r'电量|电池|[Bb]attery|充电|[Cc]harg', line)]
    emit('\n'.join(lines) if lines else result)
    return 0


def termux_dialog(argv):
    values, rest = options(argv, {'-t': 'title', '-i': 'hint', '-v': 'values'})
    question = values.get('title') or ' '.join(rest) or '请选择'
    params = {'q': question}
    if values.get('values'):
        params['options'] = '|'.join(v.strip() for v in values['values'].split(',') if v.strip())
    return simple('/app/ask', params)


def termux_wake_lock(argv):
    emit('DSHA 前台服务运行期间已保持唤醒，无需单独加锁。')
    return 0


def termux_info(argv):
    for route in ('/app/version', '/app/device'):
        emit(call(route))
    return 0


# ---- dsha-am：以 DSHA 身份发 Intent ---------------------------------------

AM_USAGE = (
    '用法：dsha-am start -n 包名/.类 [-a ACTION] [-d 链接] [-c CATEGORY] [--es 键 值]...\n'
    '      dsha-am start -p 包名 -a ACTION [-d 链接]\n'
    '      dsha-am broadcast -p 包名 -a ACTION [--es 键 值]...\n'
    '以 DSHA 自身身份发起（不需要 root/ADB），按设备能力授权中的屏幕确认策略询问。\n'
    '只支持 start / broadcast；-d 只接受 http(s)/geo/tel/mailto/market。'
)


def dsha_am(argv):
    if not argv or argv[0] in ('-h', '--help'):
        raise BridgeError(AM_USAGE, 0 if argv else 2)
    if argv[0] not in ('start', 'broadcast'):
        raise BridgeError('dsha-am 只支持 start / broadcast；其它 am 子命令需要 shell/root 身份\n' + AM_USAGE, 2)
    require_app_code('dsha-am')
    command = ' '.join(shell_quote(a) for a in argv)
    return simple('/app/intent', {'cmd': command})


# ---- dsha-su：不受限设备 shell --------------------------------------------

SU_USAGE = (
    '用法：dsha-su [--root] <命令...>      或   dsha-su -c "<shell 脚本>"\n'
    '经已授权的 root（优先）或 Shizuku 执行，支持管道、重定向与脚本。\n'
    '需在 DSHA 设置 → 设备能力授权中开启“不受限制的设备 shell”；默认关闭。\n'
    '分区、SELinux、挂载、刷机仍被拒绝；ADB 通道不提供不受限执行。'
)


def dsha_su(argv):
    force_root = False
    if argv and argv[0] == '--root':
        force_root, argv = True, argv[1:]
    if not argv or argv[0] in ('-h', '--help'):
        raise BridgeError(SU_USAGE, 0 if argv else 2)
    if argv[0] == '-c':
        if len(argv) != 2:
            raise BridgeError(SU_USAGE, 2)
        command = argv[1]
    else:
        command = ' '.join(shell_quote(a) for a in argv)
    require_app_code('dsha-su')
    result = call('/device/execute', {'cmd': command, 'unrestricted': '1', 'su': '1' if force_root else '0'}, timeout=160)
    try:
        value = json.loads(result)
    except ValueError:
        emit(result)
        return fail_code(result) or 1
    output = value.get('output', '')
    code = value.get('exit')
    # 桥把 [EXIT=n] 附在输出末尾；拆出来作为本地退出码。
    match = re.search(r'\n?\[EXIT=(\d+|timeout|interrupted)\]\s*$', output)
    if match:
        output = output[:match.start()]
        if match.group(1).isdigit():
            code = int(match.group(1))
        else:
            code = 124
    if output:
        sys.stdout.write(output if output.endswith('\n') else output + '\n')
    return code if isinstance(code, int) and 0 <= code <= 255 else 125


def shell_quote(value):
    if value and re.fullmatch(r'[A-Za-z0-9_@%+=:,./-]+', value):
        return value
    return "'" + value.replace("'", "'\"'\"'") + "'"


COMMANDS = {
    'termux-clipboard-get': termux_clipboard_get,
    'termux-clipboard-set': termux_clipboard_set,
    'termux-notification': termux_notification,
    'termux-toast': termux_toast,
    'termux-vibrate': termux_vibrate,
    'termux-torch': termux_torch,
    'termux-open-url': termux_open_url,
    'termux-share': termux_share,
    'termux-location': termux_location,
    'termux-sensor': termux_sensor,
    'termux-battery-status': termux_battery_status,
    'termux-dialog': termux_dialog,
    'termux-wake-lock': termux_wake_lock,
    'termux-wake-unlock': termux_wake_lock,
    'termux-info': termux_info,
    'dsha-am': dsha_am,
    'dsha-su': dsha_su,
}


def main(argv):
    name = os.path.basename(argv[0]) if argv else ''
    args = argv[1:]
    if name in ('dsha-native', 'dsha-native.py'):
        if not args or args[0] not in COMMANDS:
            sys.stderr.write('用法：dsha-native <%s> [参数]\n' % '|'.join(sorted(COMMANDS)))
            return 2
        name, args = args[0], args[1:]
    handler = COMMANDS.get(name)
    if handler is None:
        sys.stderr.write('dsha-native: 未知命令 %s\n' % name)
        return 2
    try:
        return handler(args)
    except BridgeError as error:
        if str(error) != 'help':
            (sys.stdout if error.code == 0 else sys.stderr).write(str(error) + '\n')
        return error.code
    except KeyboardInterrupt:
        return 130


if __name__ == '__main__':
    sys.exit(main(sys.argv))
