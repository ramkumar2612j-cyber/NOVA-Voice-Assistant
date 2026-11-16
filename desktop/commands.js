'use strict';
// Pure logic (no Electron): Hinglish/English laptop command -> {act, ...} or null. Unit-tested with node --test.
const APPS = {
  chrome: 'chrome', browser: 'chrome', edge: 'edge', firefox: 'firefox',
  notepad: 'notepad', calculator: 'calculator', calc: 'calculator', cmd: 'terminal', terminal: 'terminal',
  explorer: 'files', files: 'files', paint: 'paint', word: 'word', excel: 'excel', powerpoint: 'powerpoint',
  vscode: 'vscode', code: 'vscode', spotify: 'spotify', whatsapp: 'whatsapp', telegram: 'telegram', settings: 'settings'
};
const SITES = { youtube: 'https://www.youtube.com', gmail: 'https://mail.google.com', github: 'https://github.com', instagram: 'https://www.instagram.com', facebook: 'https://www.facebook.com', whatsapp: 'https://web.whatsapp.com', google: 'https://www.google.com', maps: 'https://maps.google.com' };
const FOLDERS = { downloads: 'downloads', download: 'downloads', documents: 'documents', document: 'documents', desktop: 'desktop', pictures: 'pictures', photos: 'pictures', music: 'music', videos: 'videos' };
const OPEN = ['kholo', 'khol', 'open', 'chalao', 'start', 'launch', 'chalu'];
const FILL = new Set(['jarvis', 'please', 'plz', 'zara', 'karo', 'kro', 'kar', 'do', 'de', 'ko', 'ka', 'ki', 'ke', 'me', 'mein', 'par', 'pe', 'ye', 'wo', 'mera', 'meri', 'mere', 'the', 'a', 'an', 'my', 'on', 'in', 'for', 'to']);
const SEARCH = ['search', 'serch', 'dhundo', 'dhundho', 'khojo', 'find'];
const HARD_NO = ['format', 'delete', 'remove', 'uninstall', 'rm', 'del', 'shutdown', 'restart', 'password', 'otp', 'hack'];

function toks(t) { return String(t || '').toLowerCase().replace(/[^a-z0-9.:\/_ -]/g, ' ').split(/\s+/).filter(Boolean); }
function clean(w) { return w.filter(x => !FILL.has(x)); }

function parse(text) {
  const raw = String(text || '').trim();
  if (!raw || raw.length > 300) return null;
  const t = toks(raw);
  if (!t.length) return null;
  if (t.some(w => HARD_NO.includes(w))) return { act: 'refuse', why: 'Ye kaam (delete / shutdown / password) JARVIS laptop par nahi karta.' };
  const has = (...a) => a.some(x => t.includes(x));
  if (has('time', 'samay') && !has('open', 'kholo')) return { act: 'time' };
  if (has('date', 'tareekh', 'aaj') && has('date', 'tareekh')) return { act: 'time' };
  if (has('screenshot') || (has('screen') && has('capture', 'shot'))) return { act: 'screenshot' };
  if (has('lock') && has('screen', 'laptop', 'pc', 'computer')) return { act: 'lock' };
  if (has('system', 'battery', 'ram', 'cpu') && has('info', 'status', 'kitni', 'kitna', 'batao', 'battery')) return { act: 'sysinfo' };
  const sIdx = t.findIndex(w => SEARCH.includes(w));
  if (sIdx >= 0) {
    if (has('file', 'files', 'folder')) { const q = clean(t.filter((w, i) => i !== sIdx && !['file', 'files', 'folder', 'naam', 'name', 'wali', 'wala'].includes(w) && !SEARCH.includes(w))).join(' ').trim(); return q ? { act: 'findfile', query: q } : null; }
    const yt = has('youtube', 'yt');
    const q = clean(t.filter((w, i) => i !== sIdx && !['youtube', 'yt', 'google', 'web', 'internet'].includes(w) && !OPEN.includes(w))).join(' ').trim();
    if (!q) return null;
    return { act: yt ? 'youtube' : 'google', query: q.slice(0, 120) };
  }
  const oIdx = t.findIndex(w => OPEN.includes(w));
  if (oIdx >= 0) {
    const rest = clean(t.filter((w, i) => i !== oIdx));
    for (const w of rest) if (FOLDERS[w]) return { act: 'folder', folder: FOLDERS[w] };
    for (const w of rest) if (SITES[w] && !(APPS[w] && has('app'))) return { act: 'url', url: SITES[w], name: w };
    for (const w of rest) if (APPS[w]) return { act: 'app', app: APPS[w] };
    const dom = rest.find(w => /^[a-z0-9-]+\.[a-z]{2,}$/.test(w));
    if (dom) return { act: 'url', url: 'https://' + dom, name: dom };
  }
  return null;
}
module.exports = { parse, APPS, SITES, FOLDERS };
