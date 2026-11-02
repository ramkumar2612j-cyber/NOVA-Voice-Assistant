package com.nova.assistant

/**
 * v7.149 STEP 9 OF 10: modern LIGHT popup. The default stays the dark Ultron card; the light theme is OPT-IN ("nova light popup karo",
 * "nova dark popup karo") so a phone that is not proven stable yet gets no surprise. Pure Kotlin (no Android), unit tested:
 * palette, text-colour mapping and the slow shimmer sweep. NovaHud's HudView only calls these.
 */
object HudTheme {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val INK = 0xFF0B1B3A.toInt()
    private const val SOFT = 0xFF51658A.toInt()

    /** Three-stop card gradient. Light = frosted white-blue (rose tint while speaking); dark = the v7.121 Ultron colours. */
    fun fillColors(light: Boolean, speaking: Boolean): IntArray = when {
        light && speaking -> intArrayOf(0xF7FFF1F3.toInt(), 0xF7FFE4E9.toInt(), 0xF7FFEEF1.toInt())
        light -> intArrayOf(0xF7F8FBFF.toInt(), 0xF7E8F1FF.toInt(), 0xF7F3F7FF.toInt())
        speaking -> intArrayOf(0xF2300A16.toInt(), 0xF2180610.toInt(), 0xF2220812.toInt())
        else -> intArrayOf(0xF2081A48.toInt(), 0xF2050D2C.toInt(), 0xF2081034.toInt())
    }

    /** Flat panel colour (menu card, minimized pill). */
    fun flat(light: Boolean): Int = if (light) 0xF7F2F6FF.toInt() else 0xF20A1236.toInt()

    /** Dark-theme text colour -> readable colour for the theme. Unknown colours pass through unchanged. */
    fun ink(light: Boolean, c: Int): Int {
        if (!light) return c
        return when (c) {
            WHITE, 0xFFF4FAFF.toInt() -> INK
            0xFFEAF2FF.toInt() -> 0xFF12234A.toInt()
            0xFFA9BCD9.toInt(), 0xFFA9C4E8.toInt(), 0xFF9FB4D6.toInt() -> SOFT
            else -> c
        }
    }

    /** Bright state colours (cyan) are unreadable as text on white: darken them for the light theme only. */
    fun stateInk(light: Boolean, c: Int): Int = if (light) HudMath.mix(c, 0xFF000000.toInt(), 0.42f) else c

    const val SHIMMER_MS = 2800L

    /** 0..1 position of the soft sweep, repeating. */
    fun shimmer(nowMs: Long): Float = ((nowMs % SHIMMER_MS).toFloat() / SHIMMER_MS).coerceIn(0f, 1f)

    /** Alpha 0..1 of the sweep: 0 at both ends (no pop), peak in the middle, kept subtle. */
    fun shimmerAlpha(p: Float): Float {
        val x = p.coerceIn(0f, 1f)
        return 0.16f * (4f * x * (1f - x))
    }

    /** Spoken phrase -> true = light, false = dark, null = not about the popup. Needs the word "popup" so "torch light" never matches. */
    fun parse(text: String): Boolean? {
        val n = text.lowercase()
        if (!n.contains("popup") && !n.contains("pop up") && !n.contains("pop-up")) return null
        val light = listOf("light", "halka", "halki", "white", "safed", "safaid", "ujala").any { n.contains(it) }
        val dark = listOf("dark", "andhera", "kala", "black", "ultron", "purana").any { n.contains(it) }
        return if (light == dark) null else light
    }

    /** v7.152: a QUESTION about the popup ("popup light hai?", "popup light hai ya dark?", "popup ka rang kya hai")
     *  must answer the state, not silently flip the setting — NovaService sends it to the FeatureVoice "popuptheme"
     *  status route instead. EVERYTHING ELSE keeps the old v7.149 behaviour: "popup dark", "light popup karo",
     *  "popup halka kar do" all still switch the theme, so no old command is removed. Word-boundary match, so
     *  "phone" never looks like it contains "on" and "kahan" never matches "hai". */
    fun isQuestion(text: String): Boolean {
        val n = text.lowercase()
        if (n.contains("?")) return true
        val qWords = setOf("kya", "kaun", "batao", "bata", "bataiye", "bata do", "status", "hai", "ya", "nahi")
        return n.split(" ", ",", "!", "\u0964").any { it.trim() in qWords }
    }
}
