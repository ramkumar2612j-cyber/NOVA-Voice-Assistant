package com.nova.assistant

/**
 * v6.91 EASY HELP: NOVA knows what it can do and tells ordinary (non-technical) users in plain Hinglish / English.
 * Pure Kotlin (no Android classes), unit tested. Used by (a) NovaService.handle: "tum kya kar sakte ho" / "can you make a website"
 * is answered locally, offline, without any AI, for both typed chat and voice; (b) the cloud AI system prompt (aiFacts), so the AI
 * never invents abilities. The same topics (ids) are shown in the MADAD popup in ui.html (tools/test_help_contract.py keeps them equal).
 * One topic per line, keep the id first: the contract test reads this file.
 */
object HelpGuide {
    class Topic(
        val id: String, val hi: String, val en: String, val keys: List<String>, val example: String,
        val descHi: String, val descEn: String, val needHi: String, val needEn: String
    )

    val TOPICS: List<Topic> = listOf(
        Topic("call", "Call aur SMS", "Calls and SMS", listOf("call", "dial", "sms", "kall"), "nova mummy ko call karo", "Naam bolo, main call ya SMS taiyaar kar deta hoon. Call se pehle aapse haan poochta hoon.", "Say a name and I prepare the call or SMS. I ask you to confirm before calling.", "Contacts permission chahiye.", "Needs the Contacts permission."),
        Topic("whatsapp", "WhatsApp message", "WhatsApp messages", listOf("whatsapp", "whatsap", "watsapp"), "nova Rahul ko WhatsApp karo kal milte hain", "WhatsApp message bhej sakta hoon. Bhejne se pehle aapse haan poochta hoon.", "I can send a WhatsApp message. I ask you to confirm before sending.", "Accessibility permission chahiye.", "Needs the Accessibility permission."),
        Topic("alarm", "Alarm, timer, reminder", "Alarm, timer, reminder", listOf("alarm", "timer", "reminder", "yaad dila"), "nova subah 6 baje ka alarm lagao", "Alarm, timer aur reminder lagata hoon, bina internet ke.", "I set alarms, timers and reminders, even without internet.", "", ""),
        Topic("phone", "Torch, volume, wifi", "Torch, volume, wifi", listOf("torch", "flashlight", "volume", "wifi", "bluetooth", "brightness"), "nova torch jala do", "Torch, volume, wifi, bluetooth jaise phone ke kaam, bina internet ke.", "Phone basics like torch, volume, wifi and bluetooth, without internet.", "", ""),
        Topic("apps", "App kholna", "Open apps", listOf("kholo", "kholna", "open"), "nova YouTube kholo", "Koi bhi app ya phone ki setting khol deta hoon.", "I open any app or phone setting.", "", ""),
        Topic("music", "Gaana aur raasta", "Music and directions", listOf("gana", "gaana", "gaane", "song", "music", "youtube", "navigate", "map", "route", "raasta"), "nova Delhi ka raasta dikhao", "YouTube par gaana ya video dhundhta hoon aur Google Maps mein jagah ya raasta dikhata hoon.", "I search a song or video on YouTube and show places or routes in Google Maps.", "Internet chahiye.", "Needs internet."),
        Topic("camera", "Camera kholna", "Open camera", listOf("camera", "selfie"), "nova selfie camera kholo", "Camera khol deta hoon (photo ya video). Photo ka button dabana mere haath mein nahi, wo aap dabao.", "I open the camera (photo or video). I cannot press the shutter button, you do that.", "", ""),
        Topic("image", "Photo banana (AI)", "Make a picture (AI)", listOf("photo", "image", "picture", "tasveer", "pic"), "nova ek billi ki photo banao", "AI se nayi photo banata hoon.", "I make a new picture with AI.", "Gemini key ya free image option chahiye.", "Needs a Gemini key or the free image option."),
        Topic("website", "Website banana", "Build a website", listOf("website", "site", "webpage"), "nova ek restaurant ki website banao", "Ek line bolo, poori website ban jaati hai. Baad mein bolo isko professional banao. Chaho to GitHub par publish bhi.", "Say one line and a full website is built. Later say make it more professional. It can also be published to GitHub.", "AI key chahiye, publish ke liye GitHub token.", "Needs an AI key, and a GitHub token to publish."),
        Topic("screen", "Screen padhna aur doosre app mein kaam", "Read the screen and work in other apps", listOf("screen", "agent"), "nova screen padh kar batao", "Screen padh kar samjha sakta hoon aur doosre app mein chhote kaam kar sakta hoon. Bank, payment aur password screen par kabhi nahi.", "I can read and explain the screen and do small jobs in other apps. Never on bank, payment or password screens.", "Accessibility permission chahiye.", "Needs the Accessibility permission."),
        Topic("memory", "Yaad rakhna", "Remember things", listOf("yaad rakh", "remember", "memory"), "nova yaad rakh mera naam Shiv hai", "Aapki baatein sirf is phone mein yaad rakhta hoon, kahin upload nahi hoti.", "I remember what you tell me, only on this phone, nothing is uploaded.", "", ""),
        Topic("learn", "Naya topic seekhna", "Learn a new topic", listOf("seekh", "learn", "wikipedia"), "nova solar panel ke baare mein seekho", "Naya topic Wikipedia se seekh leta hoon.", "I learn a new topic from Wikipedia.", "Internet chahiye.", "Needs internet."),
        Topic("driving", "Driving mode", "Driving mode", listOf("driving", "gaadi"), "nova driving mode on", "Gaadi chalate waqt WhatsApp aur SMS bol kar sunata hoon, aur aap bolo to reply bhej deta hoon.", "While you drive I read WhatsApp and SMS aloud and send your spoken reply.", "Notification access chahiye.", "Needs Notification access."),
        Topic("recording", "Recording", "Recording", listOf("recording", "record"), "nova background me recording on karo", "Sirf aapke kehne par recording chalata hoon. Status Settings mein dikhta hai.", "I record only when you ask. The status shows in Settings.", "", ""),
        Topic("live", "Mausam, khabar, rate, matlab", "Weather, news, rates, meanings", listOf("browser", "live jankari", "live info"), "nova jaipur ka mausam batao", "Mausam, taaza khabar, dollar ya bitcoin ka rate, shabd ka matlab aur kisi cheez ki jankari internet se turant laata hoon, bina kisi key ke. Chhote sawal phone ka offline AI bhi sambhalta hai.", "I fetch the weather, latest news, dollar or bitcoin rates, word meanings and facts from the internet quickly, with no key. Short questions are also handled by the phone's offline AI.", "Internet chahiye.", "Needs internet."),
        Topic("questions", "Sawal ka jawab", "Answers to questions", listOf("sawal", "question", "jawab", "answer", "pooch"), "nova solar panel kaise kaam karta hai", "Aam sawalon ka jawab deta hoon, hindi, hinglish ya english mein. Live news ya mausam ke liye internet chahiye.", "I answer everyday questions in Hindi, Hinglish or English. Live news or weather needs internet.", "AI key se behtar jawab milta hai.", "An AI key gives better answers.")
    )

