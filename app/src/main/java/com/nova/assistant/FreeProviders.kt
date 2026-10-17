package com.nova.assistant

/**
 * Part 1A of the free AI provider chain. PURE Kotlin: no Android classes, no network, no org.json
 * (org.json is only a stub in JVM unit tests, so this file has its own tiny JSON reader/writer).
 * The network call is passed in as a lambda, so every failover rule below is unit tested.
 *
 * Chain order used by the app (wired in part 1B; v34 adds Qwen): Gemini (existing, user key) -> Groq (user's free key) -> Qwen (same Groq key) -> GLM (Z.ai, own free key)
 * -> OpenRouter free models (user's free key) -> Pollinations text (anonymous try; their current docs say a key is required, so this may 401 and is then skipped for 10 min) -> offline model -> offline skills.
 * Failover is between DIFFERENT providers and different models of ONE provider. It never rotates several
 * accounts of the same provider to get more quota (invariant 9).
 */

/** Minimal JSON: enough to build OpenAI-style requests and read their replies. */
object MiniJson {
    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /** Returns Map / List / String / Double / Boolean / null. Any malformed input also returns null (never throws). */
    fun parse(s: String): Any? = try {
        Reader(s).readAll()
    } catch (e: IllegalArgumentException) {
        null
    }

    fun field(o: Any?, key: String): Any? = (o as? Map<*, *>)?.get(key)

    fun at(o: Any?, index: Int): Any? = (o as? List<*>)?.getOrNull(index)

    private class Reader(private val s: String) {
        private var i = 0

        fun readAll(): Any? {
            val v = value(0)
            ws()
            if (i != s.length) fail()
            return v
        }

        private fun fail(): Nothing = throw IllegalArgumentException("bad json at $i")

        private fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        private fun value(depth: Int): Any? {
            if (depth > 40) fail()
            ws()
            if (i >= s.length) fail()
            return when (s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> num()
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail()
            i += word.length
            return v
        }

        private fun num(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
            if (st == i) fail()
            return s.substring(st, i).toDoubleOrNull() ?: fail()
        }

        private fun str(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                if (c == '"') return sb.toString()
                if (c != '\\') {
                    sb.append(c)
                    continue
                }
                if (i >= s.length) fail()
                val e = s[i++]
                when (e) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (i + 4 > s.length) fail()
                        val h = s.substring(i, i + 4).toIntOrNull(16) ?: fail()
                        sb.append(h.toChar())
                        i += 4
                    }
                    else -> fail()
                }
            }
            fail()
        }

        private fun obj(depth: Int): Map<String, Any?> {
            i++ // {
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') {
                i++
                return m
            }
            while (i < s.length) {
                ws()
                if (i >= s.length || s[i] != '"') fail()
                val k = str()
                ws()
                if (i >= s.length || s[i] != ':') fail()
                i++
                m[k] = value(depth + 1)
                ws()
                if (i >= s.length) fail()
                val c = s[i++]
                if (c == '}') return m
                if (c != ',') fail()
            }
            fail()
        }

        private fun arr(depth: Int): List<Any?> {
            i++ // [
            val l = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') {
                i++
                return l
            }
            while (i < s.length) {
                l.add(value(depth + 1))
                ws()
                if (i >= s.length) fail()
                val c = s[i++]
                if (c == ']') return l
                if (c != ',') fail()
            }
            fail()
        }
    }
}

/** One OpenAI-compatible chat endpoint. [keyName] = which user key it needs (null = no key needed). */
class FreeProvider(
    val id: String,
    val label: String,
    val url: String,
    val models: List<String>,
    val keyName: String?,
    /** Extra top-level JSON members for the request body (without braces), e.g. "thinking":{"type":"disabled"}. "" = none. */
    val extra: String = ""
)

/** One HTTP answer. code 598 = network is down, 599 = timeout / IO error (same convention as Logic.nextStep). */
class NetAnswer(val code: Int, val body: String)

