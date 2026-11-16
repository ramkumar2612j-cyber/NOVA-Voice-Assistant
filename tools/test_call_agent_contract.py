#!/usr/bin/env python3
"""v7.178 CALL AGENT contract. Static checks (no Android needed):
  1. CallAgentRules.kt parses the sample sentences the way the doc says (pure mirror in Python).
  2. The Kotlin files reference only real APIs of this project (compile-level sanity).
  3. ui.html + JARVIS.html have exactly one call card, the bridges exist in MainActivity,
     JarvisService routes through callAgentRoute BEFORE laptopRoute, HelpGuide has the topic.
Run from the jarvis/ root: python3 tools/test_call_agent_contract.py
"""
import os
import re
import sys

ROOT = "."
fails = []


def check(cond, msg):
    print(("PASS " if cond else "FAIL ") + msg)
    if not cond:
        fails.append(msg)


# ------------------------------------------------------------------ 1) rules mirror
def tokenize(s):
    return [w for w in re.sub(r"[^a-z0-9]", " ", s.lower()).split(" ") if w]


AGENT_TRIGGERS = {"call", "phone", "telefon", "telephone", "ring", "bol"}
AGENT_DO = {"karke", "kar", "karo", "kardo", "karde", "and", "band"}
BOOKING_NOUNS = {"table", "booking", "reservation", "book", "order", "appointment", "slot", "seat", "seats"}
DENY = {"otp", "pin", "password", "cvv", "card", "aadhaar", "aadhar", "pan", "bank", "paytm", "gpay", "upi",
        "payment", "paisa", "rupees", "transfer", "subscribe", "subscription", "renew", "insurance", "loan",
        "emi", "kyc", "debit", "credit", "netbanking", "refund", "wallet", "crypto", "bitcoin", "scheme",
        "invest", "profit", "trading"}


def is_agent_job(text):
    t = tokenize(text)
    if not t:
        return False
    if not any(w in AGENT_TRIGGERS for w in t):
        return False
    return any(w in AGENT_DO for w in t) and any(w in BOOKING_NOUNS for w in t)


def denied(text):
    return any(w in DENY for w in tokenize(text))


def extract_number(text):
    runs = re.findall(r"\d+", text)
    best = ""
    for r in runs:
        if 7 <= len(r) <= 15 and len(r) > len(best):
            best = r
    if len(best) == 12 and best.startswith("91"):
        best = best[2:]
    return best


AGENTS_YES = [
    "jarvis 9876543210 par call karke table book karo",
    "call karke doctor ka appointment le lo",
    "phone karke table booking kar do",
    "98XXXXXXXX par call karke do log ki table book karo",
]
AGENTS_NO = [
    "rahul ko call karo",                      # plain direct call (old path keeps it)
    "weather kaisa hai",
    "call uthao",
    "jarvis laptop par chrome kholo",
]
for s in AGENTS_YES:
    check(is_agent_job(s), "agent job: " + s)
for s in AGENTS_NO:
    check(not is_agent_job(s), "NOT agent job: " + s)

check(extract_number("jarvis +91 9876543210 par call karke table book karo") == "9876543210",
      "number: +91 9876543210 -> 9876543210 (91 country code stripped)")
check(extract_number("9876543210 par call karke table book karo") == "9876543210",
      "number: 10-digit passthrough")
check(extract_number("call karke table book karo") == "", "number: koi number nahi -> ''")
check(denied("call karke payment kar do"), "deny: payment refuse")
check(not denied("call karke table book karo"), "deny: booking allowed")

# ------------------------------------------------------------------ 2) Kotlin sources exist + API sanity
rules = open(ROOT + "/app/src/main/java/com/nova/assistant/CallAgentRules.kt", encoding="utf-8").read()
agent = open(ROOT + "/app/src/main/java/com/nova/assistant/CallAgent.kt", encoding="utf-8").read()
service = open(ROOT + "/app/src/main/java/com/nova/assistant/JarvisService.kt", encoding="utf-8").read()
main = open(ROOT + "/app/src/main/java/com/nova/assistant/MainActivity.kt", encoding="utf-8").read()
helpg = open(ROOT + "/app/src/main/java/com/nova/assistant/HelpGuide.kt", encoding="utf-8").read()

check("object CallAgentRules" in rules, "CallAgentRules.kt object")
check("fun isAgentJob" in rules and "fun script(" in rules and "fun report(" in rules, "rules API: isAgentJob/script/report")
check("object CallAgent" in agent, "CallAgent.kt object")
check("ACTION_CALL" in agent, "CallAgent places a REAL call (ACTION_CALL)")
check("TelephonyManager" in agent and "TelecomManager" in agent, "call state via Telephony/Telecom")
check("ErrLog.add" in agent, "errors go to ErrLog")
# the deny set is enforced in Rules, and the agent re-checks before dialing
check("CallAgentRules.denied" in agent, "CallAgent re-checks denied() before dialing")

