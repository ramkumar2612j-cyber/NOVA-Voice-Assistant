'use strict';
// Laptop notes + reminders + "remember this" (Part 4). Pure logic + a tiny JSON store; no Electron needed, so it is unit-tested with node --test.
// Add-only: nothing here deletes a note unless the owner says "note hatao <number>". Data stays in one local file (userData/brain.json), never sent anywhere.
const fs = require('fs');
const { parse: parseLaptop } = require('./commands');
const MAX_ITEMS = 500, MAX_TEXT = 500, MAX_WS = 20, MAX_WS_ITEMS = 10;
const NUM = { ek: 1, do: 2, teen: 3, char: 4, paanch: 5, panch: 5, chhe: 6, saat: 7, aath: 8, nau: 9, das: 10, gyarah: 11, barah: 12, pandrah: 15, bees: 20, tees: 30, chalis: 40, pachas: 50 };
const num = w => (/^\d+$/.test(w) ? parseInt(w, 10) : NUM[w]);

function parseWhen(text, now) {
  const t = String(text).toLowerCase(), n = new Date(now || Date.now());
  let m = t.match(/(\d+|[a-z]+)\s*(minute|min|mint|ghante|ghanta|ghantey|hour|hours|hr|second|sec)\s*(?:baad|bad|me|mein|after)?/);
  if (m && num(m[1]) != null) {
    const k = num(m[1]), u = m[2], ms = /^(minute|min|mint)$/.test(u) ? 60000 : /^(second|sec)$/.test(u) ? 1000 : 3600000;
    return { at: n.getTime() + k * ms, rest: t.replace(m[0], ' ') };
  }
  m = t.match(/(\d{1,2})(?::(\d{2}))?\s*(?:baje|am|pm|bje)?/);
  if (m && /baje|bje|am|pm|:/.test(m[0])) {
    let h = parseInt(m[1], 10), mi = m[2] ? parseInt(m[2], 10) : 0;
    if (h > 23 || mi > 59) return null;
    const pm = /\bpm\b|shaam|raat|sham|dopahar|evening|night/.test(t), am = /\bam\b|subah|savere|morning/.test(t);
    if (pm && h < 12) h += 12; if (am && h === 12) h = 0;
    const d = new Date(n); d.setSeconds(0, 0); d.setHours(h, mi);
    if (/\bkal\b|tomorrow/.test(t)) d.setDate(d.getDate() + 1);
    else if (d.getTime() <= n.getTime()) { if (!pm && !am && h < 12 && h + 12 > n.getHours()) d.setHours(h + 12); if (d.getTime() <= n.getTime()) d.setDate(d.getDate() + 1); }
    return { at: d.getTime(), rest: t.replace(m[0], ' ') };
  }
  return null;
}
const STRIP = /\b(jarvis|please|plz|zara|mujhe|mere|ko|ki|ka|ke|se|me|mein|par|pe|aaj|kal|subah|shaam|raat|dopahar|baad|baje|yaad|dilao|dila|dena|do|karo|kro|batao|bata|ye|wo|ki|ke liye|remind|me|to|at|in|tomorrow|today|minute|min|ghante|ghanta|hour)\b/g;
const cleanText = s => s.replace(STRIP, ' ').replace(/[\d:]+/g, ' ').replace(/\s+/g, ' ').trim();

