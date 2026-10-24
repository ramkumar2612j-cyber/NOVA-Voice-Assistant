package com.nova.assistant

import android.content.Context

/**
 * v7.114 FAMILY NUMBERS: Papa, Mummy and a few more named people, typed (or said) once and kept inside NOVA.
 * "nova papa ko call karo" / "mummy ko whatsapp bhejo" then use the saved number straight away: no Contacts permission, no contact search, no "kaun sa contact" question.
 * Pure rules at the top (JUnit-able); the small store at the bottom is the only Android part.
 *
 * Decisions made HERE:
 *  - Papa / Mummy keep using the SAME saved numbers the low-battery SOS already uses (ExtCfg.sosNumber), so there is ONE place for them.
 *  - Other people: at most [MAX_CUSTOM], name 2..24 letters, number 7..15 digits (+ allowed at the start). Anything else is refused, never half-saved.
 *  - A spoken name matches only as a WHOLE name ("papa", "papaji", "mummy"), never as part of a longer name ("Mama Ji Sweet Shop" is not Mummy).
 *  - Saving by voice needs the words "number" (or no / num / mobile) AND a save word AND a real number, so a normal sentence is never taken for it.
 */
object FamilyContacts {
    const val MAX_CUSTOM = 12
    const val MIN_DIGITS = 7
    const val MAX_DIGITS = 15

    class Entry(val key: String, val label: String, val number: String, val builtin: Boolean)
    class SaveReq(val name: String, val number: String)

    private val PAPA = BatteryRules.PAPA + listOf("पापा", "पिताजी", "abbu", "abba", "baba", "appa")
    private val MUMMY = BatteryRules.MUMMY + listOf("मम्मी", "माँ", "मां", "ammi", "amma", "mumy")

    /** Digits kept, one leading "+", 7 to 15 digits. "" = not a usable number. */
    fun cleanNumber(raw: String?): String {
        val t = (raw ?: "").trim()
        val digits = t.filter { it.isDigit() }
        if (digits.length !in MIN_DIGITS..MAX_DIGITS) return ""
        return (if (t.startsWith("+")) "+" else "") + digits
    }

    /** 2..24 letters / spaces, one or two words, first letters upper case. "" = refused. */
    fun cleanName(raw: String?): String {
        val t = (raw ?: "").trim().replace(Regex("\\s+"), " ")
        if (t.length !in 2..24) return ""
        if (!t.all { it.isLetter() || it == ' ' }) return ""
        val words = t.split(' ')
        if (words.size > 2) return ""
        return words.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }

