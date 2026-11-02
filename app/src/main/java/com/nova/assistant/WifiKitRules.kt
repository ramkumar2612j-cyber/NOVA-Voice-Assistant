package com.nova.assistant

/**
 * v7.153 WIFI KIT: pure rules (NO Android imports, unit tested) for NOVA's LEGAL Wi-Fi tools:
 * - scan: list nearby networks with signal / band / security type (information only)
 * - best / switch: move the phone to the strongest of the OWNER'S OWN saved networks
 * - open connect: join a FREE OPEN network (no password) on request
 * - qr make / qr read: share MY OWN Wi-Fi with a QR, or join from the owner's own QR photo
 * - fix (doctor): explain why "internet nahi chal raha" and what to tap next
 * - password store grammar: the owner saves THEIR OWN network password (kept encrypted on the phone)
 * - guard (optional, default OFF): switch among the owner's own saved networks only
 *
 * HARD LEGAL LINE (checked by tools/test_wifi_kit_contract.py): nothing here bypasses, guesses or forces its way
 * into any network. A network is joined only when the owner supplies the password
 * (typed, spoken, or from their own QR) or the network is genuinely open. No attack tooling of any kind:
 * no password guessing, no lists of tries, no WPS tricks - those would be crimes, and they do not work anyway.
 */
object WifiKitRules {
    const val K_SCAN = "wifi_scan"
    const val K_BEST = "wifi_best"
    const val K_SWITCH = "wifi_switch"
    const val K_OPEN = "wifi_open"
    const val K_QR_MAKE = "wifi_qr_make"
    const val K_QR_READ = "wifi_qr_read"
    const val K_FIX = "wifi_fix"
    const val K_SAVE_PASS = "wifi_save_pass"
    const val K_STATUS = "wifi_status"
    const val K_GUARD_ON = "wifi_guard_on"
    const val K_GUARD_OFF = "wifi_guard_off"
    const val K_FORGET = "wifi_forget"

    val KINDS = setOf(K_SCAN, K_BEST, K_SWITCH, K_OPEN, K_QR_MAKE, K_QR_READ, K_FIX, K_SAVE_PASS,
        K_STATUS, K_GUARD_ON, K_GUARD_OFF, K_FORGET)

    /** Separator inside Cmd.arg for wifi_save_pass: "ssid\u0001pass" (a password may contain almost any other character). */
    const val SEP = "\u0001"

    // ---------------------------------------------------------------- scan data + classification

    data class Net(val ssid: String, val level: Int, val freqMhz: Int, val caps: String)

    /** Security type from a ScanResult.capabilities string like "[WPA2-PSK-CCMP][ESS][SAE]". */
    fun security(caps: String): String = when {
        caps.contains("SAE") -> "WPA3"
        caps.contains("WPA3") -> "WPA3"
        caps.contains("WPA2") -> "WPA2"
        caps.contains("WEP") -> "WEP"
        caps.contains("WPA") -> "WPA"
        caps.contains("OWE") -> "open (enhanced)"
        else -> "open"
    }

    fun isOpen(caps: String): Boolean {
        val s = security(caps)
        return s == "open" || s == "open (enhanced)"
    }

    /** Wi-Fi band from the scan frequency. "" when unknown. */
    fun band(freqMhz: Int): String = when {
        freqMhz in 2412..2484 -> "2.4 GHz"
        freqMhz in 4900..5899 -> "5 GHz"
        freqMhz in 5935..7115 -> "6 GHz"
        else -> ""
    }

    /** 0..4 signal bars from RSSI (same rough edges Android uses). */
    fun bars(level: Int): Int = when {
        level >= -55 -> 4
        level >= -66 -> 3
        level >= -77 -> 2
        level >= -88 -> 1
        else -> 0
    }

    private fun word(b: Int, en: Boolean): String = when {
        b >= 4 -> if (en) "very strong" else "bahut strong"
        b == 3 -> if (en) "strong" else "strong"
        b == 2 -> if (en) "okay" else "theek-theek"
        b == 1 -> if (en) "weak" else "kamzor"
        else -> if (en) "very weak" else "bahut kamzor"
    }

