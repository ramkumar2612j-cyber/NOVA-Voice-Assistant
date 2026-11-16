'use strict';
// Real laptop numbers for the HUD dashboard (pure; the os module is passed in so tests can fake it). No fake data: anything unknown is null.
function snapshot(os, now) {
  const tot = os.totalmem(), free = os.freemem(), cpus = os.cpus() || [];
  let busy = 0, all = 0;
  for (const c of cpus) { const t = c.times || {}; const s = (t.user || 0) + (t.nice || 0) + (t.sys || 0) + (t.irq || 0), a = s + (t.idle || 0); busy += s; all += a; }
  return { ramUsedPct: tot ? Math.round((tot - free) / tot * 100) : null, ramGb: +(tot / 1073741824).toFixed(1), cores: cpus.length,
    cpuSincePct: all ? Math.round(busy / all * 100) : null, uptimeH: +(os.uptime() / 3600).toFixed(1), host: os.hostname(), platform: os.platform(), time: new Date(now || Date.now()).toISOString() };
}
// cpu % between two snapshots' raw times is better than since-boot; keep a tiny sampler
function cpuSampler(os) {
  let prev = null;
  return () => {
    let busy = 0, all = 0; for (const c of os.cpus() || []) { const t = c.times || {}; const s = (t.user || 0) + (t.nice || 0) + (t.sys || 0) + (t.irq || 0); busy += s; all += s + (t.idle || 0); }
    const r = prev && all > prev.all ? Math.round((busy - prev.busy) / (all - prev.all) * 100) : null; prev = { busy, all }; return r;
  };
}
module.exports = { snapshot, cpuSampler };
