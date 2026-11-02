# Continuation notes - Part 7 (v6.79) DONE in source, NOT compiled: memory manager

Base: this ZIP. Read CONTINUATION_PART1-6.md, TEST_REPORT_PART7.md, CHANGELOG v6.79.

## DONE (written; never compiled or run in Kotlin)
- PersonalMemory.kt: "key" refusal, Devanagari-aware tokens(), search / removeAt / replaceAt / exportText / importText / screenReply / importReply.
- PersonalMemoryStore.kt: update() and importFrom() (one lock).
- MainActivity.kt: bridge getMemoryJson / memoryAdd / memoryEdit / memoryDelete / memoryExport / memoryImport; onActivityResult REQ_EXPORT_MEM (44) / REQ_IMPORT_MEM (45).
- ui.html: new Memory screen (search, EDIT, HATAO, JODO, EXPORT, IMPORT, limit + privacy line), window.onMemBackup.
- NovaService.askCloud: Gemini gets FallbackContext.taskLine too.
- Tests: MemoryScreenOpsTest (new), PersonalMemoryTest (regenerated), tools/test_memory_contract.py (159 checks).

## Decisions
- Gemini gets NO personal facts (unchanged); only the one safe task line.
- An imported file is data: every line passes add().
- Edit / delete need the old text, so a voice command in between cannot hit the wrong fact.
- No confirmation added back (owner rule). Single-fact delete is direct; SAB HATAO keeps its double tap.

## First step next time
Run GitHub Actions on this ZIP (`./gradlew assembleDebug testDebugUnitTest`); send the log if red. Likely places: PersonalMemory.kt (new functions), PersonalMemoryStore.kt (`return@synchronized null`), MainActivity.kt (bridge `memResult` + two new onActivityResult branches), MemoryScreenOpsTest.kt, plus all never-compiled Part 5 / 6 files (ToolVerify.kt, UnsupportedRules.kt, VoiceDiagnosis.kt). Then Part 8.
