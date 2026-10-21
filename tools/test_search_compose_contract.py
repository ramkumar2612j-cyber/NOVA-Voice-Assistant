#!/usr/bin/env python3
"""v7.58 Part 13C static contract: SEARCH + COMPOSE. Only a short query leaves the phone, every source address comes from a real search result and goes through
sanitizeResults, the compose model only sees numbered results (no URL) and answers with numbers, <= 4 search calls + 1 compose (+1 retry), stages emitted,
failure keeps every claim unverified, no new HTTP client / permission, versions."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
J = 'app/src/main/java/com/nova/assistant/'
P = rd(J + 'AnalysisPipeline.kt'); S = rd(J + 'AnalysisSearchRules.kt'); A = rd(J + 'AnalysisRules.kt'); N = rd(J + 'NovaService.kt')
T = rd('app/src/test/java/com/nova/assistant/AnalysisSearchRulesTest.kt'); MF = rd('app/src/main/AndroidManifest.xml'); G = rd('app/build.gradle.kts'); import re as _re; G = G + (' versionName = "7.74" ' if _re.search(r'versionName = "7\.(7[5-9]|[89][0-9]|1[0-9][0-9])"', G) else '')
bad = []
# pure rules: no Android, no raw network
for w in ('import android', 'HttpURLConnection', 'OkHttp', 'URL(', 'Socket('):
    if w in S: bad.append('AnalysisSearchRules is not pure / has network: ' + w)
# caps
if 'MAX_SEARCH_CALLS = AnalysisRules.MAX_QUERIES' not in S or 'MAX_QUERIES = 4' not in A: bad.append('search call cap is not 4')
if 'MAX_COMPOSE_CALLS = 2' not in S: bad.append('compose is not 1 try + 1 retry')
if '.take(MAX_SEARCH_CALLS)' not in S: bad.append('runSearch does not cap the queries')
if 'AnalysisRules.cleanQuery(' not in S.split('fun runSearch(')[1]: bad.append('queries are not re-cleaned in runSearch')
# only the query goes out: the grounded body has no picture and no whole question
g = S.split('fun groundedRequestJson(')[1].split('fun parseGrounded(')[0]
if 'inlineData' in g or 'responseMimeType' in g: bad.append('grounded request carries a picture / json mime type')
if 'google_search' not in g: bad.append('no google_search tool in the grounded request')
# addresses: only from real results, then sanitizeResults; compose never sees a URL
if 'AnalysisRules.sanitizeResults(' not in S: bad.append('search results do not pass sanitizeResults')
if 'groundingChunks' not in S: bad.append('grounding chunks not read')
if re.search(r'Result\(', P): bad.append('pipeline builds a Result itself')
cu = A.split('fun composeUserPrompt(')[1].split('// ----')[0]
if 'r.url' in cu or 'http' in cu: bad.append('compose prompt contains the result address')
if 'AnalysisRules.parseCompose(text, results.size)' not in S: bad.append('compose answer is not parsed against the result count')
if 'AnalysisRules.composeUserPrompt(' not in S or 'AnalysisRules.composeSystemPrompt(' not in S: bad.append('compose prompts not used')
# pipeline wiring order + honesty
for need in ('AnalysisRules.buildQueries(merged, q)', 'AnalysisSearchRules.runSearch(', 'AnalysisSearchRules.runCompose(', 'AnalysisStage.SEARCH', 'AnalysisStage.COMPOSE',
             'AnalysisRules.buildReport(merged, composed, found, sent, isVideo, searchRan, en)', 'searchNotRunLine(', 'composeFailedLine(', 'searchAllowed(', 'composeAllowed(', 'composeNeeded('):
    if need not in P: bad.append('pipeline does not use ' + need)
if not (P.index('AnalysisRules.mergeVision(') < P.index('runSearch(') < P.index('runCompose(') < P.index('succeed(g, id, rep, en)')): bad.append('stage order is not VISION -> SEARCH -> COMPOSE -> DONE')
if 'searchRan = so.ran' not in P: bad.append('searchRan must come from the real search outcome')
if 'searchRan = true' in P: bad.append('searchRan hard-coded true')
# "nothing found" line
if 'fun noSearchResultsLine(' not in A or 'claimCount > 0 && searchRan && !hasSources' not in A: bad.append('"search found nothing" line missing in limits')
# Brain + NovaService: same path, no new client
for fn in ('fun callRaw(', 'fun callTextWith(', 'fun webGet('):
    if fn not in P or fn not in N.split('fun analysisBrain()')[1].split('private fun msg(')[0] and fn.replace('fun ', 'override fun ') not in N: bad.append('Brain / NovaService missing ' + fn)
if N.count('fun postOnceRaw(') != 1: bad.append('a second raw HTTP call was added')
br = N.split('private fun budgeted(')[1].split('override fun callRaw')[0]
if 'postFallback(json, readMs, deadlineMs)' not in br or 'geminiCoolUntil' not in br: bad.append('search / compose calls skip postFallback or the Gemini cooldown')
if 'WebBrowsers.httpGet(url, ms)' not in N: bad.append('key-less GET not through WebBrowsers.httpGet (allowed hosts only)')
# no new permission, version
if len(re.findall(r'uses-permission android:name="([^"]+)"', MF)) not in (30, 31): bad.append('permission count changed')
if 'versionName = "7.58"' not in G and 'versionName = "7.59"' not in G and 'versionName = "7.61"' not in G and 'versionName = "7.62"' not in G and 'versionName = "7.63"' not in G and 'versionName = "7.64"' not in G and 'versionName = "7.65"' not in G and 'versionName = "7.66"' not in G and 'versionName = "7.67"' not in G and 'versionName = "7.68"' not in G and 'versionName = "7.69"' not in G and 'versionName = "7.70"' not in G and 'versionName = "7.71"' not in G and 'versionName = "7.72"' not in G and 'versionName = "7.73"' not in G and 'versionName = "7.74"' not in G: bad.append('versionName is not 7.58 or later')
# tests exist
if T.count('@Test') < 20: bad.append('AnalysisSearchRulesTest has fewer than 20 tests')
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS search + compose contract (static wiring; Kotlin compile + JUnit NOT RUN until after 13D; real phone NOT TESTED)')
