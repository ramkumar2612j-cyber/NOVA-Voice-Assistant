#!/usr/bin/env python3
"""PART 2 contract test: runs the REAL regexes and word lists of PersonalMemory.kt (extracted from the source text)
through a Python port of the pure logic and checks the shared case table. It is NOT a run of the Kotlin code
(no Kotlin compiler in the build sandbox): it catches wrong regexes / wrong expectations before CI does."""
import os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import memory_cases as C

KT_PATH = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "nova", "assistant", "PersonalMemory.kt")
KT = open(KT_PATH, encoding="utf8").read()
fails = []
def check(name, cond, extra=""):
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else "  " + extra))
    if not cond: fails.append(name)

def rx(name):
    m = re.search(r'private val %s = Regex\("""(.*)"""\)' % name, KT)
    assert m, "regex not found in Kotlin: " + name
    return re.compile(m.group(1))
def wordset(name):
    m = re.search(r'val %s = setOf\((.*?)\)\n' % name, KT, re.S)
    assert m, name
    return set(re.findall(r'"([^"]+)"', m.group(1)))

LEAD, FORGET_ALL, LIST, FORGET, FORGET_POST = rx("LEAD_RE"), rx("FORGET_ALL_RE"), rx("LIST_RE"), rx("FORGET_RE"), rx("FORGET_POST_RE")
SAVE, SAVE_POST, ARG_LEAD = rx("SAVE_RE"), rx("SAVE_POST_RE"), rx("ARG_LEAD_RE")
CARD, ID, BANK, SECRET, DIGIT_RUN = rx("CARD_RE"), rx("ID_RE"), rx("BANK_RE"), rx("SECRET_RE"), rx("DIGIT_RUN_RE")
KEY_WORD, KEY_PREFIX = rx("KEY_WORD_RE"), rx("KEY_PREFIX_RE")
STOP, DIGIT_WORDS, PRONOUNS = wordset("STOP"), wordset("DIGIT_WORDS"), wordset("PRONOUNS")
MAX_FACTS = int(re.search(r"const val MAX_FACTS = (\d+)", KT).group(1))
MAX_CHARS = int(re.search(r"const val MAX_FACT_CHARS = (\d+)", KT).group(1))
MIN_CHARS = int(re.search(r"const val MIN_FACT_CHARS = (\d+)", KT).group(1))

