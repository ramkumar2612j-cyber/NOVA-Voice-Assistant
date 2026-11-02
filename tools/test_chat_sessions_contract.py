#!/usr/bin/env python3
"""v6.69 structural contract (Kotlin NOT run here): chat sessions (New Chat, separate ids, history list, resume), nothing old removed."""
import os, sys
B = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main")
R = os.path.join(B, "java", "com", "nova", "assistant")
rd = lambda n: open(os.path.join(R, n), encoding="utf-8").read()
NS, MA, CS, CR = rd("NovaService.kt"), rd("MainActivity.kt"), rd("ChatSessions.kt"), rd("ChatSessionRules.kt")
UI = open(os.path.join(B, "assets", "ui.html"), encoding="utf-8").read()
bad = 0
def check(n, c):
    global bad
    print(("PASS " if c else "FAIL ") + n)
    if not c: bad += 1
check("every chat line goes through emit() and is stored", "listener?.invoke(" not in NS.replace("        listener?.invoke(role, text)", "") and "ChatSessions.append(applicationContext, role, text)" in NS)
check("only me/ai are stored, status lines are not", 'role == "me" || role == "ai"' in NS and '(role == "me" || role == "ai")' in CR)
check("store is private, local, one id per chat, bounded", 'MODE_PRIVATE' in CS and 'MAX_CHATS' in rd('ChatStore.kt') and 'MAX_MSGS' in CR and 'm_$id.txt' in rd('ChatStore.kt'))
check("an empty current chat is reused (no pile of empty chats)", "it.count == 0" in rd('ChatStore.kt'))
check("New Chat clears the model context, memory facts are not touched", "fun resetChatContext()" in NS and "history.clear()" in NS.split("fun resetChatContext()")[1][:200] and "PersonalMemoryStore" not in NS.split("fun resetChatContext()")[1][:300])
check("open chat gives back only the bounded newest turns", "ChatSessionRules.contextTurns(msgs)" in MA and "fun contextTurns(" in CR)
check("chat cannot be switched while NOVA is busy", MA.count("isBusyNow()") >= 2 and '"busy"' in MA)
for m in ("newChat", "listChats", "openChat", "deleteChat", "currentChat"):
    check("bridge method " + m, "fun " + m + "(" in MA.split("v6.69 chat sessions")[1])
check("UI: New chat, History, close, back handling", all(i in UI for i in ('id="c_new"', 'id="c_hist"', 'id="c_hl"', "g('c_new').onclick=newChat", "hl.classList.contains('on')")))
check("UI: chat titles are shown with textContent (no innerHTML of user text)", "t.textContent=(c.pinned" in UI and "hll.innerHTML" not in UI)
check("UI: delete needs a second tap", "x.dataset.sure" in UI)
check("old chat screen parts still there", all(i in UI for i in ('id="c_in"', 'id="c_send"', "Android.sendText(t)", "window.onNova=function(r,t)")))
check("no voice lock / speaker recognition added", not any(w in (CS + CR + NS.split("private fun emit(role")[1][:1500]).lower() for w in ("speaker", "voicelock", "enrol")))
check("no catch (_ and no !! in new files", "catch (_:" not in CS + CR and "!!" not in CS + CR)
# ---- v6.80 (Part 8)
ST = rd("ChatStore.kt")
check("v6.80 files store: append-only, atomic tmp+rename, ids validated", "FileOutputStream(file, true)" in ST and ".tmp" in ST and "validId" in ST)
check("v6.80 migration verifies and rolls back", "fun migrate(" in ST and "to.clearAll()" in ST and '"done"' in CS and '"files"' in CS)
check("v6.80 bridge: new methods validate the id", all(("fun " + m + "(") in MA for m in ("searchChats", "renameChat", "pinChat", "deleteAllChats", "exportChat", "contextInfo")) and MA.count("ChatSessionRules.validId(id)") >= 5)
check("v6.80 delete all is refused while busy", 'fun deleteAllChats' in MA and '"busy"' in MA.split("fun deleteAllChats")[1][:300])
check("v6.80 export goes through the system save box", "REQ_EXPORT_CHAT" in MA and "ACTION_CREATE_DOCUMENT" in MA.split("fun exportChat")[1][:900])
check("v6.80 UI: search, pin, rename, export, delete all with two taps, context line", all(i in UI for i in ('id="c_hs"', "Android.searchChats", "Android.pinChat", "Android.renameChat", "Android.exportChat", "Android.deleteAllChats", 'id="c_ctx"', "hall.dataset.sure")))
check("v6.80 UI: no innerHTML of chat text", "hll.innerHTML" not in UI and "ctxEl.innerHTML" not in UI)
sys.exit(1 if bad else 0)
