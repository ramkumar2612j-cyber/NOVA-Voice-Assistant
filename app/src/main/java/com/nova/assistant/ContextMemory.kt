package com.nova.assistant

/** Part 8: short-lived, RAM-only follow-up context. Risky or irreversible commands are never repeated by context. */
object ContextMemory {
    const val TTL_MS = 120_000L
    data class Last(val kind: String, val arg: String, val num: Int, val at: Long)
    private var last: Last? = null
    private val NEVER_REPEAT = setOf("call", "sms", "whatsapp", "delete", "remove", "send", "pay", "payment", "tap", "type", "agent", "calendar_event", "publish", "install", "record", "rec", "camera_record")

    @Synchronized fun remember(cmd: Logic.Cmd, now: Long) {
        if (cmd.kind in NEVER_REPEAT || cmd.kind.contains("delete") || cmd.kind.contains("send")) { last = null; return }
        last = Last(cmd.kind, cmd.arg.take(120), cmd.num, now)
    }

    @Synchronized fun clear() { last = null }
    @Synchronized fun snapshot(now: Long): Last? {
        val x = last ?: return null
        if (now < x.at || now - x.at > TTL_MS) { last = null; return null }
        return x
    }

    /** Return a safe follow-up Cmd, or null. Caller must route it through normal runCommand. */
    @Synchronized fun resolve(text: String, now: Long): Logic.Cmd? {
        val t = Logic.norm(text)
        val x = snapshot(now) ?: return null
        if (x.kind in NEVER_REPEAT) return null
        return when (t) {
            "wahi dobara", "phir se wahi", "same again", "do that again", "repeat that" -> Logic.Cmd(x.kind, x.arg, x.num)
            "thoda aur tez", "aur tez", "volume aur badhao", "a little louder", "make it louder" ->
                if (x.kind in setOf("volume", "media") && x.kind != "media") Logic.Cmd("volume", "up", 0) else null
            "aur kam", "thoda aur kam", "volume aur kam", "make it quieter" ->
                if (x.kind == "volume") Logic.Cmd("volume", "down", 0) else null
            "isko band karo", "ise band karo", "turn it off", "close that" -> when (x.kind) {
                "torch_on" -> Logic.Cmd("torch_off")
                "media" -> Logic.Cmd("media", "pause")
                "open_app" -> Logic.Cmd("global", "back")
                else -> null
            }
            else -> null
        }
    }
}
