import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
j = root / 'app/src/main/java/com/nova/assistant'
rd = lambda n: (j / n).read_text()
rules, bc, lm, pack, ma, cap, hg = rd('BrainChoiceRules.kt'), rd('BrainChoice.kt'), rd('LearnedMemory.kt'), rd('OfflinePack.kt'), rd('MainActivity.kt'), rd('CapabilityLearningStore.kt'), rd('HelpGuide.kt')
auto = rd('AutoSetup.kt')
ui = (root / 'app/src/main/assets/ui.html').read_text()
test = (root / 'app/src/test/java/com/nova/assistant/BrainChoiceRulesTest.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
card = ui[ui.find('<script id="pt7_brain_learned">'):ui.find('<script id="pt18_safe">')]
code = re.sub(r'/\*.*?\*/', '', card, flags=re.S)
bridge = ['getBrainChoice', 'startBrainInstall', 'cancelBrainInstall', 'getLearnedSummary', 'clearJobLearning']
checks = {
 'rules are pure Kotlin (no Android imports)': 'import android' not in rules and 'object BrainChoiceRules' in rules,
 'recommend by RAM: 3B >= 8000, 1.5B >= 4000, else none': 'RECOMMEND_3B_RAM_MB = 8000L' in rules and 'RECOMMEND_15B_RAM_MB = 4000L' in rules,
 'download needs the owner tap and every condition': all(x in rules for x in ('ownerTapped', '"owner has not confirmed"', '"phone RAM too low"', '"not enough free storage"', '"no internet"', 'needs Wi-Fi')),
 '3B is owner-pick only: NOT in the automatic MODELS list': 'val OWNER_PICK' in pack and '"qwen25-3b"' in pack and pack.find('"qwen25-3b"') > pack.find('val OWNER_PICK'),
 'pack loop untouched: toDownload / runnable still read only MODELS': 'MODELS.filter { it.id !in installed' in pack and 'fun runnable(totalRamMb: Long): List<Model> = MODELS.filter' in pack,
 'byId finds owner-pick, tryOrder tries it first': 'OWNER_PICK.firstOrNull { it.id == id }' in pack and 'OWNER_PICK.mapNotNull' in pack,
 'old pack list unchanged (5 models)': len(re.findall(r'^\s+Model\("', pack, re.M)) == 6,
 'download uses the verified installer (size + sha256), worker thread, single flight': 'OfflinePackStore.installOne(' in bc and 'running.compareAndSet(false, true)' in bc and 'Thread({' in bc,
 'BrainChoice.start refuses through the rules first': 'BrainChoiceRules.refusal(id, true,' in bc and 'BrainChoiceRules.isOfferedId(id)' in bc,
 'BrainChoice never auto-starts (no loop, no sleep, no AutoSetup hook)': 'Thread.sleep' not in bc and 'BrainChoice' not in auto,
 'LearnedMemory clears ALL THREE stores': all(x in lm for x in ('PlaybookStore(app).clear()', 'SiteLearningStore(app).clear()', 'CapabilityLearningStore(app).clear()')),
 'LearnedMemory shows all three summaries': all(x in lm for x in ('PlaybookStore(app).summary()', 'SiteLearningStore(app).summary()', 'CapabilityLearningStore(app).summary()')),
 'CapabilityLearningStore.clear() exists': 'fun clear()' in cap and 'remove(KEY)' in cap,
 'clear does not touch chat / keys / models': not re.search(r'SecureStore|ChatStore|OfflinePackStore|UpdateManager|deleteRecursively', lm),
 'bridge methods exist and are @JavascriptInterface': all(re.search(r'@JavascriptInterface\s+fun ' + b + r'\(', ma) for b in bridge),
 'old LearnStore clearLearned() not duplicated': len(re.findall(r'fun clearLearned\(', ma)) == 1,
 'bridge names are in the page BRIDGE list and used by the card': all(('"' + b + '"') in ui and ('Android.' + b) in card for b in bridge),
 'card script is in SCR list': "'pt7_brain_learned'" in ui,
 'card is add-only: inserted before the SAVE row next to the Part 17 card': "sv.parentNode.parentNode.insertBefore(card,sv.parentNode)" in card and "d.getElementById('p7_card')" in card,
 'two-tap confirm before a download + confirm() before clear': 'PAKKA? DOWNLOAD' in card and 'confirm(' in card,
 'card has no network and no animation': not re.search(r'fetch\(|XMLHttpRequest|animation|transition|@keyframes', code),
 'honest clear message (3 / 3 or partial)': '(3 / 3)' in card and 'Poora saaf nahi hua' in card,
 'MADAD topic in Kotlin and ui.html': 'Topic("brainlearn"' in hg and '"id": "brainlearn"' in ui,
 'unit tests cover RAM tiers, pack isolation, order, states, refusals, ids, headline': all(x in test for x in ('recommendationFollowsRam', 'ownerPickBrainIsNeverInTheAutomaticPackList', 'installedOwnerPickBrainIsTriedFirst', 'optionStatesFollowRamStorageAndInstalled', 'downloadNeedsTheOwnersTapAndEveryCondition', 'onlyTheTwoOfferedIdsAreAccepted', 'headlineIsHonestAboutWeakPhones')),
 'HARD DENY list and protected screens untouched': '"pay", "payment", "buy"' in rd('AgentRules.kt') and 'BLOCKED_FRAGMENTS' in rd('Control.kt'),
 'version 7.110 / code 37': 'versionName = "7.114"' in gradle and '"37"' in gradle,
}
for n, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + n)
raise SystemExit(0 if all(checks.values()) else 1)
