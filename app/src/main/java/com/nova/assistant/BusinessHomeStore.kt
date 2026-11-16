package com.nova.assistant

import android.content.Context

/**
 * v7.192 L4: reads the REAL stores for the launcher "Aaj ka business" card (LeadStore, AutopilotStore orders, the owner's own
 * WooCommerce / Shopify order list, LeadFinder's last report). Nothing is invented: no data = "abhi koi nahi".
 * The website-order pull is READ only, uses the keys the owner already saved in the AUTOPILOT tab (SecureStore) and keeps no
 * customer phone or address. Not compiled / not phone-tested in this build.
 */
internal object BusinessHomeStore {
    private const val PREF = "biz_home"
    private const val PULL_EVERY_MS = 15L * 60_000L
    private const val MAX_SEEN = 100

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun snapshot(ctx: Context): BusinessHomeRules.Snap {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val p = prefs(app)
        val leads = try { LeadStore.list(app) } catch (e: Throwable) { emptyList() }
        val orders = try { AutopilotStore.orders(app) } catch (e: Throwable) { emptyList() }
        val on = try { AutopilotStore.cfg(app).on } catch (e: Throwable) { false }
        val done = orders.filter { it.step == AutopilotRules.Step.DONE }
        val open = orders.count { it.step != AutopilotRules.Step.DONE && it.item.isNotBlank() }
        val storeToday = if (BusinessHomeRules.isToday(p.getLong("store_day_at", 0L), now)) p.getInt("store_today", 0) else 0
        return BusinessHomeRules.Snap(
            leadsToday = BusinessHomeRules.countToday(leads.map { it.capturedAt }, now), leadsTotal = leads.size,
            ordersToday = BusinessHomeRules.countToday(done.map { it.updated }, now), ordersOpen = open,
            storeOrdersToday = storeToday, storeOrdersTotal = p.getInt("store_total", -1), storeSource = p.getString("store_src", "") ?: "",
            autopilotOn = on, lastReport = lastReport(app)
        )
    }

    /** LeadFinder keeps its last report only in memory: remember it here so it survives a restart (shown for 7 days). */
    private fun lastReport(app: Context): String {
        val p = prefs(app)
        val live = try { LeadFinder.lastReport } catch (e: Throwable) { "" }
        if (live.isNotBlank()) {
            if (live != p.getString("report", "")) p.edit().putString("report", live.take(200)).putLong("report_at", System.currentTimeMillis()).apply()
            return live
        }
        val saved = p.getString("report", "") ?: ""
        return if (saved.isBlank() || System.currentTimeMillis() - p.getLong("report_at", 0L) > 7L * 24 * 3600_000L) "" else saved
    }

    fun voiceReport(ctx: Context, en: Boolean): String = BusinessHomeRules.voiceReport(snapshot(ctx), en)

    fun websiteInfo(ctx: Context): String {
        val p = prefs(ctx)
        return BusinessHomeRules.websiteInfo(p.getString("store_src", "") ?: "", p.getInt("store_total", -1) >= 0, p.getString("store_err", "") ?: "")
    }

    /** City + category the owner saved in his business profile (used by the "leads dhoondo" tap). */
    fun profileCityCategory(ctx: Context): Pair<String, String> = try {
        val b = BizStore.profile(ctx.applicationContext); Pair(b.city, b.category)
    } catch (e: Throwable) { Pair("", "") }

    /**
     * Looks at the owner's own online store (WooCommerce first, else Shopify) at most every 15 minutes and tells the boss about
     * orders that are new since the last look. The very first look only remembers what is already there (no flood of old orders).
     * Worker / background thread only. Returns how many new orders were announced.
     */
    fun pullStoreOrders(ctx: Context, force: Boolean = false): Int {
        val app = ctx.applicationContext
        val p = prefs(app)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong("pull_at", 0L) < PULL_EVERY_MS) return 0
        p.edit().putLong("pull_at", now).apply()
        val cfg = AutopilotStore.cfg(app)
        val wk = SecureStore.getSlot(app, AutopilotNet.SLOT_WOO_K)
        val ws = SecureStore.getSlot(app, AutopilotNet.SLOT_WOO_S)
        val st = SecureStore.getSlot(app, AutopilotNet.SLOT_SHOP)
        val src: String; val out: AutopilotNet.OrdersOut
        if (wk.isNotEmpty() && ws.isNotEmpty() && cfg.wooUrl.isNotBlank()) { src = "woocommerce"; out = AutopilotNet.wooOrdersRaw(cfg.wooUrl, wk, ws) }
        else if (st.isNotEmpty() && cfg.shopDomain.isNotBlank()) { src = "shopify"; out = AutopilotNet.shopifyOrdersRaw(cfg.shopDomain, st) }
        else { p.edit().putString("store_src", "").putString("store_err", "").putInt("store_total", -1).apply(); return 0 }
        if (!out.ok) { p.edit().putString("store_src", src).putString("store_err", out.msg).apply(); return 0 }
        val list = if (src == "woocommerce") BusinessHomeRules.parseWoo(out.raw) else BusinessHomeRules.parseShopify(out.raw)
        if (list.isEmpty() && out.raw.trim().length > 4 && out.raw.trim() != "[]" && !out.raw.contains("\"orders\":[]")) {
            p.edit().putString("store_src", src).putString("store_err", "order list padh nahi payi").apply(); return 0
        }
        val seenNow = (p.getStringSet("seen_$src", null) ?: emptySet()).toSet()
        val firstLook = !p.contains("seen_$src")
        val fresh = BusinessHomeRules.fresh(list.map { it.id }, seenNow)
        p.edit()
            .putString("store_src", src).putString("store_err", "")
            .putInt("store_total", list.size)
            .putInt("store_today", BusinessHomeRules.countToday(list.map { it.createdAt }, now)).putLong("store_day_at", now)
            .putStringSet("seen_$src", (list.map { it.id } + seenNow).distinct().take(MAX_SEEN).toSet())
            .apply()
        if (firstLook || fresh.isEmpty()) return 0
        var told = 0
        for (o in list.filter { it.id in fresh }.take(3)) {
            try { JarvisService.instance?.onStoreOrder(BusinessHomeRules.newStoreOrderCard(o)); told++ } catch (e: Throwable) { ErrLog.add("BusinessHome", "tell: $e") }
        }
        return told
    }
}
