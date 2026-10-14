package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v6.80 (Part 8): codec, file backend, engine rules, migration. Pure JVM, real files in a temp dir. */
class ChatStoreTest {
    private fun tmp(): File { val d = File.createTempFile("novachat", ""); d.delete(); d.mkdirs(); d.deleteOnExit(); return File(d, "chats") }
    private fun engine(dir: File = tmp()): ChatEngine { var n = 0; return ChatEngine(FileChatBackend(dir)) { n++ } }
    private fun m(r: String, t: String, at: Long = 1L) = ChatSessionRules.Msg(r, t, at)

    // ---- codec
    @Test fun codecRoundTripsTabsNewlinesBackslashesAndUnicode() {
        val nasty = "a\tb\nc\r\nd\\e\\n literal नमस्ते 😀"
        val back = ChatCodec.decodeMsgs(ChatCodec.encodeMsgs(listOf(m("me", nasty, 5L), m("ai", "ok", 6L))))
        assertEquals(2, back.size)
        assertEquals(nasty, back[0].text)
        assertEquals(5L, back[0].at)
        assertEquals("ai", back[1].role)
    }

    @Test fun codecSkipsBrokenLinesAndKeepsTheRest() {
        val good = ChatCodec.msgLine(m("me", "ek"))
        val back = ChatCodec.decodeMsgs(good + "m\tme\n" + "x\tme\t1\tzzz\n" + "m\tsys\t1\tstatus\n" + "m\tai\tNaN\tbad time\n" + ChatCodec.msgLine(m("ai", "do")) + "m\tme\t9\thalf writt")
        assertEquals(listOf("ek", "do", "half writt"), back.map { it.text })
    }

    @Test fun indexRoundTripKeepsPinRenameAndTitleWithTabs() {
        val c = ChatSessionRules.Chat("cabc", "naam\twith tab", 77L, 3, true, true)
        val back = ChatCodec.decodeIndex(ChatCodec.encodeIndex(listOf(c)))
        assertEquals(1, back.size)
        assertEquals("naam\twith tab", back[0].title)
        assertTrue(back[0].pinned); assertTrue(back[0].renamed)
        assertEquals(3, back[0].count)
    }

    @Test fun indexLineWithAnUnsafeIdIsDropped() {
        assertEquals(0, ChatCodec.decodeIndex("c\t../evil\t1\t1\t0\t0\tx\n").size)
    }

