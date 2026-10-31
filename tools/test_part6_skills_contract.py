#!/usr/bin/env python3
"""v7.20 Part 6 static guard: every HelpGuide topic has a Skills-screen entry, new entries exist, and every go('id') target exists in ui.html."""
import os,re,sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..')
U=open(os.path.join(R,'app/src/main/assets/ui.html'),encoding='utf-8').read()
H=open(os.path.join(R,'app/src/main/java/com/nova/assistant/HelpGuide.kt'),encoding='utf-8').read()
a=U.index('function skRegistry'); b=U.index('function skRun')
REG=U[a:b]
topics=re.findall(r'Topic\("([a-z]+)"',H)
MAP={'call':'Call karna','whatsapp':'WhatsApp / WhatsApp Business','alarm':'Alarm, timer','phone':'Torch','apps':'App kholna',
'music':'Music control','camera':'Camera kholna','image':'Photo / image banana','chrome':'Website Chrome','download':'App download',
'website':'Website banana','screen':'Screen padhna','scan':'Screen scan (laser)','cameraeye':'Camera Eye (camera se dekhna)','profilesearch':'Profile search (naam / username)','trade':'Trading (MT5 practice','ownerfirst':'AI jawab: pehle aap dekho','unreadmsg':'Naya message aaya?','featuresfix':'Band features set karo','meetingadd':'Meeting schedule banao','memory':'Yaad rakhna','learn':'Naya topic','driving':'Driving mode',
'recording':'Background audio recording','live':'Mausam','questions':'Sawal ka jawab','wabiz':'WhatsApp Business watch',
'autoreply':'Auto reply','connectors':"CONN('github'",'routines':'Routines','lockguard':'Lock guard','teach':'Teach once',
'powerguide':'Background me chalte rehna','gmail':'Gmail','agent':'Agent: doosre app me kaam','screenalert':'Screen alert',
'watchmic':'Watch mic','quiet':'Chup aur Quiet','offlineai':'Phone AI','brainlearn':'Phone AI','leads':'Business leads','limits':'API limits',
'persona':'Jarvis andaaz','callanswer':'Call uthana / kaatna','calendar':'Aaj ka schedule (calendar)','analysis':'Photo / video ANALYSE karna','roundabout':'Ghuma ke bole kaam','featurestatus':'Apne aap test','photoedit':'Photo edit (professional','sitemedia':'Website ke liye apni photo','chathistory':'Meri chats (history','batterysos':'Battery kam: yaad','waitsec':'Bolne ke baad kitna rukna','musicplay':'Gaana chalana (Spotify','media':'Media: Share karke bhejna','frames':'Video se frames nikalna','switches':'Settings ke 3 switch','safemode':'Safe mode (crash ke baad)','reels':'Reels / Instagram (scroll','familynum':'Family numbers (Papa','webjobs':'Koi bhi website par kaam (Website operator)'}
MAP.update({'agentscore':'Kaam ka hisaab (agent score)','screenid':'Ye kaun sa game / video hai','smartroute':'Seedha raasta, seekhe raaste, screen tap','mainstall':'Atakna (hang) ki jaanch','blocked':'Kya band hai (NOVA ko kya rok raha)'})   # v7.146
MAP.update({'aibrain':'GLM 5.3 Flash brain'})   # v7.152: NOVA ka cloud brain (Settings switch LIGHT POPUP bhi Skills me hai: 'Light popup (halka card)')
MAP.update({'wifi':'WiFi Kit (scan, switch, QR'})   # v7.153: WIFI KIT (Skills entry 'WiFi Kit (scan, switch, QR, doctor)')
MAP.update({'jdeep':'JARVIS deep voice (offline neural awaaz)','barge':'Bol ke rokna (barge-in)','afocus':'Audio focus (gaana dheema ho jata hai)','provcheck':'AI provider check (abhi ka haal)','glmall':'GLM 5.3 sab jagah (option)'})   # v7.154: JARVIS UPGRADE (deep voice + barge-in + focus + provider check + glm in Skills)
bad=[]
for t in topics:
    if t not in MAP: bad.append('help topic without mapping: '+t)
    elif t == 'limits': pass   # v7.120: the LIMITS screen is hidden on purpose (owner request)
    elif MAP[t] not in REG: bad.append('help topic %s has no Skills entry (%s)'%(t,MAP[t]))
for t in ['Photo edit (professional','Website ke liye apni photo','Gaana chalana (Spotify','Meri chats (history','Bolne ke baad kitna rukna','Battery kam: yaad']:
    if t not in REG: bad.append('missing new skill: '+t)
for i in set(re.findall(r"go\('([a-z_0-9]+)'\)",REG)):
    if 'id="%s"'%i not in U: bad.append('go() target missing in ui.html: '+i)
if bad:
    [print('FAIL',x) for x in bad]; sys.exit(1)
print('PASS part6 skills contract (%d help topics mapped)'%len(topics))
