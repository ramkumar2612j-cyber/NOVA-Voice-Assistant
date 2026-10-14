package com.nova.assistant

/**
 * Central, side-effect-free intent parsing for phone communication and app navigation.
 * Parsing never resolves a contact, checks permissions, asks for confirmation, or executes an action.
 */
object IntentParser {
    enum class Kind { OPEN_APP, CONTACT, CALL, MESSAGE, APP_CALL, APP_MESSAGE, AMBIGUOUS }
    enum class Confidence { HIGH, MEDIUM, LOW }
    enum class Action { OPEN, CALL, MESSAGE, UNKNOWN }

    data class ParsedIntent(
        val kind: Kind,
        val action: Action,
        val appId: String? = null,
        val target: String? = null,
        val message: String? = null,
        val confidence: Confidence,
        val clarification: String? = null
    )

    private val openPrefix = Regex("^(?:(?:hey )?nova[ ,]+)?(?:please )?(?:open|launch|start|kholo|khol|khol do|app kholo)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val openSuffix = Regex("^(.+?)\\s+(?:kholo|khol do|open karo|launch karo)$", RegexOption.IGNORE_CASE)
    private val appPrefix = Regex("^(whatsapp business|whatsapp|wa business|wa)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val messageVerbs = Regex("\\b(message|msg|text|sms|bhejo|bhejna|send|likh kar bhejo|whatsapp)\\b", RegexOption.IGNORE_CASE)
    private val callVerbs = Regex("\\b(call|phone|dial|ring|milao|mila do|baat karao|baat karwao)\\b", RegexOption.IGNORE_CASE)
    private val negation = Regex("\\b(mat|nahi|nahin|nhi|dont|don't|do not|not|never|bina)\\b", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ParsedIntent? {
        if (raw.isBlank()) return null
        val roman = romanNorm(raw)
        val s = Logic.stripWake(roman).replace(Regex("^(?:please|zara|jara|abhi|now) +"), "").trim()
        if (s.isBlank()) return null
        // Never elevate a negated utterance into an executable intent. The negation test only looks at the COMMAND part:
        // words inside a WhatsApp message payload ("... kal nahi aaunga") are the user's text, not a cancelled command.
        // A payload that is itself just a negation ("message mat bhejo") is still blocked.
        val wa = if (Regex("^(?:whatsapp|wa)\\b").containsMatchIn(s)) WhatsAppCommandRules.parse(raw) else null
        var commandPart = s
        if (wa != null) {
            val payload = romanNorm(wa.message)
            if (payload.isNotBlank() && s.endsWith(payload)) commandPart = s.dropLast(payload.length).trim()
            val tokens = payload.split(" ").filter { it.isNotBlank() }
            if (tokens.size <= 2 && negation.containsMatchIn(payload)) return null
        }
        if (negation.containsMatchIn(commandPart)) return null

        // Exact launch phrases must preserve the requested package identity.
        if (s in setOf("whatsapp kholo", "whatsapp khol", "whatsapp open", "open whatsapp", "launch whatsapp")) {
            return ParsedIntent(Kind.OPEN_APP, Action.OPEN, appId = "whatsapp", target = "WhatsApp", confidence = Confidence.HIGH)
        }
        if (s in setOf("whatsapp business kholo", "whatsapp business khol", "whatsapp business open", "open whatsapp business", "launch whatsapp business", "wa business kholo")) {
            return ParsedIntent(Kind.OPEN_APP, Action.OPEN, appId = "whatsapp_business", target = "WhatsApp Business", confidence = Confidence.HIGH)
        }

        // Explicit app + contact actions. Contact identity is resolved later against real contacts.
        val appContact = Regex("^(whatsapp business|wa business|whatsapp|wa)\\s+(?:par\\s+)?(.+)$").matchEntire(s)
        if (appContact != null) {
            val appLabel = appContact.groupValues[1]
            val app = if (appLabel.contains("business")) "whatsapp_business" else "whatsapp"
            val rest = appContact.groupValues[2].trim()
            val msg = Regex("^(.+?)\\s+ko\\s+(?:message|msg|text)\\s+(?:bhejo|bhejna|send karo|kar do|karo)(?:\\s+(.+))?$").matchEntire(rest)
                ?: Regex("^(.+?)\\s+ko\\s+message\\s+(?:bhejo|bhejna|send karo|kar do|karo)(?:\\s+(.+))?$").matchEntire(rest)
            if (msg != null) {
                // Prefer the strict parser's result: it hands back the user's original casing / punctuation / script.
                val who = wa?.recipient ?: msg.groupValues[1].trim()
                val body = wa?.message ?: msg.groupValues.getOrNull(2)?.trim().orEmpty()
                if (who.isBlank()) return ambiguous("Kisko message bhejna hai?", app)
                if (body.isNotBlank()) return ParsedIntent(Kind.APP_MESSAGE, Action.MESSAGE, app, who, body, Confidence.HIGH)
                return ambiguous("Kya message bhejna hai?", app)
            }
            val call = Regex("^(.+?)\\s+ko\\s+(?:call|phone|dial)\\s+(?:karo|kar do|lagao)?$").matchEntire(rest)
            if (call != null && call.groupValues[1].isNotBlank())
                return ParsedIntent(Kind.APP_CALL, Action.CALL, app, call.groupValues[1].trim(), confidence = Confidence.MEDIUM)
        }

        appPrefix.matchEntire(s)?.let { m ->
            val app = if (m.groupValues[1].contains("business")) "whatsapp_business" else "whatsapp"
            val rest = m.groupValues[2].trim()
            if (rest.isBlank()) return ambiguous("$app mein kya karna hai?", app)
            val businessRest = Regex("^(?:whatsapp business|wa business)\\s+(.+)$", RegexOption.IGNORE_CASE)
                .find(Logic.stripWake(raw.trim()))?.groupValues?.get(1)
            val sendInput = if (app == "whatsapp_business") "WhatsApp ${businessRest ?: rest}" else raw
            val send = WhatsAppCommandRules.parse(sendInput)
            if (send != null) return ParsedIntent(Kind.APP_MESSAGE, Action.MESSAGE, app, send.recipient, send.message, Confidence.HIGH)
            if (callVerbs.containsMatchIn(rest)) {
                val target = rest.replace(Regex("\\b(call|phone|dial|ring|karo|kar do|lagao|laga do|milao|mila do)\\b"), " ")
                    .replace(Regex("\\s+"), " ").trim().removeSuffix("ko").trim()
                if (target.isBlank()) return ambiguous("Kise $app se call karna hai?", app)
                return ParsedIntent(Kind.APP_CALL, Action.CALL, app, target, confidence = Confidence.MEDIUM)
            }
            if (messageVerbs.containsMatchIn(rest)) return ambiguous("Kisko aur kya message bhejna hai?", app)
            // App-only commands remain app-open requests, not communication actions.
            if (rest in setOf("open", "kholo", "khol", "khol do", "start", "launch", "open karo")) {
                val label = if (app == "whatsapp_business") "WhatsApp Business" else "WhatsApp"
                return ParsedIntent(Kind.OPEN_APP, Action.OPEN, appId = app, target = label, confidence = Confidence.HIGH)
            }
        }

        // Contact-only lookup requests are identified separately from call execution.
        val contactLookup = listOf(
            Regex("^(?:find|search|look up|lookup) contact (.+)$"),
            Regex("^contact (.+)$"),
            Regex("^(.+?) ka number (?:batao|dikhao|bata do)$")
        ).firstNotNullOfOrNull { it.matchEntire(s) }
        if (contactLookup != null) {
            val who = contactLookup.groupValues[1].trim()
            if (who.isNotBlank() && who.length <= 80 && who.split(" ").size <= 8)
                return ParsedIntent(Kind.CONTACT, Action.UNKNOWN, target = who, confidence = Confidence.HIGH)
            return ambiguous("Kis contact ko dhoondhna hai?", null)
        }

        val contact = ContactRequestParser.parseCommand(raw)
        if (contact != null) {
            if (contact.isReference || contact.requestedName.isBlank()) return ambiguous("Kis contact ko call karna hai?", null)
            return ParsedIntent(Kind.CALL, Action.CALL, target = contact.requestedName, confidence = Confidence.HIGH)
        }

        // Strict app-specific message parser first; it preserves the existing message format and safety checks.
        WhatsAppCommandRules.parse(raw)?.let {
            return ParsedIntent(Kind.APP_MESSAGE, Action.MESSAGE, "whatsapp", it.recipient, it.message, Confidence.HIGH)
        }
        // Generic SMS/message phrasing is classified only when both recipient and payload are explicit.
        val genericMessage = listOf(
            Regex("^(?:please )?(?:send|text|message|sms) (?:a )?message to (.+?) (?:saying|that says|with text) (.+)$"),
            Regex("^(.+?) ko (?:message|msg|sms|text) (?:bhejo|bhejna|send karo|kar do) (.+)$"),
            Regex("^(?:send|text|message|sms) (?:to )?(.+?) (?:saying|that says|with text) (.+)$")
        ).firstNotNullOfOrNull { it.find(s) }
        if (genericMessage != null) {
            val groups = genericMessage.groupValues
            val recipient = groups[1].trim(); val body = groups[2].trim()
            if (recipient.isNotBlank() && body.isNotBlank() && recipient.length <= 80 && body.length <= 1000)
                return ParsedIntent(Kind.MESSAGE, Action.MESSAGE, "sms", recipient, body, Confidence.HIGH)
        }

        openPrefix.matchEntire(s)?.let { m ->
            val app = m.groupValues[1].trim().removeSuffix(" app").trim()
            if (app.isBlank()) return ambiguous("Kaun sa app kholna hai?", null)
            if (app.split(" ").size > 5 || app.length > 40) return ambiguous("Kaun sa app kholna hai?", null)
            return ParsedIntent(Kind.OPEN_APP, Action.OPEN, appId = appKey(app), target = app, confidence = Confidence.HIGH)
        }
        openSuffix.matchEntire(s)?.let { m ->
            val app = m.groupValues[1].trim().removeSuffix(" app").trim()
            if (app.isNotBlank() && app.length <= 40 && app.split(" ").size <= 5)
                return ParsedIntent(Kind.OPEN_APP, Action.OPEN, appId = appKey(app), target = app, confidence = Confidence.MEDIUM)
        }

        if (s in setOf("call", "phone", "dial", "ring", "call karo", "phone lagao", "message", "msg", "send message", "sms bhejo", "text someone", "open", "open app", "app kholo", "kholo", "khol do")) {
            return ambiguous(when (s) {
                "message", "msg", "send message", "sms bhejo", "text someone" -> "Kisko aur kya message bhejna hai?"
                "open", "open app", "app kholo", "kholo", "khol do" -> "Kaun sa app kholna hai?"
                else -> "Kis contact ko call karna hai?"
            }, null)
        }

        // A sentence that mixes communication and navigation verbs is unsafe to guess about.
        if ((callVerbs.containsMatchIn(s) && (messageVerbs.containsMatchIn(s) || Regex("\\b(open|launch|kholo|khol)\\b").containsMatchIn(s))) ||
            (messageVerbs.containsMatchIn(s) && Regex("\\b(open|launch|kholo|khol)\\b").containsMatchIn(s))) {
            return ambiguous("Call, message ya app open — kaunsa kaam karna hai?", null)
        }
        return null
    }

    private fun romanNorm(raw: String): String = HindiRoman.toRoman(raw).lowercase()
        .replace(Regex("[^a-z0-9+ ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun ambiguous(prompt: String, app: String?) = ParsedIntent(Kind.AMBIGUOUS, Action.UNKNOWN, appId = app, confidence = Confidence.LOW, clarification = prompt)

    /** Stable app identity is distinct from the action. Resolution to an installed package happens later. */
    fun appKey(label: String): String {
        val n = label.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        return when (n) {
            "whatsapp", "wa" -> "whatsapp"
            "whatsapp business", "wa business" -> "whatsapp_business"
            "instagram", "insta" -> "instagram"
            "facebook", "fb" -> "facebook"
            "phone", "dialer" -> "phone"
            "messages", "message", "sms", "messaging" -> "sms"
            else -> n.replace(' ', '_')
        }
    }
}
