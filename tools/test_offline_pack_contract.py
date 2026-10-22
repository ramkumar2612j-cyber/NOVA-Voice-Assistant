#!/usr/bin/env python3
"""v6.96 offline AI pack: static wiring checks (no compiler here). PASS = wired as designed, NOT = works on a phone."""
import re, sys
D = 'app/src/main/java/com/nova/assistant/'
rd = lambda n: open(D + n).read()
P, S, B, A = rd('OfflinePack.kt'), rd('OfflinePackStore.kt'), rd('LocalBrains.kt'), rd('AutoSetup.kt')
fails = []
def need(c, m):
    print(('PASS ' if c else 'FAIL ') + m)
    if not c: fails.append(m)
ids = re.findall(r'Model\("([a-z0-9\-]+)"', P.split('val OWNER_PICK')[0])   # v7.110: the owner-pick list (3B, tap only) is separate from the automatic catalog
need(len(ids) == 5 and len(set(ids)) == 5, '5 distinct models in the catalog')
need(re.findall(r'Model\("([a-z0-9\-]+)"', P.split('val OWNER_PICK')[1]) == ['qwen25-3b'], 'v7.110: only qwen25-3b is owner-pick, and it is not in the automatic catalog')
need(P.count('"CN"') >= 3 and P.count('"World"') >= 2, '3 Chinese + 2 world models')
need('Q4_K_M' in P or 'q4_k_m' in P, 'Q4_K_M files only')
need('sha256' in P and 'HEX64' in P, 'real 64-hex sha256 required')
need('ModelDownload.download' in S and 'Updater.hostAllowed' in S, 'verified resumable download with host allow-list')
need('"https"' in S and 'huggingface.co' in S, 'https + huggingface.co only for the file list')
need('OfflinePackStore.any' in B and 'MAX_TRIES' in B and 'busy.get()' in B, 'LocalBrains: pack counts as available, bounded cascade, busy never cascades')
need('PackPrompt.convert' in B and 'PackText.clean' in B, 'template + cleanup applied per model')
need('startPack' in A and 'unmetered' in A and 'ExtCfg.autoModels' in A, 'AutoSetup downloads packs on Wi-Fi only and respects the auto-models switch')
need('api_key' not in (P + S).lower() and 'Authorization' not in S, 'no key / login used by the pack')
sys.exit(1 if fails else 0)
