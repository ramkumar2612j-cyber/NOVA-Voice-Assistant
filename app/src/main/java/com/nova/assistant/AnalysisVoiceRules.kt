package com.nova.assistant

/**
 * v7.59 PART 13D - pure rules for starting an analysis by VOICE / typed sentence and for a CHAT PHOTO + question (no Android, no network; JUnit-able).
 * Uses only MediaIntake (pure) and AnalysisRules / AnalysisPipelineRules. The Android side is NovaService (voice) and MainActivity.chatPhotoAsk (chat photo).
 *
 * Decisions that are made HERE so the owner never has to choose:
 *  - Which file: the NEWEST shared photo / video (by creation time). Its name is said out loud. A link is never analysed.
 *  - ANALYSE = a media word (video / reel / photo / image / clip / media) with an analyse word, OR "ye / is / isko" + media word + a what / who / check word.
 *    CHECK = "isko check karo ki sach hai ya nahi" (no media word): only acted on when a media file really exists, else the normal chat handles the sentence.
 *  - A sentence about the SCREEN never matches (screen analysis belongs to the screen feature). "reel kya hai" (no "ye / is") is a general question, not a request.
 *  - The question part goes through AnalysisRules.gateQuestion in the pipeline's intake BEFORE any network use ("ye kaun hai" is refused there).
 *  - Persona is never applied to an analysis answer (lastKind "media" stays out of PersonaRules).
 */
object AnalysisVoiceRules {
    enum class Ask { NONE, ANALYSE, CHECK }

    const val MAX_WORDS = 12

    private val MEDIA = setOf("video", "videos", "reel", "reels", "media", "clip", "vedio", "photo", "photos", "image", "images", "pic", "picture", "tasveer", "foto")
    private val ANALYSE = setOf("analyse", "analyze", "analysis", "analyser", "vishleshan")
    private val DEMO = setOf("ye", "yeh", "is", "iss", "isko", "ise", "this", "these", "isme", "isse")
    private val WHAT = setOf("kya", "kaun", "kon", "what", "who", "whats", "check", "verify", "jaanch", "sach", "jhoot", "jhuth", "fake", "real", "true", "asli", "nakli", "dikh", "dikha", "likha")
    private val TRUTH = setOf("sach", "jhoot", "jhuth", "fake", "real", "true", "false", "asli", "nakli")
    private val CHECKW = setOf("check", "verify", "jaanch", "confirm")
    private val FILLER = setOf(
        "nova", "ye", "yeh", "is", "iss", "isko", "ise", "isme", "isse", "this", "these", "the", "a", "me", "mein", "main", "ka", "ki", "ko", "se", "par", "pe", "hai", "hain", "ho", "hota",
        "karo", "kar", "kro", "do", "dena", "dekho", "please", "pls", "zara", "jara", "bhai", "kya", "what", "whats", "is", "it", "of", "in", "analyse", "analyze", "analysis", "check", "verify",
        "video", "videos", "reel", "reels", "media", "clip", "vedio", "photo", "photos", "image", "images", "pic", "picture", "tasveer", "foto", "dikh", "raha", "rahi", "dikha", "batao", "bata"
    )

    private fun words(text: String?): List<String> =
        (text ?: "").lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(' ').filter { it.isNotEmpty() }

    /** What the sentence asks for. NONE for everything else (the normal chain then handles it). */
    fun parse(text: String?): Ask {
        val w = words(text)
        if (w.isEmpty() || w.size > MAX_WORDS) return Ask.NONE
        val set = w.toSet()
        if ("screen" in set || "skrin" in set) return Ask.NONE
        val media = set.any { it in MEDIA }
        val analyse = set.any { it in ANALYSE }
        val demo = set.any { it in DEMO }
        val what = set.any { it in WHAT }
        if (media && analyse) return Ask.ANALYSE
        if (media && demo && what) {
            // "ye kya mila" style status questions stay with MediaIntake.voiceIntent STATUS
            val joined = w.joinToString(" ")
            if (joined.contains("kya mila") || joined.contains("kya mili") || joined.contains("kya mile") || joined.contains("what did")) return Ask.NONE
            return Ask.ANALYSE
        }
        if (!media && demo && set.any { it in CHECKW } && set.any { it in TRUTH }) return Ask.CHECK
        return Ask.NONE
    }

