const test = require('node:test'), assert = require('node:assert');
const { parse } = require('../commands');
const cases = [
  ['jarvis chrome kholo', { act: 'app', app: 'chrome' }],
  ['youtube kholo', { act: 'url', name: 'youtube' }],
  ['open github.com', { act: 'url', url: 'https://github.com' }],
  ['downloads folder kholo', { act: 'folder', folder: 'downloads' }],
  ['youtube par arijit singh search karo', { act: 'youtube', query: 'arijit singh' }],
  ['google search python tutorial', { act: 'google', query: 'python tutorial' }],
  ['resume file dhundo', { act: 'findfile', query: 'resume' }],
  ['screenshot lo', { act: 'screenshot' }],
  ['laptop lock karo', { act: 'lock' }],
  ['time kya hai', { act: 'time' }],
  ['battery batao', { act: 'sysinfo' }],
  ['file delete karo', { act: 'refuse' }],
  ['laptop shutdown karo', { act: 'refuse' }],
  ['kal ka mausam kaisa hai', null],
  ['', null]
];
for (const [inp, want] of cases) test(inp || '(empty)', () => {
  const g = parse(inp);
  if (want === null) return assert.strictEqual(g, null);
  assert.ok(g, 'no result'); for (const k of Object.keys(want)) assert.strictEqual(g[k], want[k], k);
});
