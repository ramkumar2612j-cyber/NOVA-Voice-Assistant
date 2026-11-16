#!/usr/bin/env python3
"""v7.184 Part B(ii) static contract: upgrade push (branch+note only) + poll (read-only Actions status). No .kt patch yet."""
import sys
R='app/src/main/java/com/nova/assistant/'
g=open(R+'GitHubUpgradePublish.kt',encoding='utf-8').read()
u=open(R+'UpgradeLedger.kt',encoding='utf-8').read()
ma=open(R+'MainActivity.kt',encoding='utf-8').read()
ui=open('app/src/main/assets/ui.html',encoding='utf-8').read()
chk={
 'token never logged/printed':all(x not in g for x in ('println(token', 'Log.d(', 'Log.e(', 'ErrLog.add("' + 'token')),
 'token only via SecureStore slot':'SecureStore.getSlot(ctx, GitHubPublish.SLOT)' in g,
 'push creates branch from main, does not touch main directly':'/git/ref/heads/main' in g and '"refs/heads/" + branch' in g,
 'push writes a NOTE only, no .kt file path':'UPGRADE_REQUEST_' in g and '.kt"' not in g,
 'honest: no AI patch generation here':'does NOT ask an AI to generate a' in g or 'does NOT write or change any .kt file' in g,
 'poll is read-only (no PUT/POST to repo contents)':'fun pollLatestRun' in g and g.split('fun pollLatestRun')[1].count('"POST"')==0 and g.split('fun pollLatestRun')[1].count('"PUT"')==0,
 'push only allowed from APPROVED state':'if (e.state != UpgradeRules.APPROVED) return' in u,
 'poll increments tries on failure, capped by MAX_TRIES':'val tries = e.tries + 1' in u and 'UpgradeRules.retryAllowed(tries)' in u,
 'poll moves to BUILD_OK only on real success':'UpgradeRules.BUILD_OK' in u and 'conclusion == "success"' in u,
 'ledger push/poll call GitHubUpgradePublish, not raw network itself':'GitHubUpgradePublish.pushBranch' in u and 'GitHubUpgradePublish.pollLatestRun' in u,
 'bridge exposes push/poll acts, capped by act.take(8)':'"push" -> UpgradeLedger.push' in ma and '"poll" -> UpgradeLedger.poll' in ma and 'act.take(8)' in ma,
 'UI shows PUSH only for APPROVED, POLL only for PUSHED':"x.state==='APPROVED'" in ui and "x.state==='PUSHED'" in ui and 'data-a="push"' in ui and 'data-a="poll"' in ui,
}
bad=[k for k,v in chk.items() if not v]
for k,v in chk.items(): print(('PASS' if v else 'FAIL')+': '+k)
sys.exit(1 if bad else 0)
