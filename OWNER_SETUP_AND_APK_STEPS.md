# NOVA - SETUP aur APK (sirf phone se, ek-ek step) - v7.24

## A. Code GitHub par daalna (PUSH tabhi jab aap khud karna chaho)
1. Phone Chrome mein kholo: https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (agar repo/account alag hai to Claude ko bata do).
2. Zip ko phone par Files app mein "Extract" karo.
3. GitHub page par: Add file > Upload files > extract hui saari files/folders chuno > "Commit changes" (branch: main).
   Ya Termux: cd ~/NOVA-Voice-Assistant; git status -s; (patch apply ya files copy); git add -A; git commit -m "v7.24"; git push.
4. NOTE: .github/workflows/build.yml ko mat badalna.

## B. APK banana (GitHub khud banata hai)
1. Repo > Actions > "NOVA Android CI" > Run workflow (branch main) > Run workflow.
2. 10-20 minute ruko. Green tick = APK ready. Red cross = us step ka sirf laal (red) text Claude ko bhejo.
3. Green run kholo > neeche Artifacts > "nova-apk" download > zip extract > app-debug.apk.
   (Zip ke andar .apk hoti hai. "test-report" artifact tests ki report hai.)
4. APK par tap > "Install anyway / Is source se allow" > Install.
5. Fixed signing key (optional, update par purana data bacha rahe): repo Settings > Secrets > Actions: NOVA_KEYSTORE_B64, NOVA_KEYSTORE_PASSWORD, NOVA_KEY_ALIAS. Na ho to throwaway debug key chalti hai (nayi APK purani ke upar install na ho to purani uninstall karo).

## C. Pehli baar app kholne par (zyadatar automatic)
- App khulte hi AutoSetup khud chalta hai: offline AI/model download (WiFi par), free AI providers ki jaanch.
- Aapko sirf Android ki permission switches ON karni hoti hain (Android khud app ko ye karne nahi deta): Microphone, Notifications, phir Accessibility (App Info > Allow restricted settings), Write settings (brightness), alarm permission.
- HOME par "SETUP BAAKI HAI" card dikhata hai kya baaki hai; ALLOW dabao.
- ACTIVATE dabao. "nova torch jala do" bolo.
- Optional: SETUP > keys mein apni Gemini/Groq key daalo (cloud AI ke liye); CONNECT mein GitHub/Vercel/n8n jodo.

## D. Phone test (ek item ek baar): docs/PHONE_CHECKLIST.md
Pehle ye 6 chhote test karo: 1) nova torch jala do 2) nova volume mute 3) nova screenshot lo 4) nova kal subah 7 baje alarm 5) mummy ko call karo (haan bolo) 6) nova gaana chalao (Spotify). Har ek ka PASS / FAIL + screenshot bhejo.

## E. Faltu wip branches hatana (SIRF jab main par latest code dikh jaye)
1. Repo > "branches" > sirf main rakhna. Har wip branch ke saamne dustbin icon > Delete. Ek-ek karke.

## F. Agar APK build red ho
Red step ka naam + laal lines bhejo. Aam wajah: Vosk model download (internet), ya Gradle compile line. Local compile pehle hi PASS hai (kotlinc 1.9.24), isliye zyada chance Gradle/SDK setting ka hai.
