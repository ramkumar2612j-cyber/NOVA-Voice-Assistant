# JARVIS v7.206 - manifest (sach ke saath)

## Naye features (static contracts: 8/8 rc=0 is session ke baad) (v7.199 se v7.205)
| Feature | File(s) | Static check | Kotlin compile | Phone test |
|---|---|---|---|---|
| Multi-agent chain (website > publish > WhatsApp) | TaskManagerRules, TaskManagerStore, JarvisService.runChain | PASS | NOT RUN | NOT RUN |
| Roz ki report (daily check) | DailyCheckRules, DailyChangeLog | PASS | NOT RUN | NOT RUN |
| Blue neon theme | ui.html (v201-blue-neon) | PASS | n/a | NOT RUN |
| Auto install on green (system confirm) | AutoReleaseRules, GitHubSelfBuild | PASS | NOT RUN | NOT RUN |
| Auto fix loop on red (caps 3/streak, 3/day) | AutoFixRules, GitHubSelfBuild | PASS | NOT RUN | NOT RUN |
| Media wake guard (reels se na jaage) | MediaWakeGuard, JarvisService | PASS | NOT RUN | NOT RUN |
| Claude app route (open + copy, no auto send) | ClaudeAppRules, JarvisService | PASS | NOT RUN | NOT RUN |

## NAHI BANA (aur kyon)
- Claude API (key wala): key owner ki chahiye, abhi nahi.
- Claude app me auto type/send: Accessibility wala kaam, Anthropic terms check nahi hue.
- Roz ka alarm (auto daily report/check): AlarmManager wiring baaki.
- Auto build trigger after self-patch: baaki.
- Launcher L4 (leads + orders home): baaki.
- Professional launcher polish: design review phone par hi ho sakta hai.
- "Full control / sab kuch pata": safety ke karan nahi (pehle bataya).

## Known risks
- Kotlin compile kabhi nahi hua. Pehli GitHub build me type errors aa sakte hain.
- Auto-fix ka patch build pass karke bhi phone par galat ho sakta hai.
- InstallTap (v6.33) abhi bhi system install screen par Install dabata hai.
- perf_governor RUNTIME test (headless browser) original zip me bhi fail hota hai.

## Agla kadam (owner)
1. Zip GitHub par upload, Actions build chalao. Red log bhejo.
2. Phone par ek-ek feature test karo, TEST_STATUS.md me likho.
3. Jo kaam nahi kiya, uska list dekh kar agla part mangna.
