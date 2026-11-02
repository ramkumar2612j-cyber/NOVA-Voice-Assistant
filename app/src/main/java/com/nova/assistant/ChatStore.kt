package com.nova.assistant

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * v6.80 (Part 8): chat storage without Android classes, so it runs in plain JUnit.
 *  - ChatCodec: one line per message / chat, no JSON (a half-written last line can only lose that one line).
 *  - ChatBackend: where bytes live. FileChatBackend = filesDir/chats (append-only message files). The old SharedPreferences backend is in ChatSessions.
 *  - ChatEngine: all the rules (new chat, append, pin, rename, search, export, delete) on top of any backend.
 *  - ChatMigration: old backend -> file backend, verified, with rollback.
 */
object ChatCodec {
    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        return sb.toString()
    }

    fun unesc(s: String): String {
        if (s.indexOf('\\') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t'); '\\' -> sb.append('\\'); else -> sb.append(s[i + 1]) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    fun msgLine(m: ChatSessionRules.Msg): String = "m\t" + m.role + "\t" + m.at + "\t" + esc(m.text) + "\n"

    fun decodeMsgs(text: String): MutableList<ChatSessionRules.Msg> {
        val out = ArrayList<ChatSessionRules.Msg>()
        for (line in text.split('\n')) {
            val p = line.split('\t', limit = 4)
            if (p.size < 4 || p[0] != "m") continue
            if (p[1] != "me" && p[1] != "ai") continue
            val at = p[2].toLongOrNull() ?: continue
            out.add(ChatSessionRules.Msg(p[1], unesc(p[3]), at))
        }
        return out
    }

    fun encodeMsgs(list: List<ChatSessionRules.Msg>): String {
        val sb = StringBuilder()
        list.forEach { sb.append(msgLine(it)) }
        return sb.toString()
    }

    /** Line: c, id, updated, count, pinned(0/1), renamed(0/1), title. */
    fun encodeIndex(list: List<ChatSessionRules.Chat>): String {
        val sb = StringBuilder()
        for (c in list) {
            sb.append("c\t").append(c.id).append('\t').append(c.updated).append('\t').append(c.count).append('\t')
                .append(if (c.pinned) '1' else '0').append('\t').append(if (c.renamed) '1' else '0').append('\t').append(esc(c.title)).append('\n')
        }
        return sb.toString()
    }

    fun decodeIndex(text: String): MutableList<ChatSessionRules.Chat> {
        val out = ArrayList<ChatSessionRules.Chat>()
        for (line in text.split('\n')) {
            val p = line.split('\t', limit = 7)
            if (p.size < 7 || p[0] != "c" || !ChatSessionRules.validId(p[1])) continue
            val upd = p[2].toLongOrNull() ?: continue
            val cnt = p[3].toIntOrNull() ?: continue
            out.add(ChatSessionRules.Chat(p[1], unesc(p[6]), upd, cnt, p[4] == "1", p[5] == "1"))
        }
        return out
    }
}

/** Where chats are kept. Every method may fail (false / empty); the engine never throws because of storage. */
interface ChatBackend {
    fun readIndex(): MutableList<ChatSessionRules.Chat>
    fun writeIndex(list: List<ChatSessionRules.Chat>): Boolean
    fun readMsgs(id: String): MutableList<ChatSessionRules.Msg>
    fun writeMsgs(id: String, list: List<ChatSessionRules.Msg>): Boolean
    /** One message at the end. Backends that cannot append read, add and write. */
    fun appendMsg(id: String, m: ChatSessionRules.Msg): Boolean
    fun removeMsgs(id: String): Boolean
    fun getCurrent(): String?
    fun setCurrent(id: String?): Boolean
    /** Every chat, the index and the current marker. */
    fun clearAll(): Boolean
}

/** Files under [dir]: index.txt, current.txt, m_<id>.txt. Writes go to a .tmp file and are renamed over the old one. */
class FileChatBackend(private val dir: File) : ChatBackend {
    private fun f(name: String) = File(dir, name)
    private fun ensure(): Boolean = dir.isDirectory || dir.mkdirs()

    private fun readText(file: File): String = try { if (file.isFile) file.readText(Charsets.UTF_8) else "" } catch (e: IOException) { "" }

    private fun atomicWrite(file: File, text: String): Boolean {
        if (!ensure()) return false
        val tmp = File(dir, file.name + ".tmp")
        return try {
            FileOutputStream(tmp).use { o -> o.write(text.toByteArray(Charsets.UTF_8)); o.fd.sync() }
            // rename replaces the old file in one step on Android / Linux; if a platform refuses, delete the old one and rename again
            if (tmp.renameTo(file)) true else { file.delete(); tmp.renameTo(file) }
        } catch (e: IOException) { tmp.delete(); false }
    }

    private fun msgFile(id: String): File? = if (ChatSessionRules.validId(id)) f("m_$id.txt") else null

    override fun readIndex(): MutableList<ChatSessionRules.Chat> = ChatCodec.decodeIndex(readText(f("index.txt")))
    override fun writeIndex(list: List<ChatSessionRules.Chat>): Boolean = atomicWrite(f("index.txt"), ChatCodec.encodeIndex(list))
    override fun readMsgs(id: String): MutableList<ChatSessionRules.Msg> { val m = msgFile(id) ?: return ArrayList(); return ChatCodec.decodeMsgs(readText(m)) }
    override fun writeMsgs(id: String, list: List<ChatSessionRules.Msg>): Boolean { val m = msgFile(id) ?: return false; return atomicWrite(m, ChatCodec.encodeMsgs(list)) }

    override fun appendMsg(id: String, m: ChatSessionRules.Msg): Boolean {
        val file = msgFile(id) ?: return false
        if (!ensure()) return false
        return try { FileOutputStream(file, true).use { it.write(ChatCodec.msgLine(m).toByteArray(Charsets.UTF_8)) }; true } catch (e: IOException) { false }
    }

    override fun removeMsgs(id: String): Boolean { val m = msgFile(id) ?: return false; return !m.exists() || m.delete() }

    override fun getCurrent(): String? = readText(f("current.txt")).trim().ifEmpty { null }
    override fun setCurrent(id: String?): Boolean {
        if (id == null) { val c = f("current.txt"); return !c.exists() || c.delete() }
        if (!ChatSessionRules.validId(id)) return false
        return atomicWrite(f("current.txt"), id)
    }

    override fun clearAll(): Boolean {
        var ok = true
        dir.listFiles()?.forEach { if (it.isFile && !it.delete()) ok = false }
        return ok
    }
}

/**
 * All chat rules. One lock for everything; storage errors give "false" / empty, never an exception.
 * [now] / [salt] are injected for tests.
 */
class ChatEngine(private val b: ChatBackend, private val salt: () -> Int = { System.nanoTime().toInt() }) {
    private val lock = Any()

    fun currentId(): String? = synchronized(lock) {
        val id = b.getCurrent()
        if (id != null && b.readIndex().any { it.id == id }) id else null
    }

    /** Starts a new, empty chat and makes it current. An already empty current chat is reused (no pile of empty chats). */
    fun newChat(now: Long): String = synchronized(lock) {
        val idx = b.readIndex()
        val cur = b.getCurrent()
        val empty = idx.firstOrNull { it.id == cur && it.count == 0 }
        if (empty != null) return@synchronized empty.id
        val id = ChatSessionRules.newId(now, salt())
        idx.add(0, ChatSessionRules.Chat(id, ChatSessionRules.DEFAULT_TITLE, now, 0))
        while (idx.size > ChatSessionRules.MAX_CHATS) {
            val victim = ChatSessionRules.evictionVictim(idx.filter { it.id != id }) ?: break
            idx.removeAll { it.id == victim.id }
            b.removeMsgs(victim.id)
        }
        b.writeIndex(idx)
        b.setCurrent(id)
        id
    }

    /** Adds one message to the current chat (one is created when there is none). Status lines and empty text are ignored. */
    fun append(role: String, text: String, now: Long) {
        if (!ChatSessionRules.isStorable(role, text)) return
        synchronized(lock) {
            val id = currentId() ?: newChat(now)
            val clean = ChatSessionRules.cleanText(text)
            val idx = b.readIndex()
            val pos = idx.indexOfFirst { it.id == id }
            if (pos < 0) return
            val old = idx[pos]
            val msg = ChatSessionRules.Msg(role, clean, now)
            val newCount: Int
            if (old.count + 1 > ChatSessionRules.MAX_MSGS) {
                val all = b.readMsgs(id); all.add(msg)
                val kept = ChatSessionRules.newest(all, ChatSessionRules.MAX_MSGS)
                b.writeMsgs(id, kept)
                newCount = kept.size
            } else {
                b.appendMsg(id, msg)
                newCount = old.count + 1
            }
            val title = if (!old.renamed && old.title == ChatSessionRules.DEFAULT_TITLE && role == "me") ChatSessionRules.titleFrom(text) else old.title
            idx[pos] = old.copy(title = title, updated = now, count = newCount)
            b.writeIndex(idx)
        }
    }

    /** Pinned first, then newest. */
    fun list(): List<ChatSessionRules.Chat> = synchronized(lock) { ChatSessionRules.sortForList(b.readIndex()) }

    fun messages(id: String): List<ChatSessionRules.Msg> = synchronized(lock) { b.readMsgs(id) }

    fun open(id: String): Boolean = synchronized(lock) {
        if (b.readIndex().none { it.id == id }) false else b.setCurrent(id)
    }

    fun delete(id: String): Boolean = synchronized(lock) {
        val idx = b.readIndex()
        if (idx.none { it.id == id }) return@synchronized false
        idx.removeAll { it.id == id }
        val ok = b.writeIndex(idx)
        b.removeMsgs(id)
        if (b.getCurrent() == id) b.setCurrent(null)
        ok
    }

    /** Every chat. The caller asks the owner first. */
    fun deleteAll(): Boolean = synchronized(lock) { b.clearAll() }

    /** "ok" / "missing" / "blank". */
    fun rename(id: String, raw: String): String = synchronized(lock) {
        val t = ChatSessionRules.cleanTitle(raw) ?: return@synchronized "blank"
        val idx = b.readIndex()
        val pos = idx.indexOfFirst { it.id == id }
        if (pos < 0) return@synchronized "missing"
        idx[pos] = idx[pos].copy(title = t, renamed = true)
        if (b.writeIndex(idx)) "ok" else "missing"
    }

    /** "ok" / "missing" / "limit" (more than MAX_PINNED). */
    fun setPinned(id: String, pinned: Boolean): String = synchronized(lock) {
        val idx = b.readIndex()
        val pos = idx.indexOfFirst { it.id == id }
        if (pos < 0) return@synchronized "missing"
        if (pinned && !idx[pos].pinned && idx.count { it.pinned } >= ChatSessionRules.MAX_PINNED) return@synchronized "limit"
        idx[pos] = idx[pos].copy(pinned = pinned)
        if (b.writeIndex(idx)) "ok" else "missing"
    }

    /** Chats whose title or any message contains every word of [query]. Empty query = all chats. Pinned first, then newest. */
    fun search(query: String): List<ChatSessionRules.Hit> = synchronized(lock) {
        val tokens = ChatSessionRules.queryTokens(query)
        val out = ArrayList<ChatSessionRules.Hit>()
        for (c in ChatSessionRules.sortForList(b.readIndex())) {
            if (tokens.isEmpty()) { out.add(ChatSessionRules.Hit(c, "")); continue }
            val msgs = b.readMsgs(c.id)
            val all = StringBuilder(c.title)
            msgs.forEach { all.append('\n').append(it.text) }
            if (!ChatSessionRules.matches(all.toString(), tokens)) continue
            val line = msgs.firstOrNull { m -> tokens.any { m.text.lowercase().contains(it) } }
            out.add(ChatSessionRules.Hit(c, if (line != null) ChatSessionRules.snippet(line.text, tokens) else ""))
        }
        out
    }

    /** Text of one chat for saving, null = no such chat. */
    fun exportText(id: String, stamp: (Long) -> String): String? = synchronized(lock) {
        val c = b.readIndex().firstOrNull { it.id == id } ?: return@synchronized null
        ChatSessionRules.exportText(c, b.readMsgs(id), stamp)
    }

    fun chat(id: String): ChatSessionRules.Chat? = synchronized(lock) { b.readIndex().firstOrNull { it.id == id } }
}

/** Old backend -> file backend. The old data is never touched here; a failed copy leaves nothing behind. */
object ChatMigration {
    class Result(val ok: Boolean, val chats: Int, val messages: Int, val reason: String)

    fun migrate(from: ChatBackend, to: ChatBackend): Result {
        val idx = from.readIndex()
        if (idx.isEmpty()) return Result(true, 0, 0, "nothing to move")
        var msgCount = 0
        val copied = HashMap<String, List<ChatSessionRules.Msg>>()
        for (c in idx) {
            val m = from.readMsgs(c.id)
            copied[c.id] = m
            msgCount += m.size
            if (!to.writeMsgs(c.id, m)) return fail(to, "write failed for a chat")
        }
        if (!to.writeIndex(idx)) return fail(to, "index write failed")
        val cur = from.getCurrent()
        if (cur != null && idx.any { it.id == cur } && !to.setCurrent(cur)) return fail(to, "current marker failed")
        // verify: read everything back and compare
        val back = to.readIndex()
        if (back.size != idx.size) return fail(to, "index size differs")
        for (c in idx) {
            val src = copied[c.id] ?: return fail(to, "missing source")
            val dst = to.readMsgs(c.id)
            if (dst.size != src.size) return fail(to, "message count differs")
            for (i in src.indices) if (dst[i].text != src[i].text || dst[i].role != src[i].role || dst[i].at != src[i].at) return fail(to, "message differs")
            val bc = back.firstOrNull { it.id == c.id } ?: return fail(to, "chat missing in index")
            if (bc.title != c.title || bc.updated != c.updated) return fail(to, "title or time differs")
        }
        return Result(true, idx.size, msgCount, "ok")
    }

    private fun fail(to: ChatBackend, why: String): Result { to.clearAll(); return Result(false, 0, 0, why) }
}
