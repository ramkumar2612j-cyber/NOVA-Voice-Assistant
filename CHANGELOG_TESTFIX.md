# Test fix (CI run: 1117 tests, 3 failed)
Only test files changed, no app code:
- PhoneVoiceRulesTest.part2ScreenshotRingerRotation: expectations now match the repo's own rules (global/screenshot, rotation auto_on/auto_off).
- PhoneVoiceRulesTest.part2DoesNotStealOtherCommands: "awaaz band karo" is checked through Understand.classify (what the app calls).
- SocialReplyRulesTest: per-person limit is 3/hour (as AutoReplyRules + CHANGELOG), test renamed threeMessagesPerPersonPerHour.
NOT RUN locally (no gradle/network here). Build on GitHub to confirm.
