package com.nova.assistant

/**
 * v7.154 JARVIS UPGRADE: pure rules (NO Android imports, unit tested) for the four new opt-in features.
 * ADD-ONLY: every default keeps the exact v7.153 behaviour; each feature turns on only when the owner asks.
 *
 * 1. JARVIS DEEP VOICE  - offline neural voice (sherpa-onnx + Piper "alan"). Rules: when may it speak,
 *                        what is spoken about it, and the command grammar (on / off / download / status).
 * 2. BARGE-IN STOP      - while NOVA is speaking, the mic keeps listening for a small stop-word grammar.
 *                        A stop word that appears in NOVA's OWN reply text is ignored (self-echo guard),
 *                        so NOVA can never stop itself by saying "stop" inside an answer.
 * 3. AUDIO FOCUS        - transient may-duck focus around every utterance (default ON, can be turned off).
 * 4. PROVIDER CHECK     - on-demand health probe of every keyed AI provider + the reply text.
 * 5. GLM 5.3 SAB JAGAH  - opt-in: the reasoning provider (GLM 5.3 Flash) goes first for EVERY AI job.
 *
 * Nothing here touches the network, the mic or the filesystem; the Android layer lives in JarvisDeepVoice.kt.
 */
object JarvisDeepRules {

    // ---------------------------------------------------------------- command kinds

    const val K_DEEP_ON = "jdeep_on"
    const val K_DEEP_OFF = "jdeep_off"
    const val K_DEEP_DL = "jdeep_dl"
    const val K_DEEP_STATUS = "jdeep_status"
    const val K_BARGE_ON = "jbarge_on"
    const val K_BARGE_OFF = "jbarge_off"
    const val K_FOCUS_ON = "jfocus_on"
    const val K_FOCUS_OFF = "jfocus_off"
    const val K_PROVCHECK = "jprovcheck"
    const val K_GLM_ON = "jglm_on"
    const val K_GLM_OFF = "jglm_off"

    val KINDS = setOf(K_DEEP_ON, K_DEEP_OFF, K_DEEP_DL, K_DEEP_STATUS, K_BARGE_ON, K_BARGE_OFF,
        K_FOCUS_ON, K_FOCUS_OFF, K_PROVCHECK, K_GLM_ON, K_GLM_OFF)

    /** Max characters the deep voice speaks in one go; longer replies fall back to the normal voice chain. */
    const val MAX_LEN = 1200

    // ---------------------------------------------------------------- deep voice routing

    /**
     * May the deep voice speak this text? true only when the feature is ON, the model is installed and the text
     * is Latin-script (English / Hinglish in Roman letters). Devanagari (Hindi) replies must use the existing
     * Hindi voice chain - the Piper "alan" model is English-only, exactly like the v7.129 server Jarvis voice.
     */
    fun use(text: String, on: Boolean, modelReady: Boolean): Boolean {
        if (!on || !modelReady) return false
        if (text.isBlank() || text.length > MAX_LEN) return false
        return latinOk(text)
    }

    /**
     * Same Latin-script guard as the server Jarvis voice: no Devanagari at all, and at least 9 out of 10
     * letters are plain A-Z (a stray emoji or accent cannot break it, Devanagari can).
     */
    fun latinOk(text: String): Boolean {
        var letters = 0
        var ascii = 0
        for (c in text) {
            if (c in 'ऀ'..'ॿ') return false
            if (c.isLetter()) {
                letters++
                if (c in 'A'..'Z' || c in 'a'..'z') ascii++
            }
        }
        return letters > 0 && ascii * 10 >= letters * 9
    }

