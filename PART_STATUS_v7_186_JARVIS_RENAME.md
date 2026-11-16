# PART STATUS v7.186 - JARVIS BRAIN + RENAME (owner: "Nova nahi, Jarvis; icon/name badlo, APK-ready final do")

## Kya bana (add-only)
1. JarvisBrain.kt + hook: pehle wali baat (bolti hui command ko AI samajh kar ANSWER / TOOLS / AGENT chunta hai). Already diya gaya tha.
2. RENAME NOVA -> JARVIS (jo user ko dikhta/sunai deta hai):
   - App ka naam (home screen, app switcher): manifest `android:label` "NOVA" -> "Jarvis" (saari 5 jagah: app, accessibility service, driving mode, assistant, share target).
   - App icon: naya icon (dono normal + round + Android 13+ monochrome) - wahi orange/red orb-ring design, bas beech ka "N" letter ab "J" hai. `ic_launcher_foreground_jarvis.xml`, `ic_launcher_monochrome_jarvis.xml` (naye files), `mipmap-anydpi-v26/ic_launcher*.xml` inhe point karte hain. Purani PNG files hati nahi (add-only), bas use nahi hoti.
   - Wake word: ab "Jarvis" bol kar activate karoge (pehle "Nova" tha). `Logic.DEFAULT_WAKE`.
   - Chat mein AI ka naam "NOVA" ki jagah "Jarvis" dikhta hai; update notification title "Jarvis"; accessibility permission screen ka text "Jarvis uses this...".
   - strings.xml mein `app_name` = "Jarvis" add kiya.
3. **Bug fix (zaroori, build todega varna)**: `app/build.gradle.kts` mein `versionName` line ke aage ek galat `#` comment chipka hua tha jo Kotlin mein compile hi nahi hota. Hata diya, versionName ab saaf "7.186" hai.

## Honest: JO ABHI NAHI HUA
- App ke andar ki 3D screen (`assets/ui.html` - orb/waveform/menu wali screen) mein "NOVA" / "nova" shabd ~800 jagah hai: kai CSS class names, JS function names (`novaGo`, `onNova`...), localStorage keys. Ye text abhi bhi "NOVA" dikhega jab tak alag se poori tarah badla na jaaye.
  - Wajah: ye ek hi bahut badi file hai aur yahan Kotlin/JS compile check nahi ho sakta - agar koi internal naam galat jagah badal gaya to poori app screen crash ho sakti hai, aur mujhe pata nahi chalega jab tak aap phone par test na karo.
  - Agla chhota part ye poora karega, alag se, taaki agar kuch toote to sirf us ek part ko wapas la sake.
- Kotlin compile / GitHub Actions / phone test: abhi tak NOT RUN (jaisa hamesha).
- Android ke lock-bypass aur OTP/bank-auto-tap darwaze: jaan-bujh kar band hi hain (safety), is upgrade mein bhi nahi khole.

## Phone par kya dikhega (is upload ke baad)
- Home screen icon ka naam "Jarvis", icon par "J" (pehle "N").
- "Jarvis" bol kar activate karoge.
- Chat mein AI ka jawab "Jarvis" naam se aayega.
- 3D orb wali poori screen abhi "NOVA" hi dikhayegi (agla part isko bhi badlega).
