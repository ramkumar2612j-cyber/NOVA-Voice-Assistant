"""Static safety contract: screen-session reply approval allows typing only, never sending."""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "app", "src", "main", "java", "com", "nova", "assistant")
NS = open(os.path.join(JAVA, "NovaService.kt"), encoding="utf-8").read()
SS = open(os.path.join(JAVA, "ScreenSession.kt"), encoding="utf-8").read()
TEST = open(os.path.join(ROOT, "app", "src", "test", "java", "com", "nova", "assistant", "ScreenSessionTest.kt"), encoding="utf-8").read()

fails = 0

def check(label, ok):
    global fails
    print(("PASS " if ok else "FAIL ") + label)
    if not ok:
        fails += 1

start = NS.index("if (intent == ScreenSession.Intent.REPLY) {", NS.index("private fun screenAnswer"))
end = NS.index("\n        return out", start)
reply_branch = NS[start:end]
check("approved screen reply only types draft", "NovaAccessibilityService.typeText(draft)" in reply_branch)
check("screen reply branch never taps Send", "tapLabel" not in reply_branch and "SEND_LABELS" not in reply_branch)
check("screen reply branch never auto-waits then sends", "Thread.sleep" not in reply_branch)
check("confirmation tells English users they send manually", "You will press Send yourself" in SS)
check("confirmation tells Hindi users they send manually", "Send aap khud dabana" in SS)
check("Kotlin regression test covers manual-send wording", "replyConfirmationOnlyOffersTypingNotSending" in TEST)
check("send-button automation removed from screen session", "SEND_LABELS" not in NS + SS)

print("ALL PASSED" if not fails else f"{fails} FAILED")
sys.exit(1 if fails else 0)
