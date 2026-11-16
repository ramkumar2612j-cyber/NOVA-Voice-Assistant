package com.nova.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * v7.192 Part L4: "AAJ KA BUSINESS" card on the launcher home (add-only, pure Kotlin, unit-tested, no Android classes).
 *
 * Every number comes from a real store (LeadStore, AutopilotStore orders, the owner's own WooCommerce / Shopify order list).
 * No data = the words "abhi koi nahi". Nothing here ever invents a number, a price or a customer.
 * HARD DENY (owner decision, never changed): payment, order placing with the boss's money, password, OTP, PIN, CVV.
 * This file never acts by itself and never stores a customer's phone or address.
 */
internal object BusinessHomeRules {
    const val ZONE = "Asia/Kolkata"
    const val NONE = "abhi koi nahi"

    /** What the card shows. storeOrdersTotal < 0 = the website store was never checked (no key / no internet yet). */
    class Snap(
        val leadsToday: Int, val leadsTotal: Int,
        val ordersToday: Int, val ordersOpen: Int,
        val storeOrdersToday: Int, val storeOrdersTotal: Int, val storeSource: String,
        val autopilotOn: Boolean, val lastReport: String
    )

    // ---------------------------------------------------------------- dates

    fun isToday(ts: Long, now: Long, zone: String = ZONE): Boolean {
        if (ts <= 0L) return false
        val z = ZoneId.of(zone)
        return Instant.ofEpochMilli(ts).atZone(z).toLocalDate() == Instant.ofEpochMilli(now).atZone(z).toLocalDate()
    }

    fun countToday(times: List<Long>, now: Long): Int = times.count { isToday(it, now) }

    // ---------------------------------------------------------------- card text

    private fun num(n: Int): String = if (n <= 0) NONE else n.toString()

    fun leadsLine(s: Snap): String =
        "Naye leads aaj: " + num(s.leadsToday) + (if (s.leadsTotal > 0) "  (kul ${s.leadsTotal})" else "")

    fun totalOrdersToday(s: Snap): Int = s.ordersToday.coerceAtLeast(0) + s.storeOrdersToday.coerceAtLeast(0)

    fun ordersLine(s: Snap): String =
        "Naye orders aaj: " + num(totalOrdersToday(s)) + (if (s.ordersOpen > 0) "  (${s.ordersOpen} chat order adhoore)" else "")

    fun autopilotLine(s: Snap): String = "Autopilot: " + if (s.autopilotOn) "chalu" else "band"

    fun reportLine(s: Snap): String = "Aakhri report: " + (if (s.lastReport.isBlank()) NONE else s.lastReport.take(110))

    fun cardLines(s: Snap): List<String> = listOf(ordersLine(s), leadsLine(s), autopilotLine(s), reportLine(s))

    /** The spoken report (TTS). Same numbers as the card. */
    fun voiceReport(s: Snap, en: Boolean): String {
        val o = totalOrdersToday(s)
        val l = s.leadsToday
        return if (en) {
            "Sir, today's business. New orders: " + (if (o == 0) "none yet" else o.toString()) +
                ". New leads: " + (if (l == 0) "none yet" else l.toString()) +
                ". Autopilot is " + (if (s.autopilotOn) "on" else "off") + ". " +
                (if (s.lastReport.isBlank()) "No report yet." else "Last report: " + s.lastReport.take(160))
        } else {
            "Sir, aaj ka business. Naye orders: " + (if (o == 0) "abhi koi nahi" else o.toString()) +
                ". Naye leads: " + (if (l == 0) "abhi koi nahi" else l.toString()) +
                ". Autopilot " + (if (s.autopilotOn) "chalu hai" else "band hai") + ". " +
                (if (s.lastReport.isBlank()) "Abhi koi report nahi." else "Aakhri report: " + s.lastReport.take(160))
        }
    }

    // ---------------------------------------------------------------- card taps -> existing commands

    enum class Tap { ORDERS, LEADS, FIND, AUTOPILOT, REPORT }

    private fun word(s: String): String = s.replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim().take(30)

    /**
     * The sentence the launcher hands to JARVIS (same road as typing it). null = nothing to send (REPORT is spoken, FIND needs the
     * city and category the owner already saved in his business profile).
     */
    fun commandFor(t: Tap, city: String, category: String): String? {
        val c: String? = when (t) {
            Tap.ORDERS -> "jarvis orders dikhao"
            Tap.LEADS -> "jarvis leads dikhao"
            Tap.FIND -> {
                val ci = word(city); val ca = word(category)
                if (ci.isBlank() || ca.isBlank()) null else "jarvis $ci me $ca ke genuine leads dhoondo"
            }
            Tap.AUTOPILOT -> "jarvis autopilot status"
            Tap.REPORT -> null
        }
        return if (c != null && TaskManagerRules.isDenied(c)) null else c
    }

    // ---------------------------------------------------------------- cards over other apps

    fun newLeadCard(who: String): String = "Naya lead: " + word(who).ifBlank { "koi" }

    fun newOrderCard(qty: Int, item: String, who: String): String =
        "Naya order: " + (if (qty > 0) "$qty x " else "") + word(item).ifBlank { "item" } + " - " + word(who).ifBlank { "customer" }

    fun newStoreOrderCard(o: StoreOrder): String =
        "Website order: " + word(o.who).ifBlank { "customer" } + " - " + o.items.take(60) + (if (o.total.isNotBlank()) " (${o.total})" else "")

    /** Ids that were not seen before. */
    fun fresh(ids: List<String>, seen: Set<String>): List<String> = ids.filter { it.isNotBlank() && it !in seen }

    // ---------------------------------------------------------------- website orders: honest "how do orders arrive"

    /** [source] = "woocommerce" | "shopify" | "" (no key saved). [error] = the last pull error, "" when fine. */
    fun websiteInfo(source: String, checkedOk: Boolean, error: String): String = when {
        source.isEmpty() ->
            "Website se seedha order aane ka rasta abhi set nahi hai. Abhi orders sirf WhatsApp / Instagram chat se aate hain (Autopilot). " +
                "Ek aasan step: AUTOPILOT tab mein apni Shopify ya WooCommerce ki key daalo, tab website ke orders yahin dikhenge."
        error.isNotBlank() -> "Website ($source) ke orders abhi nahi aa paye: $error"
        !checkedOk -> "Website ($source) ke orders abhi check nahi hue. Thodi der baad (har 15 minute) dikhenge."
        else -> "Website ($source) ke orders har 15 minute mein check hote hain."
    }

    // ---------------------------------------------------------------- store orders (read-only, no phone / address kept)

    class StoreOrder(val id: String, val who: String, val total: String, val items: String, val createdAt: Long)

    private fun items(a: JSONArray?, nameKey: String): String {
        if (a == null) return ""
        val parts = ArrayList<String>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val n = o.optString(nameKey).take(40)
            if (n.isBlank()) continue
            val q = o.optInt("quantity", 1)
            parts.add(if (q > 1) "$q x $n" else n)
            if (parts.size >= 3) break
        }
        return parts.joinToString(", ")
    }

    /** WooCommerce GET /wp-json/wc/v3/orders (a JSON array). Bad / empty text = empty list. */
    fun parseWoo(raw: String): List<StoreOrder> = try {
        val a = JSONArray(raw)
        val out = ArrayList<StoreOrder>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            val b = o.optJSONObject("billing")
            val who = ((b?.optString("first_name").orEmpty()) + " " + (b?.optString("last_name").orEmpty())).trim().take(40)
            val total = (o.optString("total") + " " + o.optString("currency")).trim().take(20)
            val at = try { LocalDateTime.parse(o.optString("date_created_gmt")).toInstant(ZoneOffset.UTC).toEpochMilli() } catch (e: Exception) { 0L }
            out.add(StoreOrder("woo-$id", who, total, items(o.optJSONArray("line_items"), "name"), at))
        }
        out
    } catch (e: Exception) { emptyList() }

    /** Shopify GET /admin/api/.../orders.json ({"orders":[...]}). Bad / empty text = empty list. */
    fun parseShopify(raw: String): List<StoreOrder> = try {
        val a = JSONObject(raw).optJSONArray("orders") ?: JSONArray()
        val out = ArrayList<StoreOrder>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            val c = o.optJSONObject("customer")
            val who = ((c?.optString("first_name").orEmpty()) + " " + (c?.optString("last_name").orEmpty())).trim().take(40)
            val total = (o.optString("total_price") + " " + o.optString("currency")).trim().take(20)
            val at = try { OffsetDateTime.parse(o.optString("created_at")).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
            out.add(StoreOrder("shop-$id", who, total, items(o.optJSONArray("line_items"), "title"), at))
        }
        out
    } catch (e: Exception) { emptyList() }
}
