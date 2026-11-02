# NOVA autopsy - PART 2 of 6 (run path: start / wake / popup / Updater) - v6.99 source
Evidence: PASS = script in sandbox, NOT RUN = needs compile / phone.
## Found + fixed (written + statically checked; Kotlin compile NOT RUN, phone NOT TESTED)
1. LOCK GUARD default ON made NOVA deaf with screen off / phone locked (lockQuiet: keyguard locked OR screen not interactive). Fix: new switch ExtCfg.listenLocked (default ON) = "Screen off / lock par bhi Nova suno" in BROWSERS card (wb_ll). Lock Guard call/Papa-Mummy alerts untouched.
2. Overlay-permission nudge ("card band hai") fired once per service run, then never. Fix: repeats after 10 min.
## Checked, no code change
- Updater: download SHA-256 + size verified in UpdateManager (streamed digest vs feed hash, mismatch deletes file). Updater.sha256Hex is test-only duplicate. PASS (static).
- Voice unlock was removed in v6.48 (unlockPhone() only says "unlock yourself"). Owner believes it exists: tell owner.
- After reboot NOVA is OFF by Android 14 rule; BootReceiver posts a tap notification. NovaService is START_NOT_STICKY (not restarted if Android kills it) - left as is, needs phone proof.
- No automatic time-window ON/OFF for NOVA exists in code (only scheduled tasks + morning alarm).
## Tests
tools/test_autopsy_part2_contract.py (6 checks PASS). run_all_checks: 67 PASS, 1 FAIL = test_vosk_model_packaging (needs .github/workflows/build.yml, which is in the repo, not in the zip).
