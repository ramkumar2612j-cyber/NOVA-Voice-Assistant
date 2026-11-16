# NEXT ACCOUNT PROMPT - after JARVIS LAUNCHER L4 (zip: JARVIS_v7_192_LAUNCHER_L4.zip)

Tum JARVIS Android project ke agle account ho. Owner NON-TECHNICAL hai, sirf Android phone (Chrome + GitHub), Hinglish, ek time par ek step, exact github.com links. Owner = "boss", Jarvis = CEO. Naam sirf JARVIS/Jarvis (package `com.nova.assistant` aur `nova_*` keys jaan-bujh kar purane, mat badalna).
Pehle padho: PART_STATUS_v7_192_LAUNCHER_L4.md, PART_STATUS_v7_191_LAUNCHER_L3.md, AGENTS_AUR_KAMAI_v7_192.md, TEST_STATUS.md.

## Fixed rules
1. Har part ke baad zip, uske andar agla NEXT_ACCOUNT_PROMPT + PART_STATUS. Add-only, purana kuch mat hatao.
2. Jarvis agents khud chalaye; HARD DENY hamesha: payment, boss ke paise se order, password, OTP, PIN, CVV.
3. Honest: jo compile / phone par nahi chala use NOT RUN likho.

## Pehla kaam
1. GitHub Actions build ka result poochho (L1-L4 abhi tak kabhi compile nahi hue). Fail = L4 files pehle dekho: BusinessHomeRules / BusinessHomeStore / LauncherActivity / JarvisService (showBusinessCard, onStoreOrder, speakBusinessReport) / AutopilotNet (wooOrdersRaw, shopifyOrdersRaw).
2. Phir owner ke phone test (TEST_STATUS 25-28): card dikhe, tap se sunao, naya order par card.
## Baaki (owner ne kaha to)
- Lovable website se seedha order (backend / form / WhatsApp button) - abhi code mein nahi.
- Wix orders padhna, card par "chal raha agent" ka live progress.
