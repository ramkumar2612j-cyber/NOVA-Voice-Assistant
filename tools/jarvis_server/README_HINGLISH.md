# NOVA Jarvis awaaz - server setup (Hinglish)

**Zaroori baat:** Jarvis model (FuturePresentLabs/tts-jarvis) phone me nahi chalta. Ye Python + PyTorch + GPU maangta hai.
Isliye ek server chahiye jo awaaz banaye; NOVA phone se text bhejta hai aur wav awaaz wapas sunata hai.
Agar server band ho ya net na ho to NOVA apne aap purani awaaz (Ultron effect, phir normal phone voice) me bolta hai.

## Model ki sachchai (model card se)
- Sirf **English** ke liye bana hai. Hindi / Hinglish ke liye "validated nahi". Isliye NOVA default me Jarvis sirf tab bolta hai jab NOVA ki language English ho.
- **reference.wav aapko khud deni hai** (saaf awaaz ki recording jiska aapko haq ho). Model ke saath nahi aati. Dusri reference = alag awaaz; card ke sample jaisi awaaz tabhi aayegi jab wahi reference ho.
- Lambe text me noise aa sakta hai, isliye NOVA sirf chhote jawab (300 akshar tak) Jarvis se bulwata hai.
- Production speed "validated nahi". GPU ke bina bahut dheema ho sakta hai.

## Raasta A - pehle sirf link test (bina model, 2 minute, PC ya koi bhi server)
1. `cd tools/jarvis_server`
2. `JARVIS_FAKE=1 JARVIS_TOKEN=apna-lamba-token python3 server.py`  (Windows: `set JARVIS_FAKE=1` aur `set JARVIS_TOKEN=...` phir `python server.py`)
3. Ye sirf ek beep jaisi awaaz deta hai. Isse bas pata chalta hai ki phone -> server -> phone ka rasta theek hai.

## Raasta B - asli Jarvis awaaz (GPU wala server)
Chahiye: NVIDIA GPU wala Linux server (apna PC ya kiraye ka GPU, jaise RunPod / Vast.ai). Python 3.10+.
1. Model ke packages lagao: `pip install -r requirements.txt` aur VoxCPM2-compatible `voxcpm` (model card: upstream revision `f772e498a45fbb5fb8e13fbf9b9c48be9fe33e69`). Exact install command model card / VoxCPM repo se lena, ye mujhe verify nahi hua.
2. Apni `reference.wav` rakho (10-30 second, saaf, ek hi insaan, bina music).
3. Chalao:
   `JARVIS_TOKEN=apna-lamba-token JARVIS_REFERENCE=/path/reference.wav python3 server.py`
   Pehli baar model download hoga (kaafi bada), thoda time lagega. "Jarvis server ready" aane tak ruko.
4. Ek **https** address chahiye (NOVA http nahi manta). Sabse aasan free tareeka: `cloudflared tunnel --url http://localhost:8000` (Cloudflare Quick Tunnel). Wo `https://....trycloudflare.com` jaisa address deta hai. Ye address har restart pe badalta hai.
5. Token kabhi kisi ko mat dena. Token ke bina server khula rehta hai.

## NOVA me jodna
1. NOVA > Settings > **JARVIS AWAAZ**.
2. Server address (https://...) daalo, token daalo, **JARVIS SAVE**.
3. **JARVIS CHECK** dabao. 4 line PASS aani chahiye: address, health, speech, speed. "Awaaz kaisi hai" hamesha NOT TESTED rehta hai, wo aap kaan se suno.
4. NOVA ko English me kuch bolne ko kaho (Settings me language English). Jawab Jarvis awaaz me aana chahiye.

## Kuch fail ho to
- `token galat` = token phone aur server me alag hai.
- `URL galat (404)` = address galat; sirf base address daalo (aage /tts nahi chahiye, daal diya to NOVA hata deta hai).
- `internet / server tak nahi pahunch paya` = server band hai ya tunnel ka address badal gaya.
- `bahut dheema` = GPU nahi hai ya model abhi load ho raha hai.

## Test status (sandbox me jo hua)
- Server ka web hissa + phone ka contract: **PASS** (tools/jarvis_server/test_server.py, FAKE engine).
- Asli VoxCPM2 + LoRA engine: **NOT TESTED** (sandbox me GPU, weights aur pip nahi). Code model card ke Usage example ke hisaab se likha hai. Ek unverified cheez: `lora_weights_path` ke liye `adapter/step_0000031` use hota hai (na mile to `adapter` folder).
- Phone par Jarvis awaaz: **NOT TESTED**.

## Sabse aasan raasta (free)
**EASY_STEPS_HINGLISH.md** dekho: Google Colab notebook (NOVA_Jarvis_Colab.ipynb), ek-click, bilkul free.
