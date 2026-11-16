const test = require('node:test'), assert = require('node:assert');
const { snapshot, cpuSampler } = require('../hudinfo');
const fake = (idle, user) => ({ totalmem: () => 8 * 1073741824, freemem: () => 2 * 1073741824, cpus: () => [{ times: { user, nice: 0, sys: 0, irq: 0, idle } }], uptime: () => 7200, hostname: () => 'pc', platform: () => 'win32' });
test('snapshot numbers', () => { const s = snapshot(fake(50, 50), 0); assert.strictEqual(s.ramUsedPct, 75); assert.strictEqual(s.ramGb, 8); assert.strictEqual(s.uptimeH, 2); assert.strictEqual(s.cores, 1); });
test('zero memory / no cpu does not crash', () => { const s = snapshot({ totalmem: () => 0, freemem: () => 0, cpus: () => [], uptime: () => 0, hostname: () => '', platform: () => 'x' }); assert.strictEqual(s.ramUsedPct, null); assert.strictEqual(s.cpuSincePct, null); });
test('cpu sampler: first null, then delta', () => { let idle = 100, user = 0; const os = { cpus: () => [{ times: { user, nice: 0, sys: 0, irq: 0, idle } }] }; const f = cpuSampler(os); assert.strictEqual(f(), null); user = 30; idle = 170; assert.strictEqual(f(), 30); });
