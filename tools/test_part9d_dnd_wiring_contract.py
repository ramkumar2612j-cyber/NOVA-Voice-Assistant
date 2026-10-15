#!/usr/bin/env python3
"""Part 9D contract: "ringer silent" needs Do Not Disturb access, so the manifest, the bridge, the UI and the status rows must all know it."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
J = ROOT / "app/src/main/java/com/nova/assistant"
man = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
main = (J / "MainActivity.kt").read_text()
fs = (J / "FeatureStatus.kt").read_text()
svc = (J / "NovaService.kt").read_text()
ui = (ROOT / "app/src/main/assets/ui.html").read_text()
checks = {
    "manifest declares ACCESS_NOTIFICATION_POLICY (else NOVA never appears in the DND access list)": "android.permission.ACCESS_NOTIFICATION_POLICY" in man,
    "bridge openDndAccess opens the policy access settings": "fun openDndAccess()" in main and "ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS" in main,
    "both state JSONs report dnd": main.count('.put("dnd", dndGranted())') == 2,
    "ui has a DND row in the permission list": "['dnd','Do Not Disturb access'" in ui and "Android.openDndAccess()" in ui,
    "ui settings list has a DND row": "call('openDndAccess')" in ui,
    "ui fix map knows dnd; guided setup order stays unchanged (optional permission)": "dnd:1" in ui and "var SETUP_ORDER=['mic','notif','acc','overlay','notifAccess','contacts','writeSettings','battery']" in ui,
    "FeatureStatus has ringer and rotation rows with a fix id": 'row(a, "ringer"' in fs and 'row(a, "rotation"' in fs and '"dnd"' in fs,
    "ringerTool still checks DND before silent": "isNotificationPolicyAccessGranted" in svc,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " " + label)
raise SystemExit(0 if all(checks.values()) else 1)
