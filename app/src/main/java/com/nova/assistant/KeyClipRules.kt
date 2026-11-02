package com.nova.assistant

/**
 * v6.94: which API key is this clipboard text? Only keys with an unmistakable shape are recognised (Groq gsk_..., OpenRouter sk-or-v1-...,
 * Gemini AIza...), so ordinary copied text is never mistaken for a key. Pure rules, unit tested.
 */
object KeyClipRules {
    const val GROQ = "groq"
    const val OPENROUTER = "openrouter"
    const val GEMINI = "gemini"

    private val GROQ_RE = Regex("^gsk_[A-Za-z0-9]{40,120}$")
    private val OPENROUTER_RE = Regex("^sk-or-v1-[A-Za-z0-9]{40,120}$")
    private val GEMINI_RE = Regex("^AIza[0-9A-Za-z_\\-]{35}$")

    fun kind(text0: String?): String? {
        val t = text0?.trim() ?: return null
        if (t.length < 30 || t.length > 200 || t.any { it.isWhitespace() }) return null
        return when {
            GROQ_RE.matches(t) -> GROQ
            OPENROUTER_RE.matches(t) -> OPENROUTER
            GEMINI_RE.matches(t) -> GEMINI
            else -> null
        }
    }

    fun label(kind: String): String = when (kind) {
        GROQ -> "Groq"
        OPENROUTER -> "OpenRouter"
        else -> "Gemini"
    }
}