    /** One scan line: "1. JioFiber - signal 3/4, 2.4 GHz, WPA2" (open networks are marked free). */
    fun netLine(i: Int, n: Net, en: Boolean): String {
        val sec = security(n.caps)
        val secTxt = if (sec.startsWith("open")) (if (en) "FREE" else "FREE") else sec
        val bd = band(n.freqMhz)
        val parts = ArrayList<String>()
        parts.add((if (en) "signal " else "signal ") + bars(n.level) + "/4 (" + word(bars(n.level), en) + ")")
        if (bd.isNotEmpty()) parts.add(bd)
        parts.add(secTxt)
        return (i + 1).toString() + ". " + n.ssid + " - " + parts.joinToString(", ")
    }

    /** Scan reply: dedup by SSID (strongest kept), strongest first, at most [max] rows. */
    fun scanReply(nets: List<Net>, en: Boolean, max: Int = 8): String {
        val clean = nets.filter { it.ssid.isNotBlank() }
            .groupBy { it.ssid }
            .map { v -> v.value.maxByOrNull { it.level }!! }
            .sortedByDescending { it.level }
        if (clean.isEmpty()) {
            return if (en) "I found no nearby Wi-Fi networks. Is Location permission on for NOVA? Say: nova wifi scan karo, to try again."
            else "Paas koi WiFi network nahi mila. NOVA ko Location permission ON hai? Phir bolo: nova wifi scan karo."
        }
        val head = if (en) "Nearby Wi-Fi (" + clean.size + "), strongest first:"
        else "Paas ke WiFi (" + clean.size + "), strong pehle:"
        return head + "\n" + clean.take(max).mapIndexed { i, n -> netLine(i, n, en) }.joinToString("\n")
    }

    /** Strongest genuinely-open network, or null. */
    fun strongestOpen(nets: List<Net>): Net? = nets.filter { isOpen(it.caps) && it.ssid.isNotBlank() }.maxByOrNull { it.level }

    // ---------------------------------------------------------------- best switch (owner's own networks only)

    data class SwitchPlan(val ssid: String, val level: Int, val gainDb: Int)

    /**
     * Among [candidates] (the scanned networks that belong to the owner's own saved list) pick the strongest
     * that beats the current one by [minGainDb]. currentLevel = [DISCONNECTED] means "not connected to any Wi-Fi"
     * (any candidate is then better). Never returns the current SSID.
     */
    fun bestSwitch(currentSsid: String, currentLevel: Int, candidates: List<Net>, minGainDb: Int = 8): SwitchPlan? {
        val pool = candidates.filter { it.ssid.isNotBlank() && !it.ssid.equals(currentSsid, true) }
        val best = pool.maxByOrNull { it.level } ?: return null
        val gain = best.level - currentLevel
        return if (currentLevel <= DISCONNECTED || gain >= minGainDb) SwitchPlan(best.ssid, best.level, gain) else null
    }

    /** Not-connected sentinel for currentLevel (an RSSI is always >= -100, so -1000 is safe). */
    const val DISCONNECTED = -1000

    /** Optional guard decision: same as [bestSwitch] plus a cooldown so it can never flap back and forth. */
    fun guardDecide(currentSsid: String, currentLevel: Int, candidates: List<Net>,
                    lastSwitchAt: Long, now: Long, cooldownMs: Long = 10L * 60 * 1000, minGainDb: Int = 8): SwitchPlan? {
        if (now - lastSwitchAt < cooldownMs) return null
        return bestSwitch(currentSsid, currentLevel, candidates, minGainDb)
    }

    // ---------------------------------------------------------------- Wi-Fi QR (share MY OWN network)

    /** Escapes the Wi-Fi QR special characters: \ ; , : " */
    fun escapeQr(s: String): String = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace(":", "\\:").replace("\"", "\\\"")

