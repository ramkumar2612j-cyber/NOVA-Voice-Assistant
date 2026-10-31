package com.nova.assistant

/**
 * v7.147 STEP 7 OF 10: "KYA BAND HAI". One list of what is switched off on the phone and therefore stops NOVA from working: the Android
 * permissions, the special-access switches, battery restriction and the brand autostart screen. Pure Kotlin (no Android), unit tested.
 * It only reports. It never turns anything on (Android does not allow it): every row has a button that opens the exact Settings page and
 * the owner does the final tap. Autostart cannot be read by any app, so it is shown as "check" and never as ON or OFF.
 */
object BlockedRules {
    class Snap(
        val mic: Boolean, val notif: Boolean, val overlay: Boolean, val acc: Boolean, val notifAccess: Boolean, val battery: Boolean,
        val write: Boolean, val dnd: Boolean, val alarm: Boolean, val install: Boolean, val contacts: Boolean, val calendar: Boolean,
        val brandHasAutostart: Boolean
    )

    /** level: "must" (NOVA is crippled without it), "nice" (one feature needs it), "check" (Android cannot tell us). */
    class Item(val id: String, val hi: String, val en: String, val whyHi: String, val whyEn: String, val level: String)

    fun items(s: Snap): List<Item> {
        val out = ArrayList<Item>()
        if (!s.acc) out.add(Item("acc", "Accessibility", "Accessibility", "Doosre app me kaam, screen padhna aur tap iske bina band hain.", "Working in other apps, reading the screen and taps are off without it.", "must"))
        if (!s.battery) out.add(Item("battery", "Battery unrestricted", "Battery unrestricted", "Phone NOVA ko peeche se band kar sakta hai, tab Nova sunna ruk jaata hai.", "The phone may stop NOVA in the background, then it stops listening.", "must"))
        if (!s.mic) out.add(Item("mic", "Microphone", "Microphone", "Mic ke bina NOVA sun hi nahi sakta.", "NOVA cannot hear you without the microphone.", "must"))
        if (!s.overlay) out.add(Item("overlay", "Display over other apps", "Display over other apps", "Popup card aur laser scan dusre app ke upar nahi dikhte.", "The popup card and laser scan cannot show over other apps.", "must"))
        if (!s.notif) out.add(Item("notif", "Notifications", "Notifications", "NOVA ki chalu hone ki notification aur alert nahi dikhte.", "NOVA's running notification and alerts are hidden.", "must"))
        if (!s.notifAccess) out.add(Item("notifAccess", "Notification access", "Notification access", "Message padhna, driving mode aur auto-reply iske bina band hain.", "Reading messages, driving mode and auto-reply are off without it.", "nice"))
        if (!s.alarm) out.add(Item("alarm", "Alarms and reminders", "Alarms and reminders", "Alarm aur reminder theek samay par nahi bajte.", "Alarms and reminders may not ring on time.", "nice"))
        if (!s.write) out.add(Item("write", "Modify system settings", "Modify system settings", "Brightness aur screen rotation badalna band hai.", "Changing brightness and screen rotation is off.", "nice"))
        if (!s.dnd) out.add(Item("dnd", "Do Not Disturb access", "Do Not Disturb access", "Ringer silent karna band hai.", "Setting the ringer to silent is off.", "nice"))
        if (!s.install) out.add(Item("install", "Install unknown apps", "Install unknown apps", "NOVA ka naya version khud install nahi ho sakta.", "NOVA cannot install its own update.", "nice"))
        if (!s.contacts) out.add(Item("contacts", "Contacts", "Contacts", "Naam bolkar call ya message nahi ho sakta (Family numbers alag kaam karte hain).", "Calling or messaging by name is off (Family numbers still work).", "nice"))
        if (!s.calendar) out.add(Item("calendar", "Calendar", "Calendar", "Aaj ka schedule aur meeting yaad dilana band hai.", "Today's schedule and meeting reminders are off.", "nice"))
        if (s.brandHasAutostart) out.add(Item("autostart", "Autostart (phone company)", "Autostart (phone brand)", "Android ye nahi batata ki autostart ON hai ya nahi. Guide kholkar khud dekho, kuch phone NOVA ko band kar dete hain.", "Android does not tell apps whether autostart is on. Open the guide and check; some phones stop NOVA.", "check"))
        return out
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    fun json(list: List<Item>, en: Boolean): String =
        "{\"items\":[" + list.joinToString(",") {
            "{\"id\":\"" + esc(it.id) + "\",\"t\":\"" + esc(if (en) it.en else it.hi) + "\",\"why\":\"" + esc(if (en) it.whyEn else it.whyHi) + "\",\"level\":\"" + it.level + "\"}"
        } + "]}"

    /** One short spoken answer: honest when nothing is found, and says autostart is unknown. */
    fun reply(list: List<Item>, en: Boolean): String {
        val real = list.filter { it.level != "check" }
        val check = list.any { it.level == "check" }
        val tail = if (check) (if (en) " Autostart cannot be read by Android, so check it in the battery guide." else " Autostart Android nahi batata, battery guide me khud dekh lo.") else ""
        if (real.isEmpty()) return (if (en) "No permission or switch is off." else "Koi permission ya switch band nahi hai.") + tail
        val names = real.take(4).joinToString(", ") { if (en) it.en else it.hi }
        val must = real.count { it.level == "must" }
        val more = if (real.size > 4) (if (en) " and more." else " aur bhi.") else ""
        return if (en) "${real.size} things are off ($must important): $names$more. Open KYA BAND HAI on Home to fix them one by one.$tail"
        else "${real.size} cheez band hai ($must zaroori): $names$more. Home par KYA BAND HAI kholo, ek-ek karke theek karo.$tail"
    }
}
