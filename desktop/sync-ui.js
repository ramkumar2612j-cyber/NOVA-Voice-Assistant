// Copies the phone UI (ui.html + images) into desktop/ui so the laptop app shows the SAME screen. Run before start/build.
const fs = require('fs'), path = require('path');
const src = path.join(__dirname, '..', 'app', 'src', 'main', 'assets'), dst = path.join(__dirname, 'ui');
if (!fs.existsSync(src)) { console.error('assets folder not found: ' + src); process.exit(1); }
fs.mkdirSync(dst, { recursive: true });
let n = 0;
for (const f of fs.readdirSync(src)) { const s = path.join(src, f); if (fs.statSync(s).isFile()) { fs.copyFileSync(s, path.join(dst, f)); n++; } }
console.log('synced ' + n + ' files to desktop/ui');