class ChainResult(
    val text: String?,
    val providerId: String?,
    val model: String?,
    /** "" on success, otherwise network | quota | key | model | server | other | slow | cooling | nokey */
    val failKind: String,
    val tried: List<String>
)

/** Per-provider "skip until" times. The clock is passed in, so tests need no sleeping. */
class Cooldowns {
    private val until = HashMap<String, Long>()

    @Synchronized
    fun cooling(id: String, now: Long): Boolean = (until[id] ?: 0L) > now

    @Synchronized
    fun set(id: String, ms: Long, now: Long) {
        if (ms > 0L) until[id] = now + ms
    }

    @Synchronized
    fun remaining(id: String, now: Long): Long = maxOf(0L, (until[id] ?: 0L) - now)

    @Synchronized
    fun clear() {
        until.clear()
    }
}

object FreeProviders {
    // Model names and URLs live ONLY here. Free model ids change over time: a 404/400 moves on to the next model
    // by itself, and a provider whose models all fail is skipped for a while. Nothing here was reachable from the
    // build sandbox (no network), so the endpoints are written from the providers' public OpenAI-compatible docs.
    val GROQ = FreeProvider(
        "groq", "Groq", "https://api.groq.com/openai/v1/chat/completions",
        listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b"), "groq"
    )
    /**
     * v34 Part 3: hosted Qwen, ONE table. Looked up 2026-10-11: Groq lists qwen/qwen3.8-27b (preview, same endpoint and same key as Groq,
     * so no new key and no download). OpenRouter had a free Qwen (qwen/qwen3.8-27b:free) until 2026-10-06, but it is NOT in its free list now,
     * so no OpenRouter Qwen row exists. If one comes back, add its id to a new provider here (one line). Preview models can disappear:
     * a 404/400 simply moves on to the next provider.
     */
    val QWEN_MODELS_GROQ: List<String> = listOf("qwen/qwen3.8-27b")
    /** Own provider id so Groq GPT-OSS quota and Qwen's quota are tracked (and cooled down) separately. Same URL, same Groq key. */
    val GROQ_QWEN = FreeProvider(
        "groqqwen", "Qwen (via Groq)", "https://api.groq.com/openai/v1/chat/completions",
        QWEN_MODELS_GROQ, "groq"
    )
    /**
     * v35: free CHINESE model (looked up 2026-10-11): Zhipu / Z.ai GLM Flash models are priced at zero on docs.z.ai, sign-up with e-mail
     * on z.ai / bigmodel.cn, NO credit card (per freellm.net + docs.z.ai pricing page; NOT tested by us). OpenAI-style endpoint from docs.z.ai.
     * Needs its own key (slot "zai"). "thinking disabled" so the small token budget is not spent on hidden reasoning (if the server
     * rejects the field it answers 400 and the chain simply moves on). The question goes to a server in China: the Settings text says so.
     * Not usable without a key or a phone number: DeepSeek / Moonshot (no free API), SiliconFlow / ModelScope (Chinese phone or Alibaba account).
     */
    val ZAI_MODELS: List<String> = listOf("glm-4.7-flash", "glm-4.5-flash")
    val ZAI = FreeProvider(
        "zai", "GLM (Z.ai, China)", "https://api.z.ai/api/paas/v4/chat/completions",
        ZAI_MODELS, "zai", "\"thinking\":{\"type\":\"disabled\"}"
    )
    /** v6.30: two more GLM keys (slots "zai2", "zai3"). When one key's limit is used up (429 / cooling), the chain moves to the next key. */
    val ZAI2 = FreeProvider(
        "zai2", "GLM #2 (Z.ai, China)", "https://api.z.ai/api/paas/v4/chat/completions",
        ZAI_MODELS, "zai2", "\"thinking\":{\"type\":\"disabled\"}"
    )
    val ZAI3 = FreeProvider(
        "zai3", "GLM #3 (Z.ai, China)", "https://api.z.ai/api/paas/v4/chat/completions",
        ZAI_MODELS, "zai3", "\"thinking\":{\"type\":\"disabled\"}"
    )
    val OPENROUTER = FreeProvider(
        "openrouter", "OpenRouter (free models)", "https://openrouter.ai/api/v1/chat/completions",
        listOf("openrouter/free", "meta-llama/llama-3.3-70b-instruct:free"), "openrouter"
    )
    val POLLINATIONS = FreeProvider(
        "pollinations", "Pollinations (anonymous, may need a key)", "https://gen.pollinations.ai/v1/chat/completions",
        listOf("openai/gpt-5.4-nano", "openai"), null
    )