    private val GENERAL = listOf(
        "kya kar sakte", "kya kar sakta", "kya kar sakti", "kya kya kar", "kya kuch kar", "kya kya karte",
        "what can you do", "what all can you do", "what do you do", "what are your features", "your features",
        "tumhare features", "tumhari features", "aapke features", "apke features", "feature batao", "features batao",
        "kaise use", "kaise chalate", "kaise chalau", "kaise chalana", "how to use", "how do i use", "nova help", "help menu", "need help"
    )
    private val SHORT_HELP = setOf("help", "madad", "madat")
    private val Q_FIRST = setOf("kya", "kyaa", "can", "could", "do", "does", "are", "is")
    private val ABILITY = listOf("kar sakte", "kar sakta", "kar sakti", "kar paoge", "kar pate", "kar paate", "sakte ho", "sakta hai", "sakti hai", "can you", "could you", "do you", "are you able", "able to", "karte ho")
    private val SKIP_START = setOf("nova", "hey", "ok", "okay", "hi", "hello")

    fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) sb.append(if ((ch in 'a'..'z') || (ch in '0'..'9')) ch else ' ')
        return sb.toString().trim().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun has(t: String, key: String) = " $t".contains(" $key")

    fun isGeneral(text: String): Boolean {
        val t = norm(text)
        if (t.isEmpty()) return false
        val toks = t.split(' ')
        if (toks.size <= 3 && toks.any { it in SHORT_HELP }) return true
        return GENERAL.any { t.contains(it) }
    }

    /** "kya tum website bana sakte ho" -> the website topic. Only a question about an ability, short, no phone number. */
    fun topicFor(text: String): Topic? {
        val t = norm(text)
        if (t.isEmpty()) return null
        val toks = t.split(' ')
        if (toks.size > 9 || toks.any { it.length >= 5 && it.all { c -> c in '0'..'9' } }) return null
        val first = toks.firstOrNull { it !in SKIP_START } ?: return null
        if (first !in Q_FIRST) return null
        if (ABILITY.none { t.contains(it) }) return null
        return TOPICS.firstOrNull { tp -> tp.keys.any { k -> has(t, k) } }
    }

    fun isAsk(text: String): Boolean = isGeneral(text) || topicFor(text) != null

    private const val MORE_HI = "Poori list aur example ke liye chat mein MADAD dabao."
    private const val MORE_EN = "For the full list with examples, tap MADAD (help) in the chat."

    fun topicLine(tp: Topic, en: Boolean): String {
        val need = if (en) tp.needEn else tp.needHi
        return (if (en) "Yes! ${tp.en}: ${tp.descEn} Say: \"${tp.example}\". " else "Haan! ${tp.hi}: ${tp.descHi} Bolo: \"${tp.example}\". ") +
            (if (need.isNotEmpty()) "$need " else "") + (if (en) MORE_EN else MORE_HI)
    }

    fun general(en: Boolean): String = if (en)
        "I can: call, SMS and WhatsApp, alarms and timers, torch, volume and wifi, open apps, play songs and show routes, make pictures and websites, read the screen, remember things, learn new topics, and answer any question. Example: \"nova call mummy\". I never work on bank, payment or password screens. $MORE_EN"
    else
        "Main ye kar sakta hoon: call, SMS aur WhatsApp, alarm aur timer, torch, volume, wifi, app kholna, gaana aur raasta, photo aur website banana, screen padhna, yaad rakhna, naya seekhna, aur kisi bhi sawal ka jawab. Jaise bolo: \"nova mummy ko call karo\". Bank, payment ya password screen par main kaam nahi karta. $MORE_HI"

    fun answer(text: String, en: Boolean): String {
        val tp = topicFor(text)
        return if (tp != null) topicLine(tp, en) else general(en)
    }

    /** Added to the cloud AI system prompt so the AI answers "what can you do / how do I use this" correctly and never invents abilities. No dollar sign inside. */
    fun aiFacts(): String =
        "ABOUT YOURSELF: if the user asks what you can do, or how to use the app, answer in simple kind words in their language, no technical terms, using only this list. " +
            "NOVA can: " + TOPICS.joinToString("; ") { it.en + " (example: " + it.example + ")" } + ". " +
            "NOVA cannot: work on bank, payment or password screens, press the camera shutter, move money, or send anything without the user confirming. " +
            "If a thing is not on this list, say plainly that you cannot do it yet and offer text help instead. " +
            "Tell users that the MADAD (help) button in the chat shows the full list with examples, and that they can tap an example to try it."
}
