package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Local-only store for publicly visible business contacts and owner-reviewed outreach drafts. */
object LeadStore {
    data class Lead(
        val id: String,
        val name: String,
        val phone: String,
        val place: String,
        val url: String,
        val source: String,
        val capturedAt: Long,
        val draft: String = "",
        val draftLanguage: String = "hi",
        val approved: Boolean = false,
        val sent: Boolean = false
    )

    private const val PREFS = "nova_public_leads"
    private const val KEY_ITEMS = "items_v1"
    private const val MAX_LEADS = 100
    private const val DEFAULT_CAP = 20

    @Synchronized private fun all(ctx: Context): MutableList<Lead> {
        val raw = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null) ?: return mutableListOf()
        return try {
            val a = JSONArray(raw)
            val out = ArrayList<Lead>()
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val name = LeadRules.clean(o.optString("name")).take(100)
                if (name.isBlank()) continue
                out.add(Lead(
                    o.optString("id").take(80).ifBlank { UUID.randomUUID().toString() }, name,
                    o.optString("phone").filter { it.isDigit() || it == '+' }.take(18),
                    LeadRules.clean(o.optString("place")).take(140), o.optString("url").take(220),
                    o.optString("source").take(100), o.optLong("captured", 0L),
                    LeadRules.cleanDraft(o.optString("draft")), if (o.optString("lang") == "en") "en" else "hi",
                    o.optBoolean("approved", false), o.optBoolean("sent", false)
                ))
            }
            out.takeLast(MAX_LEADS).toMutableList()
        } catch (ignored: Exception) { mutableListOf() }
    }

    @Synchronized private fun save(ctx: Context, items: List<Lead>): Boolean {
        val arr = JSONArray()
        for (l in items.takeLast(MAX_LEADS)) {
            arr.put(JSONObject().put("id", l.id).put("name", l.name.take(100)).put("phone", l.phone.take(18))
                .put("place", l.place.take(140)).put("url", l.url.take(220)).put("source", l.source.take(100))
                .put("captured", l.capturedAt).put("draft", l.draft.take(450)).put("lang", l.draftLanguage)
                .put("approved", l.approved).put("sent", l.sent))
        }
        return ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, arr.toString()).commit()
    }

    @Synchronized fun add(ctx: Context, c: LeadRules.Candidate): Pair<Boolean, Lead?> {
        val cleanName = LeadRules.clean(c.name).take(100)
        if (cleanName.isBlank() || (!LeadRules.isAllowedPublicSource(c.sourcePackage))) return Pair(false, null)
        val items = all(ctx)
        val duplicate = items.indexOfFirst { old ->
            (c.phone.isNotBlank() && old.phone.isNotBlank() && digits(old.phone) == digits(c.phone)) ||
                (c.url.isNotBlank() && old.url.equals(c.url, true)) ||
                (old.name.equals(cleanName, true) && old.place.equals(LeadRules.clean(c.place), true))
        }
        if (duplicate >= 0) return Pair(true, items[duplicate])
        if (items.size >= MAX_LEADS) return Pair(false, null)
        val l = Lead(UUID.randomUUID().toString(), cleanName, c.phone.take(18), LeadRules.clean(c.place).take(140), c.url.take(220), c.sourcePackage, System.currentTimeMillis())
        items.add(l)
        return Pair(save(ctx, items), l)
    }

    @Synchronized fun list(ctx: Context): List<Lead> = all(ctx).toList()

    @Synchronized fun listText(ctx: Context): String {
        val items = all(ctx)
        if (items.isEmpty()) return "Lead list khaali hai. Google Maps ya Chrome mein ek public business details page kholo, phir bolo: lead capture."
        return "${items.size} local lead(s). " + items.take(12).joinToString(" ") { l ->
            "${l.name}${if (l.phone.isNotBlank()) ", phone ${l.phone}" else ", phone nahi mila"}${if (l.place.isNotBlank()) ", ${l.place}" else ""}${if (l.draft.isNotBlank()) ", draft ready" else ""}${if (l.sent) ", sent button pressed" else ""}."
        }.take(2200)
    }

    @Synchronized fun createDraft(ctx: Context, language: String, id: String, aiText: String): Boolean {
        val items = all(ctx)
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val old = items[idx]
        val lang = if (language == "en") "en" else "hi"
        val draft = LeadRules.draft(old.name, old.place, lang, aiText)
        items[idx] = old.copy(draft = draft, draftLanguage = lang, approved = false)
        return save(ctx, items)
    }

    @Synchronized fun missingDrafts(ctx: Context, language: String): List<Lead> = all(ctx).filter { it.draft.isBlank() || it.draftLanguage != language }

    @Synchronized fun drafts(ctx: Context): List<Lead> = all(ctx).filter { it.draft.isNotBlank() && !it.sent }

    @Synchronized fun eligibleWhatsAppDrafts(ctx: Context): List<Lead> = drafts(ctx).filter { digits(it.phone).length in 10..15 }

    @Synchronized fun previewBatch(items: List<Lead>): String = buildString {
        append("LEAD BATCH REVIEW — exact saved text; nothing sent yet.\n")
        items.take(20).forEachIndexed { i, l ->
            append(i + 1).append(". ").append(l.name).append(" | ").append(l.phone).append("\n")
            append(l.draft).append("\n\n")
        }
    }.take(18000)

    @Synchronized fun approveBatch(ctx: Context, ids: Set<String>): List<Lead> {
        val items = all(ctx)
        val next = items.map { if (it.id in ids && it.draft.isNotBlank() && !it.sent) it.copy(approved = true) else it }
        if (!save(ctx, next)) return emptyList()
        return next.filter { it.id in ids && it.approved && !it.sent }
    }

    @Synchronized fun approvedBatch(ctx: Context): List<Lead> = all(ctx).filter { it.approved && !it.sent && it.draft.isNotBlank() && digits(it.phone).length in 10..15 }

    fun dailyCap(ctx: Context): Int = try {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("daily_cap_whatsapp", DEFAULT_CAP).coerceIn(1, DEFAULT_CAP)
    } catch (ignored: Exception) { DEFAULT_CAP }

    @Synchronized fun lowerDailyCap(ctx: Context, proposed: Int): Int {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = dailyCap(ctx)
        val new = proposed.coerceIn(1, DEFAULT_CAP).coerceAtMost(old)
        p.edit().putInt("daily_cap_whatsapp", new).commit()
        return new
    }

    fun sentToday(ctx: Context, app: String = "whatsapp"): Int = try {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("sent_date_$app", "") != LocalDate.now().toString()) 0 else p.getInt("sent_count_$app", 0).coerceAtLeast(0)
    } catch (ignored: Exception) { 0 }

    @Synchronized fun recordSent(ctx: Context, id: String, app: String = "whatsapp"): Boolean {
        val items = all(ctx)
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val count = if (p.getString("sent_date_$app", "") == today) p.getInt("sent_count_$app", 0) else 0
        if (count >= dailyCap(ctx)) return false
        items[idx] = items[idx].copy(approved = false, sent = true)
        return save(ctx, items) && p.edit().putString("sent_date_$app", today).putInt("sent_count_$app", count + 1).commit()
    }

    @Synchronized fun clear(ctx: Context): Boolean {
        return ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ITEMS).commit()
    }

    private fun digits(s: String): String = s.filter(Char::isDigit)
}
