package com.nova.assistant

/**
 * v7.12 AUTOPSY R3 (pure Kotlin, no Android classes): the visible result line for a video recording that ended or never started.
 * Before, auto-stops and failed starts only made the notification disappear. Texts never claim a save that did not happen.
 */
object VideoResultRules {
    class Text(val title: String, val body: String, val good: Boolean)

    fun forStatus(status: String): Text? = when {
        status == "saved" -> Text("Video save ho gayi", "Gallery > Movies > NOVA mein hai.", true)
        status == "saved_max" -> Text("Video save ho gayi (3 ghante poore)", "Limit par rukne se pehle tak ki video Gallery > Movies > NOVA mein hai.", true)
        status == "saved_space" -> Text("Video save ho gayi (jagah kam thi)", "Phone ki jagah kam hui to rok di. Jitni record hui wo Gallery > Movies > NOVA mein hai.", true)
        status == "no_frames" -> Text("Video record nahi hui", "Camera se koi frame nahi aaya, isliye kuch save nahi hua.", false)
        status == "stop_failed" -> Text("Video theek se band nahi hui", "File NOVA ke app folder mein rakhi hai, Gallery mein nahi gayi. Recordings screen se recover try karo.", false)
        status == "save_failed" -> Text("Video save nahi hui", "Gallery mein save nahi ho paayi.", false)
        status == "permission" -> Text("Video shuru nahi hui", "Camera ya mic ki permission nahi hai.", false)
        status == "not_visible" -> Text("Video shuru nahi hui", "Android 14 mein NOVA screen par khuli honi chahiye, tab hi camera chalta hai.", false)
        status.startsWith("failed:") -> Text("Video shuru nahi hui", "Camera ya mic nahi khula (" + status.removePrefix("failed:").take(60) + ").", false)
        else -> null
    }
}
