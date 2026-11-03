// Locked rc3 prompt assembly + real loopback HTTP; no model/device or user profile writes.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { createRequire, syncBuiltinESMExports } from 'node:module';
import { pathToFileURL } from 'node:url';

const root = path.resolve(import.meta.dirname, '..');
const runtime = process.env.DSHA_TEST_RUNTIME
  || JSON.parse(fs.readFileSync(path.join(root, 'app/build/test-runtimes/current.json'), 'utf8')).raw;
const expectedVersion = JSON.parse(fs.readFileSync(path.join(root, 'tools/recovery-runtime/lock.json'), 'utf8')).dshVersion;
assert.equal(JSON.parse(fs.readFileSync(path.join(runtime, 'node_modules/@deepseek-ai/dsh/package.json'), 'utf8')).version, expectedVersion);
const requireRuntime = createRequire(path.join(runtime, 'package.json'));
const load = name => import(pathToFileURL(requireRuntime.resolve(name)).href);
const { Context } = await load('@deepseek-ai/cordis');
const { default: SystemPrompt } = await load('@deepseek-ai/dsh-system-prompt');
const { createScope } = await load('@deepseek-ai/dsh-scope');
const sourcePath = path.join(root, 'app/src/main/assets/builtin-plugins/dsh-device-shell-guide/lib/index.js');
const source = fs.readFileSync(sourcePath, 'utf8')
  .replace("'@deepseek-ai/dsh-llm'", JSON.stringify(pathToFileURL(requireRuntime.resolve('@deepseek-ai/dsh-llm')).href))
  .replace("'./bridge-port.cjs'", JSON.stringify(pathToFileURL(path.join(path.dirname(sourcePath), 'bridge-port.cjs')).href));
const guide = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
const recoverySource = fs.readFileSync(path.join(root, 'app/src/main/assets/recovery-agent.js'), 'utf8')
  .replace("'@deepseek-ai/dsh-tools'", JSON.stringify(pathToFileURL(requireRuntime.resolve('@deepseek-ai/dsh-tools')).href));
const recovery = await import(`data:text/javascript;base64,${Buffer.from(recoverySource).toString('base64')}`);

