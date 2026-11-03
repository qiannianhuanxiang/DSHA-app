import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const bundle = fs.readFileSync(path.join(project,
  'app/src/main/assets/builtin-plugins/dsh-web-mobile/lib/client.js'), 'utf8');
const marker = '__modules["effects/gesture-guard.js"] = function (require, module, exports) {';
const start = bundle.indexOf(marker);
const end = bundle.indexOf('\n};\n__modules["effects/session-row-fiber.js"]', start);
assert.ok(start >= 0 && end > start, 'generated bundle must contain the upstream gesture module');
const bodyStart = bundle.indexOf('\n', start) + 1;
const body = bundle.slice(bodyStart, end);
assert.match(body, /const consumed = new WeakMap\(\);/);
assert.doesNotMatch(body, /for\s*\([^)]*of\s+consumed\)/, 'weak marks cannot be globally enumerated or retain detached nodes');

let now = 100;
const mod = { exports: {} };
const factory = new Function('require', 'module', 'exports', 'performance', body);
factory(() => ({}), mod, mod.exports, { now: () => now });
const guard = mod.exports;
const parent = { parentElement: null };
const detached = { parentElement: parent };
guard.markGestureConsumed(detached, 8, parent);
assert.equal(guard.isGestureConsumed(detached), true);
assert.equal(guard.consumeIfGestured({ target: detached }), true);
assert.equal(guard.consumeIfGestured({ target: parent }), true);
now += 9;
assert.equal(guard.isGestureConsumed(detached), false);
assert.equal(guard.consumeIfGestured({ target: parent }), false);

const nonElement = {};
guard.markGestureConsumed(nonElement, 4);
assert.equal(guard.consumeIfGestured({ target: nonElement }), true);
now += 5;
assert.equal(guard.consumeIfGestured({ target: nonElement }), false);
guard.markGestureConsumed(null, 4);
assert.equal(guard.consumeIfGestured({ target: null }), false);
console.log('PASS generated mobile gesture guard uses weak per-node marks and preserves expiry/ancestor semantics');
