# PART STATUS v7.185 - FREE photo EDIT (owner: "AI image edit bhi kar ske free mein")

## Kya bana
- `FreeImageEdit.kt` (naya): Hugging Face image-to-image (model FLUX.1-Kontext-dev) se free photo edit. SAME
  SecureStore "hf" token jo pehle se FreeImage (free photo GENERATE) use karta hai — owner ko kuch naya setup
  nahi karna, bas ek hi HF token kaam aata hai dono ke liye.
- `editAi()` mein wiring: Gemini fail ho jaye (ya key hi na ho) AUR owner ne Settings > FREE IMAGE FALLBACK ON
  kiya ho, to pehle Hugging Face free edit try hota hai; tabhi, agar wo bhi fail ho, phone-only (Level 1) girta
  hai. Koi step silently skip nahi hota, har case mein honest text message.
- No-Gemini-key case mein pehle seedha phone-only pe chala jaata tha; ab agar FREE IMAGE ON hai aur HF token
  saved hai, to apna alag "Hugging Face ko bhejein?" HAAN dialog dikhta hai (naya consent screen, Gemini wale
  se alag — koi photo bina poochhe kahin nahi jaati).

## Honest: jo NAHI pakka hai
1. Hugging Face ke image-to-image API ka **exact JSON shape model-ke-hisaab-se badalta hai** aur yahan
   (sandbox, network nahi) kabhi try nahi kiya ja saka. Jo likha hai wo unke published docs ke pattern se hai;
   agar HF alag shape maange to ye sirf error message dega (jo failText() se aata hai) aur normal Level-1
   fallback chal jayega — koi crash nahi, bas free edit kaam nahi karega.
2. FLUX.1-Kontext-dev (ya koi bhi free-tier model) rate-limited/slow ho sakta hai — owner ko real phone test
   mein pata chalega.
3. Kotlin compile / GitHub Actions / phone test — sab NOT RUN (jaisa har part mein hota aaya hai).

## Test level
| Test | Level | Result |
|---|---|---|
| tools/test_free_image_edit_contract.py | machine (static) | PASS (8 checks) |
| tools/test_bridge_caps_contract.py (regression) | machine (static) | PASS |
| tools/test_photo_publish_contract.py (regression) | machine (static) | PASS |
| tools/test_wiring_audit_contract.py (regression) | machine (static) | PASS |
| Kotlin compile | NOT RUN | kotlinc sandbox me nahi |
| GitHub Actions build | NOT RUN | owner ke kehne par |
| Real HF edit on phone | NOT RUN | owner: HF token save karo, FREE IMAGE FALLBACK ON karo, phir photo edit bolo |

## Owner phone test (jab CI green ho)
1. Settings > FREE IMAGE FALLBACK ON karo.
2. Settings mein Hugging Face token daalo (agar pehle se free-image ke liye nahi daala hai) — huggingface.co
   par free account se milta hai.
3. Ek photo pick karke bolo "jarvis isko sunset wala background do" (ya koi bhi edit).
4. Gemini key na ho to free-edit wala "Hugging Face ko bhejein?" dialog aayega — HAAN dabao.
5. Result batao: edited photo mili ya error message — wahi TEST_STATUS mein likhenge.
