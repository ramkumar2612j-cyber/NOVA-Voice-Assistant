#!/usr/bin/env python3
"""AUTOPSY PART 4 (static): n8n executions by workflow NAME, and learning must not claim 'kept' when addKept returned null."""
import pathlib, sys
J = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/nova/assistant"
api = (J / "ConnectorApi.kt").read_text(encoding="utf-8")
svc = (J / "NovaService.kt").read_text(encoding="utf-8")
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
ex = api.split('"executions" -> {')[1].split('"activate", "deactivate"')[0]
check("n8n executions resolves a name to an id", "n8nWorkflowId(base, h, wf)" in ex)
check("n8n executions gives an honest error for an unknown workflow", "workflow nahi mila" in ex)
check("learn checks the addKept result", "val keptId = LearnStore.addKept(this, topic, note, src)" in svc and "keptId == null" in svc)
check("learn says honestly when a note already exists", "pehle se note hai" in svc)
print("failed:", bad); sys.exit(1 if bad else 0)
