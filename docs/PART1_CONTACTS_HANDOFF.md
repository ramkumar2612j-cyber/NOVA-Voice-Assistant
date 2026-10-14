# PART 1/5 (Intelligent Contact Resolution) - implementation added; Android build/device validation pending

Base: NOVA-Voice-Assistant-v6_88 (this zip). Sandbox of the previous account had NO network, NO kotlinc, NO Android SDK, NO JUnit,
so nothing could be compiled or run there. Build/tests must be run with GitHub Actions or a machine that has them
(tools/local_compile_check.sh needs internet; CI = `gradle testDebugUnitTest assembleDebug` + ./tools/run_all_checks.sh).

## What already exists (keep, integrate, do not duplicate)
- AndroidManifest: READ_CONTACTS declared. CALL_PHONE is used by callTool.
- Permission flow: NovaService.resolveContact() -> permRequest?.invoke("contacts") -> MainActivity REQ_CONTACTS -> pushFlow("contacts_ok"/"contacts_denied");
  Bridge.requestContacts(); status maps ".put(\"contacts\", granted(...))". Do NOT bypass it.
- NovaService.kt ~4308-4400: `resolveContact(who): CR`, `callTool`, `smsTool`, `whatsappTool` (callTool/askFirst place the call; keep that behaviour).
  Other users of resolveContact: scheduled message (~1342), battery alert Papa/Mummy (~3243). Keep them working.
  Query today: Phone.CONTENT_URI, DISPLAY_NAME LIKE %q%, max 50 rows, no CONTACT_ID.
- Logic.pickContact(query, names) (Logic.kt ~178): exact match returns the FIRST exact -> duplicate names are auto-picked (BUG vs the new rule).
- Logic.classify() (~392): call grammar = `call|phone <n>`, `<n> ko call|phone [karo|kar do|lagao|laga do]`, `<n> call karo|...` -> Cmd("call", who).
  Missing: "phone lagao" w/o call word, "<n> ko phone karo" with full names is partly OK, pronouns ("isko", "this person"), Devanagari
  (Logic.norm strips non a-z; HindiRoman.toRoman exists and is used in the STT path), synonyms (dial, ring, phone milao, baat karao).
- Dispatch: NovaService runLocalTool "call" -> callTool(c.arg); agent/tool path line ~4480 `"call" -> callTool(a.optString("who"))`.
- ContextMemory NEVER repeats call/sms/whatsapp (keep).
- Retry plumbing available: `Pending(prompt, createdAt, onNo, run)`; processAnswer(text) branches on pending identity
  (agentAnsPending / dictatePending are examples). A "contact retry" pending can be added the same way (new branch in processAnswer).
- Tests live in app/src/test/java/com/nova/assistant (JUnit4, pure Kotlin, org.json real jar). Python contract checks in tools/ (run_all_checks.sh).
  Existing tests touching this: CommandCorpusTest, LogicTest, PhoneActionRulesTest, WhatsAppCommandRulesTest, SafetyInvariantsTest - must stay green.

## Implemented design
New pure-Kotlin files (no Android classes, unit-testable):
1. ContactRequestParser.kt - Stage 1: extract name from Hindi/Hinglish/English/Devanagari(via HindiRoman) commands; detect reference
   words (isko, usko, is bande ko, this person, him/her) -> request with reference=true and no name; strip fillers/synonyms.
2. ContactMatcher.kt - Stage 3+4: normalize (lowercase, Devanagari->Roman, vowel/phonetic folding: rahul/rahool/rahul, aa->a, ee->i, oo->u, v/w, sh/s, double letters),
   score exact / phonetic / token / prefix / edit-distance; returns scored candidates + status
   {MATCH, AMBIGUOUS_DUPLICATE, AMBIGUOUS_SIMILAR, LOW_CONFIDENCE, NO_MATCH, NEED_NAME}. Group rows by CONTACT_ID (same person, several numbers = ONE candidate).
   Never auto-select when >1 plausible candidate or score < threshold.
3. ContactLookup.kt (Android, thin) - Stage 2: query ContactsContract Phone (CONTACT_ID, DISPLAY_NAME, NUMBER, TYPE, IS_SUPER_PRIMARY) with NO LIKE filter
   (so speech variants and Devanagari names can match); returns real rows or a real error (permission / SecurityException message / other exception text).
4. ContactResolution result type + user-facing messages (no match -> say so + offer retry; permission denied -> explain, no fake data; failure -> real error text).
Integration: resolveContact() in NovaService calls the new pipeline; a single confident candidate keeps today's CR(name,number) so callTool/sms/whatsapp keep working;
everything else returns an error R with the real candidates listed and executes nothing. Retry: Pending branch in processAnswer re-runs lookup with the corrected name.
"Isko/this person": no real source exists for it today -> ask "Kisko?" (optionally a RAM-only last-resolved-contact with TTL, only as NEEDS_CONFIRMATION, never auto-call).
Judgement calls to report honestly: existing callTool still places a call for ONE confident match (pre-existing); multi-number single contact picks super-primary > mobile > first.
Tests added (JUnit): `ContactResolutionTest.kt` covers exact, duplicate, similar, partial, no-match, low-confidence, multiple numbers/super-primary preference, permission-granted/denied fake source outcomes, and Hindi/Hinglish/Devanagari variations. `tools/test_contact_resolution_contract.py` is included in the existing `tools/run_all_checks.sh` wildcard. CHANGELOG.md and README.md were updated.

## Implementation and verification status (2026-10-14)
- Added `ContactRequestParser.kt`, `ContactMatcher.kt`, `ContactLookup.kt`, and `ContactResolutionPipeline.kt`.
- Integrated the parser into `Logic.classify()` and replaced the old filtered/first-row lookup in `NovaService.resolveContact()` while retaining the existing permission flow and call/SMS/WhatsApp entry points.
- Contact retries repeat lookup only and never execute a call/message. The user must repeat the full action command once the corrected contact is identified.
- Kotlin smoke assertions passed for parser, matching, ambiguity, confidence, phone-number preference, and fake lookup outcome paths. The Python contact contract script passed. The full Python contract runner did not finish within the available 5-minute execution window.
- `./gradlew testDebugUnitTest assembleDebug` could not start because the wrapper could not download Gradle from `services.gradle.org` (`UnknownHostException`). This environment has no configured Android SDK, and the JUnit suite / Android build remain unverified. Run CI or a machine with network, Gradle 8.7 and Android SDK 34 before treating the build as passed.
- A real Android device contact query was not possible here; fake lookup tests validate logic only, not device permission/provider access.

## Next step
Keep this branch on PART 1/5 until CI has run `./tools/run_all_checks.sh` and `gradle --no-daemon testDebugUnitTest assembleDebug` successfully. Do not implement PART 2/5 yet.

