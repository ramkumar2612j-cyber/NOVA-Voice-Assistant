#!/usr/bin/env bash
# Runs every Python contract script (they are plain scripts that sys.exit(), NOT unittest modules, so
# `python3 -m unittest discover tools` reports false errors) plus the skill-pack validator.
# Usage (project root):  ./tools/run_all_checks.sh
cd "$(dirname "$0")/.." || exit 2
pass=0; fail=0; stale=0; newfail=""
# v7.155: every check gets a time limit (NOVA_CHECK_TIMEOUT, default 150 s) so ONE stuck script can never hold the APK build forever.
# Slow checks (> NOVA_SLOW_SECS, default 20 s) are listed at the end. Exit code 124 = timed out (counted as FAIL, shown clearly).
TMO="${NOVA_CHECK_TIMEOUT:-150}"; SLOW="${NOVA_SLOW_SECS:-20}"; slowlist=""
for t in tools/test_*.py tools/check_p[0-9]_*.py; do
  _s=$(date +%s)
  if timeout "$TMO" python3 "$t" >/tmp/nova_check.$$ 2>&1; then pass=$((pass+1)); echo "PASS $t"; _rc=0
  else _rc=$?; fail=$((fail+1)); echo "FAIL $t"; [ "$_rc" = 124 ] && echo "  ^ TIMEOUT after ${TMO}s"; tail -5 /tmp/nova_check.$$
    if grep -qx "$(basename "$t")" tools/KNOWN_STALE_GUARDS.txt 2>/dev/null; then stale=$((stale+1)); echo "  ^ known stale guard of the other code line (see tools/KNOWN_STALE_GUARDS.txt)"
    else newfail="$newfail $(basename "$t")"; echo "$t" >> /tmp/nova_newfail_detail.$$; tail -15 /tmp/nova_check.$$ >> /tmp/nova_newfail_detail.$$; fi; fi
  _d=$(( $(date +%s) - _s )); [ "$_d" -ge "$SLOW" ] && slowlist="$slowlist $(basename "$t")(${_d}s)"
done
[ -n "$slowlist" ] && echo "slow checks (>=${SLOW}s):$slowlist"
rm -f /tmp/nova_check.$$
python3 tools/validate_skillpack.py && echo "PASS tools/validate_skillpack.py" || { fail=$((fail+1)); echo "FAIL validate_skillpack"; }
find . -name __pycache__ -prune -exec rm -rf {} + 2>/dev/null
if [ -n "$newfail" ]; then echo "=========== NEW FAIL (not known stale): $newfail ==========="; cat /tmp/nova_newfail_detail.$$ 2>/dev/null; echo "=========================================================="; fi
rm -f /tmp/nova_newfail_detail.$$
echo "contract scripts: $pass passed, $fail failed ($stale of them known stale)"
# v7.24: build.yml runs this script BEFORE the Gradle build; known-stale guards of the other code line must not stop the APK.
# Any NEW failure (fail > stale) still returns 1.
[ "$fail" -le "$stale" ]
