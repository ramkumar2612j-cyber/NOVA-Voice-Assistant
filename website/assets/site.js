/* JARVIS website JS — status fetch + admin panel. Koi framework nahi, koi tracking nahi.
   Backend URL: agar website aur backend ek saath host hue hain to same origin; warna
   localStorage me 'brain' key daal do (jaise https://brain.example.com) — admin/status dono wahan jaayenge. */
'use strict';
(function () {
  var BRAIN = (function () {
    try {
      var s = localStorage.getItem('brain');
      if (s && /^https:\/\/[a-z0-9.-]+(:\d+)?$/i.test(s)) return s.replace(/\/+$/, '');
    } catch (e) { }
    return '';   // same origin (backend ke saath deploy) — ya offline demo
  })();

  function api(path, opts, cb) {
    var url = (BRAIN || '') + path;
    fetch(url, opts).then(function (r) { return r.status === 204 ? {} : r.json().catch(function () { return {}; }); })
      .then(function (j) { cb(null, j); })
      .catch(function (e) { cb(e, null); });
  }

  // ---------------- index: public status ----------------
  var pill = document.getElementById('st-pill');
  if (pill) {
    api('/api/status', {}, function (err, j) {
      var sv = document.getElementById('st-server'), dv = document.getElementById('st-devices'), vn = document.getElementById('st-version'), nt = document.getElementById('st-note');
      if (err || !j || !j.ok) {
        pill.textContent = 'OFFLINE';
        pill.className = 'pill plan';
        if (sv) sv.textContent = '—'; if (dv) dv.textContent = '—'; if (vn) vn.textContent = '—';
        if (nt) nt.textContent = 'Backend abhi deploy nahi hua (ya address localStorage me set nahi) — isliye live status nahi dikha. Website poori chalti hai.';
        return;
      }
      pill.textContent = 'LIVE';
      if (sv) sv.textContent = 'chalu';
      if (dv) dv.textContent = String(j.devices_online);
      if (vn) vn.textContent = String(j.version);
      if (nt) nt.textContent = 'Uptime: ' + Math.round((j.uptime_s || 0) / 60) + ' minute.';
    });
  }

  // ---------------- admin ----------------
  var loginBtn = document.getElementById('loginbtn');
  if (!loginBtn) return;   // admin page hi nahi

  var TOKEN = null;
  try { TOKEN = sessionStorage.getItem('brain_admin') || null; } catch (e) { }

  function showDash(on) {
    var l = document.getElementById('login'), d = document.getElementById('dash'), o = document.getElementById('logout');
    if (l) l.style.display = on ? 'none' : '';
    if (d) d.style.display = on ? '' : 'none';
    if (o) o.style.display = on ? '' : 'none';
  }

  function loadDash() {
    api('/admin/overview', { headers: { Authorization: 'Bearer ' + TOKEN } }, function (err, j) {
      if (err || !j || !j.ok) { logout(); return; }
      var bp = document.getElementById('brain-pill'); if (bp) { bp.textContent = 'LIVE'; }
      var set = function (id, v) { var e = document.getElementById(id); if (e) e.textContent = String(v); };
      var devs = j.devices || [];
      set('d-devices', devs.length);
      set('d-online', devs.filter(function (d) { return d.online; }).length);
      set('d-tasks', (j.counts && j.counts.tasks) || 0);
      set('d-memory', (j.counts && j.counts.memory) || 0);
      set('d-version', j.version || '?');
      set('d-uptime', '—');
      var tb = document.querySelector('#devtable tbody');
      if (tb) {
        tb.innerHTML = devs.length ? devs.map(function (d) {
          var last = d.last_seen ? new Date(d.last_seen * 1000 || d.last_seen).toLocaleString('hi-IN') : '—';
          return '<tr><td>' + esc(d.name) + '</td><td>' + esc(d.platform) + '</td><td class="' + (d.online ? 'on' : 'off') + '">' + (d.online ? 'online' : 'offline') + '</td><td>' + (d.bat == null || d.bat < 0 ? '—' : d.bat + '%') + '</td><td>' + (d.run ? 'chalu' : 'band') + '</td><td>' + last + '</td></tr>';
        }).join('') : '<tr><td colspan="6" class="fine">Abhi koi device juda nahi. Neeche pairing code banao.</td></tr>';
      }
    });
    api('/admin/reports', { headers: { Authorization: 'Bearer ' + TOKEN } }, function (err, j) {
      var el = document.getElementById('reports');
      if (!el) return;
      if (err || !j || !j.ok) { el.textContent = 'Reports nahi laaye (backend band?).'; return; }
      var rs = j.reports || [];
      el.innerHTML = rs.length ? rs.map(function (r) { return '<div><b>' + esc(r.day) + '</b> — ' + esc(String((r.json || '').slice(0, 300))) + '</div>'; }).join('') : 'Abhi koi report nahi (daily-health workflow backend push karega jab secret lagega — docs/DEPLOY.md).';
    });
    loadVersions();
  }

  function loadVersions() {
    api('/admin/versions', { headers: { Authorization: 'Bearer ' + TOKEN } }, function (err, j) {
      var tb = document.querySelector('#vtable tbody');
      if (!tb) return;
      if (err || !j || !j.ok) { tb.innerHTML = '<tr><td colspan="4" class="fine">—</td></tr>'; return; }
      var vs = j.versions || [];
      tb.innerHTML = vs.length ? vs.map(function (v) {
        return '<tr><td>' + esc(v.v) + '</td><td>' + esc(v.date || '—') + '</td><td>' + esc(String(v.note || '').slice(0, 200)) + '</td><td>' + (v.published ? 'haan' : 'nahi') + '</td></tr>';
      }).join('') : '<tr><td colspan="4" class="fine">Abhi koi version publish nahi.</td></tr>';
    });
  }

  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

  function logout() {
    try { sessionStorage.removeItem('brain_admin'); } catch (e) { }
    TOKEN = null; showDash(false);
  }

  loginBtn.addEventListener('click', function () {
    var pw = document.getElementById('pw').value;
    var errEl = document.getElementById('loginerr');
    errEl.textContent = '';
    api('/admin/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password: pw }) }, function (err, j) {
      if (err || !j || !j.token) { errEl.textContent = 'Login nahi hua — backend band hai ya password galat.'; return; }
      TOKEN = j.token;
      try { sessionStorage.setItem('brain_admin', TOKEN); } catch (e) { }
      showDash(true); loadDash();
    });
  });
  var lo = document.getElementById('logout');
  if (lo) lo.addEventListener('click', logout);

  var pb = document.getElementById('pairbtn');
  if (pb) pb.addEventListener('click', function () {
    api('/admin/paircode', { method: 'POST', headers: { Authorization: 'Bearer ' + TOKEN } }, function (err, j) {
      var box = document.getElementById('paircode'), tx = document.getElementById('pairtext');
      if (err || !j || !j.code) { tx.textContent = 'Code nahi bana (backend band?).'; box.style.display = ''; return; }
      tx.textContent = j.code; box.style.display = '';
    });
  });
  var pc = document.getElementById('paircopy');
  if (pc) pc.addEventListener('click', function () {
    var t = document.getElementById('pairtext').textContent;
    try { navigator.clipboard.writeText(t); pc.textContent = 'COPY HO GAYA'; } catch (e) { }
  });

  var vp = document.getElementById('v-publish');
  if (vp) vp.addEventListener('click', function () {
    var v = document.getElementById('v-num').value.trim(), d = document.getElementById('v-date').value.trim(), n = document.getElementById('v-note').value.trim();
    var errEl = document.getElementById('v-err'); errEl.textContent = '';
    if (!v) { errEl.textContent = 'Version likho.'; return; }
    api('/admin/changelog/publish', { method: 'POST', headers: { Authorization: 'Bearer ' + TOKEN, 'Content-Type': 'application/json' }, body: JSON.stringify({ v: v, date: d, note: n }) }, function (err, j) {
      if (err || !j || !j.ok) { errEl.textContent = 'Publish nahi hua.'; return; }
      loadVersions();
    });
  });

  if (TOKEN) { showDash(true); loadDash(); } else showDash(false);
})();
