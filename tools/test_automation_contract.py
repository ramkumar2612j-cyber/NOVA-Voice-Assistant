#!/usr/bin/env python3
"""v6.97 automation connectors (webhook + n8n API): static contract. Run from the project root."""
import sys
R = "app/src/main/java/com/nova/assistant/"
def rd(p): return open(p, encoding="utf-8").read()
api, store, cat, rules, svc, main = [rd(R+f) for f in ("ConnectorApi.kt","ConnectorStore.kt","ConnectorCatalog.kt","ConnectorRules.kt","NovaService.kt","MainActivity.kt")]
ui = rd("app/src/main/assets/ui.html")
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n)
    if not ok: bad += 1
check("catalog knows hook_ and n8n_ ids", "HOOK_PREFIX" in cat and "N8N_PREFIX" in cat and "fun isDynId" in cat)
check("webhook is always a WRITE action", "isHookId(id)) return true" in api)
check("n8n activate/deactivate are WRITE, list/executions READ", 'action != "workflows" && action != "executions"' in api)
check("webhook and n8n addresses must be public https", "mcpUrlOk(h.url)" in api and "n8nBase" in cat)
check("n8n key goes only in the X-N8N-API-KEY header", "X-N8N-API-KEY" in api and "api/v1/workflows?limit=" in api and "key=" not in api.replace("key = arg", ""))
check("webhook answer does not claim the workflow succeeded", "Kaam poora hua ya nahi" in api)
check("n8n is verified with a real call before saving", "n8nVerify(base, key)" in main and "connectorAddN8n" in main)
check("webhook is saved without firing it", "connectorAddHook" in main and "n8nVerify" not in main[main.find("fun connectorAddHook"):main.find("fun connectorAddN8n")])
check("voice reaches added connectors", "parseDynamic" in svc and "dynDef(this, ci.connector)" in svc)
check("voice write still needs strict yes (existing path unchanged)", "confirmStrict(tr(\"${d.name} par: $what" in svc)
check("tokens never in summary JSON", "token(" not in store[store.find("fun summaryJson"):])
check("page has both add forms", "hook_new" in ui and "n8n_new" in ui and "connectorAddHook" in ui and "connectorAddN8n" in ui)
check("bridge accepts dynamic ids", main.count("ConnectorCatalog.isDynId(id)") >= 2)
sys.exit(1 if bad else 0)
