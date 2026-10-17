# NOVA - NEXT ACCOUNT PROMPT (v7.19 = PART 5 of 10). Paste FIRST + attach the part zip.
Rules unchanged (v7_13, v7_14, v7_15..v7_18 prompts in zip): owner NOT a programmer, Android phone only, Hinglish, SHORT, ONE step at a time, add-only, PASS / FAIL / NOT TESTED with evidence, never touch .github/workflows/build.yml, do NOT push until owner says so. Only branch `main` is wanted; owner deletes wip branches himself on github.com, one step at a time, after confirming main has the latest code.

## FINDINGS (by reading code, this tree = real wip13 line)
- The old "Part 5 points 3-5" are ALREADY in this tree: HUD speak / speak_end events (NovaService emit("mode","speak"/"speak_end")), gallery picker (pickPhoto, pickSiteMedia, handleSiteMedia), photo editor (editPhoto, editedToSite, editReport, editPhone, editAi, PhotoPro, PhotoEditIntent), website media (SiteMedia file store + SiteGen userBlock/applyUser). All referenced from ui.html (wiring audit OK).
- DESIGN NOTE: there are TWO media stores: SiteMedia (file based, filled by the gallery picker and editedToSite, used by the site generator) and SiteMediaStore (in-memory, nobody calls SiteMediaStore.add, so SiteGen's userItems is always empty = dead path but harmless). Do NOT merge blindly (double photos risk). If the owner wants the "ye photo hero mein" placement for picked photos, it works through SiteMedia.placeText (file store). tools/test_site_media_contract.py "SiteGen uses userBlock 3 times" is a stale guard of the other line.

## PART 5 DONE (static only)
1. NovaNotificationService.onNotificationPosted: handleLock / handleBiz / handleGmail each isolated with try/catch + ErrLog, outer catch now logs "posted: $e" (before: one failing helper silently stopped message reading / driving mode).
2. play_music (AI tool): only opens a YouTube SEARCH; it now says exactly that ("YouTube par 'X' ki search khol di, gaana abhi nahi baja") instead of "YouTube khul gaya", so the AI cannot claim playing.
3. ui.html schedule form: alarm_failed ("saved but Android refused the alarm", entry kept and cleared from the form), storage_failed, invalid, message_incomplete_or_sensitive now have honest texts (before: "Save nahi ho paya" even when saved).

## EVIDENCE
RUN: tools/run_all_checks.sh (numbers in chat), test_ui_contract ALL PASSED, node --check on edited UI scripts OK. NOT RUN: Kotlin compile (new edits: NovaNotificationService onNotificationPosted, NovaService play_music block = first compile suspects), JUnit, Gradle, APK, phone. NOT TESTED: all of it on a phone.

## REMAINING FAILs (read, NOT run) - mostly other-line guards, ask owner once, never delete
autopsy_part2 (listenLocked), autopsy_part6 (`val err: String` shape), autopsy_part7 (scheduleAlarmState is public here), autopsy_part8 (ALARM_*, postNote, MainActivity brace check), autopsy_part9 (needs CallHub, other line), no_feature_loss, part4_part5, site_media, social_reply, payment_reply, auto_media.

## NEXT (Part 6)
Skills-vs-UI match: list every feature (voice intents in Logic/IntentParser, HelpGuide, skills.json / knowledge) against UI entries; add missing Skills entries + HelpGuide lines (ask owner about HelpGuide / FEATURE_INVENTORY once). Then modern animations + small/large screen CSS, final zip + build steps + test report.