    private fun key(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** "papa" / "mummy" when [spoken] is exactly one of their names, else null. */
    fun builtinKey(spoken: String?): String? {
        val k = (spoken ?: "").lowercase().trim().replace(Regex("\\s+(ji|jee)$"), "ji").replace(" ", "")
        if (k.isEmpty()) return null
        if (PAPA.any { it.replace(" ", "") == k }) return "papa"
        if (MUMMY.any { it.replace(" ", "") == k }) return "mummy"
        return null
    }

    /** The saved entry a spoken name means, or null. [papa] / [mummy] are the saved SOS numbers ("" = not saved). */
    fun find(spoken: String?, papa: String, mummy: String, custom: List<Entry>): Entry? {
        val said = (spoken ?: "").trim()
        if (said.isEmpty() || said.length > 40) return null
        when (builtinKey(said)) {
            "papa" -> if (papa.isNotBlank()) return Entry("papa", "Papa", papa, true)
            "mummy" -> if (mummy.isNotBlank()) return Entry("mummy", "Mummy", mummy, true)
        }
        val k = key(said)
        if (k.isEmpty()) return null
        custom.firstOrNull { key(it.label) == k }?.let { return it }
        return null
    }

    private val SAVE_WORDS = setOf("save", "yaad", "set", "add", "store", "daalo", "dalo", "likh", "likho", "rakho", "rakh", "note", "register", "jodo")
    private val NUMBER_WORDS = setOf("number", "no", "num", "nambar", "numbar", "mobile", "phone", "contact")
    private val NAME_JUNK = setOf("nova", "mera", "meri", "mere", "apna", "apni", "ye", "yeh", "is", "ka", "ki", "ke", "ko", "me", "mein", "save", "karo", "kro", "kar", "do", "yaad", "rakho", "add", "set", "number", "no", "num", "phone", "mobile", "contact", "naya", "new", "please", "plz", "ek", "hai", "h", "ye")

    /**
     * "papa ka number 9876543210 save karo", "nova mummy ka no +91 98765 43210 yaad rakho", "rahul ka mobile number 98... save kar do".
     * null = not a save sentence.
     */
    fun parseSave(text: String?): SaveReq? {
        val raw = (text ?: "").lowercase()
        if (raw.length > 120) return null
        val words = raw.replace(Regex("[^a-z0-9+\\u0900-\\u097F ]"), " ").replace(Regex("\\s+"), " ").trim().split(' ')
        if (words.none { it in SAVE_WORDS } || words.none { it in NUMBER_WORDS }) return null
        // number: the run of digits (spaces / dashes inside allowed) in the original text
        val m = Regex("\\+?\\d[\\d\\s\\-]{5,18}\\d").find(raw) ?: return null
        val number = cleanNumber(m.value)
        if (number.isEmpty()) return null
        // name: the words before "ka / ki / ke" + number word
        val before = raw.substring(0, m.range.first).replace(Regex("[^a-z\\u0900-\\u097F ]"), " ").replace(Regex("\\s+"), " ").trim().split(' ').filter { it.isNotEmpty() }
        val idx = before.indexOfFirst { it == "ka" || it == "ki" || it == "ke" }
        val cand = (if (idx > 0) before.subList(0, idx) else before.filter { it !in NAME_JUNK }.take(2)).filter { it !in NAME_JUNK }
        if (cand.isEmpty() || cand.size > 2) return null
        val name = cleanName(cand.joinToString(" "))
        if (name.isEmpty()) return null
        return SaveReq(name, number)
    }

    fun savedText(name: String, number: String, en: Boolean): String {
        val tail = number.takeLast(4)
        return if (en) "Saved $name's number (ends with $tail). Now \"call $name\" uses it directly."
        else "$name ka number save ho gaya (aakhri 4 ank $tail). Ab \"$name ko call karo\" seedha isi number par jayega."
    }
    fun refusedText(en: Boolean): String =
        if (en) "I could not save that. Say a short name and a number of 7 to 15 digits, e.g. \"papa ka number 9876543210 save karo\"."
        else "Ye save nahi ho paya. Chhota naam aur 7 se 15 ank ka number bolo, jaise: papa ka number 9876543210 save karo."
    fun fullText(en: Boolean): String =
        if (en) "The list is full ($MAX_CUSTOM people). Remove one in Settings, then try again."
        else "List full hai ($MAX_CUSTOM log). Settings mein ek hata do, phir dobara bolo."

    // ------------------------------------------------------------------ Android store
    private const val PREF = "nova_family"
    private const val KEY = "custom"

    fun customList(ctx: Context): List<Entry> = try {
        val a = org.json.JSONArray(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]")
        val out = ArrayList<Entry>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val l = cleanName(o.optString("label")); val n = cleanNumber(o.optString("number"))
            if (l.isNotEmpty() && n.isNotEmpty()) out.add(Entry(key(l), l, n, false))
        }
        out
    } catch (e: Throwable) { emptyList() }

    private fun writeCustom(ctx: Context, list: List<Entry>) {
        val a = org.json.JSONArray()
        for (e in list.take(MAX_CUSTOM)) a.put(org.json.JSONObject().put("label", e.label).put("number", e.number))
        try { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply() } catch (e: Throwable) { ErrLog.add("FamilyContacts", e.toString()) }
    }

    /** The saved entry for a spoken name (Papa / Mummy / custom), or null. */
    fun lookup(ctx: Context, spoken: String?): Entry? =
        find(spoken, ExtCfg.sosNumber(ctx, "papa"), ExtCfg.sosNumber(ctx, "mummy"), customList(ctx))

    /** Result: the saved number ("" = refused or removed). A blank [rawNumber] removes a custom person (Papa / Mummy are cleared). */
    fun set(ctx: Context, name: String, rawNumber: String): String {
        val bk = builtinKey(name)
        if (bk != null) return ExtCfg.setSosNumber(ctx, bk, rawNumber)
        val label = cleanName(name)
        if (label.isEmpty()) return ""
        val list = customList(ctx).filter { key(it.label) != key(label) }.toMutableList()
        if (rawNumber.isBlank()) { writeCustom(ctx, list); return "" }
        val n = cleanNumber(rawNumber)
        if (n.isEmpty()) return ""
        if (list.size >= MAX_CUSTOM) return ""
        list.add(Entry(key(label), label, n, false))
        writeCustom(ctx, list)
        return n
    }

    fun isFull(ctx: Context, name: String): Boolean = builtinKey(name) == null && customList(ctx).let { l -> l.size >= MAX_CUSTOM && l.none { key(it.label) == key(name) } }

    fun json(ctx: Context): String {
        val o = org.json.JSONObject()
        o.put("papa", ExtCfg.sosNumber(ctx, "papa")); o.put("mummy", ExtCfg.sosNumber(ctx, "mummy"))
        val a = org.json.JSONArray()
        for (e in customList(ctx)) a.put(org.json.JSONObject().put("label", e.label).put("number", e.number))
        o.put("others", a)
        return o.toString()
    }
}
