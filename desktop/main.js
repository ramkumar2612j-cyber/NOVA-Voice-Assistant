'use strict';
const { app, BrowserWindow, ipcMain, shell, desktopCapturer, clipboard, screen } = require('electron');
const path = require('path');
const { parse } = require('./commands');
const { exec } = require('./actions');
const fs = require('fs');
const L = require('./link');
const { parseBrain, Brain, runWorkspace } = require('./brain');
const os = require('os');
const { snapshot, cpuSampler } = require('./hudinfo');
const cpuNow = cpuSampler(os);
let brain = null;   // notes / reminders / memory, created after app is ready
let win, link = null, relayBase = 'https://ntfy.sh';
const cfgPath = () => path.join(app.getPath('userData'), 'link.json');   // only the pairing secret + relay URL live here, never an API key
function loadCfg() { try { return JSON.parse(fs.readFileSync(cfgPath(), 'utf8')); } catch (e) { return {}; } }
function saveCfg(c) { try { fs.writeFileSync(cfgPath(), JSON.stringify(c)); } catch (e) {} }
function startLink() {
  if (link) { link.close(); link = null; }
  const c = loadCfg(); if (!c.key) return false;
  relayBase = c.relay || relayBase;
  link = new L.Link(c.key, L.ntfyTransport(relayBase), onPhone); link.start(); return true;
}
const waiting = new Map();   // message id -> timeout, so a phone reply is shown once
async function onPhone(m) {
  if (m.type === 'reply') { if (waiting.has(m.re)) { clearTimeout(waiting.get(m.re)); waiting.delete(m.re); } say('ai', '📱 ' + m.text); return; }
  if (m.type === 'cmd') {   // phone asked the laptop to do something
    const cmd = parse(m.text);
    let out = cmd ? await exec(cmd, { app, shell, desktopCapturer, screenSize: () => screen.getPrimaryDisplay().size }).catch(e => 'Gadbad: ' + String(e && e.message || e).slice(0, 100)) : 'Laptop ko ye command samajh nahi aaya.';
    say('ai', '📱→💻 ' + m.text + '\n' + (out || '')); try { await link.send('reply', out || 'ok', m.id); } catch (e) {}
  }
}
async function toPhone(type, text) {
  if (!link) return say('ai', 'Phone abhi jura nahi hai. Bolo: "phone jodo".');
  try {
    const id = await link.send(type, text);
    waiting.set(id, setTimeout(() => { waiting.delete(id); say('ai', 'Phone se jawab nahi aaya. Phone par internet / JARVIS chalu hai?'); }, 45000));
    say('ai', '📤 Phone ko bhej diya...');
  } catch (e) { say('ai', 'Phone tak nahi pahunch paya (internet / relay): ' + String(e && e.message || e).slice(0, 80)); }
}

function createWindow() {
  win = new BrowserWindow({ width: 430, height: 900, minWidth: 360, minHeight: 600, backgroundColor: '#0f0303', title: 'JARVIS',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: false, nodeIntegration: false, sandbox: false, webSecurity: true } });
  win.setMenuBarVisibility(false);
  win.loadFile(path.join(__dirname, 'boot.html'));   // boot animation, then it opens ui/ui.html itself (SKIP / any key / click also works)
  // The window must never navigate away from the local UI, and links open in the normal browser.
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('file://')) { e.preventDefault(); if (/^https:/.test(url)) shell.openExternal(url); } });
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https:/.test(url)) shell.openExternal(url); return { action: 'deny' }; });
}
function say(role, text) { if (win && !win.isDestroyed()) win.webContents.send('jarvis:reply', role, text); }

