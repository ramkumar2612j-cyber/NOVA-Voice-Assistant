# Test report - Part 6 (v6.76), agent loop hardening

Honest rule: every line is RUN / NOT RUN / PASS / FAIL / NOT TESTED.

## 1. What could be run in this sandbox
| Check | Result |
|---|---|
| kotlinc / Gradle / Android SDK | **NOT RUN**: none installed, no internet |
| Kotlin compile of `ToolVerify.kt`, the `NovaService` hooks, `AgentRules.isAiSend` | **NOT RUN** (hand read-through only: no problem seen) |
| JUnit: new `ToolVerifyTest` (7), `AgentRulesPart6Test` (6); old tests | **NOT RUN** |
| `tools/test_tool_verify_contract.py` (27 tools have a level, hooks present, spoken yes kept, classify port == test expectations) | **PASS** |
| `./tools/run_all_checks.sh` | **PASS**: 45 scripts, 0 failed |
| Real phone | **NOT TESTED** |

## 2. Audit: what each tool verifies (read from the source)
| Tool | Level | How |
|---|---|---|
| torch on / off | VERIFIED | `TorchCallback` state read back (800 ms) |
| volume up / down / set / mute / max | VERIFIED | `AudioManager.getStreamVolume` before / after |
| brightness set / step | VERIFIED | `Settings.System` value read back (needs write permission) |
| media play / pause | PARTIAL | `isMusicActive` after 400 ms; text says "not confirmed" if no player |
| media next / previous | UNVERIFIABLE | key event only; text says "cannot be confirmed" |
| open app / url / navigate / camera / settings | PARTIAL | `launch()` watches the foreground package via Accessibility, else "requested, not confirmed" + notification |
| call | PARTIAL | Android accepted the dial request; connection is not read back; spoken yes is mandatory |
| alarm / timer | UNVERIFIABLE | Android gives no answer; the text already says so |
| sms / whatsapp / calendar event | HANDOFF | NOVA prepares; WhatsApp needs a spoken yes first; SMS / calendar: the owner presses send / save |
| tap / type / scroll (screen) | PARTIAL | screen change checked by the agent loop, not by a system read-back |

## 3. Changes and their proof
| Change | Python | Kotlin JUnit | Device |
|---|---|---|---|
| One retry for torch / volume / brightness when not confirmed | source hook checked (exec() runs at most twice) | NOT RUN | NOT TESTED |
| `finish()` honest note for unconfirmable successes, one guide for failures | classify port agrees with 15 expectations | NOT RUN | NOT TESTED |
| AI-app send is free only for a plain send button (`isAiSend`) | source checked | NOT RUN | NOT TESTED |
| DENY / CONFIRM cases for banking, payment, installer, own screen, money words, risky words | (none) | NOT RUN | NOT TESTED |

## 4. Not done / limits
- `ToolVerify.report(...)` (one-sentence report) is written and tested but not yet spoken by the agent loop.
- Screen-agent steps (tap / type) still rely on the existing screen-signature check and the separate verifier; no new per-step read-back exists, because Android has none.
- A retry might not help on a phone that blocks the torch in the background: that stays "not confirmed" after the second try, with the guide text.
