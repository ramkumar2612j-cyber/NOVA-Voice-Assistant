#!/usr/bin/env python3
"""v6.18b structural check (does NOT run Kotlin): the agent analyses the job first and the rules cover AI-app text/website jobs."""
import os
here = os.path.dirname(os.path.abspath(__file__))
rd = lambda p: open(os.path.join(here, '..', 'app/src/main/java/com/nova/assistant', p), encoding='utf-8').read()
ar, ns = rd('AgentRules.kt'), rd('NovaService.kt')
chk = [
    ('planSystem exists', 'fun planSystem()' in ar and 'fun planNote(' in ar),
    ('plan says never payments/purchases', 'Never plan payments' in ar),
    ('system prompt has AI-app make rule', 'To get something made BY an AI app' in ar),
    ('system prompt has OWNER INTENT rule', 'OWNER INTENT section' in ar),
    ('agentRun calls agentPlan', 'agentPlan(task)' in ns and 'val plan = if (fastJob)' in ns),
    ('loop uses fullTask', 'AgentRules.userPrompt(fullTask' in ns),
    ('plan never logs keys', 'x-goog-api-key' not in ar),
]
bad = [n for n, ok in chk if not ok]
print('FAIL: ' + ', '.join(bad) if bad else 'ALL PASSED (%d checks, structural only)' % len(chk))
raise SystemExit(1 if bad else 0)
