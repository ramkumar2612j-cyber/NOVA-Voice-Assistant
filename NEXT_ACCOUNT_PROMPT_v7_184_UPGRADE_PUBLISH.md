# NEXT ACCOUNT PROMPT - JARVIS v7.184: Part B (ii) PUSH+POLL ho chuka, aage ka kaam

Zip: JARVIS_v7_184_UPGRADE_PUBLISH.zip (root folder jarvis/). Padhne ka order: PART_STATUS_v7_184_UPGRADE_PUBLISH.md,
PART_STATUS_v7_183_SELF_KNOWLEDGE.md, PART_STATUS_v7_182_UPGRADE_LEDGER.md, TEST_STATUS.md.

## Owner aur compulsory rules (ab tak wahi)
- Owner non-programmer, sirf Android phone (Termux F-Droid + Chrome + GitHub web). Hinglish, CHHOTE messages
  (~7 lines), ek step ek time, numbered steps.
- ADD-ONLY: purani file/feature mat hatao. build.yml mat chhuo. Kabhi DONE/WORKING mat likho bina evidence.
  Sirf PASS/FAIL/NOT TESTED.
- Push owner karta hai Termux se (repo ramkumar2612j-cyber/JARVIS-Voice-Assistant). Claude ke paas network nahi.
- Owner ne saaf mana kiya hai: phone lock/OTP/password/captcha/security todna NAHI. Ye abhi bhi firm hai.

## v7.184 mein kya hua
GitHubUpgradePublish.kt (push branch + note, poll Actions status) + UpgradeLedger.push/poll + MainActivity
bridge push/poll acts + UI PUSH/POLL buttons + naya contract test (PASS). Poora detail PART_STATUS_v7_184 mein.

**Sabse zaroori honest baat**: push abhi sirf ek NOTE file daalta hai, koi real code patch nahi. Jab tak AI
patch engine nahi banta, PUSH+POLL cycle "build green" dega lekin app mein koi naya feature nahi aayega — ye
sirf pipeline ka scaffolding hai.

## Agla kaam (is order mein)
### Step 1 - CI result (pehla kaam hamesha yahi)
Owner se pucho: GitHub Actions green ya red? Red ho to sirf failed step ki red lines.

### Step 2 - BADA FAISLA: AI patch engine
Owner ke "feature chahiye" wish ko safe Kotlin patch mein kaise badla jaye, bina 300-file app tode?
Options likh kar owner ko do (udaharan ke taur par):
- (a) Chhote, pre-defined "feature templates" (sirf known-safe patterns: naya voice trigger, naya UI button,
  text reply) — kam risk, kam flexibility
- (b) Pura freeform AI patch (SiteGen.requestHtml jaisa koi AI call jo .kt diff likhe) — zyada flexible,
  zyada risk, compile se pehle static check + CI hi judge
Owner ki HAAN ke bina koi design final nahi; dono gate chahiye: patch likhne se pehle HAAN, push se pehle HAAN.

### Step 3 - Phir Part B (iii): install + real undo
Tag se purana APK wapas, undo list UI mein (already UpgradeLedger.tag() maujood hai).

### Step 4 - Baaki purana pending kaam
Purane NEXT_ACCOUNT_PROMPT_v7_177/v7_175 ke khule items (kinetic UI phone test, Instagram filler/DM, Video
Studio, Payment QR, Laptop app end-to-end) — abhi bhi NOT TESTED.

## UPDATE v7.185 (is zip mein): FREE photo EDIT bhi ban gaya
FreeImageEdit.kt (Hugging Face image-to-image, same "hf" token jo free-image-generate use karta hai) + editAi()
wiring (Gemini fail/missing -> free HF edit, tabhi phone-only). Detail: PART_STATUS_v7_185_FREE_PHOTO_EDIT.md.
HF ka exact request shape unverified hai (docs se likha, kabhi network se try nahi hua) — phone test chahiye.
