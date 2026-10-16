#!/usr/bin/env python3
"""v6.86 connectors: static contract. Tokens never reach the page, writes are gated twice, UI + bridge + voice are wired."""
import re, sys
R = "app/src/main/java/com/nova/assistant/"
def rd(p): return open(p, encoding="utf-8").read()
main, svc, api, store, cat, ui = rd(R+"MainActivity.kt"), rd(R+"NovaService.kt"), rd(R+"ConnectorApi.kt"), rd(R+"ConnectorStore.kt"), rd(R+"ConnectorCatalog.kt"), rd("app/src/main/assets/ui.html")
manifest = rd("app/src/main/AndroidManifest.xml")
bad = 0
def check(name, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + name)
    if not ok: bad += 1

for svc_id in ("github", "vercel", "netlify", "cloudflare", "supabase", "notion", "lovable"):
    check("catalog has " + svc_id, 'const val ' + svc_id.upper() + ' = "' + svc_id + '"' in cat)
check("custom MCP supported", "mcpOpen" in api and "tools/list" in api and "tools/call" in api)
check("bridge exposes connector calls", all(("fun " + f) in main for f in ("getConnectors", "connectorBegin", "connectorUseClip", "connectorPaste", "connectorSetPerm", "connectorDisconnect", "connectorRun", "connectorAddMcp", "connectorDeviceLogin")))
# the page must never receive a token: summaryJson and the bridge push only account/status text
check("summaryJson has no token field", "token(" not in store[store.find("fun summaryJson"):])
check("bridge never returns a token to JS", not re.search(r"fun \w*[Tt]oken\w*\(\): String", main[main.find("v6.86 CONNECTORS"):]))
check("tokens stored only through SecureStore", "SecureStore.saveSlot" in store and "SharedPreferences" not in store.replace("getSharedPreferences", ""))
check("clipboard cleared after a clipboard token is used", "clearPrimaryClip" in main)
# write gating: bridge needs confirmed=true, API re-checks the level, voice uses a strict yes that skip-confirm cannot bypass
check("bridge write needs confirmation", 'if (write && !confirmed) return "confirm"' in main)
check("API re-checks permission level", "ConnectorCatalog.allows(ConnectorStore.perm(ctx, id), write)" in api)
check("unknown action counts as write", "?: true   // unknown = treated as write" in api)
check("voice write uses confirmStrict", "confirmStrict(" in svc and "private fun confirmStrict" in svc and "ExtCfg" not in svc[svc.find("private fun confirmStrict"):svc.find("private fun connectorRun")])
check("voice route is wired", "ConnectorRules.parse(text)" in svc and "connectorBegin(conn)" in svc)
check("the service never handles or stores a token itself", "connectorPaste" not in svc and "ConnectorStore.save(" not in svc and "ConnectorStore.token(" not in svc)
check("HTTPS only, no redirects", 'u.protocol != "https"' in api and "instanceFollowRedirects = false" in api)
check("token only in Authorization header", '"Authorization"' in api and not re.search(r'[?&](access_token|token|key)=', api))
check("custom MCP url validated (https, public host)", "mcpUrlOk" in api and "mcpUrlOk" in main)
check("no new Android permissions", len(re.findall(r"<uses-permission", manifest)) == 29)   # alarm song: +READ_MEDIA_AUDIO, +READ_EXTERNAL_STORAGE(<=32). Part 9D: +ACCESS_NOTIFICATION_POLICY (ringer silent; a normal permission, the owner still grants Do Not Disturb access by hand); v6.97-wip9: +BLUETOOTH_CONNECT, +BLUETOOTH(<=30), +MODIFY_AUDIO_SETTINGS (watch mic); connectors themselves add none
check("no forbidden permissions added", "READ_CLIPBOARD" not in manifest)
check("UI: tab + view + sheet", all(x in ui for x in ('id="nova-conn-v686"', "t_conn", "v_conn", "connectorUseClip", "connectorRun", "onConnRun", "onConn=")))
check("UI: write needs its own confirm step", "HAAN, KARO" in ui and "READ + WRITE" in ui)
check("UI: permission explanation before ALLOW", "NOVA YE KAR PAYEGA" in ui and "TUMHARA KAABU" in ui)
check("Lovable is honest about having no public API", "Lovable ka public API nahi hai" in cat)
flow = rd(R+"ConnectorFlow.kt")
check("voice/chat can start a connect only after a strict yes", re.search(r'"connect" -> \{.*?confirmStrict\(.*?ConnectorFlow\.start', svc, re.S) is not None)
check("token is picked up when the app regains focus (clipboard needs focus)", "onWindowFocusChanged" in main and "connectFromFlow" in main and "ConnectorFlow.active()" in main)
check("voice can raise the level to WRITE only after a strict yes", re.search(r'"perm_write" -> \{.*?confirmStrict', svc, re.S) is not None)
check("chained steps stop on a refusal, an error or 'stop'", "connQueue.clear()" in svc and "connectorAdvance(good)" in svc and "if (!lastOk) { connQueue.clear(); return }" in svc)
check("results of connector commands land in the chat", 'emit("ai", head' in svc)
check("flow object holds no token", "SecureStore" not in flow and "saveSlot" not in flow and "getSlot" not in flow)
check("UI: chat has a connectors bar and picker", all(x in ui for x in ("cn_strip", "openPicker", "renderStrip", "＋ CONNECTORS")))
check("UI: chat chips only offer WRITE chips at level WRITE", "if(w&&d.perm!=='write')return" in ui)
sys.exit(1 if bad else 0)
