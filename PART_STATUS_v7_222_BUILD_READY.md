# PART STATUS v7.222 — BUILD-READY (home mockup jaisa + final check)

Owner ne bola: home reference mockup jaisa ho, JARVIS service se juda ho, GitHub build ke liye ready zip do.

## Kya badla (add-only)
- `app/src/main/assets/ui.html`: sirf ek naya `<style id="v7222-jh-mockup-css">` (sabse aakhir me). Home HUD me upar wala "Notifications" panel
  ab dikhta nahi (display:none; HTML + JS ids waise hi), reactor seedha logo ke neeche. Battery / network / AI status neeche tiles me live hi hain.
- Baaki kuch nahi badla. `.github/workflows/build.yml` byte-for-byte SAME (cmp se check). Koi file delete nahi.
- Home v7.216 se pehle hi JARVIS service se juda tha: ACTIVATE = `Android.toggle()` -> startFlow -> JarvisService; tiles = `Android.homeJson()` -> HomeHud.json();
  PLAY/SEND/NEWS = `Android.sendText()` -> `JarvisService.typed()`.

## RUN hua (sandbox, is session) — evidence
| Check | Result |
|---|---|
| Kotlin main compile (kotlinc 1.9.24 + android-34 jar + stubs, real org.json) | **0 errors** |
| Kotlin JUnit (JUnit 4.13.2, TarBzTest sandbox me skip) | **2052 run, 0 failures** |
| Python contract suite (200 scripts) | **200/200 PASS** (ui.html badalne ke BAAD dobara chalaya) |
| Desktop node tests | **69/69 PASS** |
| Backend tests (real HTTP) | **14/14 PASS** |
| Security scan | CLEAN |
| Home render (headless Chromium, fake bridge) | PASS: reactor upar, WELCOME, weather, tiles, activity, neural card, ACTIVATE ring |

## NOT TESTED (sach)
- GitHub Actions APK build (Gradle). Sandbox me Android SDK/Gradle nahi.
- Asli phone (home, ACTIVATE, service), asli TV, asli laptop link, backend live deploy.
- TarBzTest sandbox me skip (commons-compress Maven blocked); CI me chalega.

## Pending (owner ki haan chahiye)
- Ultron voice default OFF (TEST_STATUS #21 FAIL).

## v7.222 boot/permission fix (add-only, phone par NOT TESTED)
- Boot animation: "v7.165"/"JARVIS 7.162" -> 7.221; "v7.1650K" overlap theek (version ke baad ` ... `).
- Permission card: backdrop transparent, taps home tak jate hain, card chhota (44%); BAAD MEIN 3 din; first-run banner us card ke upar nahi aata.
- Page-wide hue-rotate: NOT changed (poora UI red source + filter se blue; hatane par rang badal jata). Phone par lag ho to batao.
