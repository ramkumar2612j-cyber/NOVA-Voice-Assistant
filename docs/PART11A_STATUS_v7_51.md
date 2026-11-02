# NOVA v7.51 : PART 11-A MEDIA INTAKE RULES - status (simple Hindi, honest)

## Part 11 progress: 25% (11-A DONE)
- 11-A (DONE): MediaIntake.kt rules + 15 JUnit + contract script
- 11-B (baaki): MediaIntakeStore (stream copy, 24h saaf) + Manifest "Share -> NOVA" filter
- 11-C (baaki): bridge mediaIntakeList / Remove / Clear / mediaPick + chat "MEDIA MILA" card (DEKHO / CHAT ME JODO / HATAO)
- 11-D (baaki): MADAD + SKILLS + CAPS parity, voice "kya mila", contract + headless test, final status

## Kya juda (add-only)
MediaIntake.kt: photo <= 25 MB, video <= 300 MB, ek baar me 6 file, sirf https link (user:pass@, localhost, raw IP mana; tracking params waise hi), sirf content:// uri, naam saaf, duplicate hatana, 24 ghante rule, link par honest line "video file nahi mili, sirf link ki jankari". Abhi app me koi naya button / screen nahi (wo 11-B..D).

## Test status (honest)
- RUN PASS: MediaIntakeTest (15/15) asli kotlinc 1.9.24 + JUnit 4.13.2 par; tools/run_all_checks.sh = 110 pass, 0 fail.
- RUN: poora project (v7.45-7.51 ka Kotlin, jo pehle kabhi compile nahi hua tha) kotlinc se COMPILE HO GAYA (0 compile error). Poore JUnit: 1161 chale, 30 fail - ye sab WebSourcesTest / WebBrowsersTest jaise org.json wale test hain (sandbox me android.jar ka org.json khaali stub hai, NullPointerException). Real org.json jar ke bina ye sandbox ki seema hai; GitHub Actions me hi final saboot. Fail ki list: ConnectorParseTest 11, WebSourcesTest 6, WebBrowsersTest 6, VercelPublishTest 2, aur 1-1: ConnectorRulesTest, MusicCatalogTest, FeatureVerifyTest, NovaV73Test, **RoundaboutRulesTest.repeatAndUndoPhrases (Part 10, asli fail: pehle account ko ye pehle theek karna hai)**. MediaIntakeTest (Part 11) 15/15 PASS. Jyadatar org.json stub wale hain; baaki ko Actions log se alag karna hai.
- NOT RUN: asli phone, GitHub Actions.
