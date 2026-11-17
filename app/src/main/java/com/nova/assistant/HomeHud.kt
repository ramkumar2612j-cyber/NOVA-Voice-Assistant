package com.nova.assistant

import android.app.ActivityManager
import android.content.Context
import android.content.Intent        // v7.216-fix: ACTION_BATTERY_CHANGED ke liye chhoot gaya tha (kotlinc ne pakda)
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * v7.216 HOME HUD (add-only): home dashboard (launcher.html + ui.html ka naya JARVIS HOME) ke liye
 * REAL phone numbers ek jagah — battery / RAM / storage / CPU / network / weather / agla reminder /
 * aakhri kaam / news headline. Sirf wahi values jo phone khud de sakta hai; kuch bhi banaya nahi jaata.
 *
 * - Weather launcher wali "launcher" prefs file reuse karta hai (same city + cache keys), isliye
 *   launcher aur app dono ka mausam hamesha ek hi source se aata hai.
 * - Weather / news sirf background thread par fetch hote hain (20 / 30 min cache), UI kabhi block nahi.
 * - CPU % /proc/stat ke do sample ke delta se aata hai (pehli call par 0, uske baad live).
 */
internal object HomeHud {
    private const val PREFS = "launcher" // wahi file jo LauncherActivity mausam ke liye use karta hai
    private var cpuLast = -1
    private var cpuTotal = 0L
    private var cpuIdle = 0L
    private var cpuAt = 0L
    private var wxBusy = false
    private var newsBusy = false

    // ------------------------------------------------------------------ numbers

    /** Battery percent, sticky battery intent se (koi listener nahi chahiye). */
    fun battery(ctx: Context): Int = try {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (l >= 0 && s > 0) l * 100 / s else 0
    } catch (e: Throwable) { 0 }

    /** RAM use % (LauncherRules wala hi percent, jaise launcher home dikhata hai). */
    fun ramUsed(ctx: Context): Int = try {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        LauncherRules.percent(mi.totalMem - mi.availMem, mi.totalMem)
    } catch (e: Throwable) { 0 }

    /** Internal storage use %. */
    fun storageUsed(): Int = try {
        val sf = StatFs(Environment.getDataDirectory().path)
        LauncherRules.percent(sf.totalBytes - sf.availableBytes, sf.totalBytes)
    } catch (e: Throwable) { 0 }

    /** CPU % — /proc/stat ke delta se; 800 ms ke andar dobara poochho to pichhla hi value milta hai. */
    fun cpu(): Int {
        val prev = if (cpuLast < 0) 0 else cpuLast
        try {
            val line = java.io.File("/proc/stat").bufferedReader().readLine() ?: return prev
            val p = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (p.size < 5) return prev
            val total = p.take(8).sum()
            val idle = p[3] + p[4]
            val now = System.currentTimeMillis()
            if (cpuAt > 0L && now - cpuAt > 800) {
                val dT = total - cpuTotal
                val dI = idle - cpuIdle
                if (dT > 0) cpuLast = (100 * (dT - dI) / dT).toInt().coerceIn(0, 100)
            }
            cpuTotal = total; cpuIdle = idle; cpuAt = now
        } catch (e: Throwable) { }
        return if (cpuLast < 0) 0 else cpuLast
    }

