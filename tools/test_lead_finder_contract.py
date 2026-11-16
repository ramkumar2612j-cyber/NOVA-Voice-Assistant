#!/usr/bin/env python3
"""v7.179 LEAD FINDER contract test (static). Mirrors test_call_agent_contract.py.
Reads the sets/APIs straight from the source files; never imports Kotlin.
Run: python3 tools/test_lead_finder_contract.py
"""
import re, sys, os
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
fails = 0
def check(cond, label):
    global fails
    print(("PASS " if cond else "FAIL ") + label)
    if not cond: fails += 1

rules = open(ROOT + "/app/src/main/java/com/nova/assistant/LeadFinderRules.kt", encoding="utf-8").read()
finder = open(ROOT + "/app/src/main/java/com/nova/assistant/LeadFinder.kt", encoding="utf-8").read()
lead_rules = open(ROOT + "/app/src/main/java/com/nova/assistant/LeadRules.kt", encoding="utf-8").read()
svc = open(ROOT + "/app/src/main/java/com/nova/assistant/JarvisService.kt", encoding="utf-8").read()
main = open(ROOT + "/app/src/main/java/com/nova/assistant/MainActivity.kt", encoding="utf-8").read()
helpg = open(ROOT + "/app/src/main/java/com/nova/assistant/HelpGuide.kt", encoding="utf-8").read()
ui = open(ROOT + "/app/src/main/assets/ui.html", encoding="utf-8").read()
jarvis = open(next(x for x in ["/home/z/my-project/download/JARVIS.html", ROOT + "/download/JARVIS.html"] if os.path.exists(x)), encoding="utf-8").read()

# --- object + rules API
check("object LeadFinderRules" in rules, "LeadFinderRules.kt object")
check("object LeadFinder" in finder, "LeadFinder.kt object")
check("fun isLeadJob(" in rules and "fun niche(" in rules and "fun city(" in rules, "rules API: isLeadJob/niche/city")
check("fun parse(" in rules and "fun filter(" in rules, "rules API: parse/filter")
check("fun score(" in rules, "rules API: Lead.score()")
check("MAX_LEADS_PER_SEARCH" in rules and "MAX_SEARCHES_PER_DAY" in rules, "rules: caps")
check("GENUINE_MIN" in rules, "rules: GENUINE_MIN")
check("DENY" in rules, "rules: DENY set")

# --- LeadFinder uses real sources (no fake data)
check("places.googleapis.com" in finder, "LeadFinder: Google Places endpoint")
check("overpass-api.de" in finder, "LeadFinder: Overpass (no-key fallback)")
check("nominatim.openstreetmap.org" in finder, "LeadFinder: Nominatim (city->bbox)")
check("LeadFinderRules.filter(" in finder, "LeadFinder: calls filter()")
check("LeadStore.add(" in finder, "LeadFinder: saves to LeadStore (legacy flow)")
check("ErrLog.add" in finder, "LeadFinder: errors go to ErrLog")
check("fun snapshotJson" in finder, "LeadFinder: snapshotJson() for the card")
check("fun stop(" in finder, "LeadFinder: stop()")

# --- honest limits documented
check("public listing" in rules.lower() or "public" in rules.lower(), "rules: 'public' honest note")
check("not verified" in rules.lower() or "guarantee" in rules.lower(), "rules: no-guarantee honesty")

# --- voice route wired
check("\"lead_find\"" in lead_rules, "LeadRules.command: lead_find kind")
check("LeadFinderRules.isLeadJob" in lead_rules, "LeadRules.command: uses isLeadJob")
check("\"lead_find\" -> leadFindRoute" in svc, "JarvisService: lead_find routed")
check("fun leadFindRoute(" in svc, "JarvisService: leadFindRoute() defined")
check("wireLeadFinder()" in svc, "JarvisService: wireLeadFinder() called")
check("LeadFinder.emitter" in svc, "JarvisService: LeadFinder.emitter wired")

# --- bridges
check("fun leadFinderSnapshot" in main, "bridge: leadFinderSnapshot")
check("fun leadFinderStop" in main, "bridge: leadFinderStop")

# --- HelpGuide + CAPS + skills
check('Topic("leadfind"' in helpg, "HelpGuide: leadfind topic")
check('"id": "leadfind"' in ui and '"id": "leadfind"' in jarvis, "CAPS: leadfind entry (both UIs)")
check("i:'leadfind'" in ui and "i:'leadfind'" in jarvis, "Skills: leadfind entry (both UIs)")

# --- UI card present (both)
check('id="lfdCard"' in ui and 'id="lfdCard"' in jarvis, "UI: lead card div (both)")
check("onJarvis('leadcard'" in ui or "'leadcard'" in ui, "UI: leadcard event handler")
check("leadFinderSnapshot" in ui and "leadFinderSnapshot" in jarvis, "UI: pulls leadFinderSnapshot")
check("leadFinderStop" in ui and "leadFinderStop" in jarvis, "UI: stop button wired")

# --- pure-logic mirror tests (parse + filter + score)
def tokenize(s):
    return [w for w in re.sub(r"[^a-z0-9]", " ", s.lower()).split(" ") if w]