def norm(text):  # port of Logic.norm
    s = text.lower().replace("[unk]", " ").replace("-", " ")
    s = re.sub(r"[^a-z0-9% ]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s.replace("wi fi", "wifi").replace("flash light", "flashlight").replace("blue tooth", "bluetooth").replace("you tube", "youtube")

def clean_arg(t): return ARG_LEAD.sub("", t.strip()).strip()
def parse(n):
    s = n.strip()
    if not s or len(s) > 150: return None
    s = LEAD.sub("", s).strip()
    if not s: return None
    if FORGET_ALL.fullmatch(s): return ("mem_forget_all", None)
    if LIST.fullmatch(s): return ("mem_list", None)
    m = FORGET.search(s)
    if m:
        t = clean_arg(m.group(1))
        if t: return ("mem_forget", t)
    m = FORGET_POST.search(s)
    if m:
        t = clean_arg(m.group(1))
        if t and t not in PRONOUNS: return ("mem_forget", t)
    m = SAVE.search(s)
    if m:
        t = clean_arg(m.group(1))
        if len(t) >= MIN_CHARS: return ("mem_save", t)
    m = SAVE_POST.search(s)
    if m:
        t = clean_arg(m.group(1))
        if len(t) >= MIN_CHARS: return ("mem_save", t)
    return None

def has_long_number(text):
    s = text.lower()
    if DIGIT_RUN.search(s): return True
    run = 0
    for w in re.split(r"[^a-z0-9]+", s):
        if w in DIGIT_WORDS:
            run += 1
            if run >= 6: return True
        elif w: run = 0
    return False
def has_key_like(text):
    s = text.lower()
    if KEY_PREFIX.search(s): return True
    for w in re.split(r"\s+", s):
        t = w.strip("".join(chr(c) for c in range(128) if not (chr(c).isalnum() and chr(c).islower() or chr(c).isdigit())))
        if len(t) >= 24 and re.fullmatch(r"[a-z0-9_-]+", t) and re.search(r"[0-9]", t) and re.search(r"[a-z]", t): return True
    return False
def refuse_reason(fact):
    s = fact.lower()
    if ID.search(s): return "id"
    if CARD.search(s): return "card"
    if BANK.search(s): return "bank"
    if SECRET.search(s): return "secret"
    if KEY_WORD.search(s) or has_key_like(s): return "key"
    if has_long_number(s): return "number"
    return None
def clean_fact(raw):
    s = "".join(" " if (ord(c) < 32 or 127 <= ord(c) < 160) else c for c in raw)
    return re.sub(r"\s+", " ", s.replace("<|", " ").replace("|>", " ")).strip()
def add(lst, raw):
    f = clean_fact(raw)
    if len(f) < MIN_CHARS: return ("short", lst)
    if len(f) > MAX_CHARS: return ("long", lst)
    if refuse_reason(f): return ("refused", lst)
    if any(e.lower() == f.lower() for e in lst): return ("dup", lst)
    out = [f] + list(lst)
    while len(out) > MAX_FACTS: out.pop()
    return ("saved", out)
def tokens(s):
    out = []
    for w in norm(s).split(" "):
        if len(w) >= 2 and w not in STOP and w not in out: out.append(w)
    return out
def same(a, b): return a == b or (len(a) >= 4 and len(b) >= 4 and a[:4] == b[:4])
def overlap(q, e): return sum(1 for w in q if any(same(w, x) for x in e))
def forget(lst, query):
    q = tokens(query)
    if not q or not lst: return (0, False, lst)
    sc = [overlap(q, tokens(x)) for x in lst]
    best = max(sc)
    if best == 0 or best / len(q) < 0.5: return (0, False, lst)
    if sc.count(best) > 1: return (0, True, lst)
    return (1, False, [x for x, s in zip(lst, sc) if s != best])
def private(f): return has_long_number(f) or "@" in f.lower() or refuse_reason(f) is not None
def relevant(facts, question, mx=3):
    q = [w for w in tokens(question) if len(w) >= 3]
    if not q or not facts: return []
    sc = {}
    for i, f in enumerate(facts):
        if private(f): continue
        s = overlap(q, tokens(f))
        if s > 0: sc[i] = s
    order = sorted(sc, key=lambda i: (-sc[i], i))
    return [facts[i] for i in order[:mx]]

# ---- 1. parser table
for text, kind, arg in C.PARSE:
    r = parse(norm(text))
    got = (r[0], r[1]) if r else None
    want = (kind, arg) if kind else None
    check("parse %r -> %s" % (text, kind), got == want, "got %r want %r" % (got, want))
# ---- 2. refusal table
for fact, want in C.REFUSE:
    check("refuse %r -> %s" % (fact, want), refuse_reason(norm(fact)) == want, "got %r" % refuse_reason(norm(fact)))
    check("refuse raw %r -> %s" % (fact, want), refuse_reason(clean_fact(fact)) == want, "got %r" % refuse_reason(clean_fact(fact)))
# ---- 3. forget / relevant
for q, removed, amb in C.FORGET:
    r = forget(C.FACTS[:3], q)
    check("forget %r" % q, (r[0], r[1]) == (removed, amb), "got %r" % (r[:2],))
for q, want in C.RELEVANT:
    got = relevant(C.FACTS, q)
    check("relevant %r" % q, got == want, "got %r" % got)
# ---- 4. add rules
st, l = add([], "mera naam shiv hai"); check("add saves", st == "saved" and l == ["mera naam shiv hai"])
st, l2 = add(l, "MERA   naam shiv HAI"); check("add dedupes case-insensitively", st == "dup" and l2 == l)
check("add too short", add([], "ab")[0] == "short")
check("add too long", add([], "x" * (MAX_CHARS + 1))[0] == "long")
check("add exactly max chars ok", add([], "y" * MAX_CHARS)[0] == "saved")
check("add refuses secret and keeps list", add(l, "my otp is 5555")[0] == "refused")
big = []
for i in range(MAX_FACTS + 5): big = add(big, "fact number %s about tea" % ("x" * (i % 7)) + " item%d" % i)[1]
check("limit %d, newest first, oldest dropped" % MAX_FACTS, len(big) == MAX_FACTS and big[0].endswith("item%d" % (MAX_FACTS + 4)) and not any(b.endswith("item0") for b in big))
check("control chars become spaces", clean_fact("a\nb\tc<|d|>") == "a b c d")

# ---- 4b. PART 7: memory screen operations (Python port of PersonalMemory.search / removeAt / replaceAt / importText)
def search(lst, q):
    ws = [w for w in re.split(r"\s+", q.lower()) if w]
    return [i for i, f in enumerate(lst) if all(w in f.lower() for w in ws)]
def remove_at(lst, i, expected):
    if not (0 <= i < len(lst)) or lst[i] != expected: return ("gone", lst)
    return ("saved", lst[:i] + lst[i + 1:])
def replace_at(lst, i, expected, raw):
    if not (0 <= i < len(lst)) or lst[i] != expected: return ("gone", lst)
    f = clean_fact(raw)
    if len(f) < MIN_CHARS: return ("short", lst)
    if len(f) > MAX_CHARS: return ("long", lst)
    if refuse_reason(f): return ("refused", lst)
    if any(j != i and e.lower() == f.lower() for j, e in enumerate(lst)): return ("dup", lst)
    out = list(lst); out[i] = f; return ("saved", out)
def import_text(lst, text):
    body = text.lstrip("\ufeff")
    if len(body) > MAX_IMPORT_CHARS: return None
    lines = [l.strip() for l in body.split("\n") if l.strip() and not l.strip().startswith("#")]
    if not lines or len(lines) > MAX_IMPORT_LINES: return None
    cur, c = list(lst), {"saved": 0, "dup": 0, "refused": 0, "other": 0}
    for line in reversed(lines):
        st, nl = add(cur, line)
        if st == "saved": c["saved"] += 1; cur = nl
        elif st in c: c[st] += 1
        else: c["other"] += 1
    return c, cur
MAX_IMPORT_CHARS = int(re.search(r"const val MAX_IMPORT_CHARS = ([\d_]+)", KT).group(1).replace("_", ""))
MAX_IMPORT_LINES = int(re.search(r"const val MAX_IMPORT_LINES = (\d+)", KT).group(1))
L = ["mera naam shiv hai", "meri car blue hai", "papa ka naam ram hai"]
check("search: all words, any case", search(L, "NAAM") == [0, 2] and search(L, "papa naam") == [2] and search(L, "") == [0, 1, 2] and search(L, "xyz") == [])
check("remove: right fact", remove_at(L, 1, "meri car blue hai") == ("saved", [L[0], L[2]]))
check("remove: list changed meanwhile -> gone, nothing deleted", remove_at(L, 1, "papa ka naam ram hai") == ("gone", L) and remove_at(L, 9, "x")[0] == "gone")
check("edit: same position", replace_at(L, 1, L[1], "meri car red hai") == ("saved", [L[0], "meri car red hai", L[2]]))
check("edit: secret / key / short / long refused, list kept", all(replace_at(L, 0, L[0], t)[0] == w and replace_at(L, 0, L[0], t)[1] == L for t, w in [("my otp 5555", "refused"), ("api key abc", "refused"), ("ab", "short"), ("z" * 201, "long")]))
check("edit: duplicate of ANOTHER fact refused, same fact re-saved ok", replace_at(L, 0, L[0], "MERI car blue hai")[0] == "dup" and replace_at(L, 0, L[0], "MERA naam shiv hai")[0] == "saved")
r = import_text(["purani baat hai"], "\ufeff# NOVA memory export v1\n# comment\nnayi pehli baat\nnayi doosri baat\nmera otp 4821\nmera otp 4821\nPURANI baat hai\nab\n")
check("import: header skipped, order kept newest first, secrets refused, dup skipped, short skipped",
      r is not None and r[1] == ["nayi pehli baat", "nayi doosri baat", "purani baat hai"] and r[0] == {"saved": 2, "dup": 1, "refused": 2, "other": 1}, "got %r" % (r,))
check("import: empty / only comments / too big / too many lines -> None",
      import_text([], "") is None and import_text([], "# a\n# b\n") is None and import_text([], "x" * (MAX_IMPORT_CHARS + 1)) is None and import_text([], "\n".join("baat number %d hai" % i for i in range(MAX_IMPORT_LINES + 1))) is None)
r = import_text([], "\n".join("tea fact item%d" % i for i in range(MAX_FACTS + 10)))
check("import: stays inside the fact limit", r is None or len(r[1]) <= MAX_FACTS)
exp_kt = KT[KT.index("fun exportText"):KT.index("class ImportResult")]
check("export has header + one fact per line (source)", "EXPORT_HEADER" in exp_kt and 'joinToString("\\n")' in exp_kt)
# ---- 5. Kotlin source invariants
check("memory kinds are not in Brain.SAFE_KINDS", not re.search(r'"mem_', open(os.path.join(HERE, "..", "app/src/main/java/com/nova/assistant/Brain.kt"), encoding="utf8").read()))
svc = open(os.path.join(HERE, "..", "app/src/main/java/com/nova/assistant/NovaService.kt"), encoding="utf8").read()
check("handle() short-circuits memory before brain.record", svc.index("val memCmd") < svc.index("brain.record(System.currentTimeMillis(), text, \"local\", \"local_skill\")"))
check("forget-all uses askFirst", re.search(r"memForgetAll\(\): String \{.*?askFirst\(", svc, re.S) is not None)
check("Gemini path has no memory facts", "PersonalMemory" not in svc[svc.index("private fun askCloud"):svc.index("// ---------------------------------------------------------------- screen session")])
check("manifest allowBackup=false (memory is not cloud-backed-up)", 'android:allowBackup="false"' in open(os.path.join(HERE, "..", "app/src/main/AndroidManifest.xml"), encoding="utf8").read())

mact = open(os.path.join(HERE, "..", "app/src/main/java/com/nova/assistant/MainActivity.kt"), encoding="utf8").read()
html = open(os.path.join(HERE, "..", "app/src/main/assets/ui.html"), encoding="utf8").read()
store = open(os.path.join(HERE, "..", "app/src/main/java/com/nova/assistant/PersonalMemoryStore.kt"), encoding="utf8").read()
cloud = svc[svc.index("private fun askCloud"):svc.index("// ---------------------------------------------------------------- screen session")]
check("Gemini gets the same safe task line as the backup AI (v6.79)", "fallbackTaskLine(" in cloud and "FallbackContext.systemWith(systemPrompt()" in cloud)
check("Gemini path still has no personal facts", "chainFacts" not in cloud and "PersonalMemory" not in cloud)
for name in ["getMemoryJson", "memoryAdd", "memoryEdit", "memoryDelete", "memoryExport", "memoryImport"]:
    check("bridge method " + name, re.search(r"@JavascriptInterface\s+fun %s\(" % name, mact) is not None)
check("add / edit / delete go through the atomic store update", mact.count("PersonalMemoryStore.update(this@MainActivity)") >= 3 and "fun update(" in store and "synchronized(lock)" in store)
check("bridge caps input length", mact.count("raw.take(1000)") >= 2)
check("export + import request codes are unique", len(set(re.findall(r"const val REQ_\w+ = (\d+)", mact))) == len(re.findall(r"const val REQ_\w+ = (\d+)", mact)))
check("import file is size capped before parsing", "raw.size > 80_000" in mact)
mem_ui = html[html.index("PART 7 memory screen"):html.index("var md=g(\"mem_del\")")]
check("memory screen draws facts with textContent only (no innerHTML / outerHTML / insertAdjacentHTML)", not re.search(r"innerHTML|outerHTML|insertAdjacentHTML|document\.write", mem_ui))
check("memory screen shows the limit and the privacy line", "Limit: 100 baatein" in html and "koi bhi padh sakta hai" in html)
print()
print("ALL PASSED" if not fails else "FAILED: %d" % len(fails))
sys.exit(1 if fails else 0)