    fun ordered(): List<FreeProvider> = listOf(GROQ, GROQ_QWEN, ZAI, ZAI2, ZAI3, OPENROUTER, POLLINATIONS)

    const val MAX_USER_CHARS = 1000
    const val MAX_REPLY_CHARS = 700
    const val MAX_USER_CHARS_RICH = 6000   // v7.1: typed chat turn (ChatGPT/Claude style) may carry a long question
    const val MAX_REPLY_CHARS_RICH = 8000
    const val RICH_MAX_TOKENS = 2000

    /** v7.1 typed chat turn: full, structured answer (steps, lists, code blocks), owner's language, one "Samjha:" line first for complex asks. */
    fun richSystemPrompt(en: Boolean): String =
        if (en) {
            "You are NOVA, a capable assistant on the user's phone, answering in a chat window like ChatGPT or Claude. Give a complete, " +
                "well-organised answer: short paragraphs, numbered steps or - bullets where they help, code in fenced blocks. Write, explain, " +
                "translate, plan, analyse, teach and debug as needed, in the language the user used (Hindi, Hinglish or English). " +
                "For a complex request start with one line 'Samjha: ...' saying what you understood. " +
                "If WEB RESULTS are given, use them and end with a 'Sources:' line. Without them you have no live data (weather, prices, scores, news): say so instead of guessing. " +
                "You cannot press buttons or change phone settings in this answer, so never say you did. Never say you are building or changing an app, website or file: you only answer in text here. If you are not sure, say so."
        } else {
            "Tum NOVA ho, user ke phone par ek kaabil assistant, chat window mein ChatGPT ya Claude ki tarah jawab dete ho. Poora aur saaf jawab do: " +
                "chhote paragraph, jahan kaam aaye wahan numbered steps ya - bullets, code fenced block mein. Likhna, samjhana, translate, plan, " +
                "analysis, padhai aur debug jo bhi maange, usi bhasha mein jisme user ne poocha (Hindi, Hinglish ya English). " +
                "Mushkil request par pehli line 'Samjha: ...' likho ki tumne kya samjha. " +
                "Agar WEB RESULTS diye hain to unhe use karo aur aakhir mein 'Sources:' line do. Bina unke live data (mausam, bhaav, score, khabar) tumhare paas nahi: andaza mat lagao, saaf bolo. " +
                "Is jawab mein tum phone par button nahi dabaa sakte ya setting nahi badal sakte, isliye kabhi mat kehna ki kar diya. Kabhi mat kehna ki tum app, website ya file bana rahe ho: yahan sirf text mein jawab dete ho. Pakka na ho to bolo."
        }

    /** v7.3: appended to Gemini's system prompt on a typed chat turn; it overrides the read-aloud / no-markdown / 8-sentence rules for real questions only. */
    const val GEMINI_RICH_NOTE = "TYPED CHAT: this message was typed in the chat window, not spoken. For phone actions keep the reply to one short sentence. " +
        "For questions, writing, code, analysis, translation, planning or study give a complete ChatGPT-style answer: markdown is allowed (short headings, - lists, numbered steps, fenced code blocks) and any length that is needed, " +
        "so ignore the rules about being read aloud, no markdown, no lists and under 8 sentences. For a complex request start with one line 'Samjha: ...' saying what you understood. " +
        "Use search_web for anything current and end with a 'Sources:' line when you used it."

