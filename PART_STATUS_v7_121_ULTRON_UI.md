# v7.121 - Ultron UI (status)

DONE and SEEN (headless Chromium, 390x844, fake Android bridge, no JS errors): home, listening card, thinking card, speaking (red) card, chat screen.
- assets/ui.html: add-only block "ultron-skin-v7121" at the end (CSS + JS). Theme, Ultron hero (ultron_hero.jpg), "AI Voice Assistant", "Always With You", chat bubbles with avatar/name/time (avatar is INSIDE the bubble because "#clog .msg" has content-visibility:auto which clips anything outside), cut-corner input bar, state card #u_state (SILENT in listen/think, STOP in speak, X hides it).
- assets: ultron_head.jpg, ultron_hero.jpg, ultron_bg.jpg.

WRITTEN BUT NOT COMPILED, NOT SEEN ON A PHONE (no kotlinc / Gradle in this chat):
- NovaHud.kt: HudView redesigned - cut-corner card in state colour (cyan listen/think, red speaking), round Ultron head from assets/ultron_head.jpg, texts "I'm listening... / Thinking... / Speaking...", visuals (mic ring + waveform / globe + red orbit / red ring + waveform), big button SILENT (listen/think) or STOP (speaking), small red STOP square in listen/think, close X, 3-dots menu kept, HAAN/NAHI kept, reply row kept, minimized chip kept, HudMath untouched. New helpers chamfer(), hexPath(), avatarBitmap(). New hit ids: 9 = X, 10 = big SILENT.
- NovaHud.openChat(): same neon look + round avatar, hint "Type a message...", box width 332dp, y = 270dp.
Original NovaHud.kt before the change is NOT in the zip (git history / the v7.120 zip has it).

NOT DONE:
1. Gradle build + unit tests (HudMathTest, UiCompletion116Test, UltronFxTest, PhotoEditRowPhrasesTest reads ui.html ...). Fix any Kotlin compile error in NovaHud.kt first.
2. Phone test of the popup: all 3 states, STOP/SILENT/X/dots/HAAN-NAHI, minimize, typing box, long reply text fits (card height = 204dp + 38dp reply + 54dp ask).
3. Optional idle popup "How can I help you today?" (Voice / Message / More) - not written.
4. v7.120 Ultron voice: build + listening tune.
5. In SMOOTH/lowfx mode (default) the in-app card animation is off (static) on purpose.
