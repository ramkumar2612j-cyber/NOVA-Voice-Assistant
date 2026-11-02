package com.nova.assistant

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * v6.53: "delete the message NOVA sent" (WhatsApp: Delete for everyone, Instagram: Unsend), done through the Accessibility service.
 * Best effort: it needs Accessibility ON and depends on the app's own screens, which the apps change often. It checks the result
 * on screen and reports honestly ("deleted" only if the message text is really gone). It only ever touches the ONE message
 * whose text is exactly the reply NOVA sent, and only inside an open conversation (a screen that has a message box),
 * so a chat-list row can never be long-pressed by mistake. Run on a worker thread (it waits for screens).
 */
object MessageDelete {
    private val MENU = setOf("delete", "delete message", "unsend", "हटाएं", "हटाएँ", "मिटाएं", "डिलीट")
    private val EVERYONE = setOf(
        "delete for everyone", "delete for all", "unsend", "remove for everyone",
        "सभी के लिए हटाएं", "सभी के लिए हटाएँ", "सबके लिए हटाएं", "सभी के लिए डिलीट करें"
    )

    fun norm(s: String): String = s.lowercase().replace(Regex("\\s+"), " ").trim()

    /** Pure: position of the NEWEST (last on screen) bubble whose text equals [text]; message boxes are never matched. -1 = none. */
    fun findBubble(nodes: List<AgentRules.Node>, text: String): Int {
        val want = norm(text)
        if (want.isEmpty()) return -1
        var found = -1
        for (n in nodes) if (!n.editable && norm(n.text) == want) found = n.i
        return found
    }

    /** Pure: item of the screen whose whole label is one of [labels]. First match. -1 = none. */
    fun findLabel(nodes: List<AgentRules.Node>, labels: Set<String>): Int {
        for (n in nodes) if (!n.editable && (norm(n.text) in labels || norm(n.desc) in labels)) return n.i
        return -1
    }

    /** Pure: an open conversation shows a message box. */
    fun inConversation(nodes: List<AgentRules.Node>): Boolean = nodes.any { it.editable }

    private fun snap(): Pair<String, List<AgentRules.Node>>? = NovaAccessibilityService.agentSnapshot()

    private fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (e: InterruptedException) { ErrLog.add("MessageDelete", e.toString()) } }

    private fun count(nodes: List<AgentRules.Node>, text: String): Int {
        val want = norm(text)
        return nodes.count { !it.editable && norm(it.text) == want }
    }

    /** "deleted", or a reason code: no_acc, no_text, no_app, no_screen, no_chat, no_message, no_menu, no_everyone, unverified. */
    fun run(ctx: Context, pkg: String, who: String, text: String): String {
        if (NovaAccessibilityService.instance == null) return "no_acc"
        if (text.isBlank()) return "no_text"
        val li = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return "no_app"
        try { ctx.startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { return "no_app" }
        sleep(1900)
        // 1. get inside the conversation with [who]
        var s = snap() ?: return "no_screen"
        if (!inConversation(s.second)) {
            val title = s.second.firstOrNull { !it.editable && norm(it.text) == norm(who) }?.i ?: return "no_chat"
            NovaAccessibilityService.agentTap(title, false)
            sleep(1800)
            s = snap() ?: return "no_screen"
            if (!inConversation(s.second)) return "no_chat"
        }
        // 2. long press the newest bubble with exactly our reply text
        val bubble = findBubble(s.second, text)
        if (bubble < 0) return "no_message"
        val before = count(s.second, text)
        if (!NovaAccessibilityService.agentTap(bubble, true).ok) return "no_message"
        sleep(1000)
        // 3. menu: Delete (WhatsApp) / Unsend (Instagram)
        s = snap() ?: return "no_screen"
        val menu = findLabel(s.second, MENU + EVERYONE)
        if (menu < 0) return "no_menu"
        NovaAccessibilityService.agentTap(menu, false)
        sleep(1000)
        // 4. confirm dialog: Delete for everyone / Unsend (never "Delete for me")
        s = snap() ?: return "no_screen"
        val every = findLabel(s.second, EVERYONE)
        if (every >= 0) {
            NovaAccessibilityService.agentTap(every, false)
            sleep(1400)
        } else if (inConversation(s.second) && count(s.second, text) < before) {
            // Instagram-style single step already removed it
        } else {
            return "no_everyone"
        }
        // 5. check on screen
        s = snap() ?: return "unverified"
        return if (count(s.second, text) < before) "deleted" else "unverified"
    }

    fun reasonText(code: String, hinglish: Boolean): String = when (code) {
        "deleted" -> if (hinglish) "Message delete ho gaya" else "Message deleted"
        "no_acc" -> if (hinglish) "Accessibility ON nahi hai" else "Accessibility is off"
        "no_text" -> if (hinglish) "Is entry me jawab ka text nahi hai" else "No reply text in this entry"
        "no_app" -> if (hinglish) "App khul nahi paya" else "Could not open the app"
        "no_screen" -> if (hinglish) "Screen padh nahi paya" else "Could not read the screen"
        "no_chat" -> if (hinglish) "Chat nahi mili (naam screen par nahi dikha)" else "Could not find the chat"
        "no_message" -> if (hinglish) "Chat me wo message nahi dikha (bahut upar chala gaya ho sakta hai)" else "The message is not visible in the chat"
        "no_menu" -> if (hinglish) "Delete ka option nahi dikha" else "No delete option appeared"
        "no_everyone" -> if (hinglish) "'Sabke liye delete' ka option nahi aaya (time nikal gaya ho sakta hai)" else "No delete-for-everyone option (time limit may be over)"
        else -> if (hinglish) "Pakka nahi pata delete hua ya nahi, chat khud dekh lo" else "Could not confirm the delete, please check the chat"
    }

    /** Deletes the history entry [ts]'s sent reply and writes the result back into the history. Worker thread. Returns the spoken result. */
    fun runForEntry(ctx: Context, ts: Long): String {
        val e: JSONObject = ActionLog.find(ctx, ts) ?: return reasonText("no_text", true)
        if (e.optString("st") != "sent" && e.optString("st") != "delete_failed") return reasonText("no_text", true)
        val code = try { run(ctx, e.optString("pk"), e.optString("who"), e.optString("d")) } catch (x: Exception) { "unverified" }
        ActionLog.setStatus(ctx, ts, if (code == "deleted") "deleted" else "delete_failed", reasonText(code, true))
        return reasonText(code, true)
    }
}
