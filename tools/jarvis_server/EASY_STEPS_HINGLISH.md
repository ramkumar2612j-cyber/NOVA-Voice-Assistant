# Jarvis awaaz - 100% FREE, 2 tap (Google Colab)

Kuch paid nahi: Google Colab ka free GPU + Cloudflare ka free link. Bas ek Google account chahiye. Recording / upload / address-token typing ab nahi.

## Har baar jab Jarvis awaaz chahiye (5-15 minute, pehli baar zyada)
1. Drive me **NOVA_Jarvis_Colab.ipynb** kholo (Open with > Google Colaboratory). Pehli baar hi upload karna padta hai.
2. **Runtime > Change runtime type > T4 GPU > Save**.
3. Neeche wale **ek** cell ka ▶ dabao (ya Runtime > Run all).
4. Jab **COPY CODE** button aaye, use dabao. (Na chale to neeche chhapi **NOVA-JARVIS:...** line long-press karke copy karo.)
5. NOVA > Settings > JARVIS AWAAZ > **CODE PASTE (1 TAP)** > **JARVIS CHECK**. 4 PASS lines aani chahiye.
6. NOVA ko English me kuch bolo. Jawab Jarvis awaaz me aayega.
7. Colab ka page **band mat karna**. Band ya idle hua to server band; fir 3-5 dobara (naya code).

## Apni awaaz jaisi chahiye to
Cell me `MY_VOICE = True` karo: tab recording upload maangega (sirf apni ya jiska haq ho). Default me kuch upload nahi.

## Pehle sirf test
`FAKE = True` karke chalao. Beep aayega. Isse phone-link theek hai ya nahi pata chalta hai.

## Free ki sachchai (saaf baat)
- Colab free GPU **guarantee nahi**: kabhi GPU nahi milta, ya kuch ghante baad session band.
- Code **har baar naya** banta hai (address + token badalte hain). Har baar 1 tap paste.
- **Hamesha-on** nahi hai. Server band = NOVA apni purani awaaz me bolta hai (apne aap).
- Hamesha-on chahiye to paid GPU server lagta hai.
- Awaaz sirf **English** ke liye hai.

## Test status (honest)
- Phone paste code (Kotlin): NOT COMPILED, NOT TESTED (sandbox me Kotlin compiler / internet nahi). GitHub build + JUnit JarvisPairingTest chalega.
- Notebook cell: Python syntax PASS; server FAKE test PASS. Asli model + Cloudflare + COPY CODE button (browser clipboard): NOT TESTED.
- Model ka apna reference mila to wahi use hota hai, warna bina reference: NOT TESTED (asli model par).
