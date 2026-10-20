package com.nova.assistant

/**
 * v7.90: "band features set karo" / "jo permission baaki hai wo kholo" by voice.
 * Android never lets an app switch Accessibility / Notification access / Overlay / Write settings ON by itself,
 * so NOVA opens the exact Settings page for the first missing one and says what to press. Pure Kotlin (JUnit-able).
 */
object FeatureFixRules {
    private val WHAT = listOf("band feature", "band features", "feature band", "features band", "permission", "permissions", "setup", "set up", "warn")
    private val DO = listOf("set karo", "set kar do", "chalu karo", "chalu kar do", "on karo", "on kar do", "kholo", "khol do", "fix karo", "theek karo", "theek kar do", "poora karo", "complete karo", "de do", "enable", "allow karo", "allow kar do", "agla", "next")
    private val BLOCK = listOf("kya", "kaun", "status", "batao", "list", "kitne", "kitni", "?", "torch", "wifi", "bluetooth", "brightness", "volume", "silent", "alarm", "meeting", "schedule")

    fun matches(raw: String): Boolean {
        val n = Logic.norm(raw)
        if (n.isBlank() || n.length > 80) return false
        val t = n.split(" ")
        if (t.any { it in BLOCK }) return false
        val hasWhat = WHAT.any { n.contains(it) }
        val hasDo = DO.any { d -> if (d.contains(" ")) n.contains(d) else d in t }
        return hasWhat && hasDo
    }

    /** Short spoken name for each missing special access key (same keys as MainActivity.missingSpecial). */
    fun label(key: String): String = when (key) {
        "overlay" -> "Display over other apps"
        "write" -> "Modify system settings"
        "battery" -> "Battery unrestricted"
        "alarm" -> "Alarms and reminders"
        "fullscreen" -> "Full screen notifications"
        "install" -> "Install unknown apps"
        "listener" -> "Notification access"
        "access" -> "Accessibility"
        else -> key
    }

    fun reply(missing: List<String>, opened: String?): String {
        if (missing.isEmpty()) return "Sab features set hain, koi permission baaki nahi."
        val names = missing.take(4).joinToString(", ") { label(it) }
        val more = if (missing.size > 1) " Phir bolo: band features set karo, agla khol dunga." else ""
        return if (opened != null) "${missing.size} cheez baaki hai: $names. ${label(opened)} ki screen khol di, NOVA ke aage switch ON karo.$more"
        else "${missing.size} cheez baaki hai: $names. Screen nahi khul paayi, Setup me jaakar ON karo."
    }
}
