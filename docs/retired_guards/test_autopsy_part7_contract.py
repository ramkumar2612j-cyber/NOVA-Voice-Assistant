#!/usr/bin/env python3
"""AUTOPSY R4 (static): routines / scheduled tasks / settings + owner-required UI invariants."""
import pathlib, sys
R = pathlib.Path(__file__).resolve().parent.parent
J = R / "app/src/main/java/com/nova/assistant"
ui = (R / "app/src/main/assets/ui.html").read_text(encoding="utf-8")
man = (R / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
st = (J / "ScheduledTaskStore.kt").read_text(encoding="utf-8")
rc = (J / "ScheduledTaskReceiver.kt").read_text(encoding="utf-8")
bt = (J / "BootReceiver.kt").read_text(encoding="utf-8")
svc = (J / "NovaService.kt").read_text(encoding="utf-8")
ma = (J / "MainActivity.kt").read_text(encoding="utf-8")
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1

# 1. no alarm at all must never be reported as saved
check("alarm tri-state exists", "ALARM_FAILED" in st and "scheduleAlarmState" in st)
add = st.split("fun add(")[1].split("fun update(")[0]
check("add() reports alarm_failed and rolls back", '"alarm_failed"' in add and "write(ctx, old)" in add)
fn = st.split("private fun scheduleAlarmState")[1].split("private fun cancelAlarm")[0]
check("no silent 'false' catch left", "catch (ignored: Exception) { false }" not in fn and fn.count("ALARM_FAILED") >= 3)
check("service says NOT saved", '"alarm_failed" ->' in svc)
check("UI knows alarm_failed", "alarm_failed:" in ui)

# 2. repeating job must re-arm even if the write fails
cs = st.split("fun consume(")[1].split("fun rescheduleAll")[0]
check("consume re-arms regardless of write result", "if (write(ctx, old.map { if (it.id == id) next else it })) scheduleAlarm" not in cs and "scheduleAlarm(ctx, next)" in cs)

# 3. alarms are lost on app update; time/zone/exact-permission change
check("BootReceiver handles package replaced / time / zone / exact", all(x in bt for x in ("ACTION_MY_PACKAGE_REPLACED", "ACTION_TIME_CHANGED", "ACTION_TIMEZONE_CHANGED", "SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")))
check("manifest lists those actions", all(x in man for x in ("MY_PACKAGE_REPLACED", "TIME_SET", "TIMEZONE_CHANGED", "SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")))
check("boot still asks to start only on boot", "askToStart(c)" in bt and bt.index("MY_PACKAGE_REPLACED") < bt.index("askToStart(c)"))
check("alarms re-armed on every app open", "ScheduledTaskStore.rescheduleAll(this)" in ma)

# 4. past-due one-shot is reported as missed, never fired a day late
ra = st.split("fun rescheduleAll")[1].split("fun listText")[0]
check("rescheduleAll drops stale one-shot + notifies", "notifyMissed" in ra and "missed" in ra and "repeatDaily" in ra)
check("receiver has notifyMissed", "fun notifyMissed" in rc)

# 5. OWNER-REQUIRED UI (must never be removed): HOME tab, ACTIVATE button, Settings SAVE = done, 3D live orb
check("HOME tab exists", 'id="t_home"' in ui and "<span>HOME</span>" in ui)
check("ACTIVATE button on home", 'id="btn">ACTIVATE</button>' in ui and "btn.textContent=o?'STOP':'ACTIVATE'" in ui)
check("Settings SAVE closes panel + assemble animation", "setTimeout(closePanel,900)" in ui and "if(play&&window.tfAssemble)tfAssemble()" in ui and 'id="s_save"' in ui)
check("3D live orb canvas + WebGL2 path", 'id="orb3d"' in ui and "__orb3d" in ui and "webgl2" in ui.lower())
print("failed:", bad); sys.exit(1 if bad else 0)
