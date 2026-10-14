#!/usr/bin/env bash
# Runs every Python contract script (they are plain scripts that sys.exit(), NOT unittest modules, so
# `python3 -m unittest discover tools` reports false errors) plus the skill-pack validator.
# Usage (project root):  ./tools/run_all_checks.sh
cd "$(dirname "$0")/.." || exit 2
pass=0; fail=0
for t in tools/test_*.py; do
  if python3 "$t" >/tmp/nova_check.$$ 2>&1; then pass=$((pass+1)); echo "PASS $t"
  else fail=$((fail+1)); echo "FAIL $t"; tail -5 /tmp/nova_check.$$; fi
done
rm -f /tmp/nova_check.$$
python3 tools/validate_skillpack.py && echo "PASS tools/validate_skillpack.py" || { fail=$((fail+1)); echo "FAIL validate_skillpack"; }
find . -name __pycache__ -prune -exec rm -rf {} + 2>/dev/null
echo "contract scripts: $pass passed, $fail failed"; [ "$fail" -eq 0 ]