// text -> command or null. Never throws.
function parseBrain(text, now) {
  const raw = String(text || '').trim(); if (!raw || raw.length > 600) return null;
  const low = raw.toLowerCase();
  let m;
  if ((m = raw.match(/^(?:jarvis\s+)?(?:note|notes)\s+(?:likho|likh do|likh lo|add|save|karo)\s*[:\-]?\s*(.+)$/i)) || (m = raw.match(/^(?:jarvis\s+)?(?:ye\s+)?(?:note|likh)\s+(?:kar\s+)?(?:lo|le|rakh)\s*[:\-]?\s*(.+)$/i))) return { act: 'note_add', text: m[1].trim().slice(0, MAX_TEXT) };
  if (/^(?:jarvis\s+)?(?:meri\s+|saare\s+|sab\s+)?notes?\s+(?:dikhao|dikha|batao|list|padho|show)\b/.test(low) || /^(?:jarvis\s+)?show notes$/.test(low)) return { act: 'note_list' };
  if ((m = low.match(/^(?:jarvis\s+)?note\s+(?:hatao|mita|delete|remove)\s+(?:number\s+)?(\d+)$/))) return { act: 'note_del', n: parseInt(m[1], 10) };
  if ((m = raw.match(/^(?:jarvis\s+)?(?:yaad rakho|yaad rakhna|remember)\s*(?:ki|that)?\s*[:\-]?\s*(.+)$/i))) return { act: 'remember', text: m[1].trim().slice(0, MAX_TEXT) };
  if (/^(?:jarvis\s+)?(?:maine|mene)\s+(?:kya\s+)?(?:yaad|remember)/.test(low) || /^(?:jarvis\s+)?(?:kya|meri)\s+(?:yaad|memory)\s*(?:hai|dikhao|batao)?$/.test(low) || /^(?:jarvis\s+)?(?:what do you remember|memory dikhao)$/.test(low)) return { act: 'memory_list' };
  if (/^(?:jarvis\s+)?(?:reminders?|yaad dilane wali baatein)\s+(?:dikhao|dikha|batao|list|show)\b/.test(low)) return { act: 'remind_list' };
  if (/^(?:jarvis\s+)?(?:undo|wapas karo|pichla kaam wapas(?: karo)?|ulta karo|revert)$/.test(low)) return { act: 'undo' };
  if ((m = raw.match(/^(?:jarvis\s+)?workspace\s+(?:banao|bana do|save|set)\s+([a-z0-9_-]{1,20})\s*[:\-]\s*(.+)$/i))) return { act: 'ws_save', name: m[1].toLowerCase(), items: m[2].split(/\s*(?:,|\baur\b|\band\b|\+)\s*/i).map(x => x.trim()).filter(Boolean).slice(0, MAX_WS_ITEMS + 1) };
  if ((m = low.match(/^(?:jarvis\s+)?workspace\s+(?:kholo|chalao|load|open)\s+([a-z0-9_-]+)$/)) || (m = low.match(/^(?:jarvis\s+)?([a-z0-9_-]+)\s+workspace\s+(?:kholo|chalao|load|open)$/))) return { act: 'ws_open', name: m[1] };
  if (/^(?:jarvis\s+)?workspaces?\s+(?:dikhao|dikha|list|batao|show)$/.test(low)) return { act: 'ws_list' };
  if ((m = low.match(/^(?:jarvis\s+)?workspace\s+(?:hatao|delete|remove)\s+([a-z0-9_-]+)$/))) return { act: 'ws_del', name: m[1] };
  if (/(yaad dila|remind)/.test(low)) {
    const w = parseWhen(low, now);
    if (!w) return { act: 'remind_bad', why: 'Time samajh nahi aaya. Aise bolo: "10 minute baad chai yaad dilao" ya "kal 9 baje meeting yaad dilao".' };
    const what = cleanText(w.rest);
    return { act: 'remind', at: w.at, text: (what || 'reminder').slice(0, MAX_TEXT) };
  }
  return null;
}

