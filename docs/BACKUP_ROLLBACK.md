# BACKUP & ROLLBACK — JARVIS (v7.221)

> Rule: koi bhi naya version lagane se PEHLE backup. 2 minute ka kaam, tension ki chhutti.

## Backup lena

**A. Automatic (repo ke andar):**
```
./tools/backup.sh "kyun liya, jaise: v7.221 se pehle"
```
- `backups/JARVIS_v<version>_<date>.zip` banega (source + docs + website + workflows; secrets/state NAHI)
- `backups/BACKUP_LEDGER.txt` me date + version + sha256 likha jaata hai

**B. GitHub pe sab (sabse important):**
- Repo push hone par GitHub sab kuch rakhta hai — `git push` hi sabse bada backup hai.
- Tag bana lo har release par: GitHub repo → Releases → "Draft a new release" → tag `v7.221` → zip upload karke publish.
  (build.yml khud `jarvis-latest` release bana deta hai — wahi APK bhi backup hai.)

**C. APK ka backup:** GitHub Actions ka artifact + `jarvis-latest` release download kar ke phone/gallery me rakh lo.

## Rollback karna (kuch karab ho jaye)

**Case 1: Naya APK kharab laga, purana wapas chahiye**
1. GitHub → Releases → `jarvis-latest` ki purani file (ya apna saved APK) download karo
2. Phone me install kar do (same signing key ho to seedha upar likh dega; nahi to pehle uninstall — DATA KHARAB: chats/yaad jayengi, keys dubara daalni padengi)
3. **Isiliye:** GitHub repo Settings → Secrets → `NOVA_KEYSTORE_*` set karo — phir har APK same key se banega aur bina uninstall ke update hoga

**Case 2: Code kharab ho gaya (build hi nahi ho raha)**
1. GitHub repo → Actions → jo bhi last GREEN run hai uska commit dekho
2. Us commit par "Revert" ya download-zip karke wapas push
3. Ya local backup se: `backups/JARVIS_vXXX_....zip` unzip karke poori jagah badal do, phir push

**Case 3: Brain server ka data (tasks/memory)**
- `backend/state/brain.db` — server band karke copy kar lo, wapas rakh do. Bas. (SQLite ek file hai.)
- Render/fly par: dashboard se disk download.

**Case 4: Phone ka data (chats, yaad, keys)**
- App me: Settings → chats export (purana feature). Yaad/facts ab cloud se aayenge (cloud on ho to).
- Keys: settings me dobara paste (Keystore encrypted tha — backup nahi hota, jaan-bujh ke).

## Kab rollback NAHI karna

- APK me naya feature crash kare → pehle TEST_STATUS.md me FAIL likho, agle zip me fix aayega; purana APK sirf tab jab phone par ROZ ka kaam ruk raha ho.
- Rollback se pehle bhi: `./tools/backup.sh "rollback se pehle"` — kyunki rollback bhi ek change hai.

## Ledger dekhna

```
cat backups/BACKUP_LEDGER.txt
```
Har line: `<date> <version> <note> sha256=<hash>` — zip kholne se pehle `sha256sum file.zip` se verify kar sakte ho.
