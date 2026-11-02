# v7.126 - Ultron live mouth (status)

DONE and SEEN (headless Chromium 390x844, no JS errors):
- assets/ui.html: add-only block "ultron-mouth-v7126" at the very end (CSS + small JS). A dark red-glow mouth sits on the Ultron hero (home screen, .orbbox). It is HIDDEN normally and shows + opens/closes in a speech rhythm ONLY while `#app` has class `speak` (same class the Speaking card uses). Stops and hides when speaking ends.
- Pure CSS animation (keyframes uTalk), no JS timer (the page's JS timers are very slow, so a timer-based mouth lagged).
- Position follows the hero image (440x340, cover, center 22%) on any screen size via resize/ResizeObserver.

NOT DONE: not compiled / not phone-tested (no Gradle here). Mouth is a rhythm, not matched to the real audio loudness.
