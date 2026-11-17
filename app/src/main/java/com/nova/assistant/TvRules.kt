package com.nova.assistant

/**
 * v7.219 OSCAR TV voice/text phrases — PURE rules (no Android). "tv <command>" / "oscar <command>" bole
 * to key ya deep-link event banta hai. Unit tested; asli TV par chalana BLOCKED BY HARDWARE (docs/OSCAR_TV.md).
 */
object TvRules {
    /** Known Android TV app ids (Google Play package names) — deep link "market://launch?id=<pkg>". */
    val APPS: Map<String, String> = linkedMapOf(
        "youtube" to "com.google.android.youtube.tv",
        "netflix" to "com.netflix.ninja",
        "prime" to "com.amazon.amazonvideo.livingroom",
        "prime video" to "com.amazon.amazonvideo.livingroom",
        "hotstar" to "in.startv.hotstar",
        "jiohotstar" to "in.startv.hotstar.drm",
        "disney" to "com.disney.disneyplus",
        "spotify" to "com.spotify.tv.android",
        "sony liv" to "com.sonyliv",
        "zee5" to "com.graymatrix.did",
        "mx player" to "com.mxtech.tv.mxplayer",
        "jio cinema" to "com.jiomedia.ondemand.tv",
        "jiocinema" to "com.jiomedia.ondemand.tv",
        "airtel xstream" to "com.airtel.xstream.tv",
        "play store" to "com.android.vending"
    )

    data class TvEvent(val keyCode: Int = 0, val deepLink: String = "", val n: Int = 1, val friendly: String = "")

    private fun key(k: Int, n: Int = 1, f: String) = TvEvent(keyCode = k, n = n, friendly = f)

    /** "<prefix> tv/oscar <baat>" -> event; null = TV baat nahi hai. "tv kholo" bhi home hai. */
    fun parse(raw: String?): TvEvent? {
        if (raw == null || raw.length > 200) return null
        var t = raw.trim().lowercase()
        t = t.removePrefix("jarvis ").trim()
        val m = Regex("^(?:oscar|tv|television)\\s+(.+)$").find(t) ?: return null
        var c = m.groupValues[1].trim()
        // "volume 3 bar badhao" YA "volume badhao 3 bar" — number kahin bhi ho sake
        var n = 1
        val nm = Regex("\\s(\\d)\\s*(?:bar|baar|times?)\\b").find(c)
        if (nm != null) {
            n = nm.groupValues[1].toInt().coerceIn(1, 10)
            c = c.replace(nm.value, " ").replace(Regex("\\s+"), " ").trim()
        }
        return command(c, n)
    }

    private fun command(c: String, n: Int): TvEvent? = when {
        c.matches(Regex("(kholo|khol do|on karo|chalao|chalu karo|jagao|wake)")) -> key(TvProto.KEY_WAKEUP, 1, "TV jaga diya")
        c.matches(Regex("(band karo|band kar do|band karo tv|off karo|so jao|sleep|binga do)")) -> key(TvProto.KEY_SLEEP, 1, "TV sula diya")
        c.matches(Regex("(ghuma|power|power dabao)")) -> key(TvProto.KEY_POWER, 1, "Power button")
        c.matches(Regex("home( jao| screen| kholo)?")) -> key(TvProto.KEY_HOME, 1, "Home")
        c.matches(Regex("back( jao| karo)?")) -> key(TvProto.KEY_BACK, 1, "Back")
        c.matches(Regex("(volume|awaaz|awaz)\\s*(badhao|badha do|up|tez)")) -> key(TvProto.KEY_VOLUME_UP, n, "Volume badhaya")
        c.matches(Regex("(volume|awaaz|awaz)\\s*(ghatao|kam karo|kam kar do|down|dheema)")) -> key(TvProto.KEY_VOLUME_DOWN, n, "Volume ghata diya")
        c.matches(Regex("(mute|chup)\\s*(karo|kar do|on)?")) -> key(TvProto.KEY_VOLUME_MUTE, 1, "Mute")
        c.matches(Regex("(upar|up)\\s*(jao|karo)?")) -> key(TvProto.KEY_DPAD_UP, n, "Upar")
        c.matches(Regex("(neeche|niche|down)\\s*(jao|karo)?")) -> key(TvProto.KEY_DPAD_DOWN, n, "Neeche")
        c.matches(Regex("(baaye|baye|left)\\s*(jao|karo)?")) -> key(TvProto.KEY_DPAD_LEFT, n, "Baaye")
        c.matches(Regex("(daaye|daye|right)\\s*(jao|karo)?")) -> key(TvProto.KEY_DPAD_RIGHT, n, "Daaye")
        c.matches(Regex("(ok|select|enter|dabao|center|centre)")) -> key(TvProto.KEY_DPAD_CENTER, 1, "OK")
        c.matches(Regex("(menu|settings)\\s*(kholo|karo|dikhao)?")) -> key(if (c.startsWith("menu")) TvProto.KEY_MENU else TvProto.KEY_HOME, 1, "Menu")
        c.matches(Regex("(play|chalao dobara|resume)")) -> key(TvProto.KEY_MEDIA_PLAY_PAUSE, 1, "Play")
        c.matches(Regex("(pause|rok do|ruko)")) -> key(TvProto.KEY_MEDIA_PLAY_PAUSE, 1, "Pause")
        c.matches(Regex("(agla|next|aage)\\s*(gaana|video|kar do)?")) -> key(TvProto.KEY_MEDIA_NEXT, 1, "Next")
        c.matches(Regex("(pichla|previous|peechhe)\\s*(gaana|video|kar do)?")) -> key(TvProto.KEY_MEDIA_PREVIOUS, 1, "Previous")
        c.matches(Regex("(search|khojo|dhundo)")) -> key(TvProto.KEY_SEARCH, 1, "Search")
        else -> appLaunch(c)
    }

    /** "<app> kholo/chalao" -> deep link event. */
    private fun appLaunch(c: String): TvEvent? {
        val m = Regex("^(.+?)\\s*(?:kholo|khol do|chalao|chalu karo|on karo|laga do)$").find(c) ?: return null
        val name = m.groupValues[1].trim()
        val pkg = APPS[name] ?: APPS.entries.firstOrNull { it.key.contains(name) || name.contains(it.key) }?.value ?: return null
        return TvEvent(deepLink = pkg, friendly = name.replaceFirstChar { it.uppercase() } + " TV par khol diya")
    }

    /** App id -> deep link URL (androidtvremote2 ka rule: scheme na ho to market://launch?id=). */
    fun deepLinkOf(app: String): String =
        if (app.contains("://")) app else "market://launch?id=" + app
}
