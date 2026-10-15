package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Part 5: spoken STATUS questions for the Part 2/3 features: "auto reply ka status batao", "lock guard on hai ya off", "api limit kitni bachi".
 * READ-ONLY on purpose: it only tells the saved state. It never turns anything on or off, never calls, never sends, never skips a confirmation.
 * To switch a feature the owner still uses Settings. An order ("... on karo", "... band karo") is NOT matched here, it goes on to the normal routes.
 * [key] and [reply] are pure Kotlin (unit tested); [read] only reads saved state from the phone.
 */
object FeatureVoice {
    class State(val on: Boolean, val note: String = "")

    private val FEATURES = listOf(
        "autoreply" to listOf("auto reply", "autoreply"),
        "routines" to listOf("routine", "routines"),
        "lockguard" to listOf("lock guard", "lockguard"),
        "batteryalert" to listOf("battery alert", "battery warning", "battery kam alert"),
        "offlinepack" to listOf("offline pack", "offline ai pack", "offline ai"),
        "apilimits" to listOf("api limit", "api limits", "limit kitni", "api use")
    )
    private val STATUS_CUE = listOf("status", "kya hai", "on hai", "off hai", "chalu hai", "band hai", "chal raha", "chal rha", "check", "jaanch",
        "batao", "bata do", "bataiye", "is it on", "is it off", "kitni bachi", "kitna use", "how much", "tell me")
    private val ORDER_WORDS = listOf("karo", "kar do", "kardo", "karna", "enable", "disable", "turn", "set ", "shuru", "start", "stop", "ruko", "delete", "hatao",
        "bhejo", "send", "call", "banao", "create", "add", "remove", "on kar", "off kar", "band kar", "chalu kar")

    /** Which feature is asked about, or null. Needs the feature name AND a status cue AND no order word. */
    fun key(text: String): String? {
        val n = Logic.norm(text)
        if (n.isBlank() || n.length > 90) return null
        if (ORDER_WORDS.any { (" $n ").contains(" $it") || n.contains(it) && it.length > 5 }) return null
        if (STATUS_CUE.none { n.contains(it) }) return null
        for ((k, names) in FEATURES) if (names.any { n.contains(it) }) return k
        return null
    }

    fun reply(key: String, s: State, en: Boolean): String {
        fun t(hi: String, e: String) = if (en) e else hi
        return when (key) {
            "autoreply" -> if (s.on) t("Auto-reply ON hai. Badalna ho to Settings mein jao.", "Auto-reply is ON. Change it in Settings.")
                           else t("Auto-reply OFF hai. Chalu karna ho to Settings mein jao.", "Auto-reply is OFF. Turn it on in Settings.")
            "lockguard" -> if (s.on) t("Lock Guard ON hai. ${s.note}".trim(), "Lock Guard is ON. ${s.note}".trim())
                           else t("Lock Guard OFF hai. Settings mein ON kar sakte ho.", "Lock Guard is OFF. You can turn it on in Settings.")
            "batteryalert" -> if (s.on) t("Battery kam alert ON hai.", "Low-battery alert is ON.") else t("Battery kam alert OFF hai. Settings mein ON kar sakte ho.", "Low-battery alert is OFF. You can turn it on in Settings.")
            "offlinepack" -> if (s.on) t("Offline AI pack phone mein installed hai.", "The offline AI pack is installed on this phone.")
                             else t("Offline AI pack install nahi hai. Settings mein Offline pack se install kar sakte ho.", "The offline AI pack is not installed. Install it from Settings > Offline pack.")
            "routines" -> if (s.on) t("Routines bani hain: ${s.note}. Naya banane ya hatane ke liye Settings mein jao.", "Routines saved: ${s.note}. Add or remove them in Settings.")
                          else t("Abhi koi routine nahi hai. Settings mein bana sakte ho.", "No routines yet. You can make one in Settings.")
            "apilimits" -> if (s.on) t("Kuch AI limit ke paas hai: ${s.note}. LIMITS screen dekho.", "Some AI limits are close: ${s.note}. See the LIMITS screen.")
                           else t("Koi AI limit ke paas nahi hai. Poori list LIMITS screen par hai.", "No AI is near its limit. The full list is on the LIMITS screen.")
            else -> ""
        }
    }

    /** Reads the saved state. Never changes anything. */
    fun read(ctx: Context, key: String): State = try {
        when (key) {
            "autoreply" -> State(ActionLog.autoReplyOn(ctx))
            "lockguard" -> State(ExtCfg.lockGuard(ctx), if (DrivingBridge.listenerEnabled(ctx)) "" else "Par Notification access OFF hai, isliye alert nahi aayega.")
            "batteryalert" -> State(ExtCfg.alerts(ctx))
            "offlinepack" -> State(OfflinePackStore.any(ctx))
            "routines" -> {
                val arr = JSONArray(BrainStore.get(ctx).skillsJson())
                val names = ArrayList<String>()
                for (i in 0 until minOf(arr.length(), 5)) arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { names.add(it) }
                State(arr.length() > 0, names.joinToString(", ") + if (arr.length() > 5) " aur " + (arr.length() - 5) + " aur" else "")
            }
            "apilimits" -> {
                val rows = JSONObject(ApiUsage.snapshotJson(ctx)).optJSONArray("rows")
                val near = ArrayList<String>()
                if (rows != null) for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    if (r.optInt("percent", -1) >= 90) near.add(r.optString("label"))
                }
                State(near.isNotEmpty(), near.joinToString(", "))
            }
            else -> State(false)
        }
    } catch (e: Throwable) { State(false) }
}
