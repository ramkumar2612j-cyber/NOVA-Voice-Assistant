#!/usr/bin/env python3
"""Independent-review source guards: provider models, URL allowlist, screen input privacy.
These are source-level regression checks, not an Android compile or device test.
"""
from pathlib import Path
from html.parser import HTMLParser
import re
ROOT = Path(__file__).resolve().parent.parent
fails = []
def check(name, condition):
    print(("PASS " if condition else "FAIL ") + name)
    if not condition: fails.append(name)
main = (ROOT / "app/src/main/java/com/nova/assistant/MainActivity.kt").read_text(encoding="utf-8")
providers = (ROOT / "app/src/main/java/com/nova/assistant/FreeProviders.kt").read_text(encoding="utf-8")
accessibility = (ROOT / "app/src/main/java/com/nova/assistant/NovaAccessibilityService.kt").read_text(encoding="utf-8")
video = (ROOT / "app/src/main/java/com/nova/assistant/VideoRecordService.kt").read_text(encoding="utf-8")
ui = (ROOT / "app/src/main/assets/ui.html").read_text(encoding="utf-8")
main_bridge = set(re.findall(r"@JavascriptInterface\s+fun\s+(\w+)\s*\(", main))
check("Groq uses the current GPT-OSS models", 'listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b"), "groq"' in providers)
check("deprecated Groq Llama ids are absent from active provider config", "llama-3.3-70b-versatile" not in providers and "llama-3.1-8b-instant" not in providers)
check("published URL bridge validates HTTPS and real github.io hostname", 'uri.scheme != "https"' in main and 'host.endsWith(".github.io")' in main and 'host == "github.io"' in main)
check("published URL bridge rejects user-info and unexpected ports", 'uri.userInfo != null' in main and '(uri.port != -1 && uri.port != 443)' in main)
check("editable controls don't export text or contentDescription", 'val t0 = if (edit) ""' in accessibility and 'val d0 = if (edit) ""' in accessibility and 'val editValue = if (edit)' in accessibility)
check("screen-text privacy documentation covers explicit session and agent consent", "explicitly starts a screen session" in accessibility and "confirms an agent task" in accessibility)
check("video start/stop/destroy lifecycle is synchronized", "@Synchronized\n    private fun startVideo" in video and "@Synchronized\n    private fun stopVideo" in video and "@Synchronized\n    override fun onDestroy" in video)
check("video refuses silently substituting the wrong camera facing", "front_camera_unavailable" in video and "back_camera_unavailable" in video)
check("Pollinations UI describes anonymous text request as fallible", "Pollinations (keyless try; may fail)" in ui and "keyless no-key" not in ui)
check("FREE AI settings disclose the Qwen, Z.ai, and offline fallback stages", "Qwen/Z.ai" in ui and "offline fallback" in ui)
static_calls = set(re.findall(r"\bAndroid\.([A-Za-z_]\w*)\s*\(", ui))
check("all literal Android bridge calls exist in @JavascriptInterface methods", not (static_calls - main_bridge))
# Every static button ID must be referred to by wiring code; generated controls are reviewed in their builders.
class ButtonParser(HTMLParser):
    def __init__(self): super().__init__(); self.ids=[]
    def handle_starttag(self, tag, attrs):
        attrs=dict(attrs)
        if tag == "button" and attrs.get("id"): self.ids.append(attrs["id"])
parser=ButtonParser(); parser.feed(ui)
missing_buttons=[]
for bid in parser.ids:
    lit = re.escape(bid)
    if not (re.search(r"(?:g|\$)\(['\"]"+lit+r"['\"]\)", ui) or re.search(r"\b"+lit+r"\.onclick", ui) or re.search(r"getElementById\(['\"]"+lit+r"['\"]\)", ui)):
        missing_buttons.append(bid)
check("all static button IDs have a code reference/wiring", not missing_buttons)
html_ids = set(re.findall(r"\bid=[\"']([^\"']+)[\"']", ui))
selectors = set(re.findall(r"document\.getElementById\([\"']([^\"']+)[\"']\)", ui))
for pat in (r"\bg\([\"']([^\"']+)[\"']\)", r"\$\([\"']([^\"']+)[\"']\)"):
    selectors.update(re.findall(pat, ui))
missing_ids = selectors - html_ids
check("all literal UI selectors resolve or are explicitly optional", missing_ids == {"mode", "tf"} and "if(!m)return" in ui and "if(!cv||!cv.getContext)return" in ui)
print("\n%d failed" % len(fails) if fails else "\nALL PASSED")
raise SystemExit(1 if fails else 0)