    /** Chat-only text: strip markdown marks and cap, so the TTS reads a clean short version when "chat speak" is ON. */
    fun speakable(text: String, maxChars: Int = 800): String {
        var t = text.replace(Regex("(?s)```.*?```"), " ")
        t = t.replace(Regex("[*#`_>]+"), "").replace(Regex("(?m)^\\s*[-\u2022]\\s+"), "")
        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.length > maxChars) {
            val cut = t.substring(0, maxChars)
            val p = maxOf(maxOf(cut.lastIndexOf('.'), cut.lastIndexOf('?')), maxOf(cut.lastIndexOf('!'), cut.lastIndexOf('\u0964')))
            t = if (p > 200) cut.substring(0, p + 1) else cut.trimEnd()
        }
        return t
    }

    fun systemPrompt(en: Boolean): String =
        if (en) {
            "You are NOVA, a short, honest voice assistant on the user's phone. Reply in plain text only: no markdown, no lists, " +
                "at most 3 short sentences, in the language the user used. You cannot press buttons or change phone settings in " +
                "this answer, so never say you did. You have no live data (weather, prices, scores, news): say so instead of " +
                "guessing. If you are not sure, say so. Never say you are building, adding, changing or creating anything (websites, apps, files, designs): you only answer in text here, so say plainly that you cannot do it from this chat. Do not ask follow-up questions."
        } else {
            "Tum NOVA ho, user ke phone par ek chhota aur imaandaar voice assistant. Sirf plain text mein jawab do: koi markdown ya " +
                "list nahi, zyada se zyada 3 chhote vaakya, aur usi bhasha mein jisme user ne poocha (Hindi, Hinglish ya English). " +
                "Is jawab mein tum phone par koi button nahi dabaa sakte ya setting nahi badal sakte, isliye kabhi mat kehna ki kar diya. " +
                "Live data (mausam, bhaav, score, khabar) tumhare paas nahi hai: andaza mat lagao, saaf bolo ki nahi pata. " +
                "Pakka na ho to bolo. Kabhi mat kehna ki tum website, app, file ya design bana rahe ho / add kar rahe ho / badal rahe ho: yahan tum sirf text mein jawab dete ho, isliye saaf bolo ki ye kaam is chat se nahi ho sakta. Wapas sawaal mat poochho."
        }

    /** turns = (role, text) pairs of the running conversation (roles "user" / "assistant"), put between the system text and the new question. Empty = the old single-question body. */
    fun buildBody(model: String, system: String, user: String, maxTokens: Int = 350, extra: String = "", turns: List<Pair<String, String>> = emptyList(), userCap: Int = MAX_USER_CHARS): String {
        val u = if (user.length > userCap) user.substring(0, userCap) else user
        val sb = StringBuilder()
        for ((r, t) in turns) {
            if (r != "user" && r != "assistant") continue
            sb.append(",{\"role\":\"").append(r).append("\",\"content\":").append(MiniJson.quote(t)).append("}")
        }
        return "{\"model\":" + MiniJson.quote(model) +
            ",\"messages\":[{\"role\":\"system\",\"content\":" + MiniJson.quote(system) + "}" + sb.toString() +
            ",{\"role\":\"user\",\"content\":" + MiniJson.quote(u) +
            "}],\"max_tokens\":" + maxTokens + ",\"temperature\":0.6" + (if (extra.isEmpty()) "" else "," + extra) + "}"
    }

    /** choices[0].message.content as clean spoken text, or null (also for 200-answers that carry an "error" object). */
    fun parseReply(body: String, rich: Boolean = false): String? {
        val root = MiniJson.parse(body) ?: return null
        val msg = MiniJson.field(MiniJson.at(MiniJson.field(root, "choices"), 0), "message")
        val content = MiniJson.field(msg, "content") as? String ?: return null
        val t = if (rich) cleanRich(content) else clean(content)
        return if (t.isBlank()) null else t
    }

    /** v7.1 chat text: only the reasoning block is removed; newlines, lists, code and markdown stay (the chat window renders them). */
    fun cleanRich(text: String): String {
        var t = text.replace(Regex("(?s)<think>.*?</think>"), "").replace("\r\n", "\n").trim()
        t = t.replace(Regex("\\n{3,}"), "\n\n")
        if (t.length > MAX_REPLY_CHARS_RICH) t = t.substring(0, MAX_REPLY_CHARS_RICH).trimEnd()
        return t
    }

    /** Spoken-text cleanup: no reasoning blocks, no markdown marks, one line, capped on a sentence end. */
    fun clean(text: String): String {
        var t = text.replace(Regex("(?s)<think>.*?</think>"), "")
        t = t.replace(Regex("[*#`]+"), "")
        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.length > MAX_REPLY_CHARS) {
            val cut = t.substring(0, MAX_REPLY_CHARS)
            val p = maxOf(maxOf(cut.lastIndexOf('.'), cut.lastIndexOf('?')), maxOf(cut.lastIndexOf('!'), cut.lastIndexOf('\u0964')))
            t = if (p > 200) cut.substring(0, p + 1) else cut.trimEnd()
        }
        return t
    }

    fun kindOf(code: Int): String = when {
        code == 598 || code == 599 -> "network"
        code == 401 || code == 403 -> "key"
        code == 429 -> "quota"
        code == 400 || code == 404 || code == 422 -> "model"
        code in 500..597 -> "server"
        else -> "other"
    }

    /** How long a provider is skipped after it failed with this kind. A wrong key is retried rarely (10 min). */
    fun cooldownFor(kind: String): Long = when (kind) {
        "key" -> 600_000L
        "other" -> 20_000L
        else -> AiRouter.cooldownMs(kind)
    }

    private val RANK = listOf("nokey", "cooling", "slow", "other", "model", "server", "quota", "key", "network")

    /** The more informative of two failure kinds (what the user is told if nothing worked). */
    fun worse(a: String, b: String): String = if (RANK.indexOf(b) > RANK.indexOf(a)) b else a

    // Questions that need LIVE data. The free models have none, so they would only guess: answer honestly instead.
    private val LIVE_ONLINE = setOf(
        "news", "weather", "mausam", "score", "price", "stock", "sensex", "nifty", "bitcoin", "cricket", "headlines",
        "khabar", "election", "trending"
    )

    /**
     * Local answer BEFORE any network call, or null = the question may go to the chain.
     * Crisis words always get the fixed safe answer (112 + Tele-MANAS) and never leave the phone.
     * Live-data questions get an honest "I have no live source" (LocalChat.preAnswer would say "offline", which is false here).
     */
    fun chainPreAnswer(said: String, en: Boolean): String? {
        val crisis = LocalChat.preAnswer(said, en)
        if (crisis != null && crisis.contains("14416")) return crisis
        val w = Logic.norm(said).split(" ").toSet()
        if (w.any { it in LIVE_ONLINE }) {
            return if (en) {
                "That needs live information such as weather, prices, scores or news, and I have no live source, so I would only be guessing. Please check a news or weather app."
            } else {
                "Is ke liye live jaankari chahiye, jaise mausam, bhaav, score ya khabar, aur mere paas live source nahi hai, andaza lagana galat hoga. Kisi news ya weather app mein dekh lo."
            }
        }
        return null
    }

    /** The chain is used only for a real, non-junk question and only if the user left the switch ON. */
    fun canUseChain(switchOn: Boolean, said: String): Boolean =
        switchOn && said.isNotBlank() && !Logic.isJunk(said)

    /**
     * What to tell the user when every online source failed: (text, kind).
     * "No internet" beats everything (it is the real reason); otherwise Gemini's own message and kind win, then the chain's.
     * Nothing tried at all (for example Gemini still cooling and the chain switched off) = "cooling".
     */
    fun combinedFailure(geminiFail: String, geminiText: String, chainKind: String, en: Boolean): Pair<String, String> {
        val kind = when {
            chainKind == "network" -> "network"
            geminiFail.isNotEmpty() -> geminiFail
            chainKind.isNotEmpty() -> chainKind
            else -> "cooling"
        }
        val text = if (kind == geminiFail && geminiText.isNotEmpty()) geminiText else failText(kind, en)
        return Pair(text, kind)
    }

    fun failText(kind: String, en: Boolean): String = if (en) {
        when (kind) {
            "network" -> "There is no internet, so I could not get an online answer."
            "quota" -> "The free AI services have hit their limit for now. Please try again in a few minutes."
            "key" -> "An AI key was rejected. Please check the keys in Settings."
            "cooling" -> "The AI services are resting for a moment after errors. Please try again shortly."
            "slow" -> "The answer took too long, so I stopped waiting."
            else -> "The free AI services could not answer right now."
        }
    } else {
        when (kind) {
            "network" -> "Internet nahi hai, isliye online jawab nahi mil paaya."
            "quota" -> "Free AI services ki limit abhi poori hai. Thodi der baad try karo."
            "key" -> "Ek AI key reject hui. Settings mein keys check karo."
            "cooling" -> "Errors ke baad AI services thodi der rest par hain. Kuch der baad try karo."
            "slow" -> "Jawab aane mein bahut der lag rahi thi, isliye ruk gaya."
            else -> "Free AI services abhi jawab nahi de paayin."
        }
    }
}

