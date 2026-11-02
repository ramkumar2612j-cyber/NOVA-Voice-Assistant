package com.nova.assistant

/** Part 9: rules for explicit, local-only alias teaching. No learned item can itself execute a command. */
object TeachRules {
    data class Pairing(val alias: String, val value: String)
    private val markers = listOf(" ka matlab " to " ", " means " to " ", " ko " to " = ")
    private val forbidden = setOf("password", "otp", "pin", "cvv", "secret", "api", "key", "token", "bank", "aadhaar", "aadhar", "pan", "passport")

    private val prefixes = listOf("yaad rakho ", "yaad rakhna ", "remember ", "teach ")
    private val questionWords = setOf("kya", "kaun", "kaise", "kyun", "kyu", "kitna", "kitni", "kitne", "kab", "kahan",
        "what", "who", "why", "how", "when", "where", "which")

    /** Lower-case, single spaces, optional leading "nova " removed. */
    private fun clean(text: String): String {
        var s = text.trim().lowercase().replace(Regex("\\s+"), " ")
        if (s.startsWith("nova ")) s = s.substring(5).trim()
        return s
    }

    /**
     * Only an explicit teaching sentence counts. "X = Y" and "X ka matlab Y" are explicit; the loose forms "X is Y" and
     * "X means Y" only count after a teach word (yaad rakho / remember / teach), so ordinary questions such as
     * "what is the weather" or "pyar ka matlab kya hai" are never swallowed and never saved as an alias.
     */
    fun isIntent(text: String): Boolean {
        // Part 2 personal-memory commands keep priority even when they start with "yaad rakho".
        if (PersonalMemory.parse(text) != null) return false
        val s = clean(text)
        if (s.length !in 5..180 || s.endsWith("?")) return false
        if (s.split(" ").any { it.trim('?', '.', ',', '!') in questionWords }) return false
        val prefixed = prefixes.any { s.startsWith(it) }
        val strong = " = " in s || " ka matlab " in s
        val loose = " means " in s || Regex("\\s+is\\s+").containsMatchIn(s)
        return strong || (prefixed && loose)
    }

    fun parse(text: String): Pairing? {
        var raw = text.trim()
        if (raw.startsWith("nova ", true)) raw = raw.substring(5).trim()
        if (raw.length !in 5..180) return null
        val s = raw.lowercase()
        val lead = prefixes.firstOrNull { s.startsWith(it) }
        val prefixed = lead != null
        val body = lead?.let { raw.substring(it.length).trim() } ?: raw
        val lower = body.lowercase()
        val split: Pair<String, String> = when {
            " = " in body -> { val i = body.indexOf(" = "); body.substring(0, i) to body.substring(i + 3) }
            " ka matlab " in lower -> { val i = lower.indexOf(" ka matlab "); body.substring(0, i) to body.substring(i + 11) }
            prefixed && " means " in lower -> { val i = lower.indexOf(" means "); body.substring(0, i) to body.substring(i + 7) }
            prefixed && " is " in lower -> { val i = lower.indexOf(" is "); body.substring(0, i) to body.substring(i + 4) }
            else -> null
        } ?: return null
        var alias = split.first.trim().trim('"', '\'', ':', '=', '.', ',')
        var value = split.second.trim().trim('"', '\'', ':', '=', '.', ',')
        if (alias.startsWith("yaad rakho ")) alias = alias.removePrefix("yaad rakho ").trim()
        if (!valid(alias, value)) return null
        return Pairing(alias, value)
    }

    fun valid(alias: String, value: String): Boolean {
        val a = Logic.norm(alias); val v = Logic.norm(value)
        if (a.length !in 2..40 || v.length !in 2..80 || a == v) return false
        if (a.split(" ").size > 5 || v.split(" ").size > 8) return false
        if (a.any { it.isDigit() } || value.contains(Regex("https?://|[\\r\\n]"))) return false
        if (a.split(" ").any { it in forbidden } || v.split(" ").any { it in forbidden }) return false
        // Alias/value are data labels (for example "mera camera = Camera app"), not executable steps.
        // TeachStore only substitutes them in an explicit target slot, never as a command or arbitrary chat.
        return true
    }

    fun exportValidJson(raw: String): Boolean {
        if (raw.length !in 2..20_000) return false
        val root = MiniJson.parse(raw) as? Map<*, *> ?: return false
        if (root.keys != setOf("version", "items") || root["version"] != 1.0) return false
        val items = root["items"] as? List<*> ?: return false
        if (items.size > 100) return false
        return items.all { row ->
            val m = row as? Map<*, *> ?: return@all false
            m.keys == setOf("alias", "value") && (m["alias"] as? String)?.let { a ->
                (m["value"] as? String)?.let { v -> valid(a, v) } == true
            } == true
        }
    }
}
