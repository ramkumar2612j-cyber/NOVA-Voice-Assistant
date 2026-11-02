# NOVA phone check (ONLY AFTER GitHub build is green)

**Do not install/test this zip until Actions > “Build and Test APK” is green after the “Unpack zip” workflow.** These checks have NOT been performed by the reviewer.

1. **Wake/listening:** Settings mein permissions allow karein, NOVA ACTIVATE karein; normal awaaz mein “nova torch jala do” bolkar dekhein. Torch jalni chahiye; beech mein khud se baat nahi honi chahiye. 5 baar test karein.
2. **Camera:** “nova mere mobile ka camera kholo aur front camera se video on karo” bolen. Camera front select ho to manually Record tap karein; NOVA camera shutter khud nahi dabata. Camera band karke waapas NOVA kholen.
3. **Audio recording:** ACTIVATE ke baad Settings > RECORDING SHURU dabayen. Android notification/timer dikhna chahiye. 2 minute baad notification STOP dabayen; Music > NOVA mein file play karein.
4. **Video recording:** NOVA ko visible rakhein; Settings ke video manager se front/back choose karke start karein. Notification aur camera privacy indicator dikhna chahiye. 2 minute baad notification STOP; Movies > NOVA mein video play karein.
5. **Safety + learning:** “mummy = Sunita” teach karein, phir “what is the weather?” poochein (question teach alias nahi banna chahiye). Safe 2-step routine test karein. Call/message request par spoken yes se pehle action nahi hona chahiye.
6. **Screen privacy/reply:** Non-protected screen par screen session run karein; reply draft sirf type hona chahiye, Send manually aap dabayen. Banking/payment app par NOVA ko read/tap refuse karna chahiye.

Report exact phone model, Android version, and screenshots for any failed step. Never send API keys or private audio in screenshots.

7. **Agent task ledger (new, only after CI-green):** NOVA ke saath ek harmless task chalayein, jaise “kaam chatgpt kholo aur ek joke poochho”. Task ke dauran “nova agent status” bolein—current status aur step count batana chahiye. “ruko” bolne par status CANCELLED hona chahiye. Task ke beech NOVA service ko band karke dobara activate karne ka test sirf harmless task par karein; status INTERRUPTED bataye aur purane taps ko khud se repeat na kare. Agar agent “complete” bole, woh actual screen par result verify karne ko kahe—automatic verified-success claim nahi karna chahiye.

## Part 1-3 additions (v6.73, all NOT TESTED on a phone yet)
8. **Chats:** NEW CHAT, then ask "mera naam kya hai" (YAAD memory must still answer, the old chat text must not be in the model context). Open an old chat from HISTORY and ask about it. Delete a chat.
9. **Backup AI:** with the main key removed or invalid, ask a follow-up to an earlier answer; the backup must continue the same thread.
10. **Setup check:** say/type "nova setup check"; it must list exactly what is really missing.
11. **Agent restart:** harmless task, stop the service mid-way, activate again: one notification with the exact resume status; nothing repeats by itself.
