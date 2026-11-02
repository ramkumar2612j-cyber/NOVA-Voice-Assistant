Project: NOVA Voice Assistant (com.nova.assistant, Kotlin + WebView UI assets/ui.html).
Source zip: NOVA-v7_121-ULTRON-UI.zip. Read PART_STATUS_v7_121_ULTRON_UI.md first.

Owner rules: reply in short Hinglish, simple words, numbered steps. Owner uses only an Android phone: give direct links, never ask to run PC commands; if something fails ask only for "the red lines of the failed step". Start with one line on what you will do, keep a task list with a % done, finish with: what changed, the files, one next step. Do not make the owner re-explain; decide sensibly, say it, continue. Never remove a feature (additive only). Never say "working" without evidence: report PASS / FAIL / NOT TESTED.

Task: finish the Ultron UI (app + floating popup) and test every item.
State: in-app UI (ui.html skin) is done and seen in a browser render. NovaHud.kt popup + typing box are WRITTEN but NEVER COMPILED or seen on a phone.

To do, in order:
1. Run the real Gradle build. Fix every Kotlin compile error in NovaHud.kt (new code: chamfer, hexPath, avatarBitmap, onDraw ULTRON section, layoutRects, hitControl ids 9 and 10).
2. Run unit tests (HudMathTest, UiCompletion116Test, UltronFxTest, PhotoEditRowPhrasesTest ...). Fix failures.
3. Owner installs APK: test popup listen / think / speaking, SILENT, STOP, X, 3-dots menu (SILENT, CHAT, CHHOTA, BAND), HAAN/NAHI, minimize, typing box, long reply text. Fix layout bugs from the owner's red lines/screenshots.
4. Optional: idle popup "How can I help you today?" with Voice / Message / More.
5. Report PASS / FAIL / NOT TESTED per item. Deliver full ZIP + next prompt.
Also pending from v7.120: Ultron voice build + phone tuning.