    /**
     * Text cleanup for the neural voice: emojis, arrows, boxes and other symbols are removed (the Piper model
     * would try to READ them out loud). Letters, digits, normal punctuation, currency and newlines are kept.
     * Only ever called after [latinOk] passed, so no Devanagari arrives here.
     */
    fun clean(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val keep = c.isLetterOrDigit() || c.isWhitespace() ||
                c in ".,!?'\"-:;()%&/+*=#@_[]{}<>~^$€₹£¥" || c in "…•·|\\/"
            if (keep) sb.append(c)
        }
        return sb.toString().replace(Regex("[ ]{2,}"), " ").trim()
    }

    // ---------------------------------------------------------------- barge-in (spoken STOP while NOVA speaks)

    /** The ONLY words barge-in listens for while NOVA is speaking. Nothing else can trigger it. */
    val STOP_WORDS = listOf("stop", "ruko", "rukja", "ruk jao", "chup", "khamosh", "bas", "band karo", "chup ho jao")

    /** Minimum Vosk confidence for a barge-in hit (high on purpose: a false stop is worse than a late stop). */
    const val MIN_CONF = 0.85f

    /** Minimum mic loudness (RMS, 16-bit samples) for a barge-in hit: room noise and quiet TV must not stop NOVA. */
    const val MIN_RMS = 1000.0

    /** Vosk grammar JSON for the barge-in recognizer: only the stop words plus [unk], nothing else can be heard. */
    fun stopGrammar(): String = "[" + (STOP_WORDS.map { "\"" + it + "\"" } + "\"[unk]\"").joinToString(", ") + "]"

    /**
     * The LOWEST word confidence inside a Vosk result JSON. No word objects (setWords not supported) = 0, the
     * safe direction: barge-in simply does not fire until real confidences arrive.
     */
    fun minConf(json: String): Float {
        var min = 1f
        var judged = false
        for (m in Regex("\\{[^{}]*\\}").findAll(json)) {
            val o = m.value
            val w = Regex("\"word\"\\s*:\\s*\"([^\"]*)\"").find(o)?.groupValues?.get(1) ?: continue
            if (w == "[unk]") continue
            val c = Regex("\"conf\"\\s*:\\s*([0-9.]+)").find(o)?.groupValues?.get(1)?.toFloatOrNull() ?: continue
            judged = true
            if (c < min) min = c
        }
        return if (judged) min else 0f
    }

    /**
     * Does the recognized chunk [heard] (already lowercase, may contain several words) contain a stop word as a
     * WHOLE word? The FIRST matching stop word is returned, null when none. "stopper" or "chupke" do not match.
     */
    fun stopWordIn(heard: String): String? {
        val h = " " + heard.trim().lowercase().replace(Regex("\\s+"), " ") + " "
        for (w in STOP_WORDS) {
            val parts = w.split(" ")
            if (parts.size == 1) {
                if (" $w " in h) return w
            } else {
                if (" $w " in h) return w
            }
        }
        return null
    }

    /**
     * Self-echo guard: [spokenText] is what NOVA is saying right now. If the stop word itself occurs in it
     * (for example NOVA is reading "maine video stop kar di"), the mic would hear NOVA's own speaker say
     * "stop" - that hit is IGNORED so NOVA never stops itself. A human saying "stop" while NOVA says
     * "aaj mausam..." still works.
     */
    fun echoSafe(word: String, spokenText: String): Boolean {
        val s = " " + spokenText.lowercase().replace(Regex("\\s+"), " ") + " "
        return " $word " !in s
    }

    /**
     * Full barge-in decision for one recognized chunk while speaking.
     * [heard] = recognized text, [spokenText] = the text NOVA is currently speaking,
     * [conf] = recognizer confidence 0..1, [rms] = mic loudness of this chunk, [minRms] = loudness gate.
     */
    fun hit(heard: String, spokenText: String, conf: Float, rms: Double, minRms: Double): Boolean {
        if (conf < MIN_CONF) return false
        if (rms < minRms) return false
        val w = stopWordIn(heard) ?: return false
        return echoSafe(w, spokenText)
    }

    // ---------------------------------------------------------------- command grammar

    /**
     * Command grammar for all four features. Returns null for anything that is not clearly one of them,
     * so every older route (wifi, settings, movie, chat...) keeps working unchanged.
     * Negated orders ("mat karo") never match, same rule as the Wi-Fi kit.
     */
    fun parse(text: String): Logic.Cmd? {
        if (text.isBlank() || text.length > 160) return null
        val l = Logic.norm(text)
        if (l.isEmpty()) return null
        val t = l.split(" ")
        fun has(vararg w: String): Boolean = w.any { it in t }
        val negated = t.any { it in setOf("mat", "nahi", "nahin", "nhi", "nai", "mt") }
        if (negated) return null

        val deep = has("deep") || l.contains("jarvis deep") || l.contains("deep voice") || l.contains("deep awaaz") || l.contains("deep awaz")
        val onW = has("on", "chalu", "chalu)", "start", "enable", "lagao", "laga")
        val offW = has("off", "band", "bandh", "stop", "disable", "hatao", "bandkaro")

        // ---- deep voice (requires the word "deep" + a voice word, so normal chat is never captured)
        if (deep && (has("voice", "awaaz", "awaz", "awaaj", "bolne", "speaking") || l.contains("jarvis deep"))) {
            if (has("download", "dal", "daal", "install", "lana", "la", "model")) return Logic.Cmd(K_DEEP_DL)
            if (has("status", "kaisa", "kaisi", "ready", "hua", "haal")) return Logic.Cmd(K_DEEP_STATUS)
            if (onW) return Logic.Cmd(K_DEEP_ON)
            if (offW) return Logic.Cmd(K_DEEP_OFF)
            return Logic.Cmd(K_DEEP_STATUS)   // "nova deep voice" alone = status
        }

        // ---- barge-in ("bol ke rokna", "awaz se rokna", "barge in")
        val barge = has("barge") || (has("bolke", "bolkar") && has("rokna", "rok")) || (has("awaz", "awaaz") && has("rokna")) ||
            l.contains("bol ke rokna") || l.contains("awaz se rokna") || l.contains("awaaz se rokna")
        if (barge) {
            if (onW) return Logic.Cmd(K_BARGE_ON)
            if (offW) return Logic.Cmd(K_BARGE_OFF)
            return Logic.Cmd(K_BARGE_ON)
        }

        // ---- audio focus (always the two words "audio focus", never a bare "focus")
        if (l.contains("audio focus") || l.contains("focus audio")) {
            if (onW) return Logic.Cmd(K_FOCUS_ON)
            if (offW) return Logic.Cmd(K_FOCUS_OFF)
            return Logic.Cmd(K_FOCUS_ON)
        }

        // ---- provider check ("provider check karo", "providers kaise hain", "ai health")
        if ((has("provider", "providers") && has("check", "checkup", "health", "test", "kaise", "kaisa", "haal")) ||
            l.contains("provider health") || l.contains("providers health") || l.contains("ai health check")) {
            return Logic.Cmd(K_PROVCHECK)
        }

        // ---- GLM 5.3 everywhere (opt-in; the words "glm" + "sab jagah")
        if (has("glm") && (has("sab", "everywhere", "har", "jagah") || l.contains("sab jagah"))) {
            if (onW) return Logic.Cmd(K_GLM_ON)
            if (offW) return Logic.Cmd(K_GLM_OFF)
            return Logic.Cmd(K_GLM_ON)
        }

        return null
    }

    // ---------------------------------------------------------------- spoken replies (Hinglish first, English second)

    private fun tr(hi: String, en: String, english: Boolean): String = if (english) en else hi

    fun replyDeepOn(ready: Boolean, english: Boolean): String = if (ready)
        tr("Jarvis deep voice ON. Ab main English aur Hinglish jawab is bhaari offline awaaz me bologa. Hindi (Devanagari) jawab waise hi Hindi awaaz me honge.",
            "Jarvis deep voice is ON. English and Hinglish replies now use this heavy offline voice. Hindi (Devanagari) replies keep the Hindi voice.", english)
    else
        tr("Deep voice ka 20 MB model abhi phone me nahi hai. Bolo: nova deep voice download karo. Jab tak download nahi hota, purani awaaz hi chalegi.",
            "The 20 MB deep-voice model is not on the phone yet. Say: nova deep voice download karo. Until then the existing voice stays.", english)

    fun replyDeepOff(english: Boolean): String =
        tr("Jarvis deep voice OFF. Wapas purani awaaz: Jarvis server, phir Ultron, phir normal TTS.",
            "Jarvis deep voice is OFF. Back to the existing chain: Jarvis server, then Ultron, then the normal voice.", english)

    fun replyDeepDl(english: Boolean, already: Boolean): String = if (already)
        tr("Deep voice model pehle se ready hai. Bolo: nova deep voice on karo.", "The deep-voice model is already installed. Say: nova deep voice on karo.", english)
    else
        tr("Deep voice model (20 MB) download shuru - Settings > UPDATES me progress dikhega. Poora hone par bolo: nova deep voice on karo. Fail hua to dobara bolo, yaadan se kabhi kharab file install nahi hogi.",
            "Downloading the 20 MB deep-voice model - progress shows in Settings > UPDATES. When it finishes, say: nova deep voice on karo. A failed download is never installed.", english)

    fun replyDeepStatus(english: Boolean, on: Boolean, ready: Boolean): String {
        val st = when {
            on && ready -> tr("ON + ready", "ON and ready", english)
            on && !ready -> tr("ON hai par model missing (20 MB download baaki)", "ON but the model is missing (20 MB download pending)", english)
            ready -> tr("OFF hai, model ready hai (bolo: nova deep voice on karo)", "OFF, model installed (say: nova deep voice on karo)", english)
            else -> tr("OFF hai, model bhi nahi hai", "OFF and the model is not installed", english)
        }
        return tr("Deep voice: $st.", "Deep voice: $st.", english)
    }

    fun replyBarge(english: Boolean, on: Boolean): String = if (on)
        tr("Barge-in ON. Jab main bol raha hoon, 'stop', 'chup', 'ruko' ya 'bas' bolo - main turant chup ho jaunga. Note: wahi word jo main khud bol raha hoon us waqt ignore hota hai.",
            "Barge-in is ON. While I am speaking, say 'stop', 'chup', 'ruko' or 'bas' and I stop instantly. A word I am myself saying at that moment is ignored.", english)
    else
        tr("Barge-in OFF. Bolte waqt rokna ho to STOP button dabao ya chat me 'stop' likho.",
            "Barge-in is OFF. To stop my speech use the STOP button or type 'stop' in the chat.", english)

    fun replyFocus(english: Boolean, on: Boolean): String = if (on)
        tr("Audio focus ON. Main bolte waqt gaana/music dheema (duck) ho jayega aur bolne ke baad wapas aa jayega.",
            "Audio focus is ON. Music ducks while I speak and comes back afterwards.", english)
    else
        tr("Audio focus OFF. Gaana/music bolte wakt bhi poora volume pe chalega.",
            "Audio focus is OFF. Music keeps full volume while I speak.", english)

    fun replyGlm(english: Boolean, on: Boolean): String = if (on)
        tr("GLM 5.3 Flash ab chat jawab, agent kaam aur smart reply me SABSE PEHLE try hoga (pehle sirf bade kaam me tha). Jawab thoda slow aa sakte hain; phone command samajhne wali fast chain waisi hi rahegi. Wapas karne ko bolo: nova glm sab jagah off karo.",
            "GLM 5.3 Flash is now tried FIRST for chat answers, agent work and smart replies (before: big jobs only). Replies may be a little slower; the fast phone-command chain stays as it was. Say 'nova glm sab jagah off karo' to go back.", english)
    else
        tr("GLM 5.3 ab wapas sirf bade kaam (long chat, agent, website) me pehle aayega. Chhote jawab purane order se aayenge.",
            "GLM 5.3 is back to big jobs only (long chat, agent, website). Short replies use the old order again.", english)

    /**
     * Provider check reply from AutoSetup.checkProviders results: [(label+model, httpCode)]. One line each,
     * max 6 lines, then a summary. 200 = OK; 401/403 = key problem; 429 = quota; 598 = no internet.
     */
    fun replyProviders(english: Boolean, results: List<Pair<String, Int>>): String {
        if (results.isEmpty())
            return tr("Koi AI provider key set nahi hai (Gemini / Groq / OpenRouter / Z.ai). Bina key ke local kaam, phone commands aur Pollinations chalte hain.",
                "No AI provider key is set (Gemini / Groq / OpenRouter / Z.ai). Local actions, phone commands and Pollinations still work.", english)
        val lines = results.take(6).map { (label, code) ->
            val st = when {
                code == 200 -> tr("OK", "OK", english)
                code == 401 || code == 403 -> tr("key galat (HTTP $code)", "bad key (HTTP $code)", english)
                code == 429 -> tr("quota/limit (HTTP 429)", "quota/limit (HTTP 429)", english)
                code == 598 -> tr("internet nahi", "no internet", english)
                else -> tr("fail (HTTP $code)", "fail (HTTP $code)", english)
            }
            "$label: $st"
        }
        val okCount = results.count { it.second == 200 }
        val head = tr("Provider check ($okCount/${results.size} OK):", "Provider check ($okCount/${results.size} OK):", english)
        return head + "\n" + lines.joinToString("\n")
    }
}
