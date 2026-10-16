# Test report - v6.97 wip14 (open websites in Chrome tabs + Play Store download), 2026-10-16

Honest status words: PASS = really run and passed. FAIL = really run and failed. NOT RUN = could not be run here. NOT TESTED = nobody has tried it on a phone.

## 1. What was run in the last sandbox
| Check | Result |
|---|---|
| Python contract scripts `bash tools/run_all_checks.sh` | **PASS, 59 passed, 0 failed** (58 old + new `tools/test_open_web_download_contract.py`) |
| `tools/test_open_web_download_contract.py` (static) | **PASS**: Control.BLOCKED_PACKAGES still holds com.android.vending; WINDOW_MS = 60_000L; playInstall clicks once, only after mayTap, disarms before the click, presses only the node whose whole text is "Install"; playRoot returns only the Play Store window; tapLabel still calls Control.blocked; handle() order parseOpen -> parseDownload -> AgentRules.isTask; "download start ho gaya" only for code `started`; Play intent pinned to com.android.vending; no EXTRA_APPLICATION_ID in openChromeTab |
| Brace balance of the 4 edited Kotlin files | `{}` balanced in all 4; `()` of NovaService is -2 in the ORIGINAL upload too (same value after the edits) |
| Word-level routing check of 5 WhatsApp send/call phrases against the block lists | parseOpen and parseDownload both null for all 5 |
| `diff` against the uploaded zip | NovaService: 3 hunks + the Part 5 edits; NovaAccessibilityService: 1 inserted block; no changed line mentions askFirst / skipOrderConfirm / skipRiskyConfirm / claimAction / waAppOf / ContactCandidate |

| Help / UI wiring: `tools/test_help_contract.py` | **PASS**: 18 topics equal in HelpGuide.kt and the ui.html MADAD list |
| `tools/test_feature_check_contract.py` | **PASS** (structural only) |
| ui.html JavaScript `node --check` on every block | **PASS**, 0 errors |
| HelpGuideTest (new test `newWebAndDownloadTopicsAreFound`) | **NOT RUN** |

## 2. NOT RUN (no kotlinc, no internet in the last sandbox)
| Item | Status |
|---|---|
| Kotlin compile of NovaService (Parts 2, 4, 5 edits) | **NOT RUN** (hand-reviewed only) |
| Kotlin compile of NovaAccessibilityService.playInstall / playOpenResult | **NOT RUN** (hand-reviewed only) |
| Kotlin compile of PlayInstallRules.kt | **NOT RUN** |
| JUnit OpenWebRoutingTest (3 tests) | **NOT RUN** |
| JUnit PlayInstallRulesTest (18 tests) | **NOT RUN** (expected results were checked by hand) |
| JUnit OpenWebRulesTest (15 tests) | compiled + run in an EARLIER session (Part 1): 15/15 PASS; **NOT RE-RUN** after Parts 2-5 |
| Full JUnit regression | **NOT RUN**. Baseline of the earlier session: 834 run, 24 failures, ALL org.json stub only (ConnectorParseTest 11, WebBrowsersTest 6, WebSourcesTest 6, ConnectorRulesTest 1). After the new tests the failure list must be the same 24 and nothing else |
| GitHub Actions "NOVA Android CI" on this ZIP | **NOT RUN** yet (the owner uploads `tools` first, then `app`) |

## 3. NOT TESTED on a phone
- "youtube chrome pe kholo" (one tab) and 4-5 sites in one sentence (separate tabs, 600 ms apart): NOT TESTED.
- "whatsapp download karo" and any other app (Play page, one Install press, honest result text): NOT TESTED.
- Unknown app name via Play search: NOT TESTED. Play Store in a non-English language: expected to press nothing: NOT TESTED.
- Spoken Hinglish / Hindi spelling of "download", "kholo", "chrome" by the speech engine: NOT TESTED.
- WhatsApp direct send after these changes: code untouched, behaviour NOT TESTED by Claude (the owner said earlier that STOP / SILENT / HAAN-NAHI work in v6.97).

## 4. Known limits (not bugs found by a test)
- Spoken STOP cannot interrupt a turn that is already running (downloadApp waits up to about 30 s in the worst case, usually a few seconds). NOT FIXED.
- An app whose Play title is longer than its label is not matched: nothing is pressed and NOVA says so.
- An unknown-name search can reach another developer's app with the identical title.
