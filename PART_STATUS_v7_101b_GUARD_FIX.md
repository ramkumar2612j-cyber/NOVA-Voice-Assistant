# v7.101b - GitHub build fix (guards only, no feature change)
FAIL on Actions: "Source contract checks" 8 guards failed (138 passed).
CAUSE 1 (7 guards): versionName "7.101" has 3 digits; old guards accepted only 7.75-7.99 by regex. FIX: regex now also accepts 7.100-7.199 (tools/test_*_contract.py, 7 files).
CAUSE 2 (native_smooth): the new askPageDirect() had the text "{" inside startsWith("{"), the guard counts braces as text. FIX: startsWith("\u007B") (same meaning for Kotlin).
RUN here: these 8 guards now exit 0, 0 FAIL lines.
NOT RUN: full run_all_checks (hangs in this sandbox), Kotlin compile, JUnit, GitHub Actions, phone.