    // ---- file backend
    @Test fun invalidIdsNeverTouchTheDisk() {
        val dir = tmp(); val b = FileChatBackend(dir)
        for (id in listOf("../x", "a/b", "", "A1", "c1.txt", "x".repeat(41))) {
            assertFalse(id, b.writeMsgs(id, listOf(m("me", "hi"))))
            assertFalse(id, b.appendMsg(id, m("me", "hi")))
            assertFalse(id, b.setCurrent(id))
        }
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun appendIsOneLineAndSurvivesAHalfWrittenTail() {
        val dir = tmp(); val b = FileChatBackend(dir)
        b.appendMsg("c1", m("me", "ek")); b.appendMsg("c1", m("ai", "do"))
        File(dir, "m_c1.txt").appendText("m\tme\t3\tcut off")   // crash in the middle of a line
        assertEquals(listOf("ek", "do", "cut off"), b.readMsgs("c1").map { it.text })
    }

    // ---- engine
    @Test fun appendCreatesChatTitleAndReadsBackAfterRestart() {
        val dir = tmp(); val e = engine(dir)
        e.append("me", "torch jala do", 100L); e.append("ai", "Torch on", 101L)
        e.append("sys", "step 1", 102L)
        val e2 = engine(dir)   // "restart": a new engine on the same files
        val id = e2.currentId()
        assertNotNull(id)
        assertEquals("torch jala do", e2.list()[0].title)
        assertEquals(2, e2.list()[0].count)
        assertEquals(listOf("torch jala do", "Torch on"), e2.messages(id!!).map { it.text })
    }

    @Test fun emptyCurrentChatIsReused() {
        val e = engine(); val a = e.newChat(1L); val b = e.newChat(2L)
        assertEquals(a, b); assertEquals(1, e.list().size)
    }

    @Test fun evictionDropsTheOldestUnpinnedAndKeepsPinned() {
        val e = engine(); val ids = ArrayList<String>()
        for (i in 1..ChatSessionRules.MAX_CHATS) { e.append("me", "chat $i", i.toLong() * 10); ids.add(e.currentId()!!); e.newChat(i.toLong() * 10 + 5) }
        // 30 chats + the empty 31st would exceed: oldest unpinned (chat 2 after pinning chat 1) must go, chat 1 stays
        val oldest = e.list().minByOrNull { it.updated }!!
        assertEquals("ok", e.setPinned(oldest.id, true))
        e.append("me", "chat 31", 999L)
        e.newChat(1000L)
        assertTrue(e.list().size <= ChatSessionRules.MAX_CHATS)
        assertTrue(e.list().any { it.id == oldest.id && it.pinned })
    }

    @Test fun pinLimitIsTen() {
        val e = engine(); val ids = ArrayList<String>()
        for (i in 1..11) { e.append("me", "c$i", i * 10L); ids.add(e.currentId()!!); e.newChat(i * 10L + 1) }
        for (i in 0 until 10) assertEquals("ok", e.setPinned(ids[i], true))
        assertEquals("limit", e.setPinned(ids[10], true))
        assertEquals("ok", e.setPinned(ids[0], true))     // pinning an already pinned chat is fine
        assertEquals("missing", e.setPinned("nope", true))
    }

    @Test fun pinnedChatsListFirst() {
        val e = engine()
        e.append("me", "alt", 10L); val a = e.currentId()!!; e.newChat(11L)
        e.append("me", "neu", 20L)
        assertEquals("neu", e.list()[0].title)
        e.setPinned(a, true)
        assertEquals("alt", e.list()[0].title)
    }

    @Test fun renameSticksEvenWhenTheFirstMessageComesLater() {
        val e = engine(); val id = e.newChat(1L)
        assertEquals("ok", e.rename(id, "  mera\n  naam  "))
        e.append("me", "pehla sawal", 2L)
        assertEquals("mera naam", e.list()[0].title)
        assertEquals("blank", e.rename(id, "   \n "))
        assertEquals("missing", e.rename("zzz", "x"))
        assertEquals(ChatSessionRules.RENAME_MAX, ChatSessionRules.cleanTitle("y".repeat(200))!!.length)
    }

    @Test fun searchNeedsEveryWordAndFindsTitleOrMessages() {
        val e = engine()
        e.append("me", "torch jala do", 10L); e.append("ai", "Torch on kar di", 11L); e.newChat(12L)
        e.append("me", "kal ka mausam", 20L); e.append("ai", "Kal baarish hogi", 21L)
        assertEquals(2, e.search("").size)
        assertEquals(listOf("torch jala do"), e.search("TORCH").map { it.chat.title })
        assertEquals(listOf("kal ka mausam"), e.search("baarish kal").map { it.chat.title })
        assertEquals(0, e.search("baarish torch").size)
        assertTrue(e.search("baarish")[0].snippet.contains("baarish"))
        assertTrue(e.search("mausam")[0].snippet.contains("mausam"))
        assertEquals("", e.search("")[0].snippet)
        assertEquals(2, e.search("   ").size)
    }

    @Test fun deleteCurrentChatClearsTheMarkerAndDeleteAllEmptiesEverything() {
        val dir = tmp(); val e = engine(dir)
        e.append("me", "ek", 1L); val a = e.currentId()!!; e.newChat(2L); e.append("me", "do", 3L)
        val cur = e.currentId()!!
        assertTrue(e.delete(cur)); assertNull(e.currentId()); assertFalse(e.delete(cur))
        assertEquals(1, e.list().size)
        assertTrue(e.open(a)); assertEquals(a, e.currentId())
        assertTrue(e.deleteAll())
        assertEquals(0, e.list().size); assertNull(e.currentId())
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        e.append("me", "naya", 9L)   // still works after delete all
        assertEquals(1, e.list().size)
    }

    @Test fun openMissingChatIsRefused() { assertFalse(engine().open("cnope")) }

    @Test fun chatOver500MessagesKeepsTheNewestAndTheCountIsRight() {
        val e = engine(); e.newChat(0L)
        for (i in 1..(ChatSessionRules.MAX_MSGS + 7)) e.append(if (i % 2 == 1) "me" else "ai", "m$i", i.toLong())
        val id = e.currentId()!!
        val msgs = e.messages(id)
        assertEquals(ChatSessionRules.MAX_MSGS, msgs.size)
        assertEquals("m${ChatSessionRules.MAX_MSGS + 7}", msgs.last().text)
        assertEquals("m8", msgs.first().text)
        assertEquals(ChatSessionRules.MAX_MSGS, e.list()[0].count)
    }

    @Test fun exportTextHasTitleCountAndSpeakers() {
        val e = engine(); e.append("me", "torch jala do", 1L); e.append("ai", "Torch on", 2L)
        val id = e.currentId()!!
        val t = e.exportText(id) { "T$it" }!!
        assertTrue(t.startsWith("NOVA chat: torch jala do\n2 messages\n"))
        assertTrue(t.contains("[T1] Main: torch jala do\n"))
        assertTrue(t.contains("[T2] NOVA: Torch on\n"))
        assertNull(e.exportText("cnope") { "" })
        assertEquals("nova-chat-torch-jala-do.txt", ChatSessionRules.exportFileName("Torch jala do!"))
        assertEquals("nova-chat-export.txt", ChatSessionRules.exportFileName("नमस्ते"))
    }

    // ---- migration
    private class MemBackend : ChatBackend {
        var idx = ArrayList<ChatSessionRules.Chat>(); val msgs = HashMap<String, MutableList<ChatSessionRules.Msg>>(); var cur: String? = null
        override fun readIndex() = ArrayList(idx)
        override fun writeIndex(list: List<ChatSessionRules.Chat>): Boolean { idx = ArrayList(list); return true }
        override fun readMsgs(id: String) = ArrayList(msgs[id] ?: mutableListOf())
        override fun writeMsgs(id: String, list: List<ChatSessionRules.Msg>): Boolean { msgs[id] = ArrayList(list); return true }
        override fun appendMsg(id: String, m: ChatSessionRules.Msg): Boolean { msgs.getOrPut(id) { ArrayList() }.add(m); return true }
        override fun removeMsgs(id: String): Boolean { msgs.remove(id); return true }
        override fun getCurrent() = cur
        override fun setCurrent(id: String?): Boolean { cur = id; return true }
        override fun clearAll(): Boolean { idx.clear(); msgs.clear(); cur = null; return true }
    }

    private fun legacy(): MemBackend {
        val l = MemBackend()
        l.idx.add(ChatSessionRules.Chat("c1", "pehli chat", 50L, 2)); l.idx.add(ChatSessionRules.Chat("c2", "doosri", 40L, 1))
        l.msgs["c1"] = arrayListOf(m("me", "namaste\nduniya", 5L), m("ai", "hello", 6L)); l.msgs["c2"] = arrayListOf(m("me", "x\ty", 7L)); l.cur = "c2"
        return l
    }

    @Test fun migrationCopiesEverythingAndLeavesTheOldDataAlone() {
        val old = legacy(); val dir = tmp(); val files = FileChatBackend(dir)
        val r = ChatMigration.migrate(old, files)
        assertTrue(r.reason, r.ok); assertEquals(2, r.chats); assertEquals(3, r.messages)
        assertEquals(2, old.idx.size); assertEquals(2, old.msgs["c1"]!!.size)   // old store untouched
        val e = ChatEngine(files)
        assertEquals("c2", e.currentId())
        assertEquals("namaste\nduniya", e.messages("c1")[0].text)
        assertEquals("x\ty", e.messages("c2")[0].text)
        assertEquals(listOf("pehli chat", "doosri"), e.list().map { it.title })
    }

    @Test fun migrationOfNothingIsOkAndEmpty() {
        val r = ChatMigration.migrate(MemBackend(), FileChatBackend(tmp()))
        assertTrue(r.ok); assertEquals(0, r.chats)
    }

    /** A target that silently loses the last message of a chat: verification must catch it and roll back. */
    @Test fun migrationThatDoesNotReadBackEqualIsRolledBack() {
        val old = legacy(); val dir = tmp(); val real = FileChatBackend(dir)
        val lossy = object : ChatBackend by real {
            override fun writeMsgs(id: String, list: List<ChatSessionRules.Msg>): Boolean = real.writeMsgs(id, list.dropLast(if (id == "c1") 1 else 0))
        }
        val r = ChatMigration.migrate(old, lossy)
        assertFalse(r.ok)
        assertTrue(dir.listFiles().orEmpty().isEmpty())     // nothing half-copied is left
        assertEquals(2, old.msgs["c1"]!!.size)
    }

    @Test fun migrationWhoseWriteFailsIsRolledBack() {
        val old = legacy(); val dir = tmp(); val real = FileChatBackend(dir)
        val failing = object : ChatBackend by real { override fun writeIndex(list: List<ChatSessionRules.Chat>) = false }
        assertFalse(ChatMigration.migrate(old, failing).ok)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }
}

class ChatRulesPart8Test {
    private fun m(r: String, len: Int) = ChatSessionRules.Msg(r, "x".repeat(len), 0L)

