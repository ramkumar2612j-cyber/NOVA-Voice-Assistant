# NOVA v6.50 - next account prompt (poori detail)

Tum NOVA (Android voice assistant, Kotlin, package `com.nova.assistant`) ke code par kaam kar rahe ho. Owner **Hinglish** me baat karta hai, chhote seedhe jawab chahta hai. Har step ke baad updated ZIP do aur **sach batao: kya RUN hua, kya NOT RUN**.

## 0. Pehle ye samjho (repo, CI, owner ka tareeka)
- GitHub repo (public): https://github.com/skfashion142/NOVA-Voice-Assistant
- Tum owner ke repo me **push nahi kar sakte**. Owner khud ZIP upload karta hai. Har baar ye direct links do:
  - Upload: https://github.com/skfashion142/NOVA-Voice-Assistant/upload/main
  - Workflow edit: https://github.com/skfashion142/NOVA-Voice-Assistant/edit/main/.github/workflows/build.yml
  - Build chalao: https://github.com/skfashion142/NOVA-Voice-Assistant/actions/workflows/build.yml
- **ZIP ka naam hamesha `NOVA-Voice-Assistant.zip`** (andar ka folder bhi `NOVA-Voice-Assistant/`). Repo ka workflow isi naam ka zip unzip karta hai.
- Repo ka **live workflow ZIP wale `.github/workflows/build.yml` se alag hai**. Live wala: "Build NOVA from zip", `workflow_dispatch`, steps: `Zip kholo` (unzip, `ROOT` = jis folder me `settings.gradle.kts` mile), `Awaaz model download` (Vosk `vosk-model-small-en-us-0.15` -> `app/src/main/assets/model-en`, plus optional en-in), setup-java 17, gradle 8.7, `Tests` (**continue-on-error: true**, matlab test fail ho tab bhi green dikhta hai), `APK banao` (`gradle assembleDebug`), upload artifacts `nova-apk` aur `test-report`. ZIP wali build.yml se owner ka workflow overwrite mat karwana.
- Owner phone se kaam karta hai (screenshots bhejta hai). Steps bahut chhote aur "kaise karein" ke saath do. Owner "direct link do" bolta hai = seedha GitHub link.
- Sandbox: Maven/Google servers **blocked**, GitHub **khula**. Isliye Gradle/Android build yahan nahi chalta.

## 1. BUILD HONESTY (zaroori)
- CI history: (a) `androidx.core:1.15.0` ne compileSdk 35 maanga -> fix: `configurations.all { force(core, core-ktx 1.13.1) }` in `app/build.gradle.kts`. (b) 3 Kotlin errors fix. (c) `LlamaBrain.kt` library ke API se match nahi karti thi -> dobara likhi. Uske baad owner ne app phone par install karke chalayi, yani CI kisi version par green hui (kaun sa exact run, mujhe nahi pata).
- **v6.48, v6.49, v6.50 ke badlav (unlock hata, 3 Gemini keys UI, startup halka mode, voice-typing button, chota popup, orb pause) ka Gradle build abhi NOT RUN, phone test NOT RUN.** Pehle CI chalao, errors theek karo, tab "kaam karta hai" kaho.
- Jo RUN hua: (1) v6.42: kotlinc 1.9.24 se 55 pure-Kotlin files + 55 test classes compile, **521 JUnit tests PASS** (BatteryRules, WakeRules, ScheduledDays shamil). WakeRulesTest v6.43 par dobara PASS (7 tests). (2) v6.50: Python contract tests PASS: test_ui_contract, test_ui_v37_contract, test_hud_controls, test_voice_contract, test_agent_contract, test_autosetup_contract, test_part1_voice_flow. (3) `ui.html` ke saare `<script>` `node --check` OK.
- Jo NOT RUN: Android compile (NovaService, MainActivity, NovaHud, LlamaBrain asli library ke saath), 7 android-dependent unit tests (ApiUsage, AutoTestRules, CrashNote, FreeImageRules, HudMath, HudMotion, PublicKnowledgeFallback), koi bhi phone test.
- Pure-Kotlin harness recipe (agar Gradle phir blocked ho): `kotlinc 1.9.24` GitHub releases se; compile karo sab main files jinme `import android|org.json|androidx|com.google|okhttp3|org.vosk|ai.onnx` na ho (`grep -L -E`), plus shim file jisme `object MessageMode { fun command(n:String, core:String): Logic.Cmd? = null }` aur `object ApiUsage { fun hit(id:String, code:Int, a:String?=null, b:String?=null, c:String?=null){} }`; tests me wahi jo android-free hon (JUnit 4.13.2 + hamcrest jar `/opt/gradle-*/lib/`); `kotlinc -J-Xmx6g`; `JUnitCore` ko `app/` directory se chalao (knowledge.json relative path). 

