# DEPLOY — JARVIS brain server + website (FREE options, step-by-step)

> Sab options free hain. Owner jo pasand kare wo chune. Har option me admin password ka intezaam
> alag hai — neeche likha hai. Phone/laptop in sejudenge, na hone par bhi sab local kaam karta rehta hai.

## Option A — Laptop hi server ban jaye (sabse aasaan, 5 minute)

1. Laptop par JARVIS desktop app chalu hai? Wahi laptop par ek terminal kholo (ya JARVIS ko bolo — agle version me ek command aayegi).
2. Repo folder me jao: `cd JARVIS-Voice-Assistant/backend`
3. `python3 brain_server.py` chalao (Windows par: `py brain_server.py`)
4. Terminal me likhega: `JARVIS BRAIN 7.217 ready on 0.0.0.0:8787`
5. Admin password: `backend/state/admin_password.txt` me hai (ek baar padh ke save kar lo)
6. Admin panel: browser me `http://127.0.0.1:8787/admin` — laptop par website/admin.html kholo, login karo, pairing code banao
7. **Phone se judne ke liye laptop ka IP chahiye** — laptop par `ipconfig` (Windows) se IPv4 address lo (jaise 192.168.1.20)
8. **Internet se bahar nahi jaayega** — sirf ghar ke Wi-Fi me. HTTPS bhi nahi hai is option me, isliye ye
   sirf ghar ke network ke liye theek hai (phone client http sirf 127.0.0.1 ke liye allow karta hai —
   isliye is option me website admin panel se code banake phone me daalo; direct IP address code me
   localhost-only rule ke wajah se phone par nahi chalega — **is case ke liye Option B behtar hai**).

**Note (sach):** Option A me phone pairing code IP ke saath hi maangta hai aur phone https-only rule ka
izzat rakhta hai — ghar ke network par http allow karne ka switch agle part me aayega. Filhal phone ke liye
Option B/C recommended.

## Option B — Render free tier (internet se, phone kahin se)

1. https://render.com par free account banao (GitHub se login)
2. "New +" → "Web Service" → apna JARVIS GitHub repo connect karo
3. Settings:
   - Environment: **Python 3**
   - Build command: `(khali chhodo)`
   - Start command: `python3 backend/brain_server.py`
   - Instance type: Free
4. Environment variables me add karo:
   - `ADMIN_PASSWORD` = apna khud ka strong password (jo yaad rahe)
   - `JARVIS_BRAIN_PUBLIC_ADDR` = https://your-app-name.onrender.com (deploy hone ke baad milega)
5. Deploy → 2-3 minute → URL milega (https://…onrender.com)
6. Admin panel: us URL par `/admin` — website/admin.html kholo, `localStorage` me 'brain' key me URL daalo (browser console me: `localStorage.setItem('brain','https://your-app.onrender.com')`), login karo
7. Phone: Settings > CLOUD BRAIN me admin panel ka code paste karo. Laptop: `cloud jodo JARVIS-CLOUD:...`

**Free tier limit (sach):** 15 min me ek baar sleep hota hai — pehle request par wakh hota hai (thoda der lagti hai). Chalega, par "hamesha on" nahi.

## Option C — fly.io free (hamesha on jaisa)

1. https://fly.io par account (GitHub login) — free allowance milta hai
2. `fly launch` (repo me `backend/` root karke) ya docs dekho — start command same `python3 brain_server.py`
3. `fly secrets set ADMIN_PASSWORD=<apna password> JARVIS_BRAIN_PUBLIC_ADDR=https://<app>.fly.dev`
4. URL https://…fly.dev — baaki sab Option B jaisa

## Website (official) — GitHub Pages (free, sabse aasaan)

1. GitHub repo → Settings → Pages
2. Source: **GitHub Actions** select karo
3. Repo → Actions → "JARVIS Website (GitHub Pages)" workflow chalao (ya website/ me kuch push karo)
4. 1-2 minute me live: `https://<username>.github.io/<repo>/`
5. Website ka live status backend se aata hai: website kholke browser console me
   `localStorage.setItem('brain','https://your-backend-url')` ek baar daal do — uske baad status LIVE dikhega

## Daily reports automation (optional)

1. GitHub repo → Settings → Secrets and variables → Actions:
   - `JARVIS_BRAIN_URL` = backend URL (jaise https://your-app.onrender.com)
   - `JARVIS_BRAIN_ADMIN_PASSWORD` = admin password
2. Actions me "JARVIS Daily Health Check" roz 07:00 IST khud chalega — report artifact banega + backend ke admin panel me dikhega

## TV (OSCAR)

Docs: `docs/OSCAR_TV.md` — TV Android TV/Google TV hai to Settings > TV LINK me IP + PIN. Nahi hai to doc me alternatives.

## JARVIS voice server (pehle se tha — yaad dilaane ke liye)

`tools/jarvis_server/` wala TTS server alag hai (Colab/laptop). Uske steps pehle jaise hi hain: `tools/jarvis_server/README_HINGLISH.md`.

## Kya kabhi paid karna padega?

Nahi — sab upar wale options free hain. Paid sirf tab sochna jab:
- website apne khud ke domain (₹500-800/saal) par chahiye ho (GitHub Pages free subdomain kaafi hai)
- brain server 24x7 heavy load par chahiye ho (abhi ke use ke liye free kaafi hai)