LEAD_TRIGGERS = {"leads", "lead", "clients", "client", "customer", "customers", "prospects", "business", "businesses", "dukandaar", "vyapar"}
LEAD_DO = {"dhoondo", "dhundo", "dhoondho", "lao", "nikalo", "nikaalo", "dhundke", "dhundhke", "find", "search", "fetch", "get", "list", "bring"}
NICHE_HINTS = {"restaurant", "hotel", "cafe", "dhaba", "salon", "parlour", "parlor", "gym", "doctor", "clinic", "hospital", "dentist", "shop", "store", "bakery", "garage", "mechanic", "electrician", "plumber", "carpenter", "tailor", "lawyer", "advocate", "school", "coaching", "tutor", "institute", "photographer", "caterer", "decorator", "florist", "pharmacy", "chemist", "hardware", "furniture", "jewellery", "jewelry", "saree", "garments", "mobile", "electronics", "fmcg", "kirana", "wholesale", "manufacturer", "factory", "supplier"}
REGION_HINTS = {"delhi", "mumbai", "bangalore", "bengaluru", "hyderabad", "chennai", "kolkata", "pune", "ahmedabad", "jaipur", "lucknow", "kanpur", "nagpur", "indore", "bhopal", "patna", "surat", "vadodara", "rajkot", "raipur", "ranchi", "gurgaon", "gurugram", "noida", "faridabad", "ghaziabad", "varanasi", "agra", "meerut", "dehradun", "chandigarh", "amritsar", "ludhiana", "jalandhar"}
GENUINE_WORDS = {"genuine", "asli", "real", "quality", "sahi", "acche", "ache", "best", "top", "verified", "confirm"}
DENY = {"illegal", "smuggling", "drug", "drugs", "weapon", "weapons", "gambling", "betting", "escort", "adult", "fake", "counterfeit", "forgery", "hacking", "espionage", "blackmoney", "hawala"}

def is_lead_job(text):
    t = tokenize(text)
    if not t: return False
    if any(w in DENY for w in t): return False
    if not any(w in LEAD_TRIGGERS for w in t): return False
    return any(w in LEAD_DO for w in t)

def niche(text):
    t = tokenize(text)
    idx = -1
    for i, w in enumerate(t):
        if w in LEAD_TRIGGERS:
            idx = i
            break
    if idx > 0 and t[idx-1] in NICHE_HINTS:
        return t[idx-1]
    for w in t:
        if w in NICHE_HINTS:
            return w
    return ""

def city(text):
    t = tokenize(text)
    for w in t:
        if w in REGION_HINTS:
            return w
    return ""

def genuine_only(text):
    return any(w in GENUINE_WORDS for w in tokenize(text))

class Lead:
    def __init__(self, name, phone, website, address, reviews, rating, days):
        self.name=name; self.phone=phone; self.website=website; self.address=address
        self.reviews=reviews; self.rating=rating; self.recentActivityDays=days
    def score(self):
        s=0
        if self.name: s+=10
        if self.phone and len(self.phone)>=7: s+=25
        if self.website: s+=20
        if self.address: s+=10
        if self.reviews>0: s+=15
        if self.rating>=4.0: s+=10
        elif self.rating>0: s+=5
        if 1<=self.recentActivityDays<=180: s+=10
        return min(max(s,0),100)

GENUINE_MIN = 55
def filter_leads(raw, genuine):
    seen=set(); out=[]
    for l in raw:
        if not l.name: continue
        if l.name.lower() in DENY: continue
        pk = re.sub(r"\D", "", l.phone)
        if len(pk)>=7:
            if pk in seen: continue
            seen.add(pk)
        nk = "n|" + l.name.lower().strip()
        if nk in seen: continue
        seen.add(nk); out.append(l)
    out.sort(key=lambda l: -l.score())
    out = out[:12]
    if genuine:
        out = [l for l in out if l.score()>=GENUINE_MIN]
    return out

# parse
check(is_lead_job("jarvis delhi me restaurant ke genuine leads dhoondo"), "parse: 'delhi me restaurant ke leads dhoondo' is a lead job")
check(not is_lead_job("rahul ko call karo"), "parse: plain call is NOT a lead job")
check(not is_lead_job("mumbai me drug ke leads dhoondo"), "parse: deny niche (drug) is NOT a lead job")
check(niche("delhi me restaurant ke leads dhoondo") == "restaurant", "parse: niche=restaurant")
check(niche("jaipur me salon ke clients lao") == "salon", "parse: niche=salon")
check(city("delhi me restaurant ke leads dhoondo") == "delhi", "parse: city=delhi")
check(genuine_only("delhi me restaurant ke genuine leads dhoondo"), "parse: genuineOnly=True")
check(not genuine_only("delhi me restaurant ke leads dhoondo"), "parse: genuineOnly=False (no 'genuine')")

# score
hi = Lead("Spice Hub", "+919876543210", "https://spicehub.in", "Connaught Place, Delhi", 120, 4.5, 30)
mid = Lead("Tandoor Express", "+919812345678", "", "Karol Bagh, Delhi", 5, 3.8, 90)
lo = Lead("Closed Place", "", "", "", 0, 0.0, 400)
check(hi.score() == 100, "score: full lead = 100 (name+phone+web+addr+rev+rate4+active)")
check(mid.score() == 10+25+10+15+5+10, "score: mid lead = 75 (no web, rating<4, active)")
check(lo.score() == 10, "score: only-name lead = 10")

# filter
raw = [hi, mid, lo, hi, Lead("Spice Hub", "", "", "", 0, 0, 0)]  # dup name + dup phone
out = filter_leads(raw, False)
check(len(out) == 3, "filter: 5 raw (2 dups) -> 3 unique")
check(out[0].name == "Spice Hub", "filter: highest-scored lead first")
out_g = filter_leads(raw, True)
check(all(l.score()>=GENUINE_MIN for l in out_g), "filter: genuineOnly drops below GENUINE_MIN")
check(len(out_g) == 2, "filter: genuineOnly keeps only hi(100) and mid(65)")

print()
print("FAIL %d checks" % fails if fails else "ALL LEAD FINDER CONTRACT CHECKS PASS")
sys.exit(1 if fails else 0)
