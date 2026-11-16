// Gives ui.html the same window.Android object the phone has. Known calls are handled; every other phone-only call returns a harmless empty value.
// contextIsolation is OFF on purpose: this window only ever loads the local ui/ files (see main.js), never a website.
const { ipcRenderer } = require('electron');
try { require('./hud-inject'); } catch (e) { /* HUD is optional: the plain UI still works */ }
const sync = { sendText: t => { ipcRenderer.send('jarvis:text', String(t || '').slice(0, 500)); return 'ok'; },
  detectIntent: () => JSON.stringify({ kind: 'NONE' }), copyText: t => { ipcRenderer.send('jarvis:copy', String(t || '')); return 'ok'; },
  getStatus: () => JSON.stringify({ running: true, platform: 'desktop' }), running: () => true, getOwnerName: () => 'Boss', stopSpeak: () => { try { speechSynthesis.cancel(); } catch (e) {} return 'ok'; },
  openingVoice: () => '', openingVoiceStop: () => '', vibrate: () => 'ok', uiLog: () => '' };
const EMPTY_ARR = /^(list|get)(Chats|Connectors|Schedules|Learned|Errors|ActionLog|Alerts|Blocked|Updates|MemoryJson|FamilyJson|AutoModels|FreeChain|AiOrder)/;
window.Android = new Proxy(sync, { get(t, k) {
  if (k in t) return t[k];
  if (typeof k !== 'string') return undefined;
  if (/^(has|is)[A-Z]/.test(k)) return () => false;
  if (EMPTY_ARR.test(k)) return () => '[]';
  return () => '{}';
} });
ipcRenderer.on('jarvis:reply', (_e, role, text) => {
  try { window.onJarvis && window.onJarvis(role, text); } catch (e) {}
  if (role === 'ai') { try { const u = new SpeechSynthesisUtterance(String(text).slice(0, 400)); u.lang = 'hi-IN'; speechSynthesis.cancel(); speechSynthesis.speak(u); } catch (e) {} }
});
