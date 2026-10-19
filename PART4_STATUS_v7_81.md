# v7.81 - PART 4 (live scan 10 sec) - status
RUN = sandbox me chalaya. NOT RUN = nahi chalaya. NOT TESTED = phone chahiye.
## Kya badla (add-only)
- "nova dekho btao kya chal ra h" bolne par screen / reel ka scan shuru (pehle "screen" shabd chahiye tha).
- Reel: 3 frame, 0.7 sec gap (pehle 4 frame, 1.5 sec). Vision AI 7 sec se zyada nahi rukta (pehle 30 sec). Web search sirf shuru ke 5.5 sec me, 1 chhoti query. Der ho gayi to "verify nahi hua" ke saath chhota jawab.
- Normal screen: pehla reading 7-9 sec me. Follow-up sawaal ke purane limits wahi.
- Laser par asli clock: "3.2 s / 10 s" + asli stage + percent. Laser screenshot me kabhi nahi aata (pehle jaisa).
## Test
- RUN: test_part4_quickscan_contract PASS; laser / movie / part18 / part3b guards PASS; baaki python guards PASS.
- FAIL (purana): test_part20_contract (CHANGELOG ki purani jaanch), v7.80 zip par bhi fail.
- NOT RUN: Kotlin compile, QuickScanTest JUnit, GitHub Actions. NOT TESTED: phone, asli 5-10 sec ka time.
## Agla: Part 5 = jude nahi huye features UI se jodna + final zip. Phone test me 3 baar time napna: reel, normal screen, "ye kaun si movie hai".