object ProviderChain {
    /**
     * Tries [providers] in order until one gives a usable answer.
     * - a provider that is disabled, cooling down, or needs a key the user has not set is skipped without a call;
     * - 429 / 400 / 404 / 422 -> next MODEL of the same provider; 401 / 403 / 5xx / timeout -> next PROVIDER;
     * - 598 (no internet) ends the whole chain at once (every other provider would fail too);
     * - a 200 answer with no usable text counts as a failure ("other") and moves on;
     * - a provider that failed is put on cooldown (FreeProviders.cooldownFor) so the next question skips it;
     * - [budgetMs] bounds the total waiting time, because a voice assistant must not hang.
     * [call] = (provider, model, jsonBody, key) -> NetAnswer; it must never throw (the Android caller returns 599 for IO errors).
     */
    fun run(
        providers: List<FreeProvider>,
        keyOf: (String) -> String,
        enabled: (String) -> Boolean,
        system: String,
        user: String,
        cd: Cooldowns,
        now: () -> Long,
        budgetMs: Long = 14_000L,
        turns: List<Pair<String, String>> = emptyList(),
        rich: Boolean = false,   // v7.1: typed chat turn -> long answer (2000 tokens, 6000 char question, newlines kept)
        accept: ((String) -> Boolean)? = null,   // v6.90: a text that fails this check counts as "no answer from this provider" (no cooldown), the next provider tries
        hedge: Boolean = false,   // v7.4 5-second rule: ask the top two providers at the same time (first valid answer wins)
        call: (FreeProvider, String, String, String) -> NetAnswer
    ): ChainResult {
        if (hedge && !rich) return runHedged(providers, keyOf, enabled, system, user, cd, now, budgetMs, turns, UnderstandBudget.HEDGE_WIDTH, accept, call)
        val start = now()
        val tried = ArrayList<String>()
        var worst = "nokey"
        for (p in providers) {
            if (!enabled(p.id)) continue
            if (now() - start > budgetMs) {
                worst = FreeProviders.worse(worst, "slow")
                break
            }
            if (cd.cooling(p.id, now())) {
                worst = FreeProviders.worse(worst, "cooling")
                continue
            }
            val key = if (p.keyName == null) "" else keyOf(p.keyName).trim()
            if (p.keyName != null && key.isEmpty()) continue
            tried.add(p.id)
            var kind = "other"
            var cool = true
            for (m in p.models) {
                if (now() - start > budgetMs) {
                    kind = "slow"
                    cool = false
                    break
                }
                val r = call(p, m, FreeProviders.buildBody(m, system, user, maxTokens = if (rich) FreeProviders.RICH_MAX_TOKENS else 350, extra = p.extra, turns = turns, userCap = if (rich) FreeProviders.MAX_USER_CHARS_RICH else FreeProviders.MAX_USER_CHARS), key)
                if (r.code in 200..299) {
                    val t = FreeProviders.parseReply(r.body, rich)
                    if (t != null && (accept == null || accept(t))) return ChainResult(t, p.id, m, "", tried)
                    kind = FreeProviders.worse(kind, "other")
                    if (t != null) { cool = false; break }   // answered, but not in the required shape: try the next provider, do not penalise this one
                    continue
                }
                if (r.code == 598) return ChainResult(null, null, null, "network", tried)
                kind = FreeProviders.worse(kind, FreeProviders.kindOf(r.code))
                val sameProviderNextModel = r.code == 429 || r.code == 400 || r.code == 404 || r.code == 422
                if (!sameProviderNextModel) break
            }
            if (cool) cd.set(p.id, FreeProviders.cooldownFor(kind), now())
            worst = FreeProviders.worse(worst, kind)
        }
        return ChainResult(null, null, null, worst, tried)
    }

