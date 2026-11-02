#!/usr/bin/env python3
"""Part 2/3 source-level security contracts. These do not replace Android/Gradle/device tests."""
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parent.parent
fails=[]
def check(name, ok):
    print(("PASS " if ok else "FAIL ")+name)
    if not ok: fails.append(name)
wa=(ROOT/'app/src/main/java/com/nova/assistant/WhatsAppCommandRules.kt').read_text()
logic=(ROOT/'app/src/main/java/com/nova/assistant/Logic.kt').read_text()
svc=(ROOT/'app/src/main/java/com/nova/assistant/NovaService.kt').read_text()
hindi=(ROOT/'app/src/main/java/com/nova/assistant/HindiPack.kt').read_text()
chat=(ROOT/'app/src/main/java/com/nova/assistant/ChatText.kt').read_text()
ui=(ROOT/'app/src/main/assets/ui.html').read_text()
heal=svc[svc.index('private fun healRound()'):svc.index('private val quietAlertAt', svc.index('private fun healRound()'))]
check('WhatsApp parser recognizes the two-word Aman Biswan form', 'words.take(2).joinToString(" ")' in wa and 'whatsapp ' in wa)
check('Biswan is not treated as an app/contact by itself', 'r.equals("biswan", true)' in wa)
check('contact picker never falls back to shortest unrelated contact', 'return names.indices.minByOrNull' not in logic and 'if (word.size == 1) return word[0]' in logic)
check('WhatsApp confirmation cannot be skipped', 'askFirst(prompt, must = true)' in svc)
check('raw JSON is sanitized before chat listener', 'ChatText.clean(text)' in svc)
check('JSON status success is converted to normal text', '"Theek hai, kaam ho gaya."' in chat)
check('HindiPack requires HTTPS', 'u.protocol != "https"' in hindi)
check('HindiPack uses exact host allow-list', 'u.host == HOST || u.host.endsWith(".$HOST")' in hindi)
check('HindiPack has compressed download size cap', 'MAX_ZIP_BYTES' in hindi and 'total > MAX_ZIP_BYTES' in hindi)
check('HindiPack has uncompressed size cap', 'MAX_UNZIPPED_BYTES' in hindi and 'unzipped > MAX_UNZIPPED_BYTES' in hindi)
check('HindiPack blocks zip-slip paths', 'dest.canonicalPath.startsWith(root)' in hindi)
check('self-heal only diagnoses readiness; no automatic model download', 'HindiPack.ensure' not in heal and 'ModelAuto.silentVoice' not in heal)
check('self-heal does not call free AI for a repair', 'runFreeChain' not in heal)
check('production UI has no MIC DEBUG panel', 'MIC DEBUG' not in ui and 'mic_debug' not in ui.lower())
check('production UI has no raw diagnostic controls named START STOP VOICE DIAGNOSIS', 'VOICE DIAGNOSIS' not in ui and 'MIC DEBUG' not in ui)
print("\n%d failed"%len(fails) if fails else "\nALL PASSED")
raise SystemExit(1 if fails else 0)