const original = Object.fromEntries(['openSync', 'fstatSync', 'readSync', 'closeSync', 'lstatSync'].map(key => [key, fs[key]]));
const previousEnv = { ...process.env };
const token = 'f'.repeat(32), fakeFd = 987651;
let language = 'en', requests = 0, status = 200, excessive = false, tokenMode = 0o100600;
const server = http.createServer(async (request, response) => {
  assert.equal(request.headers['x-token'], request.url === '/app/version' ? token : undefined);
  if (request.url === '/app/version') {
    requests++;
    response.writeHead(status, { 'Content-Type': 'application/json' });
    response.end(excessive ? 'x'.repeat(8193) : JSON.stringify({ result: `BRIDGE_PROTOCOL=3\nUI_LANGUAGE=${language}\n` }));
  } else {
    assert.equal(request.url, '/v1/diagnostics');
    assert.equal(request.headers.authorization, `Bearer ${'a'.repeat(64)}`);
    response.end(JSON.stringify({ status: 'ok', uiLanguage: language }));
  }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const port = String(server.address().port);
const contexts = [];
let recoveryDispose;
try {
  process.env.DSHA_BRIDGE_PORT = port;
  process.env.DSHA_UI_LANGUAGE = 'zh'; // captured launch language deliberately becomes stale
  fs.openSync = function(file, flags, ...args) {
    if (file === '/root/.dsh/.bridge_token') {
      assert.equal(flags, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW | fs.constants.O_NONBLOCK);
      return fakeFd;
    }
    return original.openSync.call(this, file, flags, ...args);
  };
  fs.fstatSync = function(fd, ...args) {
    return fd === fakeFd ? { isFile: () => true, size: token.length, mode: tokenMode } : original.fstatSync.call(this, fd, ...args);
  };
  fs.readSync = function(fd, buffer, offset, length, position, ...args) {
    if (fd === fakeFd) { assert.equal(length, 130); return buffer.write(token, offset); }
    return original.readSync.call(this, fd, buffer, offset, length, position, ...args);
  };
  fs.closeSync = function(fd, ...args) { if (fd !== fakeFd) return original.closeSync.call(this, fd, ...args); };
  fs.lstatSync = function(file, ...args) {
    if (file === '/root/.dsh/.bridge_port') throw Object.assign(Error('isolated fixture'), { code: 'ENOENT' });
    return original.lstatSync.call(this, file, ...args);
  };
  syncBuiltinESMExports();
  const ctx = new Context(); contexts.push(ctx);
  await ctx.plugin(SystemPrompt, {});
  await ctx.plugin(guide);
  const userText = 'User custom instructions: preserve this exact value.';
  ctx.systemPrompt.section({ name: 'user-custom', order: 160, text: userText });
  for (const selected of ['zh', 'zh-Hant', 'en', 'ru', 'ja', 'ko', 'ar', 'pt', 'la', 'fr']) {
    language = selected;
    const assembly = await ctx.systemPrompt.assemble();
    const text = assembly.sections.find(section => section.name === 'dsh:device-shell-guide').text;
    assert(text.includes(`DSHA current response language: ${selected} (`), selected);
    assert(text.includes('custom prompt or user-owned AGENTS.md language instruction takes priority'));
    assert(text.includes('android_get_state') && text.includes('android_screenshot') && text.includes('android_node'));
    assert(text.includes('android_control({action:"begin"})') && text.includes('android_control({action:"finish"})'));
    assert(text.includes('普通讨论、引用、翻译') && text.includes('不取消模型对话'));
    assert(!text.includes('与用户交流一律使用中文'));
    // 测试环境没有 bxroot 原生调用与原生能力命令：不得提示 bx-host/dsha-su 可用。
    assert(!text.includes('原生调用') && !text.includes('bx-host') && !text.includes('dsha-su'));
    assert.equal(assembly.sections.find(section => section.name === 'user-custom').text, userText);
  }
  assert.equal(requests, 10, 'same live environment must reread every assembly without restart');
  // 原生调用段只在 BXROOT_AUTO_HOST=1 且 guest 内确有 bx-host 时出现。
  const host = '/usr/local/bin/bx-host';
  assert.equal(guide.nativeCallsActive({}, () => true), false);
  assert.equal(guide.nativeCallsActive({ BXROOT_AUTO_HOST: '0' }, () => true), false);
  assert.equal(guide.nativeCallsActive({ BXROOT_AUTO_HOST: '1' }, () => false), false);
  assert.equal(guide.nativeCallsActive({ BXROOT_AUTO_HOST: '1' }, path => path === host), true);
  assert.equal(guide.nativeCallsActive({ BXROOT_AUTO_HOST: '1' }, () => { throw Error('EACCES'); }), false);
  assert.equal(guide.nativeCommandsInstalled(path => path === '/root/dsh-bin/dsha-am'), true);
  assert.equal(guide.nativeCommandsInstalled(() => false), false);
  assert.equal(guide.nativeCommandsInstalled(() => { throw Error('EACCES'); }), false);
  // 开启时原生调用排在 /app/* 与 adb-shell 之前；关闭时保持原三通道文本。
  for (const selected of ['zh', 'en']) {
    const on = guide.promptText(selected, true), off = guide.promptText(selected, false);
    assert(on.includes('原生优先') && on.includes('bx-host') && !on.includes('三条通道'));
    // 通道顺序：原生 → 提权(adb-shell) → 策略禁止 → /app/*。
    const at = marker => on.indexOf(marker);
    assert(at('原生调用（首选）') < at('需要 shell/root 身份的命令'));
    assert(at('需要 shell/root 身份的命令') < at('白名单外的命令'));
    assert(at('白名单外的命令') < at('App 层接口 /app/*'));
    assert(on.includes('[POLICY_BLOCKED] 也不能借此绕过'));
    // 提权清单与被策略禁止的清单不能混：先试原生必定白费，策略禁止则哪条通道都没有。
    assert(on.includes('settings get/list（缺 INTERACT_ACROSS_USERS') && on.includes('dsha-am start/broadcast'));
    for (const blocked of ['input', 'screencap', 'monkey', 'am start', 'am broadcast', 'dmesg'])
      assert(on.slice(at('白名单外的命令'), at('App 层接口 /app/*')).includes(blocked), blocked);
    assert(off.includes('三条通道') && !off.includes('bx-host') && !off.includes('原生优先'));
    // 原生能力命令段只在命令已安装时出现，且与引擎无关。
    const withCommands = guide.promptText(selected, false, true), without = guide.promptText(selected, false, false);
    assert(withCommands.includes('termux-clipboard-get') && withCommands.includes('dsha-am start') && withCommands.includes('dsha-su'));
    assert(withCommands.includes('设置 → 设备能力授权 打开“不受限制的设备 shell”'));
    assert(!without.includes('termux-') && !without.includes('dsha-su'));
  }
  const complete = 'User complete system prompt';
  const custom = ctx.systemPrompt.section({ name: 'user-complete', order: 0, text: complete, complete: true });
  assert.deepEqual((await ctx.systemPrompt.assemble()).sections.map(row => row.text), [complete]);
  custom();
  const scopeKey = {}, userScope = createScope(ctx, scopeKey);
  await userScope.ctx.plugin({ inject: ['systemPrompt'], apply(scope) {
    scope.systemPrompt.section({ name: 'dsh:device-shell-guide', order: 150, text: userText });
  } });
  const beforeOverride = requests;
  assert.equal((await ctx.systemPrompt.assemble({ scope: scopeKey })).sections.find(row => row.name === 'dsh:device-shell-guide').text, userText);
  assert.equal(requests, beforeOverride, 'an explicit replacement of this section must remain owned by its author');
  await userScope.dispose();

  // Minimal profiles retain old snapshots byte-for-byte, append a current one, and dedup only committed same-language guides.
  const handlers = new Map(), historical = { type: 'user/message', data: { id: 'old-guide', source: { kind: 'plugin', plugin: guide.name }, content: [{ type: 'text', text: '与用户交流一律使用中文。' }] } };
  const frozenHistory = JSON.stringify(historical);
  guide.apply({ inject() {}, on(event, handler) { handlers.set(event, handler); } });
  const session = { snapshotEvents: () => [historical] }, decision = { kind: 'enter', messages: [{ id: 'current-user', text: 'custom prompt' }] };
  const step = () => handlers.get('agent/pre-step')({ agent: { session }, step: 1, signal: new AbortController().signal }, async () => decision);
  language = 'ja';
  const first = await step();
  assert.equal(first.messages.length, 2); assert(first.messages[0].content[0].text.includes('language: ja ('));
  assert.equal(JSON.stringify(historical), frozenHistory);
  assert.equal(first.messages[1], decision.messages[0]);
  assert.equal((await step()).messages.length, 2, 'uncommitted guide cannot suppress retry after rejected admission');
  handlers.get('session/event')(session, { type: 'user/message', data: first.messages[0] });
  assert.equal(await step(), decision);
  language = 'ar';
  const changed = await step();
  assert.equal(changed.messages.length, 2); assert(changed.messages[0].content[0].text.includes('language: ar ('));

  status = 401; assert.equal(await guide.selectedLanguage(), 'zh');
  status = 200; language = 'system'; assert.equal(await guide.selectedLanguage(), 'zh');
  excessive = true; assert.equal(await guide.selectedLanguage(), 'zh'); excessive = false;
  tokenMode = 0o100644;
  const beforeUnsafe = requests; assert.equal(await guide.selectedLanguage(), 'zh'); assert.equal(requests, beforeUnsafe); tokenMode = 0o100600;
  const cancelled = new AbortController(); cancelled.abort();
  const beforeCancel = requests; assert.equal(await guide.selectedLanguage(cancelled.signal), 'zh'); assert.equal(requests, beforeCancel);

  // Recovery uses its already-authenticated independent broker, never the formal runtime token.
  process.env.DSHA_RECOVERY_INSTANCE_ID = 'a'.repeat(32);
  process.env.DSHA_RECOVERY_GENERATION = '1';
  process.env.DSHA_RECOVERY_BROKER_PORT = port;
  process.env.DSHA_RECOVERY_BROKER_TOKEN = 'a'.repeat(64);
  const recoveryHandlers = new Map(), sections = [];
  recovery.apply({ tools: { guard() {}, register() {} }, systemPrompt: { section(value) { sections.push(value); } },
    on(name, handler) { recoveryHandlers.set(name, handler); if (name === 'dispose') recoveryDispose = handler; },
    root: { loader: { await: () => new Promise(() => {}) } } });
  for (const selected of ['zh-Hant', 'fr', 'la']) {
    language = selected;
    const initial = { sections: sections.map(row => ({ name: row.name, text: typeof row.text === 'function' ? row.text({}) : row.text })) };
    const assembly = await recoveryHandlers.get('system-prompt/assemble')(initial, {}, async () => initial);
    assert(assembly.sections.find(row => row.name === 'dsha-recovery-language').text.includes(`language: ${selected} (`));
    assert.equal(assembly.sections.find(row => row.name === 'dsha-recovery').text, initial.sections.find(row => row.name === 'dsha-recovery').text);
  }
  console.log('PASS locked rc3 dynamic prompt assembly, 10 current languages, user complete/custom prompt preservation, historical snapshot refresh, cancellation/401/bounds and independent recovery language');
} finally {
  recoveryDispose?.();
  for (const ctx of contexts.reverse()) await ctx.fiber.dispose();
  Object.assign(fs, original); syncBuiltinESMExports();
  for (const key of Object.keys(process.env)) if (!(key in previousEnv)) delete process.env[key];
  Object.assign(process.env, previousEnv);
  await new Promise(resolve => server.close(resolve));
}