    @Test fun idValidation() {
        assertTrue(ChatSessionRules.validId(ChatSessionRules.newId(System.currentTimeMillis(), 7)))
        assertFalse(ChatSessionRules.validId(null)); assertFalse(ChatSessionRules.validId("a-b")); assertFalse(ChatSessionRules.validId("a b"))
    }

    @Test fun queryTokensAreCappedAndLowercase() {
        assertEquals(listOf("torch", "on"), ChatSessionRules.queryTokens("  TORCH   On "))
        assertEquals(ChatSessionRules.MAX_QUERY_TOKENS, ChatSessionRules.queryTokens("a b c d e f g h i").size)
        assertTrue(ChatSessionRules.queryTokens("x".repeat(500))[0].length <= ChatSessionRules.QUERY_MAX)
        assertEquals(0, ChatSessionRules.queryTokens(null).size)
    }

    @Test fun snippetIsShortAndAroundTheMatch() {
        val s = ChatSessionRules.snippet("a".repeat(100) + " torch " + "b".repeat(100), listOf("torch"))
        assertTrue(s.contains("torch")); assertTrue(s.length < 100); assertTrue(s.startsWith("…") && s.endsWith("…"))
        assertEquals("", ChatSessionRules.snippet("nothing here", listOf("torch")))
    }

