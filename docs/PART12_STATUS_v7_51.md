# NOVA v7.51 : PART 12 POORA (video se frames) - status (simple Hindi, honest)

## Part 12 progress: 100%  (Part 1-12 / 20 = 60%)

## App me kya milega
1. MEDIA MILA card me har video / photo ke neeche naya button **FRAMES NIKALO** (link par nahi).
2. Video: 6 tak saaf frames nikalte hain (milte-julte hata diye jate hain; chhota video ho to kam; phone ki memory kam ho to kam). Neeche chhoti patti me dikhte hain, har frame ka second bhi.
3. Photo: bas chhoti karke JPEG (1 frame).
4. Kaam peeche alag thread par chalta hai, "frames 3/6" asli ginti se dikhta hai (fake timer nahi). **ROKO** ya app ka **STOP** turant rok deta hai. 60 second me na ho to saaf error.
5. Kharab / bahut bhari video par app atakna nahi chahiye: saaf karan likha aata hai ("video se frame nahi nikle: ...").
6. Frames sirf phone me (private cache), media hatane par ya 24 ghante baad saaf. Koi nayi permission nahi (30 hi).
7. Abhi sirf frames; unka analysis Part 13 me.
8. MADAD me naya topic "Video se frames nikalna", SKILLS me bhi.

## Test status (honest)
- RUN PASS: poora project COMPILE (kotlinc 1.9.24, 0 error). FrameRulesTest (naya, 11 test) PASS.
- RUN PASS: test_frame_extract_contract (thread, recycle, watchdog, cancel, STOP, no network, no path), test_p12_ui_runtime (headless Chromium, nakli bridge: bar sirf asli ginti se, ROKO, patti 4 frame, error text, 320/360/412).
- RUN FAIL (sandbox seema): JUnit me 29 fail, sab org.json stub wale (Connector / Web / Vercel / Music / FeatureVerify / NovaV73). Asli org.json jar nahi mila. Part 11-12 ka koi nahi. Asli saboot GitHub Actions.
- NOT TESTED: asli phone par video se frames (MediaMetadataRetriever asli decoder), ROKO / STOP asli me, 60 s watchdog, kam memory, GitHub Actions build. Python/headless test asli Kotlin ka saboot nahi.
- Ek bug JUnit ne pakda aur theek kiya: duration pata na ho (0) to pehle 1 frame milta tha, ab default 6 try hote hain.

## Agla: Part 13 (REAL ANALYSIS). Prompt: NEXT_ACCOUNT_PROMPT_v7_51_PART13.md
