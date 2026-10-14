# NOVA v45 handover (read after v44, v40 and HANDOVER_v16 hard rules)
Owner: phone only, Hinglish, short steps, evidence level on every claim, one "Aapko karna hai" line at the end. Zip name MUST be NOVA_v15_assistant.zip.

## What happened
- Run #4 of "Build NOVA from zip" failed in "Zip kholo": the repo had NOVA_v15_assistant_buildfix.zip, not NOVA_v15_assistant.zip (file name only).
- After the file was renamed, the zip unpacked, tests passed, and "APK banao" failed with REAL Kotlin errors (owner's screenshot):
  1. CalendarMeetingReceiver.kt imported android.app.BroadcastReceiver (wrong package). Now android.content.BroadcastReceiver.
  2. NovaService.kt:1250/1251 called ScheduledTaskRules.clock(...) which does not exist. Now Timers.clock(...) (exists).
  3. NovaService.onMeetingReminder(title, begin) was called by the receiver but did not exist. Added: speaks the reminder, changes nothing.
## Evidence
- Errors read from the owner's CI screenshot; fixes are one-line/small. Kotlin NOT compiled here, so more errors may appear after these (the compiler can hide later errors). Phone not tested.
## Next
- Owner uploads this zip as NOVA_v15_assistant.zip, runs "Build NOVA from zip", sends the next red lines or the green run.