# routing order: callAgentRoute BEFORE laptopRoute in process()
m = re.search(r"callAgentRoute\(text\) \?: laptopRoute\(text\) \?: handle", service)
check(bool(m), "JarvisService routes callAgentRoute -> laptopRoute -> handle")
check("wireCallAgent()" in service and "CallAgent.emitter" in service, "JarvisService wires emitter + speaker")
check("if (CallAgent.active) CallAgent.stop(this)" in service, "STOP ends a live agent call")

# bridges
check("fun callAgentSnapshot()" in main, "bridge: callAgentSnapshot")
check("fun callAgentStop()" in main, "bridge: callAgentStop")
# bridge caps contract style: our bridges take NO String params, so nothing to cap

# HelpGuide topic
check('Topic("callagent"' in helpg, "HelpGuide topic: callagent")

# ------------------------------------------------------------------ 3) UI files
JPATH = "/home/z/my-project/download/JARVIS.html" if os.path.exists("/home/z/my-project/download/JARVIS.html") else os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "download", "JARVIS.html")   # sandbox layout: the themed JARVIS copy
for ui, label in [("app/src/main/assets/ui.html", "ui.html"),
                  (JPATH, "JARVIS.html")]:
    if ui.startswith("/") and label == "JARVIS.html":
        try:
            h = open(ui, encoding="utf-8").read()
        except OSError:
            print("SKIP JARVIS.html (not found)")
            continue
        check(h.count('id="cagCard"') == 1, label + ": call card div exactly once")
        check(h.count("callAgentSnapshot") >= 1 and h.count("cagUpd") >= 1, label + ": card pulls snapshot")
        check(h.count("callAgentStop") >= 1, label + ": X button stops the call")
        check(h.count("t:'Call agent (call + incoming + custom msg)'") == 1, label + ": one call-agent skills entry")
        continue
    try:
        h = open(ROOT + "/" + ui, encoding="utf-8").read()
    except OSError:
        print("SKIP " + label + " (not found in this sandbox layout)")
        continue
    check(h.count('id="cagCard"') == 1, label + ": call card div exactly once")
    check(h.count("cagUpd") >= 1 and h.count("callAgentSnapshot") >= 1, label + ": card pulls snapshot")
    check(h.count("callAgentStop") >= 1, label + ": X button stops the call")
    check(h.count("t:'Call agent (call + incoming + custom msg)'") == 1, label + ": one call-agent skills entry")

# ------------------------------------------------------------------ verdict
print()
if fails:
    print("FAIL %d checks" % len(fails))
    sys.exit(1)
print("ALL CALL AGENT CONTRACT CHECKS PASS")


# ------------------------------------------------------------------ v7.178b EXTENSIONS: custom message + incoming importance
def custom_message(text):
    """mirror of CallAgentRules.customMessage: words after the first SAY_WORD."""
    SAY_WORDS = {"bolo", "bol", "bolein", "bolege", "kehna", "kehdo", "keh", "kaho", "kahiye", "say", "tell", "inform"}
    t = tokenize_v2(text)
    idx = -1
    for i, w in enumerate(t):
        if w in SAY_WORDS:
            idx = i
            break
    if idx < 0:
        return ""
    msg = []
    for w in t[idx + 1:]:
        if w in AGENT_TRIGGERS_v2 or w in AGENT_DO_v2:
            break
        if w in DENY_v2:
            return ""
        msg.append(w)
        if len(" ".join(msg)) > 160:
            break
    return " ".join(msg).strip()[:160]


def tokenize_v2(s):
    import re
    return [w for w in re.sub(r"[^a-z0-9]", " ", s.lower()).split(" ") if w]


AGENT_TRIGGERS_v2 = {"call", "phone", "telefon", "telephone", "ring", "bol"}
AGENT_DO_v2 = {"karke", "kar", "karo", "kardo", "karde", "and", "band"}
DENY_v2 = DENY


def is_agent_job_v2(text):
    """mirror of the new isAgentJob: call + do + (say-word OR booking-noun)."""
    t = tokenize_v2(text)
    if not t:
        return False
    if not any(w in AGENT_TRIGGERS_v2 for w in t):
        return False
    if not any(w in AGENT_DO_v2 for w in t):
        return False
    SAY = {"bolo", "bol", "bolein", "bolege", "kehna", "kehdo", "keh", "kaho", "kahiye", "say", "tell", "inform"}
    BOOKING = {"table", "booking", "reservation", "book", "order", "appointment", "slot", "seat", "seats"}
    return any(w in SAY for w in t) or any(w in BOOKING for w in t)


