package com.nova.assistant

/** Stage 1: parses the user's wording only; it never looks up or acts on a contact. */
data class ContactRequest(val requestedName: String, val isReference: Boolean, val isContactCommand: Boolean)

object ContactRequestParser {
    private val referencePatterns = listOf(
        Regex("^(?:call|phone|dial|ring) (?:this person|that person|him|her|them)$"),
        Regex("^(?:isko|usko|ise|use|is bande ko|us bande ko) (?:call|phone|dial|ring)(?: karo| kar do| lagao| laga do| milao)?$"),
        Regex("^(?:is person ko|us person ko) (?:call|phone)(?: karo| kar do| lagao)?$")
    )

    /** Returns null unless this is recognizably a contact-call command. */
    fun parseCommand(raw: String): ContactRequest? {
        if (raw.isBlank()) return null
        var s = HindiRoman.toRoman(raw).lowercase()
            .replace(Regex("[^a-z0-9+ ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        s = s.replace(Regex("^(?:hey )?nova[ ,]+"), "")
            .replace(Regex("^(?:please|zara|jara|abhi|now) +"), "")
            .replace(Regex("(?: please| zara| jara| abhi| now| na)+$"), "").trim()
        if (s.isBlank()) return null
        // An explicitly negated command must never become an executable call.
        val words = s.split(" ")
        if (words.any { it in setOf("mat", "nahi", "nahin", "nhi", "dont", "not", "never", "bina") } ||
            Regex("\\bdo not\\b|\\bdon t\\b").containsMatchIn(s)) return null
        if (referencePatterns.any { it.matches(s) }) {
            return ContactRequest("", true, true)
        }
        if (s in setOf("call", "phone", "dial", "ring", "call karo", "phone karo", "phone lagao", "dial karo", "ring karo", "phone milao", "phone mila do")) {
            return ContactRequest("", false, true)
        }
        val patterns = listOf(
            Regex("^(?:phone|call) lagao (.+)$"),
            Regex("^(?:phone )?(?:milao|mila do) (.+)$"),
            Regex("^(?:call|phone|dial|ring) (.+)$"),
            Regex("^(.+?) ko (?:call|phone|dial|ring)(?: karo| kar do| lagao| laga do| milao| mila do)?$"),
            Regex("^(.+?) se baat (?:karao|karwao)$"),
            Regex("^baat (?:karao|karwao) (.+?) se$"),
            Regex("^(.+?) (?:ko )?(?:phone )?(?:milao|mila do)$"),
            Regex("^(.+?) (?:call|phone|dial|ring) (?:karo|kar do|lagao|laga do|milao|mila do)$"),
            Regex("^(?:phone|call) lagao (.+?) ko$")
        )
        var target: String? = null
        for (p in patterns) {
            val m = p.matchEntire(s)
            if (m != null) {
                target = m.groupValues[1].trim()
                // The final pattern captures a name that may include the trailing "ko".
                if (p.pattern.startsWith("^(?:phone|call) lagao")) target = target.removeSuffix(" ko").trim()
                break
            }
        }
        if (target == null && s in setOf("call", "phone", "dial", "ring", "phone lagao", "call karo")) {
            return ContactRequest("", false, true)
        }
        var name = target?.trim().orEmpty()
        name = name.replace(Regex("^(?:please|nova|hey|call|phone|dial|ring)\\s+"), "")
            .replace(Regex("\\s+(?:please|karo|kar do|kardo|lagao|laga do|lagado|milao|mila do)$"), "")
            .trim()
        if (name in setOf("isko", "usko", "ise", "use", "this person", "that person", "this contact", "that contact", "him", "her", "them", "is bande ko", "us bande ko")) {
            return ContactRequest("", true, true)
        }
        if (name.isBlank() || name.length > 80 || name.split(" ").size > 8) return null
        val nonContactTargets = setOf(
            "settings", "setting", "phone settings", "wifi", "bluetooth", "battery", "charging", "charge",
            "volume", "brightness", "torch", "flashlight", "camera", "music", "song", "internet", "browser",
            "website", "app", "apps", "screen", "home", "back", "recent", "notifications", "timer", "alarm",
            "reminder", "time", "date", "open", "launch", "kholo", "khol", "dikhao", "dekho", "karo", "kar do",
            "lock", "unlock", "silent", "silence", "mute", "sound", "awaaz", "awaz", "ki", "poori", "full",
            "band", "kam", "badhao", "zyaada", "zyada", "up", "down", "off", "on", "airplane", "mode", "data",
            "hotspot", "dnd", "do not disturb", "location", "flashlight", "saver",
            "vibrate", "vibration", "ringer", "rotate", "rotation", "autorotate", "landscape", "portrait", "restart", "reboot", "shutdown", "power", "reset", "wipe", "format", "sleep"
        )
        if (name in nonContactTargets || name.split(" ").any { it in nonContactTargets }) return null
        return ContactRequest(name, false, true)
    }
}
