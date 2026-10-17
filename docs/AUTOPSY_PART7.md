# AUTOPSY R4 (routines / scheduled tasks / settings bridge)
Traced: ScheduledTaskRules (parser), ScheduledTaskStore, ScheduledTaskReceiver, BootReceiver, WakeAlarmReceiver, RoutineRules, NovaService.scheduleCreate / onScheduledTask, MainActivity schedule bridge + getSettings / saveSettings, ui.html SAVE path.
Fixed (all proven by reading the code):
1. add(): if AlarmManager was missing or threw, the job was still reported "saved, may be delayed" although NO alarm existed. Now status alarm_failed, entry rolled back, voice + UI say NOT saved.
2. consume(): a repeating job re-armed its next alarm only if the storage write succeeded; a failed write killed the job silently. Now always re-armed (failure logged).
3. Android deletes ALL alarms when the APK is updated (owner updates often) and absolute alarms drift on time / zone change; only a reboot re-armed them. Now BootReceiver also re-arms on MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED, exact-alarm permission change, and MainActivity re-arms at every open.
4. rescheduleAll moved a past-due ONE-SHOT job to tomorrow (stale reminder a day late, and never saved). Now it is dropped and reported once as "missed"; repeating jobs move to the next real time and are saved.
Checked, no bug: ScheduledTaskRules.parse / nextAtMillis / encode-decode, 30-job limit, sensitive-text refusal, spoken send needs yes (default skip_order_confirm = true runs at once and tells after), saveSettings return codes vs UI handling (ok / need_acc / bad_wake / error all handled).
Owner-required UI guarded by a test (never remove): HOME tab, ACTIVATE button on home, Settings SAVE = done (closes panel + assemble animation), 3D live orb (WebGL2 canvas #orb3d).
NOT TESTED: Kotlin compile (kotlinc absent), real phone alarms, notification permission denied case (notification cannot be shown then, nothing more to do).
Open: WEATHER / SEND_MESSAGE jobs only run while NOVA is ACTIVE (else a notification); onScheduledTask returns silently when service not alive.
Test: tools/test_autopsy_part7_contract.py (16 checks PASS). All contract scripts: 72 PASS, 1 known FAIL (test_vosk_model_packaging needs .github/workflows, not in zip).