ipcMain.handle('jarvis:hud', () => ({
  sys: snapshot(os), cpu: cpuNow(), linked: !!link,
  workspaces: brain ? Object.keys(brain.d.workspaces) : [],
  notes: brain ? brain.d.notes.map(n => n.t) : [],
  reminders: brain ? brain.d.reminders.filter(r => !r.done).map(r => new Date(r.at).toLocaleString('hi-IN', { dateStyle: 'medium', timeStyle: 'short' }) + ' - ' + r.t) : []
}));
ipcMain.on('jarvis:copy', (_e, t) => { try { clipboard.writeText(String(t).slice(0, 5000)); } catch (e) {} });
async function handleText(text) {
  say('me', text);
  try {
    const low = String(text).toLowerCase().trim();
    if (/^(jarvis )?(phone|mobile) (jodo|jodna|pair)/.test(low)) {
      const c = loadCfg(); if (!c.key) { c.key = L.genKey(); saveCfg(c); }
      startLink(); return say('ai', 'Phone jodne ke liye JARVIS mobile app me SETTINGS > LAPTOP LINK kholo aur ye code paste karo:\n\nJARVIS-LINK:' + c.key + '\n\nYe code kisi ko mat dena. Isi se sab kuch encrypt hota hai.');
    }
    if (/^(jarvis )?(phone|mobile|link) (alag|hatao|unpair|band)/.test(low)) { if (link) link.close(); link = null; saveCfg({}); return say('ai', 'Phone ka link hata diya. Naya code banane ke liye "phone jodo" bolo.'); }
    if (/^(jarvis )?(link|phone|mobile) (status|check)/.test(low)) return say('ai', link ? 'Link chalu hai (relay: ' + relayBase + ').' : 'Link nahi hai. "phone jodo" bolo.');
    const mp = low.match(/^(?:jarvis\s+)?(?:phone|mobile)\s+(?:par|pe|me|mein)\s+(.+)$/);
    if (mp) return toPhone('cmd', String(text).trim().replace(/^(?:jarvis\s+)?(?:phone|mobile)\s+(?:par|pe|me|mein)\s+/i, ''));
    const bc = brain && parseBrain(text);
    if (bc && bc.act === 'ws_open') {
      const items = brain.d.workspaces[bc.name];
      if (!items) return say('ai', 'Is naam ka workspace nahi hai. Dekhne ke liye: "workspaces dikhao".');
      say('ai', '🗂 Workspace "' + bc.name + '" khol raha hu...');
      return say('ai', await runWorkspace(items, exec, { app, shell, desktopCapturer, screenSize: () => screen.getPrimaryDisplay().size }, ms => new Promise(r => setTimeout(r, ms))));
    }
    if (bc) return say('ai', brain.run(bc) || 'Ye kaam abhi nahi hota.');
    const ml = String(text).trim().replace(/^(?:jarvis\s+)?laptop\s+(?:par|pe|me|mein)\s+/i, '');
    const cmd = parse(ml);
    if (!cmd && link) return toPhone('ask', String(text).trim());   // free question: the phone answers with ITS keys
    if (!cmd) return say('ai', 'Ye laptop command samajh nahi aaya. Try: "chrome kholo", "youtube par gaana search karo", "downloads folder kholo", "resume file dhundo", "screenshot lo". Phone jodo ("phone jodo") to free sawal ka jawab phone ki AI dega.');
    const reply = await exec(cmd, { app, shell, desktopCapturer, screenSize: () => screen.getPrimaryDisplay().size });
    say('ai', reply || 'Ye kaam abhi nahi hota.');
  } catch (e) { say('ai', 'Kuch gadbad hui: ' + String(e && e.message || e).slice(0, 120)); }
}
ipcMain.on('jarvis:text', (_e, text) => { handleText(String(text || '').slice(0, 500)); });

require('./voice-main').register({ ipcMain, app, fs, path, handleText: t => handleText(t) });   // voice + its key live in their own file; the phone link code never touches it

app.whenReady().then(() => {
  require('./voice-main').load();
  // camera only for our own local file:// window, nothing else may ask for any permission
  require('electron').session.defaultSession.setPermissionRequestHandler((wc, perm, cb) => cb(perm === 'media' && /^file:/.test(wc.getURL())));
  brain = new Brain(path.join(app.getPath('userData'), 'brain.json'));
  createWindow(); startLink();
  setInterval(() => { try { for (const r of brain.due()) say('ai', '⏰ Yaad dila raha hu: ' + r.t); } catch (e) {} }, 5000);
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