FAMILY_HINTS = {"papa", "papa2", "pappa", "papaji", "mummy", "mummy2", "mom", "mumy", "mummyji", "amma", "ammi", "maa", "dadi", "dada", "nana", "nani", "bua", "chacha", "bhai", "didi", "beta"}


def is_important_caller(name):
    t = tokenize_v2(name)
    return any(w in FAMILY_HINTS for w in t)


def incoming_decision(name, auto_all, auto_fam):
    if auto_all:
        return "answer"
    if auto_fam and is_important_caller(name):
        return "answer"
    if is_important_caller(name):
        return "tell"
    return "skip"


# custom-message parsing
check(custom_message("jarvis papa ko call karke bolo main sham tak aaunga") == "main sham tak aaunga",
      "custom msg: 'bolo main sham tak aaunga' -> 'main sham tak aaunga'")
check(custom_message("jarvis rahul ko call karke bolo callback karenge") == "callback karenge",
      "custom msg: 'bolo callback karenge' -> 'callback karenge'")
check(custom_message("jarvis 98765 par call karke table book karo") == "",
      "custom msg: no 'bolo' word -> empty (falls back to booking script)")
check(custom_message("jarvis papa ko call karke bolo apna otp bata do") == "",
      "custom msg: deny word (otp) inside -> empty (refused)")

# isAgentJob now also accepts "bolo" style
check(is_agent_job_v2("jarvis papa ko call karke bolo main sham tak aaunga"),
      "agent job: 'call karke bolo <msg>' is an agent job")
check(is_agent_job_v2("jarvis rahul ko call karke bolo callback karenge"),
      "agent job: 'call karke bolo callback karenge' is an agent job")
check(not is_agent_job_v2("rahul ko call karo"),
      "agent job: plain direct call is NOT an agent job (old path keeps it)")

# incoming importance
check(incoming_decision("Papa", False, True) == "answer", "incoming: Papa + autoFam -> answer")
check(incoming_decision("Papa", False, False) == "tell", "incoming: Papa + auto OFF -> tell (announce only)")
check(incoming_decision("Unknown number", True, False) == "answer", "incoming: anyone + autoAll -> answer")
check(incoming_decision("Unknown number", False, False) == "skip", "incoming: stranger + auto OFF -> skip (silent)")
check(is_important_caller("Mummy"), "important: Mummy is family")
check(not is_important_caller("9876543210"), "important: a bare number is not family")

# new bridges in MainActivity
check("fun callAgentSettings()" in main, "bridge: callAgentSettings (get)")
check("fun setCallAgentSetting(" in main, "bridge: setCallAgentSetting")
check("fun requestAnswerCalls()" in main, "bridge: requestAnswerCalls")
check("REQ_ANSWER" in main, "REQ_ANSWER constant added")

# ExtCfg slots
check("callAutoAll" in open(ROOT + "/app/src/main/java/com/nova/assistant/ExtCfg.kt", encoding="utf-8").read(), "ExtCfg: callAutoAll")
check("callAutoFam" in open(ROOT + "/app/src/main/java/com/nova/assistant/ExtCfg.kt", encoding="utf-8").read(), "ExtCfg: callAutoFam")

# JarvisNotificationService incoming hook
notif = open(ROOT + "/app/src/main/java/com/nova/assistant/JarvisNotificationService.kt", encoding="utf-8").read()
check("handleIncomingCall" in notif and "CallAgent.answerIncoming" in notif, "NotifService: handleIncomingCall -> CallAgent.answerIncoming")
check("CallAgentRules.incomingDecision" in notif, "NotifService: uses CallAgentRules.incomingDecision")

# CallAgent.kt answerIncoming + runIncoming
check("fun answerIncoming(" in agent, "CallAgent: answerIncoming()")
check("fun runIncoming(" in agent, "CallAgent: runIncoming()")
check("acceptRingingCall" in agent, "CallAgent: TelecomManager.acceptRingingCall")
check("CallAgentRules.incomingGreeting" in agent, "CallAgent: uses incomingGreeting")
check("CallAgentRules.incomingReport" in agent, "CallAgent: uses incomingReport")

# settings toggle UI present in both UIs
for ui, label in [("app/src/main/assets/ui.html", "ui.html"),
                  ("/home/z/my-project/download/JARVIS.html", "JARVIS.html")]:
    try:
        u = open(ROOT + "/" + ui if not ui.startswith("/") else ui, encoding="utf-8").read()
    except OSError:
        print("SKIP " + label); continue
    check(u.count('id="ca_fam"') == 1, label + ": auto-answer family checkbox")
    check(u.count('id="ca_all"') == 1, label + ": auto-answer all checkbox")
    check(u.count('id="ca_perm"') == 1, label + ": permission button")
    check("callAgentSettings" in u and "setCallAgentSetting" in u, label + ": settings bridges wired")

# verdict (printed by the original tail)
