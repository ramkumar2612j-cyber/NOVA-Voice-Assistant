package com.nova.assistant

/**
 * v6.37 "best AI first". Pure Kotlin (no Android classes), unit tested.
 * The free chain used a fixed order. Now every provider gets a score = quality prior (what we believe about the model)
 * + what NOVA has LEARNED on this phone (recent success rate, speed). The best working provider is tried first, a provider
 * that keeps failing or is slow sinks, and it rises again after it answers well. Cooldowns, keys and the "never rotate
 * several accounts of ONE provider for quota" rule are untouched: this only changes the ORDER.
 * Gemini (the only one that can run phone tools) keeps its own place in NovaService.cloudStep.
 */
object ProviderRanker {
    /** Quality prior per provider id (higher = tried earlier when nothing else is known). Unknown ids get 50. */
    val PRIOR: Map<String, Double> = mapOf(
        "groq" to 90.0,          // gpt-oss-120b
        "groqqwen" to 85.0,      // qwen via the same Groq key
        "zai" to 70.0,
        "zai2" to 69.0,
        "zai3" to 68.0,
        "openrouter" to 60.0,
        "pollinations" to 35.0
    )

    const val ALPHA = 0.2          // how fast new results replace old ones
    const val FULL_TRUST_N = 5     // after this many results the learned part counts fully

    /** rate = recent success rate 0..1 (starts at 0.5), latMs = recent speed of SUCCESSFUL answers (0 = unknown), n = results seen. */
    class Stat(val rate: Double, val latMs: Double, val n: Int)

    fun score(id: String, st: Stat?): Double {
        val prior = PRIOR[id] ?: 50.0
        if (st == null || st.n <= 0) return prior
        val w = minOf(1.0, st.n.toDouble() / FULL_TRUST_N)
        val learned = 40.0 * (st.rate - 0.5)                         // -20 .. +20
        val slow = if (st.latMs > 0.0) minOf(15.0, st.latMs / 600.0) else 0.0   // up to -15 for a very slow provider
        return prior + w * (learned - slow)
    }

    /** Stable sort, best score first. Providers without stats keep their prior. Never drops or adds a provider. */
    fun <T> order(base: List<T>, idOf: (T) -> String, stats: Map<String, Stat>): List<T> =
        base.withIndex()
            .sortedWith(compareByDescending<IndexedValue<T>> { score(idOf(it.value), stats[idOf(it.value)]) }.thenBy { it.index })
            .map { it.value }

    fun update(old: Stat?, success: Boolean, latencyMs: Long): Stat {
        val rate0 = old?.rate ?: 0.5
        val n0 = old?.n ?: 0
        val rate = (1.0 - ALPHA) * rate0 + ALPHA * (if (success) 1.0 else 0.0)
        val lat0 = old?.latMs ?: 0.0
        val lat = if (!success || latencyMs <= 0L) lat0 else if (lat0 <= 0.0) latencyMs.toDouble() else (1.0 - ALPHA) * lat0 + ALPHA * latencyMs
        return Stat(rate.coerceIn(0.0, 1.0), lat.coerceIn(0.0, 600_000.0), minOf(n0 + 1, 1000))
    }

    // ---- tiny text format: id,rate,latMs,n;id,rate,latMs,n   (no JSON, so it is testable on a plain JVM)
    fun encode(m: Map<String, Stat>): String =
        m.entries.joinToString(";") { it.key + "," + it.value.rate + "," + it.value.latMs + "," + it.value.n }

    fun decode(s: String): Map<String, Stat> {
        val out = LinkedHashMap<String, Stat>()
        for (part in s.split(";")) {
            val f = part.split(",")
            if (f.size != 4) continue
            val id = f[0].trim()
            if (id.isEmpty() || id.length > 24 || !id.all { it.isLetterOrDigit() }) continue
            val rate = f[1].toDoubleOrNull() ?: continue
            val lat = f[2].toDoubleOrNull() ?: continue
            val n = f[3].toIntOrNull() ?: continue
            if (!rate.isFinite() || !lat.isFinite() || rate < 0.0 || rate > 1.0 || lat < 0.0 || n < 0) continue
            out[id] = Stat(rate, minOf(lat, 600_000.0), minOf(n, 1000))
        }
        return out
    }

    /** HTTP code -> true (answered), false (provider failed), null (not the provider's fault: offline / our own timeout). */
    fun outcomeOf(code: Int): Boolean? = when {
        code in 200..299 -> true
        code in 100..597 -> false
        else -> null
    }
}
