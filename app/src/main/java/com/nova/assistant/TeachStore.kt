package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local, bounded, validated alias table. Aliases are never sent to a server or written to logs. */
object TeachStore {
    private const val FILE = "teach_once.json"
    private const val MAX = 100
    data class Item(val alias: String, val value: String)

    @Synchronized private fun load(ctx: Context): MutableList<Item> {
        val out = ArrayList<Item>()
        try {
            val f = File(ctx.filesDir, FILE)
            if (!f.isFile || f.length() > 25_000) return out
            val a = JSONArray(f.readText(Charsets.UTF_8))
            for (i in 0 until minOf(a.length(), MAX)) {
                val o = a.optJSONObject(i) ?: continue
                val alias = o.optString("alias").trim(); val value = o.optString("value").trim()
                if (TeachRules.valid(alias, value) && out.none { it.alias.equals(alias, true) }) out.add(Item(alias, value))
            }
        } catch (ignored: Exception) { ErrLog.add("TeachStore", ignored.toString()) }
        return out
    }

    @Synchronized private fun save(ctx: Context, items: List<Item>): Boolean = try {
        val a = JSONArray()
        items.take(MAX).forEach { a.put(JSONObject().put("alias", it.alias).put("value", it.value)) }
        val f = File(ctx.filesDir, FILE); val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(a.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(f)) { f.writeText(a.toString(), Charsets.UTF_8); tmp.delete() }
        true
    } catch (ignored: Exception) { false }

    @Synchronized fun add(ctx: Context, alias: String, value: String): String {
        if (!TeachRules.valid(alias, value)) return "invalid"
        val list = load(ctx); val idx = list.indexOfFirst { it.alias.equals(alias.trim(), true) }
        if (idx >= 0) list[idx] = Item(alias.trim(), value.trim())
        else { if (list.size >= MAX) return "full"; list.add(Item(alias.trim(), value.trim())) }
        return if (save(ctx, list)) "ok" else "fail"
    }

    @Synchronized fun delete(ctx: Context, alias: String): Boolean {
        val list = load(ctx); val filtered = list.filterNot { it.alias.equals(alias, true) }
        return filtered.size != list.size && save(ctx, filtered)
    }

    @Synchronized fun listJson(ctx: Context): String {
        val a = JSONArray(); load(ctx).forEach { a.put(JSONObject().put("alias", it.alias).put("value", it.value)) }
        return a.toString()
    }

    /** Apply only when an alias is clearly the command target; never rewrite dictated-message text or arbitrary chat. */
    @Synchronized fun applyTargets(ctx: Context, text: String): String {
        if (text.length > 400 || Regex("(?i)\\b(message|whatsapp|dictate|reply|type|likho)\\b.*\\b(bhejo|send|likho|type)\\b").containsMatchIn(text)) return text
        var out = text
        for (item in load(ctx)) {
            val escaped = Regex.escape(item.alias)
            val target = Regex("(?i)\\b$escaped\\b(?=\\s+(ko\\s+)?(call|phone|message|whatsapp|kholo|khol|open|camera))")
            if (target.containsMatchIn(out)) out = target.replace(out) { item.value }
        }
        return out
    }

    @Synchronized fun exportJson(ctx: Context): String {
        val items = load(ctx); val a = JSONArray()
        items.forEach { a.put(JSONObject().put("alias", it.alias).put("value", it.value)) }
        return JSONObject().put("version", 1).put("items", a).toString()
    }

    @Synchronized fun importJson(ctx: Context, raw: String): String {
        if (!TeachRules.exportValidJson(raw)) return "invalid"
        return try {
            val root = JSONObject(raw); val a = root.getJSONArray("items")
            val next = ArrayList<Item>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i); val item = Item(o.getString("alias").trim(), o.getString("value").trim())
                val old = next.indexOfFirst { it.alias.equals(item.alias, true) }
                if (old >= 0) next[old] = item else next.add(item)
            }
            if (next.size > MAX) "invalid" else if (save(ctx, next)) "ok" else "fail"
        } catch (ignored: Exception) { "invalid" }
    }
}