## 2. Is project me kya-kya badla (v6.38 -> v6.51), sab owner ke kehne par
1. **No auto-start**: BootReceiver sirf reminders dobara set karta hai. Service sirf ACTIVATE tap se. App kholne se mic start nahi.
2. **Owner-only voice HATA (v6.51)**: speaker ID, voice lock, face gate, pattern unlock, enrolment sab delete (VoiceLock, OnnxSpeaker, SpeakerRules, VoiceFaceRules, VoiceEngineRules, Fbank, FaceGate, PatternUnlock, PatternSetupActivity). Phone khula ho to kisi ki bhi awaaz chalti hai. **Phone LOCKED ho to** (`PhoneLock.isLocked` = KeyguardManager) NOVA sun/bol sakta hai par **naya kaam nahi leta**: `NovaService.lockedPhoneAllows()` sirf chalte job ka jawab, "stop", wake-alarm OFF allow karta hai, baaki par ek refusal (8 s me max ek).
3. **Unlock feature HATA (v6.48)**: NOVA phone kabhi unlock nahi karta; `unlockPhone()` sirf message deta hai.
4. **Battery** (`BatteryRules.kt`, `batteryWatchRx`): 50/40/30/20/10% par ek-ek alert; <=5% par Papa/Mummy ko WhatsApp SOS (Accessibility se). Unlock ki koshish ab nahi.
5. **Schedule** (`ScheduledTaskRules/Store/Receiver`): weekday bitmask (Mon=1..Sun=64), prio (0/1/2), note (120 chars); voice + UI "MERA SCHEDULE".
6. **Morning** (`WakeRules.kt`): din ka pehla unlock 06:00-11:59 par greeting + aaj ke kaam; har reminder se 25 min pehle lead alarm. **Wake alarm (owner ne final kiya): sirf 06:00-12:00 (dopahar), OFF dabane tak baar baar bajta hai; DISMISS = 1 min snooze; 20-min auto-stop aur "unlock se band" hata diye.** Sirf us task ke liye jiska time 06:00-11:59 ho aur owner us din OFF/awake na kar chuka ho. 12:00 ke baad kabhi nahi.
7. **Gemini keys (v6.49)**: Settings me 3 boxes: KEY 1 main, KEY 2/3 fallback (`s_key`, `s_key2`, `s_key3`); save par newline se join -> `Bridge.saveKeys` -> `Logic.parseKeys` (max 10, 20..200 chars). Backend failover pehle se tha. Save purani saari keys replace karta hai (keys UI me wapas nahi dikhti).
8. **Startup smooth (v6.49)**: `ui.html` ab har phone par `lowfx` (halka mode) se shuru hota hai, ~7 s smooth frames ke baad full effects (existing adaptive `tick`). 3 sabse bhaari `backdrop-filter` hata diye. `AutoSetup.start` 1.5 s baad, voice-model thread 5 s, offline Qwen/Llama warm-up 9 s baad.
9. **Chat/voice typing (v6.50)**: chat me typing box pehle se tha (`c_in`, `Bridge.sendText`). Naya button `c_kmic` -> `Bridge.systemVoice()` -> `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (`REQ_SPEECH=40`) -> `onSystemVoice(text)` JS -> box me daal kar auto-send. NOVA service ka apna mic chalu ho to system recognizer mic na paye - ye test nahi hua.
10. **Floating card chota (v6.50)**: `NovaHud.kt` 316x148 dp (pehle 380x190), waveform 22dp, reply max 2 lines.
11. **3D orb**: jab full-screen panel/chat/skills/limits/setup popup (`#uc`) orb ko dhake, orb draw band (`window.__cover`, `sync()` me set hota hai).
12. **Fixes**: `ChatText` JSON regex (quote overrun), `WhatsAppCommandRules` recipient casing, `MainActivity` Context import, `NovaService` `whatsappTool(...).msg`, `VoiceLock.saveProfile` expression-body `return`, `LlamaBrain` (llamacpp-kotlin **0.1.0** API: `LlamaHelper(scope)`, `suspend load(path, ctx)`, `setCollector(): Flow<String>`, blocking `predict(prompt, partial)`, `release()`, `abort()`; naya 0.4.0 API Kotlin 2.x/compileSdk 36 maangta hai, upgrade mat karo), `!!` hata (rule: code me `!!` nahi), stale tests update (`test_voice_contract` lock ON, `test_autosetup_contract` androidx.core pin allowed).
13. **Repo professional (v6.44)**: root me README.md, CHANGELOG.md, `docs/` (BUILD, ARCHITECTURE, FEATURES_AND_LIMITS, PHONE_CHECKLIST, SETUP_STEPS, PROGRESS, HANDOFF), purani 70 files `docs/archive/`.

