#!/usr/bin/env python3
"""v6.99 Part 3 download / install speech spellings: static contract. Run from the project root."""
import os,sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..')
J=os.path.join(R,'app/src/main/java/com/nova/assistant/')
O=open(J+'OpenWebRules.kt',encoding='utf-8').read()
bad=[]
def need(c,m):
    if not c: bad.append(m)
i=O.find('private val DL_SPELLINGS'); b=O.find('private val BLOCK')
need(i>0 and b>0 and i<b,'DL_SPELLINGS must be declared BEFORE BLOCK (Kotlin object init order, otherwise NPE)')
blk=O[i:O.find(')',i)]
for w in ('daunlod','instol','dawnload','downlod','download','install'): need('"%s"'%w in blk,'spelling missing: '+w)
need('.plus(DL_SPELLINGS)' in O,'BLOCK must include DL_SPELLINGS')
need('private val DL_VERBS = DL_SPELLINGS' in O,'DL_VERBS must reuse DL_SPELLINGS')
need('bdown +lo+a?d' in O,'two-word "down load" fix missing in norm')
need('"daunlod"' in open(J+'HelpGuide.kt',encoding='utf-8').read(),'HelpGuide download keys missing misspellings')
need('downloadSpeechSpellingsAreUnderstood' in open(os.path.join(R,'app/src/test/java/com/nova/assistant/OpenWebRulesTest.kt'),encoding='utf-8').read(),'new JUnit tests missing')
if bad:
    for m in bad: print('FAIL',m)
    sys.exit(1)
print('PASS download spellings contract: Roman + Devanagari + two-word forms covered')
