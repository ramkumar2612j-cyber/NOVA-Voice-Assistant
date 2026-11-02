# AUTOPSY R3 (video + recordings)
Traced: VideoRecordService, RecordSession, RecordingManager, RecordRules, RecordingVoiceRules, BackgroundVideoRules, NovaService + MainActivity entry points.
Fixed (all proven by reading the code):
1. Audio recording could "start" while video owned the mic (mic loop paused, nothing fed). Now refused with a clear message.
2. MediaRecorder.stop() failure was swallowed, then the broken mp4 was exported and called "saved". Now status stop_failed, file kept in app folder (Recover can promote it if valid).
3. Auto-stops (no frames / 3 h / low space / save failed) and failed starts only made the notification vanish. Now a visible result notification.
4. Manual audio stop after the writer thread died reported a clean full-length save. Now warns (same text as auto_error).
Checked, no bug: stopSelf() before startForeground() (Android only logs, no crash), RecordRules/BackgroundVideoRules parsing, RecordingManager list/delete/recover scoping.
NOT TESTED: voice stop of video (wake-word mic is paused during video by design), screen-off camera on real phones.
Kotlin compile NOT RUN, phone NOT TESTED. Test: tools/test_autopsy_part6_contract.py
