package com.nova.assistant

/** v7.189 Part L1: pure rules for the JARVIS launcher (no Android classes, so they are unit-tested). */
internal object LauncherRules {
    class AppItem(val label: String, val pkg: String)

    /** Apps sorted A-Z (case-insensitive), duplicates by package removed. */
    fun sorted(apps: List<AppItem>): List<AppItem> =
        apps.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }

    /** Filter by what the owner typed. Blank = all apps. Matches the start of any word first, then anywhere. */
    fun filter(apps: List<AppItem>, query: String): List<AppItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps
        val starts = apps.filter { a -> a.label.lowercase().split(' ').any { it.startsWith(q) } }
        val rest = apps.filter { a -> a !in starts && a.label.lowercase().contains(q) }
        return starts + rest
    }

    /** True when the typed text looks like a command for JARVIS rather than an app name (several words / a verb). */
    fun looksLikeCommand(text: String, apps: List<AppItem>): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (filter(apps, t).size == 1 && t.split(' ').size <= 2) return false
        return t.split(' ').size >= 3 || t.endsWith("?")
    }

    fun greeting(hour: Int, en: Boolean): String = when {
        hour in 5..11 -> if (en) "Good morning, boss" else "Suprabhat, boss"
        hour in 12..16 -> if (en) "Good afternoon, boss" else "Namaste, boss"
        hour in 17..20 -> if (en) "Good evening, boss" else "Shubh sandhya, boss"
        else -> if (en) "Good night, boss" else "Shubh ratri, boss"
    }

    // ---------------------------------------------------------------- v7.189 Part L2: weather + status bars

    /** Open-Meteo weather_code -> short Hinglish label. */
    fun weatherLabel(code: Int): String = when (code) {
        0 -> "Saaf dhoop"
        1, 2 -> "Halke badal"
        3 -> "Badal chhaye"
        45, 48 -> "Kohra"
        in 51..57 -> "Halki phuhaar"
        in 61..67 -> "Barish"
        in 71..77 -> "Baraf"
        in 80..82 -> "Barish ki bauchhar"
        85, 86 -> "Baraf ki bauchhar"
        in 95..99 -> "Toofan"
        else -> "Mausam"
    }

    class Weather(val tempC: Int, val code: Int)

    /** Reads {"current":{"temperature_2m":24.3,"weather_code":1}} from Open-Meteo; null for anything else. */
    fun parseWeather(body: String): Weather? = try {
        val cur = org.json.JSONObject(body.trim()).optJSONObject("current")
        if (cur == null || !cur.has("temperature_2m")) null
        else Weather(Math.round(cur.getDouble("temperature_2m")).toInt(), cur.optInt("weather_code", -1))
    } catch (e: Exception) { null }

    /** Reads the first latitude/longitude from the Open-Meteo geocoding answer; null when the city is unknown. */
    fun parseGeo(body: String): Pair<Double, Double>? = try {
        val r = org.json.JSONObject(body.trim()).optJSONArray("results")?.optJSONObject(0)
        if (r == null || !r.has("latitude") || !r.has("longitude")) null else Pair(r.getDouble("latitude"), r.getDouble("longitude"))
    } catch (e: Exception) { null }

    fun percent(used: Long, total: Long): Int = if (total <= 0L) 0 else ((used * 100) / total).toInt().coerceIn(0, 100)

    /** Bar colour: battery is good when HIGH, RAM / storage are worrying when HIGH. */
    fun barColor(pct: Int, higherIsBetter: Boolean): Int {
        val good = 0xFF2BE36B.toInt(); val mid = 0xFFFFC53D.toInt(); val bad = 0xFFFF4D4D.toInt()
        val p = if (higherIsBetter) pct else 100 - pct
        return if (p >= 50) good else if (p >= 20) mid else bad
    }
}
