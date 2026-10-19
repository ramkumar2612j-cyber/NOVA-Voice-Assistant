#!/usr/bin/env python3
"""v7.52 Part 13A static contract: AnalysisRules.kt stays PURE (no Android / org.json), keeps the safety rules of the owner prompt
(no face names, no model URLs, claims bound to real result numbers or 'verify nahi hua', harm / secret / protected refusals,
honest offline report), and the JUnit file covers them. Static only: NOT a Kotlin compile, NOT a phone test."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
J = 'app/src/main/java/com/nova/assistant/'
K = rd(J + 'AnalysisRules.kt'); T = rd('app/src/test/java/com/nova/assistant/AnalysisRulesTest.kt'); A = rd(J + 'AnalysisStage.kt')
MF = rd('app/src/main/AndroidManifest.xml')
def code_only(x):
    x = re.sub(r'/\*.*?\*/', '', x, flags=re.S)
    return re.sub(r'(?m)(?<![:"])//.*$', '', x)
KC = code_only(K); TC = code_only(T)
bad = []
if not K.startswith('package com.nova.assistant'): bad.append('wrong package')
for imp in re.findall(r'^import\s+(\S+)', K, re.M):
    bad.append('AnalysisRules must have no import (pure Kotlin): ' + imp)
for forbidden in ('android.', 'org.json', 'JSONObject', 'JSONArray', 'Context', 'HttpURLConnection', 'Thread', 'SharedPreferences', 'System.currentTimeMillis'):
    if forbidden in KC: bad.append('impure token in AnalysisRules: ' + forbidden)
if 'MiniJson.parse' not in K or 'MiniJson.quote' not in K: bad.append('must use MiniJson (strict allow-list parse + safe quote)')
# functions the later sub-parts rely on (13B vision, 13C search + compose, 13D wiring)
for fn in ('gateQuestion', 'gateSource', 'gateFromSafety', 'refusalText', 'identityClaim', 'visionSystemPrompt', 'visionUserPrompt', 'composeSystemPrompt',
           'composeUserPrompt', 'parseVision', 'mergeVision', 'batchSizes', 'cleanQuery', 'buildQueries', 'validUrl', 'sanitizeResults', 'parseCompose',
           'buildReport', 'confidence', 'limits', 'metadataReport', 'toJson', 'reportText', 'speechSummary', 'stageMessage', 'sizeText', 'durationText', 'sameClaim', 'clean'):
    if 'fun ' + fn + '(' not in K: bad.append('missing function: ' + fn)
# constants from the owner prompt
for need in ('MAX_QUERIES = 4', 'MAX_QUERY_CHARS = 80', 'MAX_SOURCES = 8', 'MAX_CLAIMS = 6', 'VISION_FRAMES_PER_CALL = 6', 'MAX_VISION_CALLS = 2', 'MAX_RAW = 12_000'):
    if need not in K: bad.append('lost constant: ' + need)
for conf in ('"low"', '"medium"', '"high"'):
    if conf not in K: bad.append('confidence level missing ' + conf)
# prompts keep the rules the owner asked for
for need in ('NEVER name or identify a real person', 'Never write a web address', 'ONE JSON object', 'Copy each claim text exactly as given',
             'refer to results by number only'):
    if need not in K: bad.append('prompt lost rule: ' + need)
# no source address can come from the model: compose answers carry NUMBERS only
body = K.split('fun parseCompose(')[1].split('// ----')[0]
if '"url"' in body or 'validUrl' in body: bad.append('parseCompose must not read an address from the model')
if 'n <= nResults' not in body or 'n >= 1' not in body: bad.append('parseCompose does not range-check result numbers')
if 'unver.add(text)' not in body: bad.append('an unbound claim must move to unverified')
vbody = K.split('fun parseVision(')[1].split('/** Two or more')[0]
if 'clean(' not in vbody or 'identityClaim' not in vbody: bad.append('parseVision must clean URLs and drop identity lines')
if 'text_seen' not in vbody: bad.append('text_seen not parsed')
rb = K.split('fun buildReport(')[1].split('fun sizeText')[0]
if 'aligned' not in rb or 'addUn(' not in rb: bad.append('buildReport must keep every claim bound or unverified')
if 'sanitizeResults(results)' not in rb: bad.append('buildReport must re-sanitize results')
# honest offline report: no description invented
mb = K.split('fun metadataReport(')[1].split('fun toJson')[0]
if 'emptyList(), emptyList(), emptyList(), emptyList(), emptyList()' not in mb: bad.append('metadataReport must carry no seen / text / claims / sources')
# stage names are the shared AnalysisStage names
for st in ('INTAKE', 'FRAMES', 'VISION', 'SEARCH', 'COMPOSE', 'DONE', 'FAILED'):
    if 'AnalysisStage.' + st not in K: bad.append('stageMessage missing ' + st)
    if '"%s"' % st not in A: bad.append('AnalysisStage lost ' + st)
# JUnit coverage (names of the tests that prove the rules)
tests = re.findall(r'@Test fun (\w+)', T)
if len(tests) < 40: bad.append('AnalysisRulesTest has only %d tests (>= 40 expected)' % len(tests))
for t in ('gateRefusesFaceIdentity', 'gateRefusesHarm', 'gateRefusesSecrets', 'parseVisionStripsEveryUrl', 'parseVisionDropsIdentityLinesButKeepsWrittenText',
          'parseComposeBindsClaimsToRealNumbers', 'reportMisalignedResultsNeverVerify', 'reportClaimOutOfRangeNumberIsUnverified', 'metadataReportIsHonest',
          'validUrl', 'sanitizeResults', 'confidenceTable', 'toJsonRoundTrip', 'composePromptsNumberTheRealResultsAndNeverShowAddresses'):
    if t not in tests: bad.append('JUnit test missing: ' + t)
if 'org.json' in TC: bad.append('AnalysisRulesTest must not use org.json (sandbox stub fails)')
# 13A adds no permission and no UI / bridge (nothing dead): later sub-parts wire it
perms = re.findall(r'uses-permission android:name="([^"]+)"', MF)
if len(perms) != 30: bad.append('permission count is %d (was 30)' % len(perms))
G = rd('app/build.gradle.kts'); import re as _re; G = G + (' versionName = "7.74" ' if _re.search(r'versionName = "7\.(7[5-9]|[89][0-9])"', G) else '')
if 'versionName = "7.52"' not in G and 'versionName = "7.53"' not in G and 'versionName = "7.54"' not in G and 'versionName = "7.55"' not in G and 'versionName = "7.56"' not in G and 'versionName = "7.57"' not in G and 'versionName = "7.58"' not in G and 'versionName = "7.59"' not in G and 'versionName = "7.61"' not in G and 'versionName = "7.62"' not in G and 'versionName = "7.63"' not in G and 'versionName = "7.64"' not in G and 'versionName = "7.65"' not in G and 'versionName = "7.66"' not in G and 'versionName = "7.67"' not in G and 'versionName = "7.68"' not in G and 'versionName = "7.69"' not in G and 'versionName = "7.70"' not in G and 'versionName = "7.71"' not in G and 'versionName = "7.72"' not in G and 'versionName = "7.73"' not in G and 'versionName = "7.74"' not in G: bad.append('versionName not 7.52 or later 13B-1 (7.53)')
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS analysis rules contract (static; %d JUnit tests written; Kotlin compile + JUnit run NOT done here)' % len(tests))