    @Test fun contextCountsAllFitMeansNothingCut() {
        val msgs = listOf(m("me", 10), m("ai", 10), m("me", 10), m("ai", 10), m("me", 10))   // last "me" = the question asked now
        val c = ChatSessionRules.contextCounts(msgs, 1000)
        assertEquals(5, c.total); assertEquals(4, c.sent); assertEquals(0, c.cut)
    }

    @Test fun contextCountsOldMessagesAreCutAndTheWindowStartsWithTheOwner() {
        val msgs = ArrayList<ChatSessionRules.Msg>()
        for (i in 0 until 10) { msgs.add(m("me", 100)); msgs.add(m("ai", 100)) }   // 20 messages, 2000 chars
        val c = ChatSessionRules.contextCounts(msgs, 450)    // fits 4 -> newest 4 = me,ai,me,ai
        assertEquals(20, c.total); assertEquals(4, c.sent); assertEquals(16, c.cut)
        val c2 = ChatSessionRules.contextCounts(msgs, 350)   // newest 3 would start with an answer -> 2
        assertEquals(2, c2.sent); assertEquals(18, c2.cut)
    }

    /** Same as FallbackContext.fullWindow: the newest answer alone does not fit, and a window may not start with an answer, so nothing is sent. */
    @Test fun contextCountsOversizedPairMatchesFullWindow() {
        val msgs = listOf(m("me", 5000), m("ai", 5000))
        val c = ChatSessionRules.contextCounts(msgs, 100)
        assertEquals(0, c.sent); assertEquals(2, c.cut)
        assertEquals(0, FallbackContext.fullWindow(msgs.map { Pair(it.role, it.text) }, 100).turns.size)
    }

    /** The counter must agree with the real window the model gets, for many shapes of chat. */
    @Test fun contextCountsAgreeWithFullWindow() {
        for (n in 0..14) for (len in listOf(10, 300, 1400)) for (budget in listOf(100, 900, 4000, 120000)) {
            val msgs = (0 until n).map { m(if (it % 2 == 0) "me" else "ai", len + it) }
            val hist = if (msgs.isNotEmpty() && msgs.last().role == "me") msgs.dropLast(1) else msgs
            val w = FallbackContext.fullWindow(hist.map { Pair(it.role, it.text) }, budget)
            val c = ChatSessionRules.contextCounts(msgs, budget)
            assertEquals("n=$n len=$len budget=$budget", w.turns.size, c.sent)   // strictly alternating chat: 1 turn = 1 message
        }
    }

    @Test fun contextCountsEmptyChat() {
        val c = ChatSessionRules.contextCounts(emptyList(), 1000)
        assertEquals(0, c.total); assertEquals(0, c.sent); assertEquals(0, c.cut)
    }
}
