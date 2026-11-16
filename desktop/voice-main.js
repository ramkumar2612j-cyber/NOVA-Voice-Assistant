'use strict';
// Voice IPC (Step C). The Groq key lives ONLY here (main process, OS-encrypted); the phone link (link.js / main.js link code) never sees it.
const V = require('./voice');
let sttKey = '', ctx = null;
const sttPath = () => require('path').join(ctx.app.getPath('userData'), 'stt.bin');
function load() { try { const { safeStorage } = require('electron'); if (ctx && safeStorage.isEncryptionAvailable() && ctx.fs.existsSync(sttPath())) sttKey = safeStorage.decryptString(ctx.fs.readFileSync(sttPath())); } catch (e) { sttKey = ''; } }
function register(c) {
  ctx = c; const { ipcMain, fs } = c;
  ipcMain.handle('jarvis:stt-state', () => ({ hasKey: !!sttKey }));
  ipcMain.handle('jarvis:stt-key', (_e, k) => {
    k = String(k || '').trim(); if (!V.validKey(k)) return { ok: false, msg: 'Ye Groq key jaisi nahi lagti (gsk_... lambi key).' };
    sttKey = k; let saved = false;
    try { const { safeStorage } = require('electron'); if (safeStorage.isEncryptionAvailable()) { fs.writeFileSync(sttPath(), safeStorage.encryptString(k)); saved = true; } } catch (e) {}
    return { ok: true, msg: saved ? 'Key save ho gayi (laptop ke lock se encrypted).' : 'Key sirf is session ke liye hai (laptop encrypt nahi kar paya), agli baar dobara daalni hogi.' };
  });
  ipcMain.handle('jarvis:stt-clear', () => { sttKey = ''; try { fs.unlinkSync(sttPath()); } catch (e) {} return { ok: true }; });
  ipcMain.handle('jarvis:stt-audio', async (_e, buf, mime) => {
    const r = await V.transcribe((u, o) => fetch(u, o), sttKey, Buffer.from(buf || []), String(mime || 'audio/webm'));
    if (r.ok) c.handleText(r.text.replace(/^\s*jarvis[\s,.:!-]+/i, '').trim() || r.text);   // spoken "jarvis ..." is just a prefix
    return r;
  });
}
module.exports = { register, load };