class Brain {
  constructor(file) { this.file = file; this.d = { notes: [], memory: [], reminders: [], workspaces: {} }; this._undo = []; this.load(); }
  load() { try { const j = JSON.parse(fs.readFileSync(this.file, 'utf8')); for (const k of ['notes', 'memory', 'reminders']) if (Array.isArray(j[k])) this.d[k] = j[k]; if (j.workspaces && typeof j.workspaces === 'object' && !Array.isArray(j.workspaces)) this.d.workspaces = j.workspaces; } catch (e) { /* first run or unreadable: start empty, never crash */ } }
  save() { try { fs.writeFileSync(this.file + '.tmp', JSON.stringify(this.d)); fs.renameSync(this.file + '.tmp', this.file); return true; } catch (e) { return false; } }
  _push(k, item) { this.d[k].push(item); if (this.d[k].length > MAX_ITEMS) this.d[k].shift(); return this.save(); }
  run(cmd, now) {
    now = now || Date.now();
    switch (cmd.act) {
      case 'note_add': { const it = { t: cmd.text, ts: now }; if (!this._push('notes', it)) return 'Note save nahi ho paya (disk).'; this._u(() => this._drop('notes', it), 'note "' + cmd.text + '" hata diya'); return 'Note save kar liya: ' + cmd.text; }
      case 'note_list': { const a = this.d.notes; return a.length ? a.map((x, i) => (i + 1) + '. ' + x.t).join('\n') : 'Abhi koi note nahi hai. Bolo: "note likho doodh lana".'; }
      case 'note_del': { const i = cmd.n - 1; if (i < 0 || i >= this.d.notes.length) return 'Is number ka note nahi hai.'; const [x] = this.d.notes.splice(i, 1); this.save(); this._u(() => { this.d.notes.splice(Math.min(i, this.d.notes.length), 0, x); this.save(); }, 'note "' + x.t + '" wapas laga diya'); return 'Note hata diya: ' + x.t; }
      case 'remember': { const it = { t: cmd.text, ts: now }; if (!this._push('memory', it)) return 'Save nahi ho paya (disk).'; this._u(() => this._drop('memory', it), '"' + cmd.text + '" yaad se hata diya'); return 'Yaad rakh liya: ' + cmd.text; }
      case 'memory_list': { const a = this.d.memory; return a.length ? 'Mujhe ye yaad hai:\n' + a.map(x => '• ' + x.t).join('\n') : 'Abhi tak kuch yaad rakhne ko nahi bola. Bolo: "yaad rakho ki mera wifi ghar wala hai".'; }
      case 'remind': {
        if (cmd.at <= now) return 'Ye time nikal chuka hai.';
        const rm = { t: cmd.text, at: cmd.at, done: false }; this._push('reminders', rm); this._u(() => this._drop('reminders', rm), '"' + cmd.text + '" ka reminder hata diya');
        return '"' + cmd.text + '" ka reminder ' + new Date(cmd.at).toLocaleString('hi-IN', { dateStyle: 'medium', timeStyle: 'short' }) + ' par laga diya. (JARVIS laptop par chalu rehna chahiye.)';
      }
      case 'remind_list': { const a = this.d.reminders.filter(r => !r.done); return a.length ? a.map(r => '⏰ ' + new Date(r.at).toLocaleString('hi-IN', { dateStyle: 'medium', timeStyle: 'short' }) + ' - ' + r.t).join('\n') : 'Koi pending reminder nahi hai.'; }
      case 'ws_save': {
        if (!/^[a-z0-9_-]{1,20}$/.test(cmd.name)) return 'Workspace ka naam chhota rakho (akshar/number, 20 tak).';
        if (!cmd.items.length) return 'Workspace me kya kholna hai? Aise bolo: "workspace banao kaam: gmail, github, chrome".';
        if (cmd.items.length > MAX_WS_ITEMS) return 'Ek workspace me ' + MAX_WS_ITEMS + ' se zyada cheezein nahi.';
        if (!this.d.workspaces[cmd.name] && Object.keys(this.d.workspaces).length >= MAX_WS) return 'Itne workspace ho gaye (' + MAX_WS + '). Pehle ek hata do.';
        const bad = cmd.items.filter(x => { const g = parseLaptop('kholo ' + x); return !g || g.act === 'refuse' || !['app', 'url', 'folder'].includes(g.act); });
        if (bad.length) return 'Ye cheezein JARVIS nahi khol sakta: ' + bad.join(', ') + '. Sirf app (chrome, notepad...), site (gmail, youtube, ya naam.com) aur folder (downloads...) chalte hain.';
        const prev = this.d.workspaces[cmd.name]; this.d.workspaces[cmd.name] = cmd.items.slice();
        if (!this.save()) return 'Save nahi ho paya (disk).';
        this._u(() => { if (prev) this.d.workspaces[cmd.name] = prev; else delete this.d.workspaces[cmd.name]; this.save(); }, 'workspace "' + cmd.name + '" ' + (prev ? 'purane jaisa kar diya' : 'hata diya'));
        return 'Workspace "' + cmd.name + '" save: ' + cmd.items.join(', ') + '. Kholne ke liye bolo: "workspace kholo ' + cmd.name + '".';
      }
      case 'ws_list': { const k = Object.keys(this.d.workspaces); return k.length ? k.map(n => '🗂 ' + n + ': ' + this.d.workspaces[n].join(', ')).join('\n') : 'Abhi koi workspace nahi. Banao: "workspace banao kaam: gmail, github, chrome".'; }
      case 'ws_del': { const it = this.d.workspaces[cmd.name]; if (!it) return 'Is naam ka workspace nahi hai.'; delete this.d.workspaces[cmd.name]; this.save(); this._u(() => { this.d.workspaces[cmd.name] = it; this.save(); }, 'workspace "' + cmd.name + '" wapas laga diya'); return 'Workspace hata diya: ' + cmd.name; }
      case 'undo': { const u = this._undo.pop(); if (!u) return 'Wapas karne layak kuch nahi hai. (Notes, reminder, yaad, workspace ka pichla badlav wapas hota hai. Khole hue apps/tabs JARVIS band nahi karta, wo aap khud band karo. JARVIS band hone par undo list saaf ho jaati hai.)'; try { u.fn(); } catch (e) { return 'Undo nahi ho paya.'; } return 'Wapas kar diya: ' + u.msg + '.'; }
      case 'remind_bad': return cmd.why;
      default: return null;
    }
  }
  _u(fn, msg) { this._undo.push({ fn, msg }); if (this._undo.length > 20) this._undo.shift(); }
  _drop(k, it) { const i = this.d[k].indexOf(it); if (i >= 0) { this.d[k].splice(i, 1); this.save(); } }
  // call every few seconds; returns reminders that just became due (each fires once, even across restart).
  due(now) { now = now || Date.now(); const out = []; for (const r of this.d.reminders) if (!r.done && r.at <= now) { r.done = true; out.push(r); } if (out.length) this.save(); return out; }
}
// Opens each saved item one after another. exec is injected (real one in main.js, fake in tests). Returns a one-paragraph report; one failing item never stops the rest.
async function runWorkspace(items, exec, ctx, sleep) {
  const out = [];
  for (const it of items) {
    const g = parseLaptop('kholo ' + it);
    if (!g || g.act === 'refuse' || !['app', 'url', 'folder'].includes(g.act)) { out.push('✗ ' + it + ' (samajh nahi aaya)'); continue; }
    try { out.push('✓ ' + (await exec(g, ctx))); } catch (e) { out.push('✗ ' + it + ' (' + String(e && e.message || e).slice(0, 60) + ')'); }
    if (sleep) await sleep(600);
  }
  return out.join('\n');
}
module.exports = { parseBrain, parseWhen, Brain, runWorkspace };
