package com.nova.assistant

/**
 * v6.90: the owner picks which AI answers chat questions, and whether NOVA may fall back to the other AIs when that one fails.
 * Pure Kotlin (no Android classes), unit tested. "auto" = the old behaviour (Gemini first, then the ranked free chain).
 * choice = "auto" | "gemini" | a free provider id ("groq", "groqqwen", "zai", "zai2", "zai3", "openrouter", "pollinations").
 */
object AiChoice {
    const val AUTO = "auto"
    const val GEMINI = "gemini"

    enum class GeminiMode { FIRST, AFTER_FREE, NEVER }

    /** Unknown / empty values become "auto" so a damaged setting can never leave NOVA without an AI. */
    fun clean(raw: String?, providerIds: Collection<String>): String {
        val v = raw?.trim()?.lowercase().orEmpty()
        return if (v == GEMINI || v in providerIds) v else AUTO
    }

    private fun isFree(choice: String) = choice != AUTO && choice != GEMINI

    /** Gemini (the only AI that can run phone tools itself) goes first for auto/gemini; after the free chain when a free AI was chosen. */
    fun geminiMode(choice: String, fallback: Boolean): GeminiMode = when {
        !isFree(choice) -> GeminiMode.FIRST
        fallback -> GeminiMode.AFTER_FREE
        else -> GeminiMode.NEVER
    }

    /** False only when the owner chose Gemini alone (no fallback): the free chain must not run at all. */
    fun chainAllowed(choice: String, fallback: Boolean): Boolean = !(choice == GEMINI && !fallback)

    /** Chosen free provider first. Without fallback ONLY the chosen provider is tried. auto / gemini keep the ranked order. */
    fun <T> order(ranked: List<T>, idOf: (T) -> String, choice: String, fallback: Boolean): List<T> {
        if (!isFree(choice)) return ranked
        val chosen = ranked.filter { idOf(it) == choice }
        if (!fallback) return chosen
        return chosen + ranked.filter { idOf(it) != choice }
    }

    /** Short chip text, e.g. "AI: AUTO", "AI: GROQ". */
    fun chipLabel(choice: String): String = "AI: " + when (choice) {
        AUTO -> "AUTO"
        GEMINI -> "GEMINI"
        else -> choice.uppercase()
    }
}
