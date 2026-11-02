# TEST REPORT - Part 8 (v6.80) chat history + storage
Honest rule: RUN / NOT RUN / PASS / FAIL / NOT TESTED.

| Item | Result |
|---|---|
| Kotlin compile + JUnit via tools/local_compile_check.sh (kotlinc 1.9.24, android-34 jar, real JUnit 4.13.2) | RUN, compile OK, 639 tests, 0 failures |
| New JUnit: ChatStoreTest (codec, file backend, engine, migration + rollback), ChatRulesPart8Test (ids, query, snippet, contextCounts vs FallbackContext.fullWindow) | RUN, PASS |
| tools/run_all_checks.sh (45 Python contract scripts + skillpack) | RUN, 45/45 PASS |
| tools/ui_chat_check.py: headless Chromium, 360x740, FAKE stateful JS bridge: layout vs tab bar, search, snippet, pin, rename (+blank refused), export call, double-tap delete, delete-all (busy refused, double tap), empty state, read-error + retry, 300-message chat (DOM cap + note), context line | RUN, 19/19 PASS, screenshots in /home/claude/shots8 (not in zip) |
| Chat panel / input vs bottom tab bar overlap | RUN in headless: chat bottom == tab top, input above it, NO overlap. Old overlap screenshot not reproduced. NOT TESTED on a phone |
| Gradle `./gradlew assembleDebug testDebugUnitTest` / GitHub Actions | NOT RUN (Maven/Google blocked in sandbox) |
| Kotlin bridge methods (searchChats, renameChat, pinChat, deleteAllChats, exportChat, contextInfo) on a phone | NOT TESTED (compiled, source-checked by Python contract only; UI tested against a JS fake, not the real bridge) |
| System "save file" box for export (REQ_EXPORT_CHAT=46) | NOT TESTED on a phone |
| Migration SharedPreferences -> files on a real phone with real old chats | NOT TESTED (engine migration + rollback JUnit PASS with in-memory old store and real temp files) |
| Resume after restart | JUnit PASS (new engine on same files). Real app restart NOT TESTED |

## Known gaps
- Search is plain case-insensitive substring per word (no Hindi/English translation match, no Devanagari<->Roman).
- Context line counts stored messages by the same rule as FallbackContext.fullWindow (JUnit checks agreement for strictly alternating chats); chats with two same-side messages in a row can differ by a message.
- Chat screen shows only the newest 120 messages (note shown); all are stored and exported.
- Half-written last line after a crash: that one line may be lost or shortened.
