#!/usr/bin/env python3
"""Python MIRROR of NOVA's local command pipeline: Understand.classify -> Logic.classify and the rule objects it calls
(UnsupportedRules, PersonalMemory, ScheduledTaskRules, LeadRules, OfflineQueueRules, Timers, WaBiz, MessageMode, Driving, Control).

WHAT THIS IS: a hand port, used where no Kotlin compiler exists (see docs/TEST_REPORT_PART5.md). Regexes and word lists of
PersonalMemory / UnsupportedRules are EXTRACTED from the Kotlin source text, so they cannot drift; the control flow of the rest is
ported by hand. WHAT THIS IS NOT: a run of the Kotlin code. A PASS here means "the ported logic agrees with the corpus", and the
real proof is app/src/test/.../CommandCorpusTest.kt run by Gradle / GitHub Actions.
"""
import os, re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "nova", "assistant")
def kt(name): return open(os.path.join(SRC, name), encoding="utf8").read()

# ---------------------------------------------------------------- extracted from Kotlin
PM = kt("PersonalMemory.kt")
def rx(name):
    m = re.search(r'private val %s = Regex\("""(.*)"""\)' % name, PM)
    assert m, name
    return re.compile(m.group(1))
LEAD, FORGET_ALL, LIST_RE = rx("LEAD_RE"), rx("FORGET_ALL_RE"), rx("LIST_RE")
FORGET, FORGET_POST, SAVE, SAVE_POST, ARG_LEAD = rx("FORGET_RE"), rx("FORGET_POST_RE"), rx("SAVE_RE"), rx("SAVE_POST_RE"), rx("ARG_LEAD_RE")
PRONOUNS = set(re.findall(r'"([^"]+)"', re.search(r'private val PRONOUNS = setOf\((.*?)\)\n', PM, re.S).group(1)))
MIN_FACT = int(re.search(r"const val MIN_FACT_CHARS = (\d+)", PM).group(1))

UR = kt("UnsupportedRules.kt")
def kset(src, name):
    m = re.search(r'private val %s = setOf\((.*?)\n    \)' % name, src, re.S)
    assert m, name
    return set(re.findall(r'"([^"]+)"', m.group(1)))
POWER, RESET = kset(UR, "POWER_PHRASES"), kset(UR, "RESET_PHRASES")
CANCEL_ALARM, CANCEL_TIMER = kset(UR, "CANCEL_ALARM_PHRASES"), kset(UR, "CANCEL_TIMER_PHRASES")

LG = kt("Logic.kt")
def lset(name):
    m = re.search(r'private val %s = setOf\((.*?)\)\n' % name, LG, re.S)
    assert m, name
    return set(re.findall(r'"([^"]+)"', m.group(1)))
LFILLER, NAVFILL = lset("FILLER"), lset("NAVFILL")
NEGATION = lset("NEGATION")
OPEN_DONE = re.search(r'private val OPEN_DONE_WORDS = "([^"]+)"', LG).group(1)
# the tail regex is rebuilt from the Kotlin pieces (same shape as OPEN_TAIL_RE in Logic.kt)
OPEN_TAIL = re.compile(r"^(.+?)\s+(?:(?:kholo|khol)(?:\s+(?:" + OPEN_DONE + r"))?|(?:open|launch)\s+(?:" + OPEN_DONE + r"))$")
assert 'OPEN_TAIL_RE = Regex("^(.+?)\\\\s+(?:(?:kholo|khol)' in LG

UN = kt("Understand.kt")

