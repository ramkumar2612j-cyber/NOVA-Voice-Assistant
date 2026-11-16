'use strict';
// Adds a JARVIS-style dashboard on top of the SAME ui.html (add-only: the phone UI file is never edited). Loaded by preload.js.
// Shows real data only (laptop RAM/CPU, your notes + reminders, link status). Email = honest "not connected". Camera starts only when you press START.
const { ipcRenderer } = require('electron');
function el(tag, cls, html) { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; }
const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const CSS = `#nvhud-btn{position:fixed!important;top:8px!important;right:8px!important;left:auto!important;bottom:auto!important;width:auto!important;height:auto!important;margin:0!important;z-index:2147483000;background:rgba(2,16,24,.85);color:#7fe9ff;border:1px solid #1d8aa6;border-radius:6px;padding:5px 10px;font:12px Consolas,monospace;cursor:pointer;letter-spacing:.15em}
#nvhud{position:fixed;inset:0;z-index:2147482999;background:radial-gradient(circle at 50% 30%,#06222e 0,#02060c 70%);color:#7fe9ff;font:12px Consolas,Menlo,monospace;display:none;overflow:auto;padding:46px 12px 14px}
#nvhud.on{display:block}#nvhud h1{margin:0 0 10px;font-size:14px;letter-spacing:.35em;color:#bff6ff;text-shadow:0 0 10px #0cf;text-align:center}
.nvhud-g{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:10px}
.nvhud-p{border:1px solid #1d6f86;background:rgba(3,22,32,.72);border-radius:8px;padding:10px;box-shadow:0 0 12px rgba(0,200,255,.12) inset}
.nvhud-p b{display:block;color:#ffcf6b;letter-spacing:.2em;font-size:11px;margin-bottom:6px}
.nvhud-bar{height:7px;background:#073040;border-radius:4px;overflow:hidden;margin:3px 0 8px}.nvhud-bar i{display:block;height:100%;background:linear-gradient(90deg,#0cf,#ffcf6b);width:0;transition:width .6s}
.nvhud-p ul{margin:0;padding-left:16px}.nvhud-dim{color:#3f8fa5}
#nvhud-log{max-height:150px;overflow:auto;white-space:pre-wrap;margin-bottom:6px}
#nvhud-in{width:100%;box-sizing:border-box;background:#021019;color:#bff6ff;border:1px solid #1d6f86;border-radius:5px;padding:6px;font:inherit}
.nvhud-bt{width:auto!important;height:auto!important;background:#04303f;color:#7fe9ff;border:1px solid #1d8aa6;border-radius:5px;padding:4px 9px;font:inherit;cursor:pointer;margin-right:6px}
#nvh-mic{position:fixed!important;top:8px!important;right:84px!important;left:auto!important;bottom:auto!important;width:auto!important;height:auto!important;margin:0!important;z-index:2147483000;background:rgba(2,16,24,.85);color:#7fe9ff;border:1px solid #1d8aa6;border-radius:6px;padding:5px 10px;font:12px Consolas,monospace;cursor:pointer}
#nvh-mic.rec{background:#5a0f0f;color:#fff;border-color:#ff5c5c}#nvh-toast{position:fixed;top:40px;right:8px;z-index:2147483000;max-width:70vw;background:rgba(2,16,24,.92);color:#bff6ff;border:1px solid #1d8aa6;border-radius:6px;padding:5px 9px;font:12px Consolas,monospace;display:none}
.nvhud-in2{width:100%;box-sizing:border-box;background:#021019;color:#bff6ff;border:1px solid #1d6f86;border-radius:5px;padding:6px;font:inherit;margin:4px 0}
#nvhud video{width:100%;border-radius:6px;background:#000;display:none;margin-top:6px}`;
function init() {
  if (/boot\.html/.test(location.pathname)) return;
  if (document.getElementById('nvhud')) return;
  const st = el('style', null, CSS); document.head.appendChild(st);
  const btn = el('button', null, '◈ HUD'); btn.id = 'nvhud-btn'; document.body.appendChild(btn);
  const hud = el('div'); hud.id = 'nvhud';
  hud.innerHTML = '<h1>J.A.R.V.I.S &nbsp; DASHBOARD</h1><div class="nvhud-g">' +
    '<div class="nvhud-p"><b>SYSTEM</b><div id="nvh-cpu">CPU -</div><div class="nvhud-bar"><i id="nvh-cpub"></i></div><div id="nvh-ram">RAM -</div><div class="nvhud-bar"><i id="nvh-ramb"></i></div><div id="nvh-up" class="nvhud-dim"></div></div>' +
    '<div class="nvhud-p"><b>PHONE LINK</b><div id="nvh-link">-</div><div class="nvhud-dim">Jodne ke liye chat me bolo: "phone jodo"</div></div>' +
    '<div class="nvhud-p"><b>NOTES</b><ul id="nvh-notes"></ul></div>' +
    '<div class="nvhud-p"><b>WORKSPACES</b><div id="nvh-ws"></div><div class="nvhud-dim">Banane ke liye chat me: workspace banao kaam: gmail, github</div></div>' +
    '<div class="nvhud-p"><b>REMINDERS</b><ul id="nvh-rem"></ul></div>' +
    '<div class="nvhud-p"><b>CAMERA</b><button class="nvhud-bt" id="nvh-cam">START</button><span class="nvhud-dim" id="nvh-camst">band hai</span><video id="nvh-vid" autoplay muted playsinline></video></div>' +
    '<div class="nvhud-p"><b>VOICE</b><div class="nvhud-dim" id="nvh-vst">-</div><input id="nvh-key" type="password" placeholder="Groq key (gsk_...)" class="nvhud-in2" autocomplete="off"><button class="nvhud-bt" id="nvh-ksave">SAVE</button><button class="nvhud-bt" id="nvh-kdel">HATAO</button><div class="nvhud-dim">Mic dabao (ya Ctrl+Space) aur bolo. Awaaz sirf bolte waqt Groq ko jaati hai, hamesha sunta nahi.</div></div>' +
    '<div class="nvhud-p"><b>EMAIL</b><div class="nvhud-dim">Abhi jura nahi hai. Asli mail tabhi dikhegi jab Gmail jodenge (agla step).</div></div>' +
    '<div class="nvhud-p" style="grid-column:1/-1"><b>CHAT</b><div id="nvhud-log"></div><input id="nvhud-in" placeholder="Yahan likho aur Enter dabao (jaise: chrome kholo)"></div></div>';
  document.body.appendChild(hud);
  const $ = id => document.getElementById(id); let timer = null, stream = null;
  async function refresh() {
    try {
      const d = await ipcRenderer.invoke('jarvis:hud'), s = d.sys;
      $('nvh-cpu').textContent = 'CPU ' + (d.cpu == null ? '...' : d.cpu + '%') + ' (' + s.cores + ' core)'; $('nvh-cpub').style.width = (d.cpu || 0) + '%';
      $('nvh-ram').textContent = 'RAM ' + s.ramUsedPct + '% of ' + s.ramGb + ' GB'; $('nvh-ramb').style.width = s.ramUsedPct + '%';
      $('nvh-up').textContent = s.host + ' · ' + s.platform + ' · chalu ' + s.uptimeH + ' ghante';
      $('nvh-link').textContent = d.linked ? 'Link chalu (phone ka jawab abhi jaanchna baaki)' : 'Link nahi hai';
      $('nvh-notes').innerHTML = d.notes.length ? d.notes.slice(-6).map(n => '<li>' + esc(n) + '</li>').join('') : '<li class="nvhud-dim">koi note nahi</li>';
      const wsEl = $('nvh-ws'), key = (d.workspaces || []).join('|'); if (wsEl.dataset.k !== key) { wsEl.dataset.k = key; wsEl.innerHTML = (d.workspaces || []).length ? d.workspaces.map(n => '<button class="nvhud-bt" data-ws="' + esc(n) + '">▶ ' + esc(n) + '</button>').join('') : '<span class="nvhud-dim">koi workspace nahi</span>'; }
      $('nvh-rem').innerHTML = d.reminders.length ? d.reminders.slice(0, 6).map(r => '<li>' + esc(r) + '</li>').join('') : '<li class="nvhud-dim">koi reminder nahi</li>';
    } catch (e) {}
  }
  function open() { hud.classList.add('on'); refresh(); timer = setInterval(refresh, 2000); }
  function close() { hud.classList.remove('on'); clearInterval(timer); stopCam(); }
  function stopCam() { if (stream) { stream.getTracks().forEach(t => t.stop()); stream = null; } $('nvh-vid').style.display = 'none'; $('nvh-cam').textContent = 'START'; $('nvh-camst').textContent = 'band hai'; }
  btn.onclick = () => (hud.classList.contains('on') ? close() : open());
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && hud.classList.contains('on')) close(); });
  $('nvh-cam').onclick = async () => {
    if (stream) return stopCam();
    try { stream = await navigator.mediaDevices.getUserMedia({ video: true }); $('nvh-vid').srcObject = stream; $('nvh-vid').style.display = 'block'; $('nvh-cam').textContent = 'STOP'; $('nvh-camst').textContent = 'chalu (sirf is screen par, kahin save/bhej nahi hota)'; }
    catch (e) { $('nvh-camst').textContent = 'camera nahi mila / permission nahi'; }
  };
  $('nvh-ws').addEventListener('click', e => { const n = e.target && e.target.dataset && e.target.dataset.ws; if (n) window.Android.sendText('workspace kholo ' + n); });

  // ---- voice: click mic or Ctrl+Space -> record until ~1.4 s of silence (max 12 s) -> main process transcribes -> runs as a normal command
  const mic = el('button', null, '🎤'); mic.id = 'nvh-mic'; mic.title = 'Bolo (Ctrl+Space)'; document.body.appendChild(mic);
  const toast = el('div'); toast.id = 'nvh-toast'; document.body.appendChild(toast); let tt = null;
  function say(t, ms) { toast.textContent = t; toast.style.display = 'block'; clearTimeout(tt); tt = setTimeout(() => { toast.style.display = 'none'; }, ms || 4000); const v = $('nvh-vst'); if (v) v.textContent = t; }
  let cur = null;   // the recording in progress: { mr, go }
  function finish(go) { if (!cur) return; const c = cur; cur = null; c.go = go; try { if (c.mr.state !== 'inactive') c.mr.stop(); } catch (e) {} }
  async function startRec() {
    const st = await ipcRenderer.invoke('jarvis:stt-state'); if (!st.hasKey) { say('Pehle HUD > VOICE me Groq key daalo.', 6000); return; }
    let stm; try { stm = await navigator.mediaDevices.getUserMedia({ audio: true }); } catch (e) { say('Mic nahi mila / permission nahi.', 5000); return; }
    const mime = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : '', mr = new MediaRecorder(stm, mime ? { mimeType: mime } : {}), chunks = [];
    const ac = new (window.AudioContext || window.webkitAudioContext)(), an = ac.createAnalyser(); an.fftSize = 1024; ac.createMediaStreamSource(stm).connect(an);
    const buf = new Float32Array(an.fftSize), t0 = Date.now(); let heard = false, quiet = t0;
    const me = { mr, go: false }; cur = me; mic.classList.add('rec'); say('🎤 Sun raha hu... bolo', 12000);
    mr.ondataavailable = e => { if (e.data && e.data.size) chunks.push(e.data); };
    const iv = setInterval(() => {
      if (cur !== me) return; an.getFloatTimeDomainData(buf); let s = 0; for (let i = 0; i < buf.length; i++) s += buf[i] * buf[i];
      const now = Date.now(); if (Math.sqrt(s / buf.length) > 0.02) { heard = true; quiet = now; }
      if (heard && now - quiet > 1400) finish(true);
      else if (!heard && now - t0 > 6000) { say('Kuch bola nahi, band kar diya.'); finish(false); }
      else if (now - t0 > 12000) finish(heard);
    }, 120);
    mr.onstop = async () => {
      clearInterval(iv); mic.classList.remove('rec'); stm.getTracks().forEach(t => t.stop()); try { ac.close(); } catch (e) {}
      if (!me.go) return;
      say('⏳ Samajh raha hu...', 15000);
      try { const blob = new Blob(chunks, { type: mr.mimeType || 'audio/webm' }), r = await ipcRenderer.invoke('jarvis:stt-audio', await blob.arrayBuffer(), blob.type); say(r.ok ? '🗣 ' + r.text : r.err, 6000); } catch (e) { say('Voice me gadbad hui.', 5000); }
    };
    mr.start();
  }
  mic.onclick = () => { if (cur) finish(true); else startRec(); };
  document.addEventListener('keydown', e => { if (e.ctrlKey && e.code === 'Space') { e.preventDefault(); mic.onclick(); } });
  async function keyState() { const st = await ipcRenderer.invoke('jarvis:stt-state'); const v = $('nvh-vst'); if (v && !cur) v.textContent = st.hasKey ? 'Groq key lagi hui hai. Mic dabao.' : 'Groq key nahi hai (console.groq.com se free).'; }
  $('nvh-ksave').onclick = async () => { const r = await ipcRenderer.invoke('jarvis:stt-key', $('nvh-key').value); $('nvh-key').value = ''; say(r.msg, 6000); };
  $('nvh-kdel').onclick = async () => { await ipcRenderer.invoke('jarvis:stt-clear'); say('Key hata di.'); };
  keyState();
  const log = $('nvhud-log');
  ipcRenderer.on('jarvis:reply', (_e, role, text) => { const line = (role === 'me' ? 'Aap: ' : 'JARVIS: ') + String(text).slice(0, 400); log.textContent += line + '\n'; log.scrollTop = log.scrollHeight; });
  $('nvhud-in').addEventListener('keydown', e => { if (e.key === 'Enter' && e.target.value.trim()) { window.Android.sendText(e.target.value.trim()); e.target.value = ''; } });
}
window.addEventListener('DOMContentLoaded', () => { try { init(); } catch (e) {} });
