#!/usr/bin/env python3
"""dsha-native.py 的宿主端测试：本地假桥核对路由、参数与退出码，不接触真实设备。"""
import http.server
import importlib.util
import io
import json
import os
import sys
import tempfile
import threading
import unittest
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/assets/dsha-native.py'


def load():
    spec = importlib.util.spec_from_file_location('dsha_native_under_test', SOURCE)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Bridge(http.server.BaseHTTPRequestHandler):
    calls = []
    replies = {}

    def log_message(self, *args):
        pass

    def do_GET(self):
        url = urllib.parse.urlsplit(self.path)
        params = dict(urllib.parse.parse_qsl(url.query, keep_blank_values=True))
        Bridge.calls.append((url.path, params, self.headers.get('X-Token')))
        default = 'BRIDGE_PROTOCOL=3\nAPP_CODE=211' if url.path == '/app/version' else 'OK'
        result = Bridge.replies.get(url.path, default)
        body = json.dumps({'result': result(params) if callable(result) else result}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class DshaNativeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Bridge)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        token = Path(self.temp.name) / 'token'
        token.write_text('t' * 32)
        os.chmod(token, 0o600)
        self.module = load()
        self.module.TOKEN_FILE = str(token)
        self.module.PORT_FILE = str(Path(self.temp.name) / 'missing-port')
        os.environ['DSHA_BRIDGE_PORT'] = str(self.server.server_address[1])
        Bridge.calls.clear()
        Bridge.replies.clear()

    def tearDown(self):
        self.temp.cleanup()
        os.environ.pop('DSHA_BRIDGE_PORT', None)

    def run_cmd(self, *argv, stdin=''):
        out, err = io.StringIO(), io.StringIO()
        old = sys.stdout, sys.stderr, sys.stdin
        stream = io.StringIO(stdin)
        stream.isatty = lambda: not stdin
        sys.stdout, sys.stderr, sys.stdin = out, err, stream
        try:
            code = self.module.main(list(argv))
        finally:
            sys.stdout, sys.stderr, sys.stdin = old
        return code, out.getvalue(), err.getvalue()

    def test_clipboard_and_notification_map_to_app_endpoints_with_token(self):
        Bridge.replies['/app/clip'] = lambda p: 'OK: 已写入剪贴板' if p.get('text') else '剪贴板内容'
        self.assertEqual(self.run_cmd('termux-clipboard-set', 'hello', 'world')[0], 0)
        self.assertEqual(Bridge.calls[-1][:2], ('/app/clip', {'text': 'hello world'}))
        self.assertEqual(Bridge.calls[-1][2], 't' * 32)
        code, out, _ = self.run_cmd('termux-clipboard-get')
        self.assertEqual((code, out), (0, '剪贴板内容'))
        self.run_cmd('termux-notification', '-t', '标题', '-c', '正文')
        self.assertEqual(Bridge.calls[-1][:2], ('/app/notify', {'title': '标题', 'text': '正文'}))

    def test_stdin_is_used_for_clipboard_set(self):
        self.run_cmd('termux-clipboard-set', stdin='from pipe\n')
        self.assertEqual(Bridge.calls[-1][1], {'text': 'from pipe\n'})

    def test_disabled_capability_returns_nonzero(self):
        Bridge.replies['/app/location'] = 'DISABLED: 位置能力未开启'
        code, out, _ = self.run_cmd('termux-location')
        self.assertEqual(code, 1)
        self.assertIn('DISABLED', out)

    def test_sensor_list_and_read(self):
        self.run_cmd('termux-sensor', '-l')
        self.assertEqual(Bridge.calls[-1][0], '/app/sensors')
        self.run_cmd('termux-sensor', '-s', 'light')
        self.assertEqual(Bridge.calls[-1][:2], ('/app/sensor', {'name': 'light'}))

    def test_torch_vibrate_open_url(self):
        self.run_cmd('termux-torch', 'off')
        self.assertEqual(Bridge.calls[-1][:2], ('/app/torch', {'on': '0'}))
        self.run_cmd('termux-vibrate', '-d', '500')
        self.assertEqual(Bridge.calls[-1][:2], ('/app/vibrate', {'ms': '500'}))
        self.run_cmd('termux-open-url', 'https://example.com')
        self.assertEqual(Bridge.calls[-1][:2], ('/app/open', {'url': 'https://example.com'}))
        self.assertEqual(self.run_cmd('termux-torch', 'blink')[0], 2)

    def test_dsha_am_quotes_arguments_and_rejects_other_verbs(self):
        Bridge.replies['/app/intent'] = 'OK: start com.example/.Main'
        code, out, _ = self.run_cmd('dsha-am', 'start', '-n', 'com.example/.Main', '--es', 'msg', 'a b')
        self.assertEqual(code, 0)
        self.assertEqual(Bridge.calls[-1][:2], ('/app/intent', {'cmd': "start -n com.example/.Main --es msg 'a b'"}))
        code, _, err = self.run_cmd('dsha-am', 'force-stop', 'com.example')
        self.assertEqual(code, 2)
        self.assertIn('只支持 start / broadcast', err)
        Bridge.replies['/app/intent'] = '[ERR] USER_REJECTED'
        self.assertEqual(self.run_cmd('dsha-am', 'start', '-n', 'com.example/.Main')[0], 1)

    def test_dsha_su_passes_script_and_propagates_remote_exit(self):
        Bridge.replies['/device/execute'] = json.dumps(
            {'state': 'completed', 'channel': 'root', 'output': 'uid=0(root)\n[EXIT=3]', 'exit': 3})
        code, out, _ = self.run_cmd('dsha-su', '-c', 'id | head -1; exit 3')
        self.assertEqual((code, out), (3, 'uid=0(root)\n'))
        self.assertEqual(Bridge.calls[-1][:2],
                         ('/device/execute', {'cmd': 'id | head -1; exit 3', 'unrestricted': '1', 'su': '0'}))
        self.run_cmd('dsha-su', '--root', 'ls', '/data/my dir')
        self.assertEqual(Bridge.calls[-1][1], {'cmd': "ls '/data/my dir'", 'unrestricted': '1', 'su': '1'})

    def test_dsha_su_reports_policy_block_with_126(self):
        Bridge.replies['/device/execute'] = json.dumps(
            {'state': 'completed', 'channel': 'policy',
             'output': '[POLICY_BLOCKED] 不受限制的设备 shell 未开启\n[EXIT=126]', 'exit': 126})
        code, out, _ = self.run_cmd('dsha-su', 'id')
        self.assertEqual(code, 126)
        self.assertIn('未开启', out)

    def test_old_dsha_is_detected_before_new_endpoints(self):
        Bridge.replies['/app/version'] = 'BRIDGE_PROTOCOL=3\nAPP_CODE=190'
        code, _, err = self.run_cmd('dsha-su', 'id')
        self.assertEqual(code, 1)
        self.assertIn('build211', err)
        self.assertNotIn('/device/execute', [call[0] for call in Bridge.calls])
        code, _, err = self.run_cmd('dsha-am', 'start', '-n', 'com.example/.Main')
        self.assertEqual(code, 1)
        self.assertNotIn('/app/intent', [call[0] for call in Bridge.calls])
        # 版本合格但端点仍不认识（[NO_CMD]）也算失败，不能报成功。
        Bridge.replies['/app/version'] = 'APP_CODE=211'
        Bridge.replies['/app/intent'] = '[NO_CMD]'
        self.assertEqual(self.run_cmd('dsha-am', 'start', '-n', 'com.example/.Main')[0], 1)

    def test_unknown_and_unauthorized(self):
        self.assertEqual(self.run_cmd('dsha-native', 'nope')[0], 2)
        Bridge.replies['/app/toast'] = '[UNAUTHORIZED]'
        code, _, err = self.run_cmd('termux-toast', 'hi')
        self.assertEqual(code, 124)
        self.assertIn('拒绝了凭据', err)

    def test_wrappers_and_install_contract_cover_every_command(self):
        spec = json.loads((ROOT / 'app/src/main/assets/managed-runtime-inputs.json').read_text(encoding='utf-8'))
        targets = {row['target']: row for row in spec['installs']}
        self.assertTrue(targets['root/.dsh/dsha-native.py']['executable'])
        for name in self.module.COMMANDS:
            row = targets['root/dsh-bin/' + name]
            self.assertTrue(row['executable'], name)
            wrapper = (ROOT / 'app/src/main/assets' / row['asset']).read_text(encoding='utf-8')
            self.assertIn('exec python3 /root/.dsh/dsha-native.py %s "$@"' % name, wrapper)
        layout = (ROOT / 'app/src/main/java/com/deepseekharness/app/util/ManagedRuntimeLayout.java').read_text(encoding='utf-8')
        for name in self.module.COMMANDS:
            self.assertIn('"%s"' % name, layout)


if __name__ == '__main__':
    unittest.main()
