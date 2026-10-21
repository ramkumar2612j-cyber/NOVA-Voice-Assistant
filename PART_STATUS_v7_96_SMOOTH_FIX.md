# v7.96 - SMOOTH live switch hang (ui.html only)
CAUSE (likely, from code; NOT confirmed on phone): choosing SMOOTH live pinned NovaPerf, which switched off the stall safety net (orb -> 2D fallback).
FIX: NovaPerf.floorTo(n) (no pin). RUN: headless check (pinned=false, stalls -> level 4, orb falls back), python guards 146/146 PASS. Kotlin unchanged (v7.95: compile 0 error, JUnit 1454/1454).
NOT TESTED on phone. If it still hangs: send Settings > TEST REPORT text and say whether it hangs at the switch moment or after reopening.