    fun unescapeQr(s: String): String {
        val b = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) { b.append(s[i + 1]); i += 2 } else { b.append(ch); i += 1 }
        }
        return b.toString()
    }

    /** Standard Wi-Fi QR payload: WIFI:T:WPA;S:ssid;P:pass;; (T:nopass for an open network). */
    fun qrPayload(ssid: String, pass: String): String =
        if (pass.isBlank()) "WIFI:T:nopass;S:" + escapeQr(ssid) + ";;"
        else "WIFI:T:WPA;S:" + escapeQr(ssid) + ";P:" + escapeQr(pass) + ";;"

    data class QrWifi(val ssid: String, val pass: String, val open: Boolean)

    /** Parses a Wi-Fi QR payload ("WIFI:T:...;S:...;P:...;;"). null when this is not a Wi-Fi QR. */
    fun parseQr(text: String): QrWifi? {
        val t = text.trim()
        if (!t.startsWith("WIFI:", true)) return null
        val fields = splitQrFields(t.substring(5))
        var type = ""
        var ssid = ""
        var pass = ""
        for (f in fields) {
            val idx = f.indexOf(':')
            if (idx <= 0) continue
            val k = f.substring(0, idx).uppercase()
            val v = unescapeQr(f.substring(idx + 1))
            when (k) {
                "T" -> type = v
                "S" -> ssid = v
                "P" -> pass = v
            }
        }
        if (ssid.isBlank()) return null
        return QrWifi(ssid, pass, type.equals("nopass", true))
    }

    /** Splits on ';' honouring backslash escapes (the trailing ";;" yields empty fields that are dropped). */
    private fun splitQrFields(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) { cur.append(ch).append(s[i + 1]); i += 2; continue }
            if (ch == ';') { out.add(cur.toString()); cur.setLength(0); i += 1; continue }
            cur.append(ch); i += 1
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    // ---------------------------------------------------------------- doctor (why is the internet not working)

    data class DoctorState(
        val wifiOn: Boolean,
        val ssid: String,
        val level: Int,
        val hasNetwork: Boolean,   // an underlying network exists (Wi-Fi is really passing traffic)
        val validated: Boolean,    // Android marked the network INTERNET-validated
        val portal: Boolean?       // null = unknown / not probed; true = login page detected; false = 204 OK
    )

    /** Honest diagnosis lines (max two). Every step is something the OWNER taps; NOVA never claims to have fixed the router. */
    fun doctorReply(s: DoctorState, en: Boolean): String {
        fun t(hi: String, e: String) = if (en) e else hi
        if (!s.wifiOn) return t("WiFi band hai. Quick settings se WiFi ON karo - Android kisi app ko seedha on karne nahi deta.",
            "Wi-Fi is off. Turn Wi-Fi on from quick settings - Android does not let an app switch it on directly.")
        if (s.ssid.isBlank()) return t("WiFi on hai par kisi network se juda nahi hai. Bolo: nova wifi scan karo, apna network chuno aur password daalo.",
            "Wi-Fi is on but no network is joined. Say: nova wifi scan karo, pick your network and enter its password.")
        if (!s.hasNetwork) return t(s.ssid + " se juda hoon par usme internet nahi aa raha. Router ko 30 second ke liye band karke on karo, ya WiFi ruk karke dobara try karo.",
            "Joined " + s.ssid + " but it has no internet. Power-cycle the router for 30 seconds, or toggle Wi-Fi off and on and try again.")
        if (s.portal == true) return t("Is WiFi me login (captive portal) baaki hai. Koi browser kholo - login page khud khul jayega, wahan aage karo.",
            "This Wi-Fi still needs its login (captive portal). Open any browser - the login page opens by itself; continue there.")
        if (!s.validated) return t(s.ssid + " juda hai par internet verify nahi hua. Airplane mode 10 second on-off karo, ya router restart karo. Data chalu ho to mobile data pe kaam chalao.",
            "Joined " + s.ssid + " but the internet is not verified. Toggle airplane mode for 10 seconds or restart the router. If mobile data is on, use it meanwhile.")
        if (s.level <= -75) return t("Internet chal raha hai, par signal kamzor hai (" + bars(s.level) + "/4). Router ke paas jao ya 5 GHz network try karo.",
            "The internet works, but the signal is weak (" + bars(s.level) + "/4). Move closer to the router or try the 5 GHz network.")
        return t("Sab theek: " + s.ssid + ", signal " + bars(s.level) + "/4, internet chal raha hai.",
            "All good: " + s.ssid + ", signal " + bars(s.level) + "/4, the internet is working.")
    }

    // ---------------------------------------------------------------- validation

    fun validSsid(ssid: String): Boolean {
        val s = ssid.trim()
        if (s.isEmpty() || s.length > 32) return false
        if (s.contains(SEP) || s.contains('\n') || s.contains('\r')) return false
        return s.any { !it.isWhitespace() }
    }

    /** WPA passphrases are 8..63 characters; shorter/longer is refused with an honest reply (open networks pass ""). */
    fun validPass(pass: String): Boolean = pass.length in 8..63

    fun validSsidPass(ssid: String, pass: String): Boolean = validSsid(ssid) && (pass.isEmpty() || validPass(pass))

    /** SHA-256 hex, used only as a local file/pref key so the SSID name itself never sits in a pref key. */
    fun sha256(s: String): String = try {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        d.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { Integer.toHexString(s.hashCode()) }

    // ---------------------------------------------------------------- command parsing

    private val STOP_SSID_WORDS = setOf("pe", "par", "jao", "karo", "kar", "do", "dijiye", "kijiye", "le", "chalo",
        "network", "wale", "wala", "wifi", "switch", "change", "badlo", "connect", "ka", "ki", "ke", "hai", "hoga",
        "mera", "meri", "save", "rakho", "rakh", "password", "pass", "qr")
    private val QUESTION_WORDS = setOf("kya", "hai", "kaun", "kaunsa", "kaunsi", "konsa", "batao", "batado", "bata", "yaad", "kyun", "kyu", "kitna", "kitni")

    private fun cleanSsid(raw: String): String =
        raw.split(" ").filter { it.isNotBlank() && it !in STOP_SSID_WORDS }.joinToString(" ").trim()

    private fun cleanOriginal(text: String): String {
        var s = text.trim()
        s = s.replace(Regex("^[\\s]*(?:hey |ok |okay |okii )*(?:nova|noova|nuova)[\\s,:-]+", RegexOption.IGNORE_CASE), "")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Wi-Fi Kit commands. Returns null for everything else, INCLUDING plain "wifi on/off karo",
     * "wifi settings kholo" and negated orders - the older routes keep handling those exactly as before.
     */
    fun parse(text: String): Logic.Cmd? {
        if (text.isBlank() || text.length > 160) return null
        val l = Logic.norm(text)
        if (l.isEmpty()) return null
        val t = l.split(" ")
        fun has(vararg w: String): Boolean = w.any { it in t }
        val wifi = has("wifi")
        val negated = t.any { it in setOf("mat", "nahi", "nahin", "nhi", "nai", "mt") }

        // ---- doctor first: "wifi nahi chal raha" is negative on purpose
        val fixWords = has("theek", "thik", "problem", "masla", "slow", "kamzor", "dikkat") ||
            (has("wifi", "internet", "net", "data") && (has("kyun", "kyu", "kion", "nahi", "nahin", "nhi", "kaam") || l.contains("chal nahi"))) ||
            (l.contains("internet nahi") || l.contains("net nahi") || l.contains("internet kyun") || l.contains("net kyun") ||
                l.contains("internet slow") || l.contains("net slow") || l.contains("wifi slow") || l.contains("wifi theek"))
        if (fixWords && (wifi || has("internet", "net"))) return Logic.Cmd(K_FIX)

        if (!wifi) return null
        if (negated) return null
        if (has("settings", "setting")) return null

        // plain on/off stays with the old Android-settings route ("wifi kholo" also keeps opening the Wi-Fi settings page)
        if (t.all { it in setOf("wifi", "on", "off", "karo", "kar", "do", "chalu", "band", "bandh", "jala", "jalao", "kholo") }) return null

        // ---- guard (before switch: "wifi auto switch on karo")
        if (has("guard") || (has("auto") && has("switch"))) {
            if (has("on", "chalu", "start", "enable")) return Logic.Cmd(K_GUARD_ON)
            if (has("off", "band", "stop", "disable")) return Logic.Cmd(K_GUARD_OFF)
        }

        // ---- password store + forget (use the ORIGINAL text: passwords and SSIDs are case sensitive)
        val o = cleanOriginal(text)
        val ol = o.lowercase()
        if (ol.startsWith("wifi password")) {
            val rest = o.substring(13).trim()
            if (rest.isEmpty()) return null
            val lower = rest.lowercase()
            // "wifi password jiofiber bhool jao" - the network name comes FIRST, the forget phrase LAST
            val forgetM = Regex("^(.*?)(?:bhool jao|bhul jao|bhool ja|bhul ja|hatao|hata do|delete karo|forget)(?: karo| do| de do)?\\s*$").find(rest.trim())
            if (forgetM != null) {
                val ssid = cleanSsid(forgetM.groupValues[1])
                return if (validSsid(ssid)) Logic.Cmd(K_FORGET, ssid) else null
            }
            val m = Regex("^save (?:karo |kar do |kardo )?(.+)$", RegexOption.IGNORE_CASE).find(rest)
                ?: Regex("^(?:rakho |rakh do |rakhdo )?(.+)$", RegexOption.IGNORE_CASE).find(rest)
            val body = (m?.groupValues?.get(1) ?: rest).trim()
            val toks = body.split(" ").filter { it.isNotBlank() }
            if (toks.size >= 2 && toks.none { it.lowercase() in QUESTION_WORDS }) {
                val pass = toks.last()
                val ssid = cleanSsid(toks.dropLast(1).joinToString(" "))
                if (validSsid(ssid) && pass.length in 1..64) {
                    return Logic.Cmd(K_SAVE_PASS, ssid + SEP + pass)
                }
            }
            return null
        }
        Regex("^(.+?) ka wifi password (?:hai |hoga )?(.+)$", RegexOption.IGNORE_CASE).find(o)?.let {
            val ssid = cleanSsid(it.groupValues[1]); val pass = it.groupValues[2].trim()
            val asks = (ssid.split(" ") + pass.split(" ")).any { w -> w.lowercase() in QUESTION_WORDS }
            if (!asks && validSsid(ssid) && pass.length in 1..64) {
                return Logic.Cmd(K_SAVE_PASS, ssid + SEP + pass)
            }
        }

        // ---- forget, second phrasing
        Regex("^wifi (?:bhool jao|bhul jao|forget) (.+)$", RegexOption.IGNORE_CASE).find(ol)?.let {
            val ssid = cleanSsid(it.groupValues[1])
            if (validSsid(ssid)) return Logic.Cmd(K_FORGET, ssid)
        }

        // ---- QR
        if (has("qr", "q r", "code")) {
            if (has("banao", "banado", "banane", "share", "dikha", "dikhao", "generate", "bana")) return Logic.Cmd(K_QR_MAKE, qrMakeArg(o))
            if (has("scan", "kholo", "padho", "dekho", "read", "laga", "se connect")) return Logic.Cmd(K_QR_READ)
        }
        if (has("share") && has("wifi") && has("karo", "kar", "do")) return Logic.Cmd(K_QR_MAKE, qrMakeArg(o))

        // ---- status ("kaunsa wifi laga hai")
        if (l.contains("kaunsa wifi") || l.contains("kaun sa wifi") || l.contains("wifi kaunsa") || l.contains("wifi kaun sa") ||
            l.contains("konsa wifi") || l.contains("kon sa wifi") || l.contains("wifi ka haal") || l.contains("wifi kahan") ||
            l.contains("wifi kaha laga") || l.contains("wifi kis") ) return Logic.Cmd(K_STATUS)

        // ---- best
        if (has("best", "sabse", "achha", "accha", "strong") && has("wifi", "network")) return Logic.Cmd(K_BEST)
        if (l.contains("best wifi") || l.contains("wifi best")) return Logic.Cmd(K_BEST)

        // ---- open / free network (BEFORE the switch rules: "free wifi pe connect karo" is never a switch to "free")
        if ((has("open", "free", "khula", "khuli") && has("wifi", "network")) &&
            (has("connect", "jodo", "lagao", "dhundo", "dhoondo", "dhund", "mila", "join"))) {
            return Logic.Cmd(K_OPEN)
        }

        // ---- switch to a named network
        Regex("^wifi (?:switch|change|badal(?:o|do)?|badl(?:o|do)?) (?:karo |kar do |kardo )?(.+)$").find(l)?.let {
            val ssid = cleanSsid(it.groupValues[1])
            if (validSsid(ssid)) return Logic.Cmd(K_SWITCH, ssid)
        }
        Regex("^(.+?) pe switch karo$").find(l)?.let {
            if (t.contains("wifi")) { val ssid = cleanSsid(it.groupValues[1]); if (validSsid(ssid)) return Logic.Cmd(K_SWITCH, ssid) }
        }
        Regex("^(.+?) wifi pe (?:jao|le chalo|le jao|connect karo)$").find(l)?.let {
            val ssid = cleanSsid(it.groupValues[1])
            if (validSsid(ssid)) return Logic.Cmd(K_SWITCH, ssid)
        }

        // ---- scan
        if (has("scan", "kaunse", "kaun", "list", "networks", "network", "dikhao", "nearby", "dhundo", "dhoondo") ||
            (has("paas", "pass") && has("ke"))) {
            return Logic.Cmd(K_SCAN)
        }

        return null
    }

    /** "wifi qr banao" -> "" (current network); "JioFiber ka qr banao" -> "JioFiber". */
    private fun qrMakeArg(original: String): String {
        val m = Regex("^(.+?) (?:ka|ki) qr", RegexOption.IGNORE_CASE).find(original) ?: return ""
        val ssid = cleanSsid(m.groupValues[1].replace(Regex("^wifi", RegexOption.IGNORE_CASE), "").trim())
        return if (validSsid(ssid)) ssid else ""
    }
}