## 3. Purana voice-match bug ab irrelevant
Speaker check hi hata diya (v6.51), isliye "sirf owner ki baat sunta hu" wala bug ab nahi. Naya code (PhoneLock/lockedPhoneAllows) compile aur phone test ABHI NAHI hua: locked phone par nayi command refuse, "stop" chale, alarm OFF chale, ye phone par check karo.

## 4. Baaki / next steps
1. CI chalao (v6.50 zip), nayi errors theek karo. Test step `continue-on-error` hai: `test-report` artifact ya Tests step ka log zaroor dekho.
2. Phone checklist `docs/PHONE_CHECKLIST.md` + naye: wake alarm (06:00-12:00, OFF tak baar baar, USAGE_ALARM volume), battery SOS (overlay permission, locked/screen-off me fail ho sakta), schedule + morning greeting, 3-key failover (KEY 1 galat rakh ke KEY 2 chale), voice-typing button, halka mode -> full effects switch, chota card (text kata to nahi), `LlamaBrain` offline model load.
3. **Poora feature audit abhi bhi pending**: UI->Bridge static check hua (124 `Android.*` calls sab ke liye method hai, koi stub nahi). Par har button ka runtime asar phone par check nahi hua.
4. Voice/face/pattern UI aur dead code v6.51 me hata diya.
5. Docs stale: `docs/FEATURES_AND_LIMITS.md` v6.0-6.38 ka hai (upar note hai). `docs/SETUP_STEPS.md` extra workflows (signing, release, self-improve) ki baat karta hai jo ZIP me nahi hain.
6. ZIP ki `.github/workflows/build.yml` me Vosk model download step nahi; live repo workflow me hai. Chaho to ZIP wali ko live jaisa bana do (owner se pooch kar).
8. `setup-java@v4` deprecated warning: owner ko `@v5` karna bataya (sirf warning).

## 5. Owner ki invariants
- Dangerous actions par local spoken yes (`skipRiskyConfirm` owner ne ON rakha), bank/payment/password/Settings/installer screens par tap/type blocked.
- Screen monitoring default ON (v6.52, owner ne kaha; Accessibility permission phir bhi chahiye). Message Mode default ON, WhatsApp + Instagram default apps; jawab v6.53 se BINA poochhe turant jata hai (owner ka hukm), baad me NOVA bolkar batata hai; sab kuch home page ki ghanti wale HISTORY me dikhta hai (`ActionLog`, `AutoReplyRules`). `QUERY_ALL_PACKAGES` aur `CALL_PHONE` v6.54 se manifest me (owner ka hukm); CALL_PHONE mile to NOVA seedha call lagata hai, nahi to dialer + ek baar permission maangta hai.
- Keys Android Keystore me encrypted, kabhi UI/log me wapas nahi.
- Code me `!!` aur `catch (_` nahi (CI scan rule).

## 6. Rules
- Hinglish, chhota jawab. Har step ke baad ZIP (`NOVA-Voice-Assistant.zip`) + direct upload/build links.
- Build/test ka sach: **RUN / NOT RUN** saaf likho. "Kaam karta hai" tabhi jab CI green aur phone par dekha ho.
- Owner jo maange wahi karo, scope se bahar mat jao; do raaste ho to ek chhota sawal pucho.