    /**
     * The owner's question for the pipeline. "" when the sentence is only the command ("nova ye video analyse karo", "is photo me kya hai"): the vision step then just describes and checks.
     * CHECK gets a fixed plain question. Otherwise the sentence itself (it is gated + cleaned by the pipeline).
     */
    fun question(text: String?, ask: Ask, en: Boolean): String {
        if (ask == Ask.NONE) return ""
        if (ask == Ask.CHECK) return if (en) "Is this true or fake? Check what can be checked." else "Kya ye sach hai ya jhoot? Jo check ho sake wo check karo."
        val w = words(text)
        val rest = w.filter { it !in FILLER }
        if (rest.size < 2 && !w.any { it in setOf("kaun", "kon", "who") }) return ""
        return AnalysisRules.cleanQuestion(text)
    }

    /** The newest photo / video (links never). null = nothing to analyse. */
    fun pickTarget(entries: List<MediaIntake.Entry>): MediaIntake.Entry? =
        entries.filter { it.kind == MediaIntake.Kind.IMAGE || it.kind == MediaIntake.Kind.VIDEO }.maxByOrNull { it.createdMs }

    private fun t(en: Boolean, hi: String, e: String): String = if (en) e else hi

    fun noMediaText(en: Boolean): String = t(en,
        "Pehle video ya photo share karo (kisi app se Share karke NOVA chuno), phir bolo analyse karo.",
        "Share a video or photo first (Share to NOVA from any app), then ask me to analyse it.")

    private fun kindWord(k: MediaIntake.Kind, en: Boolean): String = if (k == MediaIntake.Kind.VIDEO) t(en, "video", "video") else t(en, "photo", "photo")

    /** Said at once when the job starts (the job itself runs in the background). The file is named so the owner knows which one. */
    fun startedText(e: MediaIntake.Entry, en: Boolean): String {
        val name = AnalysisRules.clean(e.name, 40)
        return t(en,
            "Theek hai, sabse naya " + kindWord(e.kind, false) + (if (name.isEmpty()) "" else " (" + name + ")") + " analyse kar raha hoon. Thoda time lagega; rukna ho to STOP bolo.",
            "Okay, analysing the newest " + kindWord(e.kind, true) + (if (name.isEmpty()) "" else " (" + name + ")") + ". It takes a little while; say STOP to end it.")
    }

    /** Short spoken lead-in before the report text in the chat (voice itself only says AnalysisRules.speechSummary). */
    fun doneLine(en: Boolean): String = t(en, "Analysis poora hua:", "Analysis finished:")

    // ------------------------------------------------------------------ chat photo + question

    private val Q_WORDS = setOf("kya", "kaun", "kon", "kaise", "kyun", "kyu", "kitna", "kitne", "kahan", "kab", "konsa", "kaunsa", "what", "who", "how", "why", "where", "which", "when",
        "batao", "bata", "samjhao", "describe", "explain", "padho", "read", "likha", "check", "verify", "sach", "jhoot", "fake", "real", "analyse", "analyze", "dekho", "dikh", "bolo", "tell", "show", "kisne", "translate")

    /**
     * A photo is attached in the chat and the owner wrote [text]: is it a QUESTION ABOUT the photo (then the vision step answers it)? Edit / picture / website sentences are decided earlier
     * (AutoIntent) and never reach here. A plain "thanks" / "ok" is not a question (no AI call, no photo sent).
     */
    fun isPhotoQuestion(text: String?): Boolean {
        val raw = (text ?: "").trim()
        if (raw.length < 4 || raw.length > AnalysisRules.MAX_QUESTION) return false
        if (raw.contains('?')) return true
        val w = words(raw)
        if (w.size > 40) return false
        return w.any { it in Q_WORDS }
    }

    fun chatPhotoAskTitle(en: Boolean): String = t(en, "Photo AI ko bhejein?", "Send the photo to the AI?")
    fun chatPhotoAskMessage(en: Boolean): String = t(en,
        "Sawaal ka jawab dene ke liye ye photo internet par AI (Gemini, ya aapki free Groq / OpenRouter key) ko bheji jayegi. Web jaanch ke liye sirf chhoti text query jaati hai.",
        "To answer your question this photo is sent over the internet to the AI (Gemini, or your free Groq / OpenRouter key). Only a short text query goes out for the web check.")
    fun chatPhotoWorking(en: Boolean): String = t(en, "Photo dekh raha hoon...", "Looking at the photo...")
    fun noPhotoText(en: Boolean): String = t(en, "Pehle photo chuno (jodne wala button).", "Pick a photo first (the attach button).")
}
