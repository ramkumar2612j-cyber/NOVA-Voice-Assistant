#!/usr/bin/env python3
"""v7.30 PART 1 AUTO INTENT static contract: python mirror of AutoIntent.detect (sets read straight from the Kotlin file) + wiring checks.
Evidence: static only, NOT a Kotlin compile."""
import re, os, sys
H = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(H, '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf8').read()
ai = rd('app/src/main/java/com/nova/assistant/AutoIntent.kt')
def sset(n):
    m = re.search(r'private val ' + n + r' = setOf\((.*?)\)\n', ai, re.S); assert m, n
    return set(re.findall(r'"([a-z0-9]+)"', m.group(1)))
BUILD, WISH, EN, DRAW, SITE, AFTW, AFTO, IMG, NOM, COMP, OTHER, INFO, FILL, BLOCK, WV, WN = (sset(n) for n in (
    'BUILD', 'WISH', 'EN_VERBS', 'DRAW', 'SITE_NOUNS', 'SITE_AFTER_WEB', 'SITE_AFTER_ONLINE', 'IMG_NOUNS', 'NO_MEDIA', 'COMPONENT', 'OTHER', 'HARD_INFO', 'FILLER', 'BLOCK', 'WRITE_VERBS', 'WRITE_NOUNS'))
tok = lambda s: re.sub(r'[^a-z0-9]', ' ', s.lower()).split()
def side(t, i, st): return any(j != i and t[j] in st for j in range(max(0, i - 2), min(len(t) - 1, i + 2) + 1))
def site_idx(t):
    o = []
    for i, w in enumerate(t):
        if w in SITE or (i > 0 and t[i-1] == 'web' and w in AFTW) or (i > 0 and t[i-1] == 'online' and w in AFTO) or (i > 0 and t[i-1] == 'home' and w == 'page'): o.append(i)
    return o
def detect(text):
    t = tok(text)
    if len(t) < 2 or len(t) > 60: return 'NONE'
    if any(w in INFO for w in t) or any(w in OTHER for w in t): return 'NONE'
    if any(w in WV for w in t) and any(w in WN for w in t) and not any(w in IMG or w in SITE for w in t): return 'WRITE'
    va = next((i for i, w in enumerate(t) if w in BUILD or w in WISH), -1)
    if va < 0: return 'NONE'
    if 'kya' in t and not any(w in BUILD for w in t): return 'NONE'
    sites = site_idx(t)
    imgs = [i for i, w in enumerate(t) if w in IMG and not side(t, i, NOM) and not (sites and side(t, i, COMP))]
    eng = va <= 2 and t[va] in EN
    nouns = sorted([(i, 'SITE') for i in sites] + [(i, 'IMAGE') for i in imgs])
    if nouns:
        b = [n for n in nouns if n[0] < va]; a = [n for n in nouns if n[0] > va]
        pick = ((a[0] if a else (b[-1] if b else None)) if eng else ((b[-1] if b else (a[0] if a else None))))
        return pick[1] if pick else 'NONE'
    return 'IMAGE' if any(w in DRAW for w in t) else 'NONE'
bad = 0
def chk(n, c):
    global bad; print(('PASS ' if c else 'FAIL ') + n); bad += 0 if c else 1
cases = [
 ('mujhe ek logo chahiye', 'IMAGE'), ('i need a poster for my cafe', 'IMAGE'), ('gym ke liye site chahiye', 'SITE'),
 ('ek billi draw karo', 'IMAGE'), ('online store banwana hai', 'SITE'), ('make a website for my gym', 'SITE'),
 ('create a logo for my website', 'IMAGE'), ('website ke liye logo banao', 'IMAGE'), ('logo wali website banao', 'SITE'),
 ('restaurant ki website photos ke sath banao', 'SITE'), ('bina photo ke website banao', 'SITE'), ('nova mujhe ek wallpaper chahiye', 'IMAGE'),
 ('i want a landing page for my startup', 'SITE'), ('web page banao', 'SITE'), ('portfolio chahiye mujhe', 'SITE'),
 ('anime avatar banao', 'IMAGE'), ('sunset mountains painting bana do', 'IMAGE'), ('thumbnail chahiye youtube ke liye', 'IMAGE'),
 ('website kaise banate hain', 'NONE'), ('what is a logo', 'NONE'), ('how to make a poster', 'NONE'),
 ('photo whatsapp pe bhejo', 'NONE'), ('gallery se photo chuno', 'NONE'), ('camera se photo kheencho', 'NONE'),
 ('website publish karo', 'NONE'), ('photo edit karo', 'NONE'), ('mujhe neend aa rahi hai', 'NONE'), ('nova torch jala do', 'NONE'),
 ('kal subah 7 baje alarm lagao', 'NONE'), ('ek kahani likh do', 'WRITE'), ('write a poem about rain', 'WRITE'), ('mere liye resume likho', 'WRITE'), ('gym ke liye site chahiye jisme code likha ho', 'SITE'), ('website ka code likho', 'NONE'), ('gaana chalao', 'NONE'), ('draw', 'NONE'),
 ('mere liye ek poster banao jisme sher ho', 'IMAGE'), ('home page design banao', 'SITE'), ('i want to build a web app', 'SITE'),
]
for q, exp in cases: chk('%-52s -> %s' % (q, exp), detect(q) == exp)
# wiring
ns = rd('app/src/main/java/com/nova/assistant/NovaService.kt'); ma = rd('app/src/main/java/com/nova/assistant/MainActivity.kt'); ui = rd('app/src/main/assets/ui.html')
chk('NovaService calls AutoIntent.detect after the old ImageIntent route', 'AutoIntent.detect(text' in ns and ns.index('ImageIntent.parse(text)') < ns.index('AutoIntent.detect(text'))
chk('NovaService routes SITE via siteBegin and IMAGE via imageBegin', 'siteBegin(SiteIntent.Parsed(autoReq.subject' in ns and 'imageBegin(ImageIntent.Parsed(autoReq.subject' in ns)
chk('NovaService keeps old routes (SiteIntent.parse, MediaIntent route)', 'SiteIntent.parse(text) ?: siteFollowUp(text)' in ns and 'mediaRoute(text)' in ns)
chk('bridge detectIntent exists', 'fun detectIntent(text: String, hasPhoto: Boolean)' in ma)
chk('ui.html uses Android.detectIntent', 'Android.detectIntent(' in ui)
chk('old IMAGE toggle code kept (add-only: only hidden)', 'id="c_imode"' in ui and 'imode.onclick' in ui)
chk('AutoIntent has no android import', 'import android' not in ai)
chk('PART2: p2_auto script exists and calls editPhoto only for PHOTO_EDIT', '<script id="p2_auto">' in ui and "r.kind!=='PHOTO_EDIT'" in ui and 'Android.editPhoto(t)' in ui)
chk('PART2: WRITE kind exists but NovaService does not route it away', 'WRITE' in ai and 'AutoIntent.Kind.WRITE' not in ns)
chk('PART2: typed + voice text is Roman before routing (VoiceText.normalize in handle)', 'VoiceText.normalize(rawText' in ns and 'HindiRoman.toRoman(raw)' in rd('app/src/main/java/com/nova/assistant/VoiceText.kt'))
chk('PART2: AutoIntentTest has write + photo edit tests', 'textJobsAreWrite' in rd('app/src/test/java/com/nova/assistant/AutoIntentTest.kt') and 'photoAttachedEditIsPhotoEdit' in rd('app/src/test/java/com/nova/assistant/AutoIntentTest.kt'))
sys.exit(1 if bad else 0)
