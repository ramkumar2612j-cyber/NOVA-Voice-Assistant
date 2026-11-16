package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v6.69 chat sessions: every conversation has its own id, title and messages, kept on this phone only (not backed up).
 * Keys and secrets are never part of a chat message. Rules live in ChatSessionRules / ChatEngine.
 *
 * v6.80 (Part 8): storage moved from one SharedPreferences string per chat to files (filesDir/chats, append-only), so a long chat no
 * longer rewrites a megabyte on every message. The first start after the update copies the old chats, reads them back and compares;
 * only then are the files used. If anything differs the copy is deleted and the old SharedPreferences store keeps working (retry
 * next start). The old data is removed one start LATER, after the files were used once without a problem.
 */
object ChatSessions {
    private const val PREF = "nova_chats"
    private const val K_INDEX = "index"
    private const val K_CUR = "current"
    private const val P_MSGS = "m_"
    private const val K_STATE = "storage_state"        // "" = not decided, "files" = copied + verified (old data still there), "done" = old data removed
    @Volatile private var engine: ChatEngine? = null
    private val initLock = Any()

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** The old store, behind the same interface. Used for the one-time copy and as the fallback when the copy fails. */
    private class PrefsBackend(private val ctx: Context) : ChatBackend {
        override fun readIndex(): MutableList<ChatSessionRules.Chat> {
            val out = ArrayList<ChatSessionRules.Chat>()
            try {
                val a = JSONArray(sp(ctx).getString(K_INDEX, "[]") ?: "[]")
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isEmpty()) continue
                    out.add(ChatSessionRules.Chat(id, o.optString("title", ChatSessionRules.DEFAULT_TITLE), o.optLong("updated"), o.optInt("count"), o.optBoolean("pinned"), o.optBoolean("renamed")))
                }
            } catch (ignored: Exception) { ErrLog.add("ChatSessions", ignored.toString()) }
            return out
        }
        override fun writeIndex(list: List<ChatSessionRules.Chat>): Boolean = try {
            val a = JSONArray()
            list.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("updated", it.updated).put("count", it.count).put("pinned", it.pinned).put("renamed", it.renamed)) }
            sp(ctx).edit().putString(K_INDEX, a.toString()).commit()
        } catch (ignored: Exception) { ErrLog.add("ChatSessions", ignored.toString()); false }
        override fun readMsgs(id: String): MutableList<ChatSessionRules.Msg> {
            val out = ArrayList<ChatSessionRules.Msg>()
            try {
                val a = JSONArray(sp(ctx).getString(P_MSGS + id, "[]") ?: "[]")
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    out.add(ChatSessionRules.Msg(o.optString("r"), o.optString("t"), o.optLong("at")))
                }
            } catch (ignored: Exception) { ErrLog.add("ChatSessions", ignored.toString()) }
            return out
        }
        override fun writeMsgs(id: String, list: List<ChatSessionRules.Msg>): Boolean = try {
            val a = JSONArray()
            list.forEach { a.put(JSONObject().put("r", it.role).put("t", it.text).put("at", it.at)) }
            sp(ctx).edit().putString(P_MSGS + id, a.toString()).commit()
        } catch (ignored: Exception) { ErrLog.add("ChatSessions", ignored.toString()); false }
        override fun appendMsg(id: String, m: ChatSessionRules.Msg): Boolean { val l = readMsgs(id); l.add(m); return writeMsgs(id, l) }
        override fun removeMsgs(id: String): Boolean = sp(ctx).edit().remove(P_MSGS + id).commit()
        override fun getCurrent(): String? = sp(ctx).getString(K_CUR, null)
        override fun setCurrent(id: String?): Boolean = if (id == null) sp(ctx).edit().remove(K_CUR).commit() else sp(ctx).edit().putString(K_CUR, id).commit()
        override fun clearAll(): Boolean {
            val e = sp(ctx).edit().remove(K_INDEX).remove(K_CUR)
            sp(ctx).all.keys.filter { it.startsWith(P_MSGS) }.forEach { e.remove(it) }
            return e.commit()
        }
    }

    private fun eng(ctx: Context): ChatEngine {
        engine?.let { return it }
        synchronized(initLock) {
            engine?.let { return it }
            val app = ctx.applicationContext
            val files = FileChatBackend(File(app.filesDir, "chats"))
            val old = PrefsBackend(app)
            val state = sp(app).getString(K_STATE, "") ?: ""
            val e: ChatEngine = when {
                state == "done" -> ChatEngine(files)
                state == "files" -> {
                    // second start: the files worked once, now the old copy can go
                    if (files.readIndex().isNotEmpty() || old.readIndex().isEmpty()) {
                        old.clearAll(); sp(app).edit().putString(K_STATE, "done").commit()
                        ChatEngine(files)
                    } else ChatEngine(old)   // files vanished: the old copy is the only one left, keep using it
                }
                else -> {
                    val r = try { ChatMigration.migrate(old, files) } catch (ex: Exception) { files.clearAll(); ChatMigration.Result(false, 0, 0, ex.toString()) }
                    if (r.ok) {
                        sp(app).edit().putString(K_STATE, if (r.chats == 0) "done" else "files").commit()
                        ErrLog.add("ChatSessions", "migrated " + r.chats + " chats / " + r.messages + " messages to files")
                        ChatEngine(files)
                    } else {
                        ErrLog.add("ChatSessions", "migration failed (" + r.reason + "), old store kept")
                        ChatEngine(old)
                    }
                }
            }
            engine = e
            return e
        }
    }

    fun currentId(ctx: Context): String? = eng(ctx).currentId()
    fun newChat(ctx: Context, now: Long = System.currentTimeMillis()): String = eng(ctx).newChat(now)
    fun append(ctx: Context, role: String, text: String, now: Long = System.currentTimeMillis()) = eng(ctx).append(role, text, now)
    fun list(ctx: Context): List<ChatSessionRules.Chat> = eng(ctx).list()
    fun messages(ctx: Context, id: String): List<ChatSessionRules.Msg> = eng(ctx).messages(id)
    fun open(ctx: Context, id: String): Boolean = eng(ctx).open(id)
    fun delete(ctx: Context, id: String): Boolean = eng(ctx).delete(id)
    fun deleteAll(ctx: Context): Boolean = eng(ctx).deleteAll()
    fun rename(ctx: Context, id: String, title: String): String = eng(ctx).rename(id, title)
    fun setPinned(ctx: Context, id: String, pinned: Boolean): String = eng(ctx).setPinned(id, pinned)
    fun search(ctx: Context, q: String): List<ChatSessionRules.Hit> = eng(ctx).search(q)

    fun exportText(ctx: Context, id: String): String? {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return eng(ctx).exportText(id) { fmt.format(Date(it)) }
    }

    fun exportName(ctx: Context, id: String): String = ChatSessionRules.exportFileName(eng(ctx).chat(id)?.title ?: "")
}
