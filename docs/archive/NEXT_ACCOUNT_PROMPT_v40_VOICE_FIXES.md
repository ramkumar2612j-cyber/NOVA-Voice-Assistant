# NOVA v40 handover (read after v39, v38, HANDOVER_v16 hard rules)
Owner: phone only, Hinglish, short steps, evidence level on every claim, one "Aapko karna hai" line at the end. Zip name NOVA_v15_assistant.zip.
Owner reported (not verified by me): GitHub build is green and the new APK runs (new tab-bar UI seen in a screenshot).

## Found from the owner's real chat (screenshot), fixed in source (Kotlin NOT compiled, phone NOT tested)
1. "...hame ek aaisa website banani h jisse ... upload kr ske ..." was NOT understood as a website request.
   Cause (read from code): SiteIntent.VERBS had "banana/banao" but not "banani/banane/bnani/bnane/banwani/banwane/banwana". Added.
   Also: this sentence has "upload" + "website" and 31 words; isPublish() could have grabbed it. isPublish() now returns false when any build verb is present.
2. "To chat gpt app open kro usse banwao or jha claude ki jaruat ho app open kro banwao ..." only opened ChatGPT.
   Cause (read from code): AgentRules.isOpenDo rejected any sentence with a SECOND open-verb after the joiner. Now a second open-verb is allowed when at least 6 words follow the joiner. "open youtube and open chrome" and "open chatgpt" still do not become agent jobs.
   Evidence: tools/test_site_contract.py (16 cases) pass; a Python mirror of isOpenDo gave old=False/new=True for the owner's sentence; test_agent_*.py pass. These are mirrors, not Kotlin.
## Typing fix (cause still UNKNOWN; these are hardening changes, Kotlin NOT compiled, phone NOT tested)
3. Third sentence started the agent but the owner saw no typing in ChatGPT. Reading the code showed no certain cause, so these were added:
   - NovaAccessibilityService.setTextChecked: focus + click the box, SET_TEXT, READ BACK; if the text is not there, clear + clipboard paste + read back. Returns failed/not_typed instead of a false "ok". Used by agentType and typeText. (The paste fallback overwrites the clipboard.)
   - AgentRules.retargetType: a "type" aimed at a label/button is moved to the ONE text box on screen (only if exactly one). Unit test added in AgentRulesTest.kt (not run: no Gradle here).
   - NovaService.agentRun now writes sys lines: "kaam samajh raha hoon", "plan ban gaya / plan nahi bana", and "step N: <act> roka gaya (reason)" when the safety check refuses a step. So the next real run shows where it stops.
   - tools/test_voice_contract.py string updated (val a0 = AgentRules.parse(answer)).
Evidence: all tools/test_*.py pass here (hindi without -I), brace balance checked; NovaService paren count -3 is the same as the original zip.
## Next
A. Owner presses Settings > SAB TEST KARO > COPY REPORT, sends it; fix FAIL lines only.
B. Owner re-tests 1 and 2 by voice and sends the chat lines.
C. Then PART 2 (message mode) from NOVA_MASTER_PROMPT_FOR_CHATGPT.md plan.

## Background jobs (owner asked "backend/background mein bhi chale") - Kotlin NOT compiled, phone NOT tested
- The agent already runs on a worker thread of the NOVA service, so NOVA's own screen does not have to be open. What it cannot do is work on a locked or dark screen.
- Added in NovaService: agentWakeOn()/agentWakeOff() = SCREEN_BRIGHT wake lock (ACQUIRE_CAUSES_WAKEUP, timeout MAX_MS+30 s) held while a job runs, released in agentStart's finally.
- agentBegin refuses when KeyguardManager.isKeyguardLocked: "Phone lock hai, unlock karo". NOVA never bypasses the lock (hard rule). tools/test_agent_background_contract.py (structural only).
- Limits to tell the owner: a locked phone, apps that hide their screen, and Android blocking background app launches (overlay permission helps) can still stop a job.
