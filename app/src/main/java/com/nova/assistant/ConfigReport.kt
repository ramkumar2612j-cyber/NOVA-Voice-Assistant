package com.nova.assistant

/**
 * v6.70 (Part 2): one clear answer to "what is not set up?". Built only from the REAL state the caller reads (keys saved, permissions
 * granted). It says "configured / not configured", never "working": a saved key can still be wrong or out of quota, and only a real
 * request proves that. Pure Kotlin (no Android), unit tested.
 */
object ConfigReport {
    class State(
        val gemini: Boolean, val groq: Boolean, val openrouter: Boolean, val zai: Boolean,
        val keyless: Boolean, val chainOn: Boolean, val mic: Boolean, val accessibility: Boolean,
        val notifAccess: Boolean, val overlay: Boolean, val localModel: Boolean,
        // v6.74 (Part 5): voice-stack facts. Defaults keep every older caller and test unchanged (nothing extra is reported).
        val ttsInitFailed: Boolean = false, val batteryRestricted: Boolean = false,
        val startFailure: VoiceDiagnosis.StartFailure = VoiceDiagnosis.StartFailure.NONE
    )

    private val QUERIES = setOf(
        "setup check", "config check", "configuration check", "setup status", "config status", "missing keys", "missing key",
        "kya missing hai", "kya setup baaki hai", "setup baaki hai", "kaun si key nahi hai", "kya setup chahiye", "what is missing",
        "what is not set up", "kya kya band hai"
    )

    fun isQuery(raw: String): Boolean = Logic.norm(raw).removePrefix("nova ").trim() in QUERIES

    /** Online keys the AGENT can use: Gemini, or a key-based free provider (no keyless one, it cannot read the screen task). */
    fun agentReady(s: State): Boolean = s.gemini || (s.chainOn && (s.groq || s.openrouter || s.zai))

    /** Any answer source for plain questions: Gemini, the free chain (keyed or keyless) or the offline model. */
    fun chatReady(s: State): Boolean = s.gemini || (s.chainOn && (s.groq || s.openrouter || s.zai || s.keyless)) || s.localModel

    /** What the agent says when a job needs an AI and none is configured. */
    fun noAiForAgent(en: Boolean): String =
        if (en) "I cannot do this job: no AI key is configured for it. Add a Gemini, Groq, OpenRouter or Z.ai key in Setup, then ask again."
        else "Ye kaam abhi nahi ho sakta: iske liye koi AI key set nahi hai. Setup me Gemini, Groq, OpenRouter ya Z.ai key daalo, phir bolo."

    fun missing(s: State, en: Boolean): List<String> {
        val m = ArrayList<String>()
        if (!s.mic) m.add(if (en) "Microphone permission: not granted (voice commands cannot be heard)" else "Microphone permission: nahi mili (awaaz nahi sunai degi)")
        if (!s.accessibility) m.add(if (en) "Accessibility: OFF (tap, scroll, screen reading and in-app jobs cannot run)" else "Accessibility: OFF (tap, scroll, screen padhna aur app ke andar kaam nahi honge)")
        if (!agentReady(s)) m.add(if (en) "AI key for phone jobs: not configured (Gemini, Groq, OpenRouter or Z.ai)" else "Phone jobs ke liye AI key: set nahi hai (Gemini, Groq, OpenRouter ya Z.ai)")
        else if (!s.gemini) m.add(if (en) "Gemini key: not configured (backup AI is used; no picture understanding)" else "Gemini key: set nahi hai (backup AI chalega; photo samajhna nahi hoga)")
        if (!chatReady(s)) m.add(if (en) "Chat answers: no online key and no offline model" else "Chat jawab: na online key hai na offline model")
        if (s.startFailure != VoiceDiagnosis.StartFailure.NONE) m.add(VoiceDiagnosis.startFailureText(s.startFailure, en))
        if (s.ttsInitFailed) m.add(if (en) "Spoken replies: the phone's text-to-speech engine did not start (Android Settings > Text-to-speech)" else "Bol kar jawab: phone ka text-to-speech engine start nahi hua (Android Settings > Text-to-speech)")
        if (s.batteryRestricted) m.add(if (en) "Battery restriction is ON: Android may stop NOVA's microphone in the background" else "Battery restriction ON hai: Android background me NOVA ka mic band kar sakta hai")
        if (!s.notifAccess) m.add(if (en) "Notification access: OFF (driving mode and message reading)" else "Notification access: OFF (driving mode aur message sunana)")
        if (!s.overlay) m.add(if (en) "Display over other apps: OFF (the wake card will not show)" else "Display over other apps: OFF (wake card nahi dikhega)")
        return m
    }

    fun report(s: State, en: Boolean): String {
        val m = missing(s, en)
        if (m.isEmpty()) return if (en) "Everything needed is configured. A saved key can still be wrong or out of quota; a real request shows that."
        else "Zaroori sab kuch set hai. Saved key galat ya limit khatam ho sakti hai; asli request se hi pata chalega."
        return (if (en) "Not configured (${m.size}):\n" else "Set nahi hai (${m.size}):\n") + m.joinToString("\n") { "- $it" }
    }
}
