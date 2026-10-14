# NOVA Connectors (v6.86, chat + voice workflow v6.87)

Link NOVA to your own services, like ChatGPT's "Connect" screen: tap CONNECT, read what NOVA will be allowed to do, tap ALLOW, finish at the provider, come back. Everything is opt-in per service and per level.

## What you get

| Connector | Needs | READ | WRITE (needs level READ + WRITE and a yes) |
|---|---|---|---|
| GitHub | token (scopes repo, workflow, read:user pre-ticked) or OAuth device login | repos, open issues, workflow runs | create issue, create private repo, commit a file |
| Vercel | token | projects, deployments, domains | redeploy latest of a project |
| Netlify | token | sites, deploys | trigger a build |
| Cloudflare | token (Read all resources) | zones, Pages projects | none |
| Supabase | token | projects and status | none |
| Notion | integration secret | search shared pages | none |
| Lovable | GitHub connector | open Lovable | none |
| Custom MCP | https address (+ optional token) | tools/list | tools/call (always treated as WRITE) |

**Lovable has no public API.** It syncs every project to a GitHub repo, so the honest way is: connect GitHub, then work on that repo (commit a file, issues, workflow runs). The Lovable card opens lovable.dev.

## The flow (CONNECT tab, bottom bar)

1. CONNECT on a card. A sheet explains what NOVA can do, where the token lives, and lets you pick SIRF PADHNA or PADHNA + LIKHNA.
2. ALLOW opens the provider's own token page in the browser (GitHub: scopes already ticked).
3. Create the token, copy it, return to NOVA. NOVA reads the clipboard by itself (only while this sheet is open and the app is in front), checks the token shape, asks the provider who it belongs to, stores it encrypted, and clears the clipboard. A paste box is the fallback.
4. The card shows the account and the level. Level can be changed any time (OFF / READ / READ + WRITE); DISCONNECT deletes the token from the phone.

Optional GitHub OAuth: register an OAuth App on GitHub with "Enable Device Flow", paste its Client ID in the GitHub card, and CODE SE LOGIN appears: NOVA shows a code, opens github.com/login/device, and polls until you approve. (Providers other than GitHub need a registered OAuth app on their side to offer a browser approve screen; personal tokens are the supported route for them.)

## Whole workflow by chat or voice (v6.87)

1. **Connect:** say or type "github connect karo". NOVA asks what it will be able to do and waits for a yes. After the yes the provider's token page opens. Create the token, copy it, come back to NOVA: it picks the token from the clipboard by itself (Android only lets the app in front read the clipboard, so this happens when NOVA regains focus), verifies it, stores it, clears the clipboard and tells you.
2. **Allow writing:** "github write on karo" (NOVA asks yes or no and lists what writing means). "github read only karo" or "github off karo" lower the level at once.
3. **Use it:** "github repos dikhao", "vercel redeploy mysite", ... Lists arrive as chat messages.
4. **Chain steps:** "github repos dikhao phir vercel deployments dikhao", "github naya repo banao myapp phir vercel projects dikhao". Only explicit words split steps (phir, fir, uske baad, aur phir, then, and then). Steps run one after another; a write step asks its own yes; "no", an error or "stop" drops the rest.
5. **In the chat screen:** a bar above the input has ＋ CONNECTORS (picker with the same permission sheet as the CONNECT tab, plus "voice se jodo") and one chip per connected service: read chips send the command at once, write chips (only shown at level WRITE) fill the input so you add the name and press send.

Chained steps may refer to the previous list: "github repos dikhao phir us repo ke issues dikhao" uses the first repo of the list; "vercel projects dikhao phir usi project ko redeploy karo" redeploys the first project (still asks yes). Files: "github file banao owner/repo notes/a.txt kal ka kaam" commits a file (WRITE level + yes).

Typing "connectors" or "connectors dikhao" lists which services are connected.

## Voice / chat

"nova github repos dikhao", "github issues owner/repo", "github issue banao owner/repo title", "vercel deployments", "vercel redeploy <project>", "netlify build <site>", "cloudflare zones", "supabase projects", "notion mein <topic> dhundo", "lovable kholo", "connectors". Voice can only USE what is connected: connecting stays a tap in the CONNECT tab. Sentences that merely mention a service ("github jaisi website banao ...") are not hijacked.

## Safety rules (tested)

- Tokens: SecureStore (AES-GCM, Android Keystore). Never sent to the page, never in a URL or log. The page only learns "found / checking / account name".
- Write actions are gated three times: level READ + WRITE, an explicit yes (page: "HAAN, KARO"; voice: a spoken yes that the "skip confirmation" settings cannot bypass), and `ConnectorApi.run` re-checks the level.
- Unknown actions count as WRITE. MCP tools/call is always WRITE.
- HTTPS only, no redirects, 300 KB reply cap, custom MCP addresses must be public https hosts (no localhost / private ranges / user:pass@).
- No new Android permission. The GitHub token is shared with "website publish"; DISCONNECT removes both.

## Status

RUN in the sandbox: Kotlin compile of the whole app, 666 JUnit tests (20 new for connectors: voice parsing, permission levels, token shapes, URL safety, provider JSON parsing), 49 contract scripts, headless UI test of the CONNECT tab with a fake bridge.
NOT RUN: Gradle/AGP build, any real provider call (the sandbox cannot reach them; endpoints are written from the public API docs), a phone. Use rows 30-41 of `REAL_DEVICE_CHECKLIST_v6.83.md`.
