package com.nova.assistant

/**
 * v6.76 (Part 6): one place that says, for every phone tool, HOW MUCH NOVA can really verify, what to do when it fails (bounded),
 * which tools are sensitive, and how the one-line report reads. Pure Kotlin (no Android classes), so it is unit tested.
 *
 * It describes what the tool code in NovaService already does (torch reads TorchCallback, volume reads AudioManager, brightness reads
 * Settings back, launch() watches the foreground package, alarm / timer get no answer from Android). It never upgrades a tool to "verified".
 */
object ToolVerify {
    /** VERIFIED = state read back from Android. PARTIAL = Android accepted the request / screen changed, result not read back.
     *  HANDOFF = NOVA prepares it, the owner finishes it (press send / save). UNVERIFIABLE = Android gives no answer at all. */
    enum class Level { VERIFIED, PARTIAL, HANDOFF, UNVERIFIABLE }

    enum class Sensitive { NONE, SPOKEN_YES, HANDOFF }

    enum class Fail { NOT_FOUND, NO_PERMISSION, NO_ACCESSIBILITY, BLOCKED, NOT_CONFIRMED, BAD_INPUT, NO_HANDLER, OTHER }

    enum class Step { RETRY_ONCE, GUIDE, STOP }

    class Plan(val step: Step, val say: String)

    /** Every tool name used by NovaService.runLocalTool (Cmd kinds) and NovaService.runTool (cloud tool names). */
    private val LEVELS: Map<String, Level> = mapOf(
        "torch" to Level.VERIFIED, "torch_on" to Level.VERIFIED, "torch_off" to Level.VERIFIED,
        "volume" to Level.VERIFIED,
        "brightness" to Level.VERIFIED, "brightness_set" to Level.VERIFIED, "brightness_step" to Level.VERIFIED,
        "open_app" to Level.PARTIAL, "open_url" to Level.PARTIAL, "navigate" to Level.PARTIAL, "play_music" to Level.PARTIAL,
        "camera" to Level.PARTIAL, "settings" to Level.PARTIAL, "global" to Level.PARTIAL, "global_action" to Level.PARTIAL,
        "call" to Level.PARTIAL, "search_web" to Level.PARTIAL,
        "tap" to Level.PARTIAL, "type" to Level.PARTIAL, "scroll" to Level.PARTIAL,
        "sms" to Level.HANDOFF, "whatsapp" to Level.HANDOFF, "calendar_event" to Level.HANDOFF,
        "alarm" to Level.UNVERIFIABLE, "alarm_any" to Level.UNVERIFIABLE, "timer" to Level.UNVERIFIABLE
    )

    val KNOWN_TOOLS: Set<String> get() = LEVELS.keys + "media"

    /** [arg] matters only for media: play / pause are read back from AudioManager.isMusicActive, next / previous cannot be. An unknown tool is UNVERIFIABLE. */
    fun level(tool: String, arg: String = ""): Level {
        if (tool == "media") return if (arg == "play" || arg == "pause") Level.PARTIAL else Level.UNVERIFIABLE
        return LEVELS[tool] ?: Level.UNVERIFIABLE
    }

    // ------------------------------------------------------------------ sensitive actions

    /** What the tool WOULD ask when the owner turns the skip-confirm settings OFF (ExtCfg.skipRiskyConfirm / skipOrderConfirm; both default ON = no question at all). SMS / calendar only open the compose / save screen: the owner presses send / save. */
    fun sensitive(tool: String): Sensitive = when (tool) {
        "call", "whatsapp" -> Sensitive.SPOKEN_YES
        "sms", "calendar_event" -> Sensitive.HANDOFF
        else -> Sensitive.NONE
    }

    /** A screen label that must never be tapped silently: money / account / security words (denied by AgentRules) or risky ones (send, delete, post, submit...). */
    fun labelIsSensitive(label: String): Boolean {
        val words = Logic.norm(label).split(" ").filter { it.isNotEmpty() }
        return words.any { it in Control.RISKY || it in MONEY_SECURITY }
    }

    private val MONEY_SECURITY = setOf(
        "pay", "payment", "buy", "purchase", "transfer", "order", "uninstall", "erase", "reset",
        "upgrade", "subscribe", "subscription", "checkout", "donate", "kharido", "password", "otp", "passcode"
    )

    // ------------------------------------------------------------------ failure kinds and bounded recovery

