package com.nova.assistant

/**
 * v7.186 JARVIS BRAIN (add-only). Pure Kotlin, no Android classes, unit-testable.
 *
 * Problem: a spoken sentence first walks through dozens of fixed keyword rules. A sentence that matches no rule
 * goes to a one-shot answer with a short tool list, so a free-form multi-step job ("flipkart par iphone ka sabse
 * sasta price dhundh ke mujhe batao") is not understood as a JOB.
 *
 * Fix: for a sentence that no local command took, ONE short AI call first UNDERSTANDS it and chooses the road:
 *   ANSWER = just talk / explain / look up (existing chat + web answer path)
 *   TOOLS  = one simple phone action (existing tool path: torch, alarm, call, whatsapp, music ...)
 *   AGENT  = a real job that needs several steps in apps or on the web (existing task agent: plan, act, verify, retry)
 * Any failure or doubt gives null and the old path runs unchanged. This file never acts by itself.
 */
object JarvisBrain {
    enum class Mode { ANSWER, TOOLS, AGENT }

    class Decision(val mode: Mode, val understood: String)

    private const val MIN_WORDS = 4
    private const val MAX_CHARS = 600

    /** Cheap pre-check so tiny or empty sentences never cost an extra AI call. */
    fun worthAsking(said: String): Boolean {
        val t = said.trim()
        if (t.length < 12 || t.length > MAX_CHARS) return false
        return t.split(Regex("\\s+")).count { it.isNotBlank() } >= MIN_WORDS
    }

    /** v7.198 UNDERSTAND-FIRST: a long, casual sentence (7+ words) is read by the brain BEFORE the fixed word-patterns, so a misheard filler word cannot trigger a wrong set command. Short direct commands (torch, volume, call) keep the instant local road. */
    fun longNatural(said: String): Boolean {
        val t = said.trim()
        if (t.length < 28 || t.length > MAX_CHARS) return false
        return t.split(Regex("\\s+")).count { it.isNotBlank() } >= 7
    }

    fun system(): String = """You are the understanding brain of JARVIS, a Jarvis-like assistant on the owner's Android phone. The owner speaks Hindi, Hinglish or English, casually, long, with filler words or hints instead of commands. The text may contain small speech-recognition mistakes: read the MEANING of the whole sentence, not single words.
Pick ONE road for what the owner wants:
"answer" = he wants information, an explanation, advice, a calculation, news, a price, or just a talk. No phone action is needed.
"tools" = ONE simple phone action: open an app, call, sms or whatsapp a contact, alarm, timer, torch, volume, brightness, music, navigation, camera, settings, calendar event.
"agent" = a real JOB that needs SEVERAL steps inside apps or on the web: search or compare on a shopping or other website, fill or send something inside an app, read content on the screen and act on it, make a post, edit a photo with an app, check an account, collect information from a site and then do something with it, or any task where the owner says "karo / kar do / dhundh ke / dekh ke / ek ek karke".
If two roads fit, choose "tools" for a single simple action and "agent" for anything with 2 or more steps. Never choose "agent" for a plain question.
Return exactly one JSON object and nothing else: {"mode":"answer|tools|agent","understood":"one short sentence in the owner's language saying what he wants"}"""

    fun user(said: String): String = "OWNER SAID: " + said.trim().take(MAX_CHARS)

    private val MODE_RE = Regex("\"mode\"\\s*:\\s*\"\\s*([A-Za-z_]+)\\s*\"")
    private val SAID_RE = Regex("\"understood\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** null = unclear / not JSON: the caller keeps the old road. */
    fun parse(raw: String?): Decision? {
        if (raw.isNullOrBlank()) return null
        val m = MODE_RE.find(raw)?.groupValues?.get(1)?.lowercase() ?: return null
        val mode = when (m) {
            "answer" -> Mode.ANSWER
            "tools", "tool" -> Mode.TOOLS
            "agent" -> Mode.AGENT
            else -> return null
        }
        val u = SAID_RE.find(raw)?.groupValues?.get(1).orEmpty()
            .replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\").trim().take(240)
        return Decision(mode, u)
    }
}
