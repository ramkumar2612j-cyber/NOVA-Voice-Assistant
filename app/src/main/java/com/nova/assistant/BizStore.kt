package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * v7.169 BIZ store: the owner's business profile, today's lead counter, and the delivery promises (due time per confirmed order).
 * Plain SharedPreferences, capped. The Google Places key is NOT here: it lives encrypted in SecureStore slot SLOT_PLACES.
 */
object BizStore {
    const val SLOT_PLACES = "biz_places"
    private const val PREFS = "nova_biz"
    private const val MAX_DELIVERIES = 100

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized fun profile(ctx: Context): BizRules.Profile = try {
        val s = p(ctx)
        BizRules.Profile(
            s.getString("product", "").orEmpty(), s.getInt("price", 0), s.getInt("advance", 0), s.getString("upi", "").orEmpty(),
            s.getString("payee", "").orEmpty(), s.getString("delivery", "").orEmpty(), s.getString("city", "").orEmpty(),
            s.getString("category", "").orEmpty(), s.getInt("perday", 5).coerceIn(BizRules.MIN_LEADS, BizRules.MAX_LEADS), s.getBoolean("leads_on", false)
        )
    } catch (e: Exception) { BizRules.Profile() }

    @Synchronized fun saveProfile(ctx: Context, b: BizRules.Profile): Boolean = p(ctx).edit()
        .putString("product", b.product).putInt("price", b.price).putInt("advance", b.advancePct).putString("upi", b.upi)
        .putString("payee", b.payee).putString("delivery", b.deliveryText).putString("city", b.city).putString("category", b.category)
        .putInt("perday", b.perDay).putBoolean("leads_on", b.leadsOn).commit()

    @Synchronized fun addedToday(ctx: Context): Int {
        val s = p(ctx)
        return if (s.getString("lead_date", "") == LocalDate.now().toString()) s.getInt("lead_count", 0).coerceAtLeast(0) else 0
    }

    @Synchronized fun noteAdded(ctx: Context, n: Int) {
        val s = p(ctx)
        val now = addedToday(ctx)
        s.edit().putString("lead_date", LocalDate.now().toString()).putInt("lead_count", now + n).commit()
    }

    // ---------------------------------------------------------------- delivery promises

    class Delivery(val orderId: String, val chat: String, val name: String, val phone: String, val item: String, val dueAt: Long, val notified: Boolean)

    @Synchronized fun deliveries(ctx: Context): List<Delivery> = try {
        val a = JSONArray(p(ctx).getString("deliveries", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Delivery(o.optString("id"), o.optString("chat"), o.optString("name"), o.optString("phone"), o.optString("item"), o.optLong("due"), o.optBoolean("n"))
        }
    } catch (e: Exception) { emptyList() }

    private fun saveDeliveries(ctx: Context, l: List<Delivery>): Boolean {
        val a = JSONArray()
        for (d in l.takeLast(MAX_DELIVERIES)) a.put(JSONObject().put("id", d.orderId).put("chat", d.chat).put("name", d.name)
            .put("phone", d.phone).put("item", d.item).put("due", d.dueAt).put("n", d.notified))
        return p(ctx).edit().putString("deliveries", a.toString()).commit()
    }

    /** One promise per order id (a repeated confirm never adds a second one). */
    @Synchronized fun addDelivery(ctx: Context, d: Delivery): Boolean {
        val l = deliveries(ctx)
        if (l.any { it.orderId == d.orderId }) return false
        return saveDeliveries(ctx, l + d)
    }

    /** Promises whose time has come and the owner has not been told yet; marks them told. */
    @Synchronized fun takeDue(ctx: Context, now: Long): List<Delivery> {
        val l = deliveries(ctx)
        val due = l.filter { !it.notified && it.dueAt in 1..now }
        if (due.isEmpty()) return emptyList()
        val ids = due.map { it.orderId }.toSet()
        saveDeliveries(ctx, l.map { if (it.orderId in ids) Delivery(it.orderId, it.chat, it.name, it.phone, it.item, it.dueAt, true) else it })
        return due
    }
}