    /** Reads the failure kind out of a tool's own message (the Hinglish / English texts NovaService produces). Order matters: the first match wins. */
    fun classify(msg: String): Fail {
        val m = msg.lowercase()
        return when {
            m.contains("accessibility") -> Fail.NO_ACCESSIBILITY
            m.contains("do not control this screen") || m.contains("control nahi karta") ||
                m.contains("background se app kholne") || m.contains("opening apps from the background") -> Fail.BLOCKED
            m.contains("no app can") || m.contains("no app could") || m.contains("koi app ye kaam") || m.contains("wala app nahi mila") -> Fail.NO_HANDLER
            m.contains("permission") -> Fail.NO_PERMISSION
            m.contains("confirm nahi") || m.contains("not confirmed") || m.contains("could not confirm") ||
                m.contains("did not change") || m.contains("nahi badla") || m.contains("badli nahi") -> Fail.NOT_CONFIRMED
            m.contains("nahi mila") || m.contains("could not find") || m.contains("not found") -> Fail.NOT_FOUND
            m.contains("galat") || m.contains("not valid") || m.contains("samajh nahi") || m.contains("did not understand") ||
                m.contains("khali") || m.contains("is empty") || m.contains("between") || m.contains("beech") -> Fail.BAD_INPUT
            else -> Fail.OTHER
        }
    }

    /**
     * What to do after a failure. [attempt] = how many times the tool already ran for this sentence (0 = first failure).
     * At most ONE automatic retry, and only for tools that set an explicit state and read it back (torch / volume / brightness):
     * running them twice can never do something different from running them once. Everything else is a guide or a stop. No endless loops.
     */
    fun recovery(tool: String, kind: Fail, attempt: Int, en: Boolean): Plan {
        val retryable = level(tool) == Level.VERIFIED
        return when (kind) {
            Fail.NOT_CONFIRMED ->
                if (retryable && attempt == 0) Plan(Step.RETRY_ONCE, "")
                else Plan(Step.GUIDE, if (en) "This is not verified; please check it yourself." else "Ye verify nahi hua; ek baar khud check kar lo.")
            Fail.NOT_FOUND ->
                if (tool == "open_app") Plan(Step.GUIDE, if (en) "Say the app name again, or install it from the Play Store." else "App ka naam dobara bolo, ya Play Store se install karo.")
                else Plan(Step.STOP, "")
            Fail.NO_PERMISSION, Fail.NO_ACCESSIBILITY ->
                Plan(Step.GUIDE, if (en) "Say \"setup check\" to see what is missing." else "Kya missing hai jaanne ke liye bolo \"setup check\".")
            Fail.BLOCKED, Fail.BAD_INPUT, Fail.NO_HANDLER, Fail.OTHER -> Plan(Step.STOP, "")
        }
    }

    private fun alreadyHonest(msg: String): Boolean {
        val m = msg.lowercase()
        return m.contains("confirm") || m.contains("verify") || m.contains("dabaiye") || m.contains("yourself") || m.contains("check")
    }

    /** Final text of a tool call. A success whose Android answer cannot be read back says so (unless its own text already does); a failure gets the bounded guide. */
    fun finish(tool: String, arg: String, ok: Boolean, msg: String, en: Boolean): String {
        if (ok) {
            if (level(tool, arg) == Level.UNVERIFIABLE && !alreadyHonest(msg)) {
                return msg + (if (en) ". Not verified: Android gives no confirmation." else ". Verify nahi hua: Android confirm nahi karta.")
            }
            return msg
        }
        val plan = recovery(tool, classify(msg), 1, en)
        return if (plan.step == Step.GUIDE && plan.say.isNotBlank() && !msg.contains(plan.say)) msg + ". " + plan.say else msg
    }

    // ------------------------------------------------------------------ the one-line report

    /**
     * ONE sentence: what happened, whether it was verified, what is left. [did] is the result in a few words, [remaining] may be blank.
     * The sentence has no inner full stops, only commas, so it reads as one sentence when spoken.
     */
    fun report(did: String, ok: Boolean, level: Level, remaining: String, en: Boolean): String {
        val d = did.trim().trimEnd('.').replace(". ", ", ")
        val verify = when {
            !ok -> if (en) "not verified" else "verify nahi hua"
            level == Level.VERIFIED -> if (en) "verified" else "verify hua"
            level == Level.PARTIAL -> if (en) "request accepted, result not read back" else "request accept hui, result read back nahi hua"
            level == Level.HANDOFF -> if (en) "waiting for you to finish it" else "aapke finish karne ka intezaar hai"
            else -> if (en) "not verified, Android gives no confirmation" else "verify nahi hua, Android confirm nahi karta"
        }
        val rest = remaining.trim().trimEnd('.').replace(". ", ", ")
        val head = if (ok) d else d + (if (en) " failed" else " nahi hua")
        val left = if (rest.isEmpty()) "" else (if (en) ", left: " else ", baaki: ") + rest
        return head + " (" + verify + ")" + left + "."
    }
}
