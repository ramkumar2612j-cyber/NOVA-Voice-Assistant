# NOVA - NEXT ACCOUNT PROMPT (v7.16 = PART 2 of 10). Paste FIRST + attach the part zip.
Rules unchanged (v7_13 + v7_14 + v7_15_PART1 prompts in the zip): owner NOT a programmer, Android phone only, Hinglish, SHORT, ONE step at a time, add-only, PASS / FAIL / NOT TESTED with evidence, never touch .github/workflows/build.yml, do NOT push until owner says so. Goal: EVERY feature visible in UI + known to NOVA as skill, real logic, modern animated UI; 10 parts, zip + prompt each part.
OWNER DECISION (2026-10-17): keep ONLY branch `main` in the repo; he does not know how wip branches appeared and does not want them. CI builds main. Branch deletion must be done by the owner on github.com (sandbox has no network): give him ONE step at a time (repo > branches > trash icon on each wip branch, after confirming main has the latest code).

## PART 2 DONE (static only)
1. SAFETY (real bug): NovaNotificationService.smartAutoReply now calls PaymentReplyRules.decide(m.text) BEFORE smartDraft (the AI). SECRET (otp/pin/cvv/password/kyc...) = no reply, ActionLog "skipped" + secretNote(). Other money talk = one fixed safe line (no AI, no amount, no UPI detail), owner notified (notifyPhone + onSmartReplied). Covers WhatsApp Business smart path (bizSmart -> smartAutoReply) too. The older non-smart autoReply path already skips code/OTP via AutoReplyRules.skipReason and sends the owner's own fixed text (no AI).
2. New guard tools/test_payment_ai_guard_contract.py = PASS (order check: payment before smartDraft).
3. PhotoEditIntent "professional banao": by reading the code it is ALREADY handled (v7.12 makeOk rule); handover said ENHANCE=null, cannot confirm without running JUnit. NOT RUN.

## EVIDENCE
RUN: run_all_checks.sh = see below numbers in chat (new guard added). OLD guard tools/test_payment_reply_contract.py still FAILS: it greps strings of the OTHER line (SocialReplyRules soc?.reply, SmartReplyRules.useAi). Behaviour is covered by the new guard. Ask owner before adapting/retiring the old one (never delete).
NOT RUN: Kotlin compile (NovaNotificationService edit is new: first compile suspect), JUnit, Gradle, APK, phone.

## NEXT (Part 3)
Classify the remaining FAILs (autopsy_part2..9, no_feature_loss, part4_part5, site_media, social_reply, auto_media): real bug = fix add-only + test; other-line guard = list for owner. Then Part5 points 3-7 (HUD speak, gallery + photo editor, SiteGen media), skills-vs-UI match, animations/responsive, final zip + build steps.