    /**
     * v7.4 5-second rule: the first [width] usable providers are asked AT THE SAME TIME and the first VALID answer wins.
     * Each worker is the normal [run] for one provider (same models, cooldowns, accept check), so nothing about the fallback is removed.
     * If nobody answered in time the remaining providers are tried one after the other with whatever time is left.
     */
    fun runHedged(
        providers: List<FreeProvider>,
        keyOf: (String) -> String,
        enabled: (String) -> Boolean,
        system: String,
        user: String,
        cd: Cooldowns,
        now: () -> Long,
        budgetMs: Long,
        turns: List<Pair<String, String>> = emptyList(),
        width: Int = UnderstandBudget.HEDGE_WIDTH,
        accept: ((String) -> Boolean)? = null,
        call: (FreeProvider, String, String, String) -> NetAnswer
    ): ChainResult {
        val start = now()
        val usable = providers.filter { p -> enabled(p.id) && !cd.cooling(p.id, now()) && (p.keyName == null || keyOf(p.keyName).trim().isNotEmpty()) }
        if (usable.size < 2 || width < 2) return run(providers, keyOf, enabled, system, user, cd, now, budgetMs, turns, rich = false, accept = accept, hedge = false, call = call)
        val first = usable.take(width)
        val rest = providers.filter { it !in first }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(first.size)
        val ecs = java.util.concurrent.ExecutorCompletionService<ChainResult>(pool)
        try {
            for (p in first) ecs.submit(java.util.concurrent.Callable<ChainResult> { run(listOf(p), keyOf, enabled, system, user, cd, now, budgetMs, turns, rich = false, accept = accept, hedge = false, call = call) })
            var worst = "nokey"
            val tried = ArrayList<String>()
            var network = false
            repeat(first.size) {
                val left = budgetMs - (now() - start)
                val f = ecs.poll(maxOf(200L, left), java.util.concurrent.TimeUnit.MILLISECONDS) ?: return@repeat
                val r = try { f.get() } catch (e: Exception) { null } ?: return@repeat
                if (r.text != null) { pool.shutdownNow(); return r }
                tried.addAll(r.tried)
                if (r.failKind == "network") network = true
                worst = FreeProviders.worse(worst, r.failKind)
            }
            if (network) return ChainResult(null, null, null, "network", tried)
            val left = budgetMs - (now() - start)
            if (rest.isNotEmpty() && left > UnderstandBudget.MIN_CALL_MS) {
                val r = run(rest, keyOf, enabled, system, user, cd, now, left, turns, rich = false, accept = accept, hedge = false, call = call)
                return ChainResult(r.text, r.providerId, r.model, r.failKind, tried + r.tried)
            }
            return ChainResult(null, null, null, worst, tried)
        } finally {
            pool.shutdown()
        }
    }
}
