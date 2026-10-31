#!/bin/bash
# v7.139 AUTO TEST on an Android emulator (runs in GitHub Actions, job "smoke"). Writes AUTO_TEST_STATUS.md with real PASS/FAIL.
# What it CAN prove: the APK installs, opens, stays alive, no crash / ANR in 30 s, survives 400 random taps, still alive after.
# What it can NOT prove: voice, wake word, WhatsApp, Play Store, MT5, Jarvis sound (an emulator has no real mic / those apps). Those stay in TEST_STATUS.md (owner phone).
APK=${1:?apk path}; PKG=com.nova.assistant; OUT=${2:-smoke_out}; mkdir -p "$OUT"; R="$OUT/AUTO_TEST_STATUS.md"
res() { echo "| $1 | $2 | $3 |" >> "$R"; [ "$2" = "FAIL" ] && FAILED=1; }
FAILED=0
{ echo "# AUTO TEST (emulator) $(date -u +%F' '%H:%M) UTC"; echo; echo "| Test | Result | Note |"; echo "|---|---|---|"; } > "$R"
adb wait-for-device; adb shell input keyevent 82 >/dev/null 2>&1
if adb install -r "$APK" 2>&1 | tee "$OUT/install.txt" | grep -q Success; then res "APK install" PASS ""; else res "APK install" FAIL "$(tail -1 "$OUT/install.txt")"; echo "$(cat "$R")"; exit 1; fi
for p in RECORD_AUDIO POST_NOTIFICATIONS CAMERA READ_CONTACTS CALL_PHONE; do adb shell pm grant $PKG android.permission.$p >/dev/null 2>&1 || true; done
adb logcat -c; adb shell am start -W -n $PKG/.MainActivity > "$OUT/start.txt" 2>&1
if grep -q "Status: ok" "$OUT/start.txt"; then res "App opens" PASS ""; else res "App opens" FAIL "$(head -3 "$OUT/start.txt" | tr '\n' ' ')"; fi
sleep 30; adb exec-out screencap -p > "$OUT/screen_after_30s.png" 2>/dev/null
if adb shell pidof $PKG >/dev/null 2>&1 && [ -n "$(adb shell pidof $PKG | tr -d '\r')" ]; then res "Alive after 30 s" PASS ""; else res "Alive after 30 s" FAIL "process gone"; fi
adb logcat -d > "$OUT/logcat_full.txt" 2>&1
if grep -q "FATAL EXCEPTION" "$OUT/logcat_full.txt"; then res "No crash (30 s)" FAIL "$(grep -A3 'FATAL EXCEPTION' "$OUT/logcat_full.txt" | head -5 | tr '\n' ' ')"; else res "No crash (30 s)" PASS ""; fi
if grep -q "ANR in $PKG" "$OUT/logcat_full.txt"; then res "No ANR (30 s)" FAIL "app not responding"; else res "No ANR (30 s)" PASS ""; fi
# v7.150 (step 10) ADD-ONLY: ANR reason lines + native crash ("Fatal signal") checks, 30 s phase
if grep -q "Fatal signal" "$OUT/logcat_full.txt"; then res "No native crash (30 s)" FAIL "$(grep -m1 'Fatal signal' "$OUT/logcat_full.txt" | cut -c1-160)"; else res "No native crash (30 s)" PASS ""; fi
if grep -q "ANR in $PKG" "$OUT/logcat_full.txt"; then grep -A6 "ANR in $PKG" "$OUT/logcat_full.txt" | grep -m3 "Reason:\|PID:\|Load:" > "$OUT/anr_reason_30s.txt"; res "ANR reason (30 s)" INFO "$(tr '\n' ' ' < "$OUT/anr_reason_30s.txt" | cut -c1-200)"; fi
adb logcat -c
adb shell monkey -p $PKG --throttle 250 --ignore-security-exceptions -s 42 -v 400 > "$OUT/monkey.txt" 2>&1
adb logcat -d > "$OUT/logcat_monkey.txt" 2>&1
if grep -q "FATAL EXCEPTION\|// CRASH" "$OUT/logcat_monkey.txt" "$OUT/monkey.txt"; then res "400 random taps" FAIL "$(grep -h -A3 'FATAL EXCEPTION' "$OUT/logcat_monkey.txt" | head -5 | tr '\n' ' ')"; else res "400 random taps" PASS ""; fi
if grep -q "ANR in $PKG" "$OUT/logcat_monkey.txt"; then res "No ANR (taps)" FAIL "app not responding"; else res "No ANR (taps)" PASS ""; fi
# v7.150 (step 10) ADD-ONLY: same checks after the 400 random taps
if grep -q "Fatal signal" "$OUT/logcat_monkey.txt"; then res "No native crash (taps)" FAIL "$(grep -m1 'Fatal signal' "$OUT/logcat_monkey.txt" | cut -c1-160)"; else res "No native crash (taps)" PASS ""; fi
if grep -q "ANR in $PKG" "$OUT/logcat_monkey.txt"; then grep -A6 "ANR in $PKG" "$OUT/logcat_monkey.txt" | grep -m3 "Reason:\|PID:\|Load:" > "$OUT/anr_reason_taps.txt"; res "ANR reason (taps)" INFO "$(tr '\n' ' ' < "$OUT/anr_reason_taps.txt" | cut -c1-200)"; fi
adb exec-out screencap -p > "$OUT/screen_after_monkey.png" 2>/dev/null
echo; cat "$R"; [ -n "$GITHUB_STEP_SUMMARY" ] && cat "$R" >> "$GITHUB_STEP_SUMMARY"
exit $FAILED