# ---------------------------------------------------------------- Logic helpers
def norm(text):
    s = text.lower().replace("[unk]", " ").replace("-", " ")
    s = re.sub(r"[^a-z0-9% ]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s.replace("wi fi", "wifi").replace("flash light", "flashlight").replace("blue tooth", "bluetooth").replace("you tube", "youtube")

WAKE_RE = re.compile(r"^(?:(?:the|hey|a)\s+)?(?:nova|nova)\b\s*")
def strip_wake(s):
    x = s.strip()
    while True:
        y = WAKE_RE.sub("", x, count=1).strip()
        if y == x: break
        x = y
    return x

ONES = {"zero":0,"one":1,"two":2,"three":3,"four":4,"five":5,"six":6,"seven":7,"eight":8,"nine":9,"ten":10,"eleven":11,"twelve":12,
        "thirteen":13,"fourteen":14,"fifteen":15,"sixteen":16,"seventeen":17,"eighteen":18,"nineteen":19}
TENS = {"twenty":20,"thirty":30,"forty":40,"fifty":50,"sixty":60,"seventy":70,"eighty":80,"ninety":90}
BIG = {"thousand":1e3,"lakh":1e5,"lac":1e5,"million":1e6,"crore":1e7,"billion":1e9}
DIGITS = re.compile(r"\d+(\.\d+)?")

def skills_parse_num(t):  # LocalSkills.parseNum
    if not t: return None
    total = cur = 0.0; seen = False; i = 0
    while i < len(t):
        w = t[i]
        if w == "and": pass
        elif w == "point":
            frac = ""
            for d in t[i+1:]:
                if d in ONES and 0 <= ONES[d] <= 9: frac += str(ONES[d])
                elif len(d) == 1 and d.isdigit(): frac += d
                else: return None
            if not frac: return None
            return total + cur + float("0." + frac)
        elif DIGITS.fullmatch(w): cur += float(w); seen = True
        elif w in ONES: cur += ONES[w]; seen = True
        elif w in TENS: cur += TENS[w]; seen = True
        elif w == "hundred": cur = (1.0 if cur == 0 else cur) * 100; seen = True
        elif w in BIG: total += (1.0 if cur == 0 else cur) * BIG[w]; cur = 0.0; seen = True
        else: return None
        i += 1
    return total + cur if seen else None

def logic_parse_number(tokens):  # Logic.parseNumber
    cur = 0; seen = False
    for t in tokens:
        tt = t[:-1] if t.endswith("%") else t
        if tt.isdigit() and re.fullmatch(r"-?\d+", tt): return int(tt)
        if t in ONES: cur += ONES[t]; seen = True
        elif t in TENS: cur += TENS[t]; seen = True
        elif t == "hundred": cur = (1 if cur == 0 else cur) * 100; seen = True
    return cur if seen else None

def settings_page(name):
    return {"wifi":"wifi","wifi settings":"wifi","bluetooth":"bluetooth","bluetooth settings":"bluetooth","settings":"main","setting":"main",
            "phone settings":"main","display settings":"display","sound settings":"sound","location settings":"location",
            "battery settings":"battery","airplane mode":"airplane","airplane settings":"airplane","flight mode":"airplane",
            "app settings":"apps","apps settings":"apps","do not disturb":"dnd","dnd":"dnd"}.get(name)

# ---------------------------------------------------------------- Timers
UNIT_SEC = {"second":1,"seconds":1,"sec":1,"secs":1,"sekand":1,"minute":60,"minutes":60,"min":60,"mins":60,
            "hour":3600,"hours":3600,"ghanta":3600,"ghante":3600,"ghanta.":3600}
HIN = {"ek":1,"do":2,"teen":3,"char":4,"paanch":5,"panch":5,"chhe":6,"che":6,"saat":7,"aath":8,"nau":9,"das":10,"pandrah":15,"bees":20,"tees":30,"chalis":40,"pachas":50}
NUMW = {"zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen",
        "seventeen","eighteen","nineteen","twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety","hundred","and"}
T_FILLER = {"nova","hey","please","zara","set","a","an","the","for","me","my","in","after","of","to","ka","ki","ke","ko","laga","lagao","lagana","do","dena",
            "karo","kar","start","timer","reminder","remind","yaad","dilao","dilana","baad","later","mujhe","that","about","from","now","tell","bata","dena"}
def is_num_tok(tok, nxt):
    return tok in NUMW or tok.isdigit() or (tok in HIN and (tok != "do" or (nxt is not None and (nxt in UNIT_SEC or nxt == "baje"))))
def num_val(toks):
    mapped = [(("2" if (x == "do" and len(toks) == 1) else str(HIN[x])) if x in HIN else x) for x in toks]
    return skills_parse_num(mapped)
def at(t, i): return t[i] if 0 <= i < len(t) else None
def duration_seconds(t):
    total = 0; found = False; i = 0
    while i < len(t):
        w = t[i]
        if w in ("half","aadha","adha") and i + 1 < len(t):
            u = t[i+2] if (t[i+1] == "an" and i + 2 < len(t)) else t[i+1]
            if u in ("hour","ghanta"): total += 1800; found = True; i += 2; continue
        if w == "dedh" and i + 1 < len(t) and t[i+1] == "ghanta": total += 5400; found = True; i += 2; continue
        j = i
        while j < len(t) and is_num_tok(t[j], at(t, j+1)) and t[j] != "and": j += 1
        if j > i and j < len(t) and t[j] in UNIT_SEC:
            v = num_val(t[i:j])
            if v is None or v != int(v) or v > 100000: return None
            total += int(v) * UNIT_SEC.get(t[j], 1); found = True; i = j + 1; continue
        i += 1
    return total if (found and 1 <= total <= 86400) else None
def t_label(t):
    out = []; i = 0
    while i < len(t):
        w = t[i]
        if w in T_FILLER: i += 1; continue
        j = i
        while j < len(t) and is_num_tok(t[j], at(t, j+1)): j += 1
        if j > i and j < len(t) and t[j] in UNIT_SEC: i = j + 1; continue
        if w in UNIT_SEC or w in ("half","aadha","adha","dedh"): i += 1; continue
        out.append(w); i += 1
    return " ".join(out)[:40]
def group(t, i):
    w = at(t, i)
    if w is None: return None
    if w.isdigit(): return (int(w), 1)
    tens = {"twenty":20,"thirty":30,"forty":40,"fifty":50}
    ones = {"one":1,"two":2,"three":3,"four":4,"five":5,"six":6,"seven":7,"eight":8,"nine":9}
    if w in tens:
        o = ones.get(at(t, i+1))
        return (tens[w] + o, 2) if o is not None else (tens[w], 1)
    if w == "do" and at(t, i+1) != "baje": return None
    v = skills_parse_num([w])
    if v is None and w in HIN: v = float(HIN[w])
    if v is None: return None
    return (int(v), 1)
AM = {"am","morning","subah","savere","savera"}
PMS = {"pm","afternoon","evening","shaam","raat","night","dopahar","sham"}
def alarm(t):
    toks = []; k = 0
    while k < len(t):
        if t[k] in ("a","p") and at(t, k+1) == "m": toks.append(t[k] + "m"); k += 2
        else: toks.append(t[k]); k += 1
    is_am = any(x in AM for x in toks); is_pm = any(x in PMS for x in toks)
    i = 0
    while i < len(toks) and group(toks, i) is None: i += 1
    if i >= len(toks): return None
    h, used = group(toks, i)
    m = 0; p = i + used
    if at(toks, p) in ("oh","o","zero"): p += 1
    if toks[i].isdigit() and 3 <= len(toks[i]) <= 4:
        m = h % 100; h //= 100; p = i + 1
    else:
        g2 = group(toks, p)
        if g2 is not None and at(toks, p) != "baje":
            m = g2[0]; p += g2[1]
    if not (0 <= h <= 24) or not (0 <= m <= 59): return None
    if h == 24: h = 0
    skip = {"alarm","baje","wake","up","at","am","pm","oh","jagana","jaga","uthana","uthao","alarm.","morning","evening"}
    kept = [w for idx, w in enumerate(toks) if idx < i or idx >= p]
    lbl = t_label([w for w in kept if w not in AM and w not in PMS and w not in skip])
    if h > 12 or h == 0: return ("alarm", lbl, h * 60 + m)
    if is_am: return ("alarm", lbl, (0 if h == 12 else h) * 60 + m)
    if is_pm: return ("alarm", lbl, (12 if h == 12 else h + 12) * 60 + m)
    return ("alarm_any", lbl, (h % 12) * 60 + m)
def timers_parse(t):
    if not t or len(t) > 14: return None
    s = set(t)
    if (s & {"cancel","delete","remove","hatao","hata","off","band","stop","disable"}) and (s & {"alarm","timer"}): return None
    dur = duration_seconds(t)
    remind = bool(s & {"remind","reminder","yaad"})
    timer = "timer" in s or "countdown" in s
    if dur is not None and (timer or remind): return ("timer", t_label(t), dur)
    if dur is not None and "alarm" in s and not timer: return ("timer", t_label(t), dur)
    if "alarm" in s or (("wake" in s or "jagana" in s or "jagao" in s) and "at" in s): return alarm(t)
    return None

# ---------------------------------------------------------------- other command objects
HINDI_NUM = {"ek":1,"do":2,"teen":3,"char":4,"chaar":4,"paanch":5,"panch":5,"chhe":6,"che":6,"saat":7,"aath":8,"nau":9,"das":10,"gyarah":11,"barah":12,
             "tera":13,"terah":13,"chaudah":14,"pandrah":15,"solah":16,"satrah":17,"atharah":18,"unnis":19,"bees":20,"ikkees":21,"baees":22,"teis":23,
             "chaubees":24,"tees":30,"paintis":35,"chalis":40,"paitalis":45,"pachas":50}
NOTE_MARK = {"kyunki","kyuki","kyonki","isliye","because"}
IMPORTANT = {"zaruri","jaruri","zaroori","jaroori","important"}
VERY = {"bahut","bohot","bahot","very","urgent","bahutzaruri"}
SCH_WAKE_OFF = {"alarm band","alarm band karo","alarm bandh karo","alarm off","alarm stop","alarm stop karo","subah ka alarm band","main uth gaya","main uth gayi","uth gaya","uth gayi","i am awake","im awake"}
SCH_WAKE_SNOOZE = {"dismiss","alarm dismiss","snooze","alarm snooze","ek minute baad","ek minute baad yaad dilao","thodi der baad","baad me yaad dilana","baad mein yaad dilana"}
SCH_NOTE_WORDS = {"note","likh","likho","likhna","remind","yaad","dilao","dilana","reminder","schedule","scheduled","plan"}
DAY_BITS = {"somvar":1,"somvaar":1,"sombar":1,"monday":1,"mangalvar":2,"mangalvaar":2,"mangal":2,"tuesday":2,"budhvar":4,"budhvaar":4,"budh":4,"wednesday":4,
            "guruvar":8,"guruvaar":8,"brihaspativar":8,"veervar":8,"thursday":8,"shukravar":16,"shukrawar":16,"shukravaar":16,"friday":16,
            "shanivar":32,"shanivaar":32,"shaniwar":32,"saturday":32,"ravivar":64,"ravivaar":64,"itwar":64,"itvaar":64,"sunday":64}
def days_of(t):
    m = 0
    for w in t: m |= DAY_BITS.get(w, 0)
    if "weekdays" in t: m |= 31
    if "weekend" in t or "weekends" in t: m |= 96
    return m
def schedule_parse(raw):
    """ScheduledTaskRules.parse: only the decision 'is this a schedule' matters for the corpus (time of day is not asserted)."""
    n = norm(raw); t0 = [x for x in n.split(" ") if x.strip()]
    ni = next((i for i, x in enumerate(t0) if x in NOTE_MARK), -1)
    t = t0[:ni] if ni >= 0 else t0
    if len(t) < 3 or len(t) > 40 or "baje" not in t: return None
    ti = t.index("baje")
    if ti == 0: return None
    htok = t[ti-1]
    ph = int(htok) if htok.isdigit() else HINDI_NUM.get(htok)
    if ph is None: return None
    minute = 0
    mt = at(t, ti+1)
    if mt is not None and mt not in {"weather","mausam","bolo","bhejna","bhejo","message","yaad","dilana","dilao"}:
        mm = int(mt) if mt.isdigit() else HINDI_NUM.get(mt)
        if mm is not None and 0 <= mm <= 59: minute = mm
    if not (0 <= ph <= 24) or not (0 <= minute <= 59): return None
    days = days_of(t)
    day_repeat = days != 0 and (("har" in t or "every" in t or "roz" in t or "weekdays" in t or "weekend" in t or "weekends" in t) or bin(days).count("1") > 1)
    is_repeat = "roz" in t or "daily" in t or ("every" in t and "day" in t) or ("har" in t and "din" in t) or day_repeat
    intent = is_repeat or "schedule" in t or "scheduled" in t or (days != 0 and any(x in SCH_NOTE_WORDS for x in t)) or \
        (any(x in {"weather","mausam","message","messages","remind","yaad","bhej","bhejna","send"} for x in t) and
         any(x in {"bolo","batao","remind","yaad","dilao","dilana","bhej","bhejna","send","schedule","scheduled"} for x in t))
    if not intent: return None
    return True
def schedule_command(raw):
    n = norm(raw)
    if n in {"meri schedule list","schedule list","schedule dikhao","meri schedule dikhao","my schedule","list schedule"}: return ("schedule_list",)
    if n in {"schedule hatao","schedule cancel","schedule saaf karo","cancel schedule","remove schedule","schedules hatao"}: return ("schedule_cancel_all",)
    if n.startswith("schedule hatao ") or n.startswith("cancel schedule "): return ("schedule_cancel",)
    if n in SCH_WAKE_OFF: return ("wake_off",)
    if n in SCH_WAKE_SNOOZE: return ("wake_snooze",)
    tk = [x for x in n.split(" ") if x.strip()]
    if len(tk) >= 3 and tk[0] == "schedule":
        rest = tk[1:]
        if rest[0] == "note" and len(rest) >= 3: return ("schedule_set",)
        if len(rest) >= 3 and rest[0] in VERY and rest[1] in IMPORTANT: return ("schedule_set",)
        if rest[0] in IMPORTANT and len(rest) > 1: return ("schedule_set",)
        if rest[0] == "normal" and len(rest) > 1: return ("schedule_set",)
    return ("schedule_create",) if schedule_parse(n) else None
def lead_command(raw):
    n = norm(raw); n = n[5:].strip() if n.startswith("nova ") else n
    if n in {"lead capture","lead save","save lead","save this lead","is business ko lead mein save karo","is business ko lead me save karo","ye business save karo","lead jodo","business save karo","is page ko lead mein save karo","is page ko lead me save karo"}: return ("lead_capture",)
    if n in {"lead list","leads list","leads dikhao","meri leads","lead report","lead drafts dikhao","drafts dikhao","lead history"}: return ("lead_list",)
    if n in {"lead draft","lead drafts","lead draft banao","leads ke draft banao","lead draft hindi","lead draft english","lead draft angrezi"}: return ("lead_draft",)
    if n in {"lead bhejo","leads bhejo","lead send","send leads","approved leads send karo"}: return ("lead_send",)
    if n in {"lead clear","leads clear","leads saaf karo","lead list saaf karo"}: return ("lead_clear",)
    if re.fullmatch(r"(?:lead limit|leads limit) [0-9]{1,2}", n): return ("lead_limit",)
    return None
def queue_command(raw):
    n = norm(raw); n = n[5:].strip() if n.startswith("nova ") else n
    if n in {"queue dikhao","queue list","show queue","meri queue","queue status"}: return ("queue_list",)
    if n in {"queue saaf karo","queue clear","clear queue","queue hatao"}: return ("queue_clear",)
    return None
def wabiz_command(n):
    s = n.lower().strip()
    m = re.search(r"^(?:business |whatsapp )?catalogue (.+?) (?:matlab|jawab|answer) (.+)$", s)
    if m: return ("biz_catalogue",)
    if not ("business watch" in s or "chat watch" in s or "whatsapp watch" in s): return None
    if re.search(r"\b(off|band|bandh|stop|khatam)\b", s): return ("biz_off",)
    if re.search(r"\bauto\b.*\b(on|chalu|start)\b", s): return ("biz_auto_on",)
    if re.search(r"\bauto\b.*\b(off|band|bandh|stop)\b", s): return ("biz_auto_off",)
    if re.search(r"\b(summary|report|kya hua)\b", s): return ("biz_summary",)
    return ("biz_on",)
QUICK = [("driving",["driving","drive","gaadi"]),("busy",["busy","vyast"]),("ok",["ok","okay","theek","thik"]),("thanks",["thanks","thank","shukriya","dhanyavad"]),("later",["later","baad"])]
def quick_id(words):
    for w in words:
        for i, ws in QUICK:
            if w in ws: return i
    return None
READ_PH = {"read messages","read message","read new messages","messages padho","message padho","new messages","read it"}
CLEAR_PH = {"clear messages","clear message","messages hatao"}
DICT_PH = {"reply likho","reply write","jawab likho","write reply","custom reply","reply dictate","dictate reply"}
D_OFF = {"off","stop","band","bandh","disable","end"}
def driving_command(n, core):
    t = n.split(" ")
    if ("driving" in t or "drive" in t) and "mode" in t and len(t) <= 6: return ("drive_off" if any(x in D_OFF for x in t) else "drive_on",)
    if core in READ_PH: return ("drive_read",)
    if core in CLEAR_PH: return ("drive_clear",)
    if core in DICT_PH: return ("drive_dictate",)
    m = re.search(r"^reply (.+)$", core)
    if m and quick_id(m.group(1).split(" ")): return ("drive_reply",)
    return None
def message_command(n, core):
    words = [x for x in n.split(" ") if x.strip()]
    if "message" in words and "mode" in words and len(words) <= 6:
        return ("message_off" if any(x in {"off","stop","band","bandh","disable"} for x in words) else "message_on",)
    m = re.search(r"^(?:message|messages) apps? (.+) (on|off)$", n)
    if m and m.group(1).strip() in {"whatsapp","telegram","sms","messages","google messages","signal","messenger","facebook messenger","instagram","x","twitter","linkedin","linked in","whatsapp business"}:
        return ("message_app",)
    if core in {"messages padho","message padho","read messages","read message","read new messages","new messages"}: return ("message_read",)
    if core in {"reply delete karo","last reply delete karo","reply delete","delete last reply","jawab delete karo","reply hatao","message delete karo","bheja hua message delete karo","delete sent reply"}: return ("message_delete",)
    if core in {"messages hatao","clear messages","clear message","message clear karo"}: return ("message_clear",)
    if core in {"reply likho","reply write","jawab likho","write reply","custom reply","reply dictate","dictate reply"}: return ("message_dictate",)
    m = re.search(r"^reply (.+)$", core)
    if m and quick_id(m.group(1).split(" ")): return ("message_reply",)
    return None
def control_direction(words):
    for w in words:
        if w in ("down","neeche","niche","nichhe"): return "down"
        if w in ("up","upar","oopar","uper"): return "up"
        if w in ("left","baayein","bayen"): return "left"
        if w in ("right","daayein","dayen"): return "right"
    return None
def pm_clean(t): return ARG_LEAD.sub("", t.strip()).strip()
def pm_parse(n):
    s = n.strip()
    if not s or len(s) > 150: return None
    s = LEAD.sub("", s).strip()
    if not s: return None
    if FORGET_ALL.fullmatch(s): return ("mem_forget_all",)
    if LIST_RE.fullmatch(s): return ("mem_list",)
    f1 = FORGET.search(s)
    if f1 and pm_clean(f1.group(1)): return ("mem_forget",)
    f2 = FORGET_POST.search(s)
    if f2:
        t = pm_clean(f2.group(1))
        if t and t not in PRONOUNS: return ("mem_forget",)
    a1 = SAVE.search(s)
    if a1 and len(pm_clean(a1.group(1))) >= MIN_FACT: return ("mem_save",)
    a2 = SAVE_POST.search(s)
    if a2 and len(pm_clean(a2.group(1))) >= MIN_FACT: return ("mem_save",)
    return None

# ---------------------------------------------------------------- Logic.classify
def toggled_page(t):  # UnsupportedRules.toggledSettingsPage (port; keep in step with the Kotlin file)
    if len(t) > 6: return None
    s = set(t)
    if not (s & {"on","off","chalu","band","bandh","chalao","start","stop","enable","disable","activate"}): return None
    if "hotspot" in s or ("hot" in s and "spot" in s) or ("mobile" in s and "data" in s) or ("cellular" in s and "data" in s): return "network"
    if (("airplane" in s or "aeroplane" in s or "flight" in s) and "mode" in s): return "airplane"
    if "location" in s or "gps" in s: return "location"
    if "dnd" in s or ("disturb" in s and "not" in s and "do" in s): return "dnd"
    return None

def logic_classify(text):
    n = strip_wake(norm(text))
    if not n or len(n) > 120: return None
    t = n.split(" "); s = set(t); size = len(t)
    def has(*w): return any(x in s for x in w)
    core = " ".join(x for x in t if x not in LFILLER)
    nav = " ".join(x for x in t if x not in LFILLER and x not in NAVFILL)
    wants_settings = has("settings", "setting")
    negated = any(x in NEGATION for x in t)
    if core in {"stop","cancel","chup","quiet","ruko","be quiet","shut up"}: return ("stop", "", 0)
    if core in {"unlock","unlock phone","phone unlock","unlock karo","phone unlock karo","unlock the phone","unlock screen"}: return ("unlock", "", 0)
    if core in POWER: return ("unsupported", "power", 0)
    if core in RESET: return ("unsupported", "reset", 0)
    if core in CANCEL_ALARM: return ("unsupported", "cancel_alarm", 0)
    if core in CANCEL_TIMER: return ("unsupported", "cancel_timer", 0)
    if core in {"check updates","check update","any updates","any update","update check","updates","kuch naya hai","kuch naya"}: return ("update_check", "", 0)
    if core in {"aaj ka schedule","aaj ki meetings","today schedule","my calendar today","meri meetings","upcoming meetings","aaj ke events"}: return ("calendar_today", "", 0)
    if core in {"quiet mode on","silent replies on","voice replies off"}: return ("quiet_on", "", 0)
    if core in {"quiet mode off","silent replies off","voice replies on"}: return ("quiet_off", "", 0)
    r = pm_parse(n)
    if r: return (r[0], "", 0)
    for f in (schedule_command, lead_command, queue_command):
        r = f(n)
        if r: return (r[0], "", 0)
    call_target = None
    m = re.search(r"^(?:please )?(?:call|phone) (.+?)(?: please)?$", n)
    if m: call_target = m.group(1)
    else:
        m = re.search(r"^(.+?) ko (?:call|phone)(?: (?:karo|kar do|lagao|laga do))?$", n)
        if m: call_target = m.group(1)
        else:
            m = re.search(r"^(.+?) (?:call|phone) (?:karo|kar do|lagao|laga do)$", n)
            if m: call_target = m.group(1)
    if call_target is not None:
        who = call_target.strip()
        if who and len(who) <= 60 and len(who.split(" ")) <= 6 and not any(x in {"call","phone","karo","lagao","kholo","khol","open","launch","website","app"} for x in who.split(" ")):
            return ("call", who, 0)
    r = timers_parse(t)
    if r: return (r[0], r[1], r[2])
    r = wabiz_command(n)
    if r: return (r[0], "", 0)
    r = message_command(n, core)
    if r: return (r[0], "", 0)
    r = driving_command(n, core)
    if r: return (r[0], "", 0)
    m = re.search(r"^(?:(?:please|nova|hey)\s+)*(?:type|likho|write)\s+(.+)$", n)
    if m:
        txt = "".join(c for c in m.group(1) if ord(c) >= 32 or c == " ").strip()[:500]
        if txt: return ("type", txt, 0)
    if has("scroll", "swipe"):
        d = control_direction(t)
        if d is not None and size <= 5: return ("scroll", d, 0)
    m = re.search(r"^(?:tap|click|press|dabao|dabana)(?:\s+on)?\s+(.+)$", core)
    if m:
        label = re.sub(r" button$", "", m.group(1)).strip()
        if label and len(label) <= 40 and len(label.split(" ")) <= 5: return ("tap", label, 0)
    m = re.search(r"^(.+?)\s+(?:dabao|dabana)$", core)
    if m:
        label = re.sub(r" button$", "", m.group(1)).strip()
        if label and len(label) <= 40 and len(label.split(" ")) <= 5: return ("tap", label, 0)
    if has("monitor", "monitoring"):
        if has("off","stop","band","bandh","disable"): return ("monitor_off", "", 0)
        if size <= 4: return ("monitor_on", "", 0)
        return None
    if has("screen") and not has("brightness","brightnes","timeout","lock","rotate","rotation","record","recording","shot","off","band") and \
       (has("analyze","analyse","analysis","dekho","check","read","summarize","summary","dekh","dekhna","dikh","dikha","dikhao","dikhta","padh","padho","padhna","batao","bata","kya","likha","describe","tell","see") or (has("what") and has("on"))):
        return ("analyze", "", 0)
    if not wants_settings and size <= 6 and has("battery","charging","charge"): return ("battery", "", 0)
    time_block = has("timer","alarm","zone","set","remind","reminder","countdown")
    if not wants_settings and size <= 5 and not time_block and (has("time","samay") or "kitne baje" in n): return ("time", "", 0)
    if not wants_settings and size <= 5 and has("date","tarikh","tareekh"): return ("date", "", 0)
    if has("torch","flashlight","flashlite"):
        if negated: return None
        if has("off","band","bandh","stop"): return ("torch_off", "", 0)
        if has("on","chalu","chalao","start"): return ("torch_on", "", 0)
        return None
    if has("volume","awaaz","awaz"):
        if negated: return None
        if has("mute","silent"): return ("volume", "mute", 0)
        if has("max","maximum","full"): return ("volume", "max", 0)
        if has("down","lower","decrease","kam","reduce","low"): return ("volume", "down", 0)
        if has("up","raise","increase","badha","badhao","higher","high"): return ("volume", "up", 0)
        num = logic_parse_number(t)
        if num is not None: return ("volume", "set", max(0, min(100, num)))
        return None
    if has("brightness","brightnes"):
        if negated: return None
        num = logic_parse_number(t)
        if num is not None: return ("brightness_set", "", max(0, min(100, num)))
        if has("max","maximum","full"): return ("brightness_set", "", 100)
        if has("min","minimum","lowest"): return ("brightness_set", "", 5)
        if has("up","increase","higher","badha","badhao","more","brighter"): return ("brightness_step", "", 10)
        if has("down","decrease","lower","kam","less","reduce","dim"): return ("brightness_step", "", -10)
        return None
    media_words = {"pause","play","resume","continue","music","media","song","track","video","playback","next","previous","last","agla","pichla","gana","it","stop"}
    if not negated and size <= 4 and all((x in media_words or x in LFILLER) for x in t):
        if has("pause"): return ("media", "pause", 0)
        if has("stop") and has("music","media","song","playback"): return ("media", "pause", 0)
        if has("next","agla"): return ("media", "next", 0)
        if has("previous","pichla") or (has("last") and has("song","track","gana")): return ("media", "previous", 0)
        if has("resume","continue") or (has("play") and has("music","media","song","playback")): return ("media", "play", 0)
    g = {"home":"home","home screen":"home","back":"back","recents":"recents","recent":"recents","recent apps":"recents","recent app":"recents",
         "app switcher":"recents","switch apps":"recents","notifications":"notifications","notification":"notifications","notification panel":"notifications",
         "notification shade":"notifications","quick settings":"quick_settings","quick panel":"quick_settings","quick settings panel":"quick_settings",
         "lock":"lock","lock phone":"lock","lock screen":"lock","phone lock":"lock","screen lock":"lock"}
    if not negated and nav in g: return ("global", g[nav], 0)
    if size <= 3 and has("wifi","bluetooth") and has("on","off","chalu","band","bandh"):
        return ("settings", "wifi" if has("wifi") else "bluetooth", 2)
    pg = toggled_page(t)
    if pg: return ("settings", pg, 2)
    name0 = None
    m = re.search(r"^(?:open|launch|start|kholo|khol)\s+(.+)$", core)
    if m: name0 = m.group(1)
    else:
        m = OPEN_TAIL.search(core)
        if m: name0 = re.sub(r" ko$", "", m.group(1))
        elif settings_page(core) is not None or core == "camera": name0 = core
    if name0 is not None:
        name = re.sub(r" app$", "", name0).strip()
        if not name or len(name) > 30 or len(name.split(" ")) > 4: return None
        if name == "camera": return ("camera", "", 0)
        pgx = settings_page(name)
        if pgx: return ("settings", pgx, 0)
        return ("open_app", name, 0)
    return None

# ---------------------------------------------------------------- Understand
def _set(name, src=UN):
    m = re.search(r'private val %s = setOf\((.*?)\)\n' % name, src, re.S)
    assert m, name
    return set(re.findall(r'"([^"]+)"', m.group(1)))
U_NEGATE, U_FILL = _set("NEGATE"), _set("FILL")
LOCK_W, VOL_W, BRIGHT_W, TORCH_W = _set("LOCK_WORDS"), _set("VOLUME_WORDS"), _set("BRIGHT_WORDS"), _set("TORCH_WORDS")
ON_W, OFF_W, UP_W, DOWN_W = _set("ON_WORDS"), _set("OFF_WORDS"), _set("UP_WORDS"), _set("DOWN_WORDS")
MAXW, MINW, OPEN_W, MEDIA_N, PAUSE_W, PLAY_W = _set("MAX_WORDS_SET"), _set("MIN_WORDS_SET"), _set("OPEN_WORDS"), _set("MEDIA_NOUNS"), _set("PAUSE_WORDS"), _set("PLAY_WORDS")
CAM_OPT = _set("CAMERA_OPTION_WORDS")

def percent(w):
    d = w[:-1] if w.endswith("%") else w
    if not d or len(d) > 3 or not d.isdigit(): return None
    v = int(d)
    return v if 0 <= v <= 100 else None
class Intent:
    def __init__(self, canon, any_, need=(), allow=(), numeric=False):
        self.canon, self.any, self.need, self.allow, self.numeric = canon, set(any_), set(need), set(allow), numeric
        self.all = self.any | self.need | self.allow
    def match(self, rest):
        has_any = False; has_need = not self.need; num = None
        for w in rest:
            d = percent(w)
            if self.numeric and d is not None:
                if num is not None: return None
                num = d; continue
            if w not in self.all: return None
            if w in self.any: has_any = True
            if w in self.need: has_need = True
        if not has_any or not has_need: return None
        if self.numeric:
            if num is None: return None
            return self.canon.replace("{n}", str(num))
        return self.canon
INTENTS = [
    Intent("lock phone", LOCK_W, allow={"screen","display","device"}),
    Intent("home", {"home"}, allow=OPEN_W | {"screen","page"}),
    Intent("back", {"back","wapas","peeche","pichhe","pichche"}, allow=OPEN_W | {"screen","page"}),
    Intent("recents", {"recents","recent"}, allow=OPEN_W | {"apps","app","list"}),
    Intent("notifications", {"notification","notifications","notif","notifs"}, allow=OPEN_W | {"panel","shade","bar","neeche","list"}),
    Intent("torch on", TORCH_W, need=ON_W, allow={"light","batti"}),
    Intent("torch off", TORCH_W, need=OFF_W, allow={"light","batti"}),
    Intent("volume up", VOL_W, need=UP_W, allow={"level"}),
    Intent("volume down", VOL_W, need=DOWN_W, allow={"level"}),
    Intent("volume max", VOL_W, need=MAXW, allow={"level"}),
    Intent("volume mute", VOL_W | {"mute","silent"}, need={"mute","silent","band","bandh"}),
    Intent("volume {n}", {"volume","awaaz","awaz","aawaz"}, allow={"percent","pratishat","level","set"}, numeric=True),
    Intent("brightness up", BRIGHT_W, need=UP_W, allow={"level"}),
    Intent("brightness down", BRIGHT_W, need=DOWN_W, allow={"level"}),
    Intent("brightness max", BRIGHT_W, need=MAXW, allow={"level"}),
    Intent("brightness min", BRIGHT_W, need=MINW, allow={"level"}),
    Intent("brightness {n}", BRIGHT_W, allow={"percent","pratishat","level","set"}, numeric=True),
    Intent("battery", {"battery","batri","bettery","charge","charging"},
           allow={"kitni","kitna","kitne","kya","level","percent","status","bata","batao","bataiye","bataye","batana","check","dekho","dekh","dikhao","dikha","much","left","bacha","bachi","how","what","is","reh","rahi","raha"}),
    Intent("time", {"time","samay","baje","waqt","wakt"},
           allow={"kitne","kitna","kya","current","what","is","bata","batao","bataiye","bataye","tell","hua","hui","raha","rha","dekho"}),
    Intent("date", {"date","tarikh","tareekh","taarikh","tarik"}, allow={"aaj","kya","kitni","today","what","is","current","bata","batao","bataiye","tell"}),
    Intent("pause music", MEDIA_N | {"pause"}, need=PAUSE_W),
    Intent("play music", MEDIA_N | {"play","resume"}, need=PLAY_W),
    Intent("next song", {"next","agla","aglaa","agli","skip"}, allow=MEDIA_N | {"badlo","badal","chalao","bajao"}),
    Intent("previous song", {"previous","prev","pichla","pichhla","pichli","pichhli"}, allow=MEDIA_N | {"chalao","bajao"}),
    Intent("camera", {"camera"}, allow={"kholo","khol","open","start","chalao","chalu","on","launch"}),
    Intent("wifi on", {"wifi"}, need=ON_W | OFF_W | {"connect"}, allow={"settings"}),
    Intent("bluetooth on", {"bluetooth"}, need=ON_W | OFF_W | {"connect"}, allow={"settings"}),
]
def simplify(text):
    n = strip_wake(norm(text))
    if not n or len(n) > 160: return None
    words = [w for w in n.split(" ") if w]
    if not words or len(words) > 14: return None
    if any(w in U_NEGATE for w in words): return None
    rest = [w for w in words if w not in U_FILL]
    if not rest: return None
    hit = None
    for i in INTENTS:
        c = i.match(rest)
        if c is None: continue
        if hit is not None and hit != c: return None
        hit = c
    if hit in ("play music", "pause music") and len(words) > 4: return None
    return hit
def classify(text):
    """Understand.classify: returns (kind, arg, num) or None."""
    strict = logic_classify(text)
    if strict is not None and strict[0] != "open_app": return strict
    s = simplify(text)
    if s is None:
        if strict is not None and strict[0] == "open_app":
            w = [x for x in strict[1].split(" ") if x]
            if "camera" in w and any(x in CAM_OPT for x in w): return None
        return strict
    return logic_classify(s) or strict

if __name__ == "__main__":
    import sys
    for line in sys.argv[1:]:
        print(repr(line), "->", classify(line))
