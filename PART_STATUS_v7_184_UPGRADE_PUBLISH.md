# PART STATUS v7.184 - PART B (ii): upgrade PUSH + POLL (GitHub Actions)

## Kya bana
- `GitHubUpgradePublish.kt` (naya file): `pushBranch()` main se `nova-up-<id>` branch banata hai aur sirf ek
  `UPGRADE_REQUEST_<id>.md` note push karta hai (koi app `.kt` file nahi chhuti). `pollLatestRun()` us branch ke
  sabse naye Actions run ki status + green hone par artifact URL padhta hai (read-only, kuch push nahi karta).
- `UpgradeLedger.kt` mein 2 naye function: `push(ctx,id)` (sirf state=APPROVED se chalta hai, success par state
  PUSHED) aur `poll(ctx,id)` (state=PUSHED se chalta hai; green -> BUILD_OK; red -> `tries+1`, cap `MAX_TRIES=5`
  ke baad owner se pucho "aur try karun?").
- `MainActivity.kt` bridge `upgradeAct(id, act)` mein `act="push"` aur `act="poll"` jode (add-only, purana
  yes/no case nahi chheda).
- `ui.html` JARVIS UPGRADES card: APPROVED request par "PUSH (GitHub)" button, PUSHED request par
  "POLL (build check)" button (card ka purana comment line/structure rakha, bas naya note jodi).
- `tools/test_upgrade_publish_contract.py` (naya, 12 static checks): token kabhi log nahi hota, push sirf
  APPROVED se, poll kabhi PUT/POST nahi karta, retry cap wired, bridge capped. PASS.

## Honest: jo NAHI bana (zaroor padho)
1. **AI patch engine bilkul nahi bana.** `pushBranch()` sirf ek note file daalta hai jisme request ka text hai —
   koi `.kt` file generate/edit nahi hoti. Jab tak yeh nahi bane, build hamesha "no real change" hi hoga
   (compile ho jayega kyunki kuch bhi naya Kotlin nahi joda, par koi naya feature bhi code mein nahi aayega).
   Yeh jaan-bujhkar hai: owner ke apne doc mein likha tha ki yahi sabse bada risk hai ("300-file app todne ka
   bada chance"), isliye ise bina alag HAAN-gated design ke chupke se nahi joda.
2. Kotlin compile yahan bhi NOT RUN hai (sandbox mein kotlinc nahi) — jaisa har pichhle part mein hota aaya hai.
3. GitHub par asli push/poll kabhi test nahi hua (sandbox mein network nahi) — sirf GitHub REST docs se likha.
4. Install + undo (Part B step iii) abhi bhi shuru nahi hua.

## Test level
| Test | Level | Result |
|---|---|---|
| tools/test_upgrade_publish_contract.py | machine (static) | PASS (12 checks) |
| tools/test_upgrade_ledger_contract.py (regression) | machine (static) | PASS (no break) |
| tools/test_bridge_caps_contract.py (regression) | machine (static) | PASS (no break) |
| tools/test_app_builder_contract.py (regression) | machine (static) | PASS (no break) |
| Kotlin compile | NOT RUN | kotlinc sandbox me nahi |
| GitHub Actions build | NOT RUN | owner ke GitHub token/push ke bina nahi ho sakta |
| Real push on a real branch | NOT RUN | owner test chahiye |

## Agla kaam
1. Owner: GitHub Actions ka current CI result (green/red) bata do — yeh purana pending kaam hai, ab bhi pehla step.
2. Phir (sabse zaroori faisla): AI patch engine ka design — kaise ek "feature chahiye" wish se safe Kotlin patch
   banega, kaun verify karega compile hone se pehle. Yeh alag HAAN-gated plan chahiye, abhi tak koi plan nahi hai.
3. Phir Part B (iii): install + real undo (tag se APK reinstall).
