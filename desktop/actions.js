'use strict';
// Runs a parsed command on the laptop. Safety: no shell strings (spawn with arg arrays), apps come from a fixed table, files only inside Downloads/Documents/Desktop/Pictures/Music/Videos, nothing is ever deleted.
const { spawn } = require('child_process');
const fs = require('fs'), os = require('os'), path = require('path');
const P = process.platform;

const APP_CMD = {
  win32: { chrome: ['cmd', '/c', 'start', '', 'chrome'], edge: ['cmd', '/c', 'start', '', 'msedge'], firefox: ['cmd', '/c', 'start', '', 'firefox'], notepad: ['notepad'], calculator: ['calc'], terminal: ['cmd', '/c', 'start', '', 'cmd'], files: ['explorer'], paint: ['mspaint'], word: ['cmd', '/c', 'start', '', 'winword'], excel: ['cmd', '/c', 'start', '', 'excel'], powerpoint: ['cmd', '/c', 'start', '', 'powerpnt'], vscode: ['cmd', '/c', 'start', '', 'code'], spotify: ['cmd', '/c', 'start', '', 'spotify:'], whatsapp: ['cmd', '/c', 'start', '', 'whatsapp:'], telegram: ['cmd', '/c', 'start', '', 'tg:'], settings: ['cmd', '/c', 'start', '', 'ms-settings:'] },
  darwin: { chrome: ['open', '-a', 'Google Chrome'], edge: ['open', '-a', 'Microsoft Edge'], firefox: ['open', '-a', 'Firefox'], notepad: ['open', '-a', 'TextEdit'], calculator: ['open', '-a', 'Calculator'], terminal: ['open', '-a', 'Terminal'], files: ['open', '-a', 'Finder'], vscode: ['open', '-a', 'Visual Studio Code'], spotify: ['open', '-a', 'Spotify'], whatsapp: ['open', '-a', 'WhatsApp'], telegram: ['open', '-a', 'Telegram'], settings: ['open', '-a', 'System Settings'] },
  linux: { chrome: ['google-chrome'], edge: ['microsoft-edge'], firefox: ['firefox'], notepad: ['gedit'], calculator: ['gnome-calculator'], terminal: ['x-terminal-emulator'], files: ['xdg-open', os.homedir()], vscode: ['code'], spotify: ['spotify'], telegram: ['telegram-desktop'], settings: ['gnome-control-center'] }
};
const LOCK = { win32: ['rundll32.exe', 'user32.dll,LockWorkStation'], darwin: ['pmset', 'displaysleepnow'], linux: ['loginctl', 'lock-session'] };

function run(argv) {
  return new Promise(res => {
    try {
      const c = spawn(argv[0], argv.slice(1), { detached: true, stdio: 'ignore', windowsHide: true });
      c.on('error', () => res(false)); c.unref(); setTimeout(() => res(true), 400);
    } catch (e) { res(false); }
  });
}
function roots(app) {
  const h = os.homedir(), g = n => { try { return app.getPath(n); } catch (e) { return path.join(h, n[0].toUpperCase() + n.slice(1)); } };
  return { downloads: g('downloads'), documents: g('documents'), desktop: g('desktop'), pictures: g('pictures'), music: g('music'), videos: g('videos') };
}
function findFiles(root, q, max) {
  const out = [], want = q.toLowerCase().split(/\s+/).filter(Boolean);
  (function walk(dir, depth) {
    if (out.length >= max || depth > 4) return;
    let ents; try { ents = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return; }
    for (const e of ents) {
      if (out.length >= max) return;
      if (e.name.startsWith('.') || e.name === 'node_modules') continue;
      const full = path.join(dir, e.name), nm = e.name.toLowerCase();
      if (want.every(w => nm.includes(w))) out.push(full);
      if (e.isDirectory() && !e.isSymbolicLink()) walk(full, depth + 1);
    }
  })(root, 0);
  return out;
}
function sysinfo() {
  const gb = b => (b / 1073741824).toFixed(1);
  return 'RAM: ' + gb(os.totalmem() - os.freemem()) + ' / ' + gb(os.totalmem()) + ' GB use me. CPU: ' + os.cpus().length + ' core. ' + os.type() + ' ' + os.release() + '. Chalu hue: ' + Math.round(os.uptime() / 3600) + ' ghante.';
}

// ctx = { app, shell, desktopCapturer, notify(text) }. Returns the sentence JARVIS says back.
async function exec(cmd, ctx) {
  switch (cmd.act) {
    case 'refuse': return cmd.why;
    case 'time': return new Date().toLocaleString('hi-IN', { dateStyle: 'full', timeStyle: 'short' });
    case 'sysinfo': return sysinfo();
    case 'url': await ctx.shell.openExternal(cmd.url); return cmd.name + ' khol diya.';
    case 'google': await ctx.shell.openExternal('https://www.google.com/search?q=' + encodeURIComponent(cmd.query)); return 'Google par "' + cmd.query + '" search kar diya.';
    case 'youtube': await ctx.shell.openExternal('https://www.youtube.com/results?search_query=' + encodeURIComponent(cmd.query)); return 'YouTube par "' + cmd.query + '" search kar diya.';
    case 'app': {
      const t = APP_CMD[P] || {}, argv = t[cmd.app];
      if (!argv) return cmd.app + ' is laptop (' + P + ') par JARVIS se nahi khul sakta.';
      return (await run(argv)) ? cmd.app + ' khol diya.' : cmd.app + ' nahi khul paya (install hai?).';
    }
    case 'folder': { const p = roots(ctx.app)[cmd.folder]; const r = await ctx.shell.openPath(p); return r ? 'Folder nahi khula: ' + r : cmd.folder + ' folder khol diya.'; }
    case 'findfile': {
      const R = roots(ctx.app); let found = [];
      for (const k of ['desktop', 'documents', 'downloads', 'pictures']) { found = found.concat(findFiles(R[k], cmd.query, 5 - found.length)); if (found.length >= 5) break; }
      if (!found.length) return '"' + cmd.query + '" naam ki file Desktop / Documents / Downloads / Pictures me nahi mili.';
      ctx.shell.showItemInFolder(found[0]);
      return found.length + ' file mili. Pehli folder me dikha di:\n' + found.map(f => '• ' + f).join('\n');
    }
    case 'lock': { const a = LOCK[P]; return a && (await run(a)) ? 'Laptop lock kar diya.' : 'Lock nahi ho paya.'; }
    case 'screenshot': {
      const sz = ctx.screenSize ? ctx.screenSize() : { width: 1920, height: 1080 };
      const src = await ctx.desktopCapturer.getSources({ types: ['screen'], thumbnailSize: sz });
      if (!src.length) return 'Screen capture nahi mila.';
      const f = path.join(roots(ctx.app).pictures, 'JARVIS_screenshot_' + Date.now() + '.png');
      fs.writeFileSync(f, src[0].thumbnail.toPNG()); ctx.shell.showItemInFolder(f); return 'Screenshot Pictures me save kar diya: ' + f;
    }
    default: return null;
  }
}
module.exports = { exec, findFiles, roots };
