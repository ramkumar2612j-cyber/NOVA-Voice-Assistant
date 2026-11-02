#!/usr/bin/env python3
"""Static contract checks for Part 3 STOP and persistent SILENT controls."""
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
hud = (root/'app/src/main/java/com/nova/assistant/NovaHud.kt').read_text()
svc = (root/'app/src/main/java/com/nova/assistant/NovaService.kt').read_text()
cfg = (root/'app/src/main/java/com/nova/assistant/Cfg.kt').read_text()
act = (root/'app/src/main/java/com/nova/assistant/MainActivity.kt').read_text()
ui = (root/'app/src/main/assets/ui.html').read_text()
checks = {
 'HUD has explicit STOP callback and touch target': 'onStop?.invoke()' in hud and '"STOP"' in hud,
 'STOP cancels agent and clears pending confirmation without confirming it': 'agentCancel = true' in svc and 'pending = null; dictatePending = null; dictTarget = null' in svc and 'it.onStop' in svc,
 'STOP shuts service down so mic/listening lifecycle is released': 'stopSelf()' in svc[svc.index('it.onStop'):svc.index('        // Android Keystore work',svc.index('it.onStop'))],
 'popup exposes SILENT toggle': 'onSilentToggle?.invoke()' in hud and 'SILENT ON' in hud and 'SILENT OFF' in hud,
 'home UI exposes SILENT toggle': 'id="silent"' in ui and 'Android.setSilentMode(!on)' in ui,
 'silent state persists in SharedPreferences': 'getBoolean("silentMode", false)' in cfg and 'putBoolean("silentMode", v)' in cfg,
 'settings bridge reads and writes the same persistent setting': 'fun setSilentMode(on: Boolean)' in act and 'fun getSilentMode(): Boolean = cfg.silentMode' in act,
 'HAAN/NAHI remain independent controls': 'inside(btnYes' in hud and 'inside(btnNo' in hud and 'while NOVA waits for HAAN / NAHI' in hud,
}
for name, ok in checks.items(): print(('PASS ' if ok else 'FAIL ') + name)
failed = [name for name, ok in checks.items() if not ok]
if failed: sys.exit(1)
print(f'ALL PASSED ({len(checks)} checks)')
