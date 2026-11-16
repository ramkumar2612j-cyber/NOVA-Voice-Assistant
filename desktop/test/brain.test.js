const test = require('node:test'), assert = require('node:assert');
const fs = require('fs'), os = require('os'), path = require('path');
const { parseBrain, parseWhen, Brain } = require('../brain');
const NOW = new Date(2026, 10, 14, 10, 0, 0).getTime();   // Sat 14 Nov 2026 10:00 local
const tmp = () => path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'nb-')), 'brain.json');
test('note add / list / delete', () => {
  const b = new Brain(tmp());
  assert.deepStrictEqual(parseBrain('note likho doodh lana'), { act: 'note_add', text: 'doodh lana' });
  b.run(parseBrain('note likho doodh lana')); b.run(parseBrain('jarvis note likho: bill bharna'));
  assert.match(b.run(parseBrain('notes dikhao')), /1\. doodh lana\n2\. bill bharna/);
  assert.match(b.run(parseBrain('note hatao 1')), /doodh lana/);
  assert.match(b.run(parseBrain('notes dikhao')), /1\. bill bharna/);
  assert.match(b.run({ act: 'note_del', n: 9 }), /nahi hai/);
});
test('remember + list', () => {
  const b = new Brain(tmp());
  assert.strictEqual(parseBrain('yaad rakho ki wifi ghar wala hai').act, 'remember');
  b.run(parseBrain('yaad rakho ki wifi ghar wala hai'));
  assert.match(b.run(parseBrain('kya yaad hai')), /wifi ghar wala/);
});
test('persists across restart', () => { const f = tmp(); new Brain(f).run({ act: 'note_add', text: 'x1' }); assert.match(new Brain(f).run({ act: 'note_list' }), /x1/); });
test('corrupt file does not crash', () => { const f = tmp(); fs.writeFileSync(f, '{bad'); assert.match(new Brain(f).run({ act: 'note_list' }), /koi note nahi/); });
test('relative time', () => {
  assert.strictEqual(parseWhen('10 minute baad chai', NOW).at, NOW + 600000);
  assert.strictEqual(parseWhen('do ghante baad', NOW).at, NOW + 7200000);
});
test('clock time: today/tomorrow/pm', () => {
  const d = parseWhen('kal 9 baje meeting', NOW).at; assert.strictEqual(new Date(d).getDate(), 15); assert.strictEqual(new Date(d).getHours(), 9);
  const e = new Date(parseWhen('shaam 6 baje', NOW).at); assert.strictEqual(e.getDate(), 14); assert.strictEqual(e.getHours(), 18);
  const f = new Date(parseWhen('subah 8 baje', NOW).at); assert.strictEqual(f.getDate(), 15); assert.strictEqual(f.getHours(), 8);
  assert.strictEqual(parseWhen('25 baje', NOW), null);
});
test('reminder text + fires once, survives restart', () => {
  const f = tmp(), b = new Brain(f), c = parseBrain('10 minute baad chai yaad dilao', NOW);
  assert.strictEqual(c.act, 'remind'); assert.strictEqual(c.text, 'chai');
  assert.match(b.run(c, NOW), /chai/);
  assert.strictEqual(b.due(NOW + 1000).length, 0);
  assert.strictEqual(b.due(NOW + 700000).length, 1);
  assert.strictEqual(b.due(NOW + 800000).length, 0);
  assert.strictEqual(new Brain(f).due(NOW + 900000).length, 0);
});
test('past reminder refused, bad time explained', () => {
  const b = new Brain(tmp()); assert.match(b.run({ act: 'remind', at: NOW - 1, text: 'x' }, NOW), /nikal chuka/);
  assert.strictEqual(parseBrain('chai yaad dilao', NOW).act, 'remind_bad');
});
test('unrelated text is not stolen', () => { for (const s of ['chrome kholo', 'kal ka mausam', 'time kya hai', '', 'note']) assert.strictEqual(parseBrain(s, NOW), null, s); });
const { runWorkspace } = require('../brain');
test('undo: note add / delete / remember / remind', () => {
  const b = new Brain(tmp());
  b.run(parseBrain('note likho a1')); b.run(parseBrain('note likho a2'));
  assert.match(b.run(parseBrain('undo')), /a2/); assert.doesNotMatch(b.run({ act: 'note_list' }), /a2/);
  b.run(parseBrain('note hatao 1')); assert.match(b.run({ act: 'note_list' }), /koi note nahi/);
  b.run(parseBrain('undo')); assert.match(b.run({ act: 'note_list' }), /1\. a1/);
  b.run(parseBrain('yaad rakho ki x9')); b.run(parseBrain('undo')); assert.match(b.run({ act: 'memory_list' }), /Abhi tak kuch/);
  b.run(parseBrain('10 minute baad chai yaad dilao', NOW), NOW); b.run(parseBrain('undo')); assert.match(b.run({ act: 'remind_list' }), /Koi pending/);
});
test('undo with nothing is honest about apps', () => { assert.match(new Brain(tmp()).run(parseBrain('undo')), /band nahi karta/); });
test('workspace save / list / open parse / delete / undo', () => {
  const b = new Brain(tmp());
  assert.deepStrictEqual(parseBrain('workspace banao kaam: gmail, github aur chrome').items, ['gmail', 'github', 'chrome']);
  assert.match(b.run(parseBrain('workspace banao kaam: gmail, github aur chrome')), /save: gmail, github, chrome/);
  assert.match(b.run(parseBrain('workspaces dikhao')), /kaam: gmail/);
  assert.deepStrictEqual(parseBrain('workspace kholo kaam'), { act: 'ws_open', name: 'kaam' });
  assert.deepStrictEqual(parseBrain('kaam workspace kholo'), { act: 'ws_open', name: 'kaam' });
  b.run(parseBrain('workspace hatao kaam')); assert.match(b.run(parseBrain('workspaces dikhao')), /Abhi koi workspace nahi/);
  b.run(parseBrain('undo')); assert.match(b.run(parseBrain('workspaces dikhao')), /kaam: gmail/);
  b.run(parseBrain('workspace banao kaam: youtube')); b.run(parseBrain('undo')); assert.match(b.run(parseBrain('workspaces dikhao')), /gmail, github, chrome/);
});
test('workspace rejects unknown / dangerous items and survives restart', () => {
  const f = tmp(), b = new Brain(f);
  assert.match(b.run(parseBrain('workspace banao x: gmail, file delete karo, kuchbhi')), /nahi khol sakta: file delete karo, kuchbhi/);
  assert.match(b.run(parseBrain('workspace banao x: ' + Array(12).fill('chrome').join(','))), /zyada/);
  b.run(parseBrain('workspace banao y: notepad')); assert.match(new Brain(f).run({ act: 'ws_list' }), /y: notepad/);
});
test('runWorkspace opens in order, one failure does not stop the rest', async () => {
  const seen = []; const exec = async g => { seen.push(g.act + ':' + (g.app || g.name || g.folder)); if (g.app === 'notepad') throw new Error('boom'); return 'ok ' + (g.app || g.name); };
  const out = await runWorkspace(['gmail', 'notepad', 'downloads', 'zzzz', 'delete'], exec, {}, null);
  assert.deepStrictEqual(seen, ['url:gmail', 'app:notepad', 'folder:downloads']);
  assert.match(out, /✓ ok gmail[\s\S]*✗ notepad \(boom\)[\s\S]*✓ ok undefined[\s\S]*✗ zzzz[\s\S]*✗ delete/);
});
test('workspace words do not steal laptop commands', () => { for (const s of ['chrome kholo', 'youtube kholo', 'downloads folder kholo']) assert.strictEqual(parseBrain(s, NOW), null, s); });