    /** Network: WI-FI / MOBILE / NET / OFFLINE — active network ke transport se (nayi permission nahi). */
    fun network(ctx: Context): String {   // v7.216-fix: block body (expression body me return allowed nahi)
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork ?: return "OFFLINE"
            val c = cm.getNetworkCapabilities(n) ?: return "OFFLINE"
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) "WI-FI"
            else if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) "MOBILE"
            else if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) "NET"
            else "OFFLINE"
        } catch (e: Throwable) { "--" }
    }

    /** Aakhri GPS/network fix ka text "lat, lon" — permission pehle se granted ho tabhi; warna "". */
    fun location(ctx: Context): String {   // v7.216-fix: block body (expression body me return allowed nahi)
        return try {
            val ok = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!ok) return ""
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val best = lm.getProviders(true)
                .mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: SecurityException) { null } }
                .maxByOrNull { it.time } ?: return ""
            String.format(java.util.Locale.ROOT, "%.4f, %.4f", best.latitude, best.longitude)
        } catch (e: Throwable) { "" }
    }

    /** Agla scheduled task "HH:MM Label" (sabse pehle bajne wala). Kuch na ho to "". */
    fun reminder(ctx: Context): String {   // v7.216-fix: block body (expression body me return allowed nahi)
        return try {
            val e = ScheduledTaskStore.list(ctx).minByOrNull { it.nextAtMs } ?: return ""
            val t = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.nextAtMs), java.time.ZoneId.systemDefault())
            val label = (e.spec.label.ifBlank { e.spec.kind.name }).take(38)
            String.format(java.util.Locale.ROOT, "%02d:%02d  %s", t.hour, t.minute, label)
        } catch (e: Throwable) { "" }
    }

    /** Aakhri kaam (TaskManagerStore — launcher wali "Aakhri kaam" line hi hai). */
    fun lastWork(ctx: Context): String = try { TaskManagerStore.last(ctx) } catch (e: Throwable) { "" }

    // ------------------------------------------------------------------ weather (launcher ke saath shared)

    private fun http(u: String): String? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Throwable) { null }

    private fun lastKnown(ctx: Context): Pair<Double, Double>? = try {
        val ok = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!ok) null
        else {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            lm.getProviders(true).mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: SecurityException) { null } }
                .maxByOrNull { it.time }?.let { Pair(it.latitude, it.longitude) }
        }
    } catch (e: Throwable) { null }

    /** 20 min se purana mausam ho to background me dobara laao (launcher jaisa hi flow, shared cache). */
    fun refreshWeather(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("w_at", 0L) < 20 * 60_000L) return
        synchronized(this) { if (wxBusy) return; wxBusy = true }
        Thread {
            try {
                var ll = lastKnown(ctx)
                var place = "aapki jagah"
                if (ll == null) {
                    val city = p.getString("city", "") ?: ""
                    if (city.isBlank()) return@Thread
                    ll = LauncherRules.parseGeo(http(WebSources.geoUrl(city)) ?: return@Thread) ?: return@Thread
                    place = city
                }
                val w = LauncherRules.parseWeather(http(WebSources.forecastUrl(ll.first, ll.second)) ?: return@Thread) ?: return@Thread
                p.edit().putString("w_temp", w.tempC.toString() + "°C")
                    .putString("w_text", LauncherRules.weatherLabel(w.code) + " · " + place)
                    .putLong("w_at", System.currentTimeMillis()).apply()
            } catch (e: Throwable) { } finally { synchronized(this) { wxBusy = false } }
        }.start()
    }

    /** Cached mausam "(temp, text)" — khali ho to default. */
    fun weather(ctx: Context): Pair<String, String> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        refreshWeather(ctx)
        return Pair(p.getString("w_temp", "--°") ?: "--°", p.getString("w_text", "Mausam laa raha hoon...") ?: "")
    }

    // ------------------------------------------------------------------ news (cached headline)

    /** Cached headline; 30 min se purani ho to background me fresh laata hai. Kuch na mile to "". */
    fun news(ctx: Context): String {
        refreshNews(ctx)
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("n_text", "") ?: ""
    }

    /** 30 min se purani news ho to background me Google News RSS se ek headline laao. */
    fun refreshNews(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("n_at", 0L) < 30 * 60_000L) return
        synchronized(this) { if (newsBusy) return; newsBusy = true }
        Thread {
            try {
                val body = http(WebSources.newsUrl("")) ?: return@Thread
                val a = WebSources.parseNews(body, "", true, 1) ?: return@Thread
                val line = a.text.replace(Regex("\\s+"), " ").trim().take(110)
                if (line.isNotBlank()) p.edit().putString("n_text", line).putLong("n_at", System.currentTimeMillis()).apply()
            } catch (e: Throwable) { } finally { synchronized(this) { newsBusy = false } }
        }.start()
    }

    /** Ek JSON me sab HUD numbers — WebView bridge se seedha page ko jaata hai. */
    fun json(ctx: Context): String {
        val o = org.json.JSONObject()
        try {
            o.put("run", JarvisService.running)
            o.put("bat", battery(ctx)); o.put("ram", ramUsed(ctx)); o.put("sto", storageUsed()); o.put("cpu", cpu())
            o.put("net", network(ctx)); o.put("loc", location(ctx))
            val w = weather(ctx); o.put("temp", w.first); o.put("wtext", w.second)
            o.put("rem", reminder(ctx)); o.put("msg", lastWork(ctx))
            o.put("news", news(ctx))
            o.put("greet", LauncherRules.greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), false))
        } catch (e: Throwable) { }
        return o.toString()
    }
}
