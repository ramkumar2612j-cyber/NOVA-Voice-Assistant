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
        Topic("call", "Call aur SMS", "Calls and SMS", listOf("call", "dial", "sms", "kall"), "nova mummy ko call karo", "Naam bolo, main call ya SMS taiyaar kar deta hoon. Pehle kaam kar deta hoon, phir bata deta hoon. Settings mein confirm ON karoge to pehle poochunga.", "Say a name and I prepare the call or SMS. I do it first and tell you after. If you switch confirmation on in Settings, I ask first.", "Contacts permission chahiye.", "Needs the Contacts permission."),
        Topic("whatsapp", "WhatsApp message", "WhatsApp messages", listOf("whatsapp", "whatsap", "watsapp"), "nova Rahul ko WhatsApp karo kal milte hain", "WhatsApp message bhej sakta hoon. Pehle kaam kar deta hoon, phir bata deta hoon. Settings mein confirm ON karoge to pehle poochunga.", "I can send a WhatsApp message. I do it first and tell you after. If you switch confirmation on in Settings, I ask first.", "Accessibility permission chahiye.", "Needs the Accessibility permission."),
        Topic("alarm", "Alarm, timer, reminder", "Alarm, timer, reminder", listOf("alarm", "timer", "reminder", "yaad dila"), "nova subah 6 baje ka alarm lagao", "Alarm, timer aur reminder lagata hoon, bina internet ke.", "I set alarms, timers and reminders, even without internet.", "", ""),
        Topic("phone", "Torch, volume, wifi", "Torch, volume, wifi", listOf("torch", "flashlight", "volume", "wifi", "bluetooth", "brightness"), "nova torch jala do", "Torch, volume, wifi, bluetooth jaise phone ke kaam, bina internet ke.", "Phone basics like torch, volume, wifi and bluetooth, without internet.", "", ""),
        Topic("apps", "App kholna", "Open apps", listOf("kholo", "kholna", "open"), "nova YouTube kholo", "Koi bhi app ya phone ki setting khol deta hoon.", "I open any app or phone setting.", "", ""),
        Topic("chrome", "Website Chrome mein kholna", "Open websites in Chrome", listOf("chrome", "tab", "tabs"), "nova youtube google aur flipkart chrome pe kholo", "Website ka naam bolo, main use Chrome mein khol deta hoon. Kai website ek saath bolo to har ek alag tab mein khulti hai.", "Say a website name and I open it in Chrome. Say several together and each one opens in its own tab.", "", ""),
        Topic("download", "App download karna", "Download an app", listOf("download", "install", "playstore", "play store", "daunlod", "dawnload", "dawnlod", "downlod", "daunload", "instol", "instal"), "nova whatsapp download karo", "App ka naam bolo, main Play Store ka page khol kar Install dabata hoon aur tabhi batata hoon jab download sach mein shuru ho jaye. Paid app ya login ki zaroorat ho to khud nahi dabata.", "Say an app name and I open its Play Store page, press Install, and say so only when the download really started. I never press a price button or sign in for you.", "Accessibility permission aur Play Store mein Google login chahiye.", "Needs the Accessibility permission and a signed-in Play Store."),
        Topic("music", "Gaana aur raasta", "Music and directions", listOf("gana", "gaana", "gaane", "song", "music", "youtube", "navigate", "map", "route", "raasta"), "nova Delhi ka raasta dikhao", "YouTube par gaana ya video dhundhta hoon aur Google Maps mein jagah ya raasta dikhata hoon.", "I search a song or video on YouTube and show places or routes in Google Maps.", "Internet chahiye.", "Needs internet."),
        Topic("camera", "Camera kholna", "Open camera", listOf("camera", "selfie"), "nova selfie camera kholo", "Camera khol deta hoon (photo ya video). Photo ka button dabana mere haath mein nahi, wo aap dabao.", "I open the camera (photo or video). I cannot press the shutter button, you do that.", "", ""),
        Topic("image", "Photo banana (AI)", "Make a picture (AI)", listOf("photo", "image", "picture", "tasveer", "pic"), "nova ek billi ki photo banao", "AI se nayi photo banata hoon.", "I make a new picture with AI.", "Gemini key ya free image option chahiye.", "Needs a Gemini key or the free image option."),
        Topic("website", "Website banana", "Build a website", listOf("website", "site", "webpage"), "nova ek restaurant ki website banao", "Ek line bolo, poori website ban jaati hai. Baad mein bolo isko professional banao. Chaho to GitHub par publish bhi.", "Say one line and a full website is built. Later say make it more professional. It can also be published to GitHub.", "AI key chahiye, publish ke liye GitHub token.", "Needs an AI key, and a GitHub token to publish."),
        Topic("screen", "Screen padhna aur doosre app mein kaam", "Read the screen and work in other apps", listOf("screen", "agent"), "nova screen padh kar batao", "Screen padh kar samjha sakta hoon aur doosre app mein chhote kaam kar sakta hoon. Bank, payment aur password screen ka text kisi online AI ko nahi bhejta, par tum bolo to wahan tap ya type kar deta hoon.", "I can read and explain the screen and do small jobs in other apps. I never send bank, payment or password screen text to any online AI, but I tap and type there when you order it.", "Accessibility permission chahiye.", "Needs the Accessibility permission."),
        Topic("memory", "Yaad rakhna", "Remember things", listOf("yaad rakh", "remember", "memory"), "nova yaad rakh mera naam Shiv hai", "Aapki baatein sirf is phone mein yaad rakhta hoon, kahin upload nahi hoti.", "I remember what you tell me, only on this phone, nothing is uploaded.", "", ""),
        Topic("learn", "Naya topic seekhna", "Learn a new topic", listOf("seekh", "learn", "wikipedia"), "nova solar panel ke baare mein seekho", "Naya topic Wikipedia se seekh leta hoon.", "I learn a new topic from Wikipedia.", "Internet chahiye.", "Needs internet."),
        Topic("driving", "Driving mode", "Driving mode", listOf("driving", "gaadi"), "nova driving mode on", "Gaadi chalate waqt WhatsApp aur SMS bol kar sunata hoon, aur aap bolo to reply bhej deta hoon.", "While you drive I read WhatsApp and SMS aloud and send your spoken reply.", "Notification access chahiye.", "Needs Notification access."),
        Topic("recording", "Recording", "Recording", listOf("recording", "record"), "nova background me recording on karo", "Sirf aapke kehne par recording chalata hoon. Status Settings mein dikhta hai.", "I record only when you ask. The status shows in Settings.", "", ""),
        Topic("live", "Mausam, khabar, rate, matlab", "Weather, news, rates, meanings", listOf("browser", "live jankari", "live info"), "nova jaipur ka mausam batao", "Mausam, taaza khabar, dollar ya bitcoin ka rate, shabd ka matlab aur kisi cheez ki jankari internet se turant laata hoon, bina kisi key ke. Chhote sawal phone ka offline AI bhi sambhalta hai.", "I fetch the weather, latest news, dollar or bitcoin rates, word meanings and facts from the internet quickly, with no key. Short questions are also handled by the phone's offline AI.", "Internet chahiye.", "Needs internet."),
        Topic("questions", "Sawal ka jawab", "Answers to questions", listOf("sawal", "question", "jawab", "answer", "pooch"), "nova solar panel kaise kaam karta hai", "Aam sawalon ka jawab deta hoon, hindi, hinglish ya english mein. Phone ki permission ka sawal poochho to batata hoon kaun si allow hai aur kaise allow karni hai. Live news ya mausam ke liye internet chahiye.", "I answer everyday questions in Hindi, Hinglish or English. Ask about a phone permission and I tell you which ones are allowed and how to allow the rest. Live news or weather needs internet.", "AI key se behtar jawab milta hai.", "An AI key gives better answers."),
        Topic("safety", "Suraksha niyam", "Safety rules", listOf("safety", "suraksha", "bank", "password", "payment", "upi"), "nova tum bank screen par kaam kar sakte ho", "Tumhare bolne par main seedha kaam karta hoon, bina haan-nahi poochhe, bank, UPI aur password screen par bhi. Installer, permission, Settings aur Play Store mein main khud tap nahi karta. Kaam rokna ho to ruko, ruk jao ya bas karo bolo. Bank, payment aur password screen ka text kisi online AI ko nahi bhejta, password padhta bhi nahi.", "On your spoken order I act directly, without asking yes or no, also on bank, UPI and password screens. I never tap by myself in the installer, permission screens, Settings or the Play Store. To cancel a running job say stop, ruk jao or bas karo. Bank, payment and password screen text is never sent to an online AI, and I never read passwords.", "", ""),
        Topic("social", "Instagram aur WhatsApp jawab", "Instagram and WhatsApp replies", listOf("instagram", "insta", "comment", "birthday", "wish"), "nova insta comment ka kya karta hai", "Birthday wish ya achha comment aaye to main Thank you likh deta hoon aur tumhe bata deta hoon. Beizzati ya sharam wala message ya comment ho to jawab nahi deta, tumhe batata hoon, Instagram par report karna hota hai. Paise ki baat ho to bina poochhe seedha ek safe jawab bhejta hoon (amount ya detail nahi deta). OTP ya password maange to jawab nahi deta. Ek bande ko ghante mein sirf 2 jawab bhejta hoon.", "For a birthday wish or a nice comment I write Thank you and tell you. For an insulting or shaming message or comment I do not reply, I tell you, and on Instagram it needs a report. For money talk I send one safe reply at once (no amount, no details), and for an OTP or password request I send no reply. I send at most 2 replies per person per hour.", "Notification access aur auto-jawab ON chahiye.", "Needs notification access and auto-reply ON."),
        Topic("callanswer", "Call uthana aur kaatna", "Answer or reject calls", listOf("call utha", "call uthao", "call kaat", "call kaato", "call reject", "reject call", "answer call", "hang up"), "nova call uthao", "Call aaye to bolo: call uthao ya call kaato. WhatsApp ya Instagram call bhi. Phone lock ho to main chup rehta hoon.", "When a call rings, say: answer the call, or reject the call. WhatsApp and Instagram calls too. While the phone is locked I stay quiet.", "Notification access ya Phone permission chahiye.", "Needs Notification access or the Phone permission."),
        Topic("phonecontrol", "Screenshot, ringer, rotation", "Screenshot, ringer, rotation", listOf("screenshot", "ringer", "vibrate", "rotation", "auto rotate"), "nova screenshot lo", "Screenshot leta hoon, ringer silent, vibrate ya normal karta hoon, rotation on ya off karta hoon. Phone ne sach mein badla ho tabhi bolta hoon ho gaya.", "I take a screenshot, set the ringer to silent, vibrate or normal, and turn rotation on or off. I say done only when the phone really changed.", "Screenshot ke liye Accessibility, silent ke liye Do Not Disturb access, rotation ke liye Modify system settings chahiye.", "Screenshot needs Accessibility, silent needs Do Not Disturb access, rotation needs Modify system settings."),
        Topic("smartreply", "AI smart jawab", "AI smart replies", listOf("smart jawab", "smart reply", "ai jawab", "ai reply"), "nova kya tum smart jawab de sakte ho", "Settings mein AI SMART JAWAB ON karo to chat message ka chhota jawab AI banata hai. Paise, OTP ya link wala message AI ko nahi jata, uska fixed jawab jata hai. AI na chale to bhi fixed jawab. History mein AI ya fixed likha aata hai.", "Turn on AI SMART JAWAB in Settings and the AI writes one short reply to a chat message. Messages with money, OTP or links never go to the AI and get the fixed reply. If the AI fails I send the fixed reply. History shows AI or fixed.", "Notification access, auto-jawab ON aur AI key chahiye.", "Needs Notification access, auto reply ON and an AI key."),
        Topic("calendar", "Calendar aur meetings", "Calendar and meetings", listOf("calendar", "meeting", "meetings"), "nova aaj ki meetings", "Aaj ki meetings batata hoon aur agle 7 din ki meeting se 10 minute pehle yaad dilata hoon. Calendar ka text sirf phone par rehta hai.", "I tell you today's meetings and remind you 10 minutes before meetings in the next 7 days. Calendar text stays on the phone.", "Calendar permission chahiye (Skills screen ke button se).", "Needs Calendar permission (from the button on the Skills screen).")
    )

    private val GENERAL = listOf(
        "kya kar sakte", "kya kar sakta", "kya kar sakti", "kya kya kar", "kya kuch kar", "kya kya karte",
        "what can you do", "what all can you do", "what do you do", "what are your features", "your features",
        "tumhare features", "tumhari features", "aapke features", "apke features", "feature batao", "features batao",
        "kaise use", "kaise chalate", "kaise chalau", "kaise chalana", "how to use", "how do i use", "nova help", "help menu", "need help"
    )
    private val PRIORITY = setOf("chrome", "download", "safety", "social", "callanswer", "phonecontrol", "smartreply")
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
        // chrome / download are checked first so "kya tum whatsapp download kar sakte ho" is not answered by the WhatsApp message topic.
        return TOPICS.firstOrNull { tp -> tp.id in PRIORITY && tp.keys.any { k -> has(t, k) } }
            ?: TOPICS.firstOrNull { tp -> tp.keys.any { k -> has(t, k) } }
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
        "I can: call, SMS and WhatsApp, alarms and timers, torch, volume and wifi, open apps, open websites in Chrome tabs, download apps from the Play Store, answer or reject calls, take screenshots and set ringer and rotation, send AI smart replies to chat messages, tell calendar meetings, play songs and show routes, make pictures and websites, read the screen, remember things, learn new topics, and answer any question. Example: \"nova call mummy\". I never send bank, payment or password screen text to any online AI. $MORE_EN"
    else
        "Main ye kar sakta hoon: call, SMS aur WhatsApp, alarm aur timer, torch, volume, wifi, app kholna, website Chrome mein kholna, app download karna, call uthana ya kaatna, screenshot, ringer aur rotation, AI smart jawab, calendar meetings, gaana aur raasta, photo aur website banana, screen padhna, yaad rakhna, naya seekhna, aur kisi bhi sawal ka jawab. Jaise bolo: \"nova mummy ko call karo\". Bank, payment ya password screen ka text main kisi online AI ko nahi bhejta. $MORE_HI"

    fun answer(text: String, en: Boolean): String {
        val tp = topicFor(text)
        return if (tp != null) topicLine(tp, en) else general(en)
    }

    /** Added to the cloud AI system prompt so the AI answers "what can you do / how do I use this" correctly and never invents abilities. No dollar sign inside. */
    fun aiFacts(): String =
        "ABOUT YOURSELF: if the user asks what you can do, or how to use the app, answer in simple kind words in their language, no technical terms, using only this list. " +
            "NOVA can: " + TOPICS.joinToString("; ") { it.en + " (example: " + it.example + ")" } + ". " +
            "NOVA cannot: press the camera shutter, or tap in system Settings, the app installer, permission screens or (by free tap) the Play Store. Bank, payment and password screen text is never sent to an online AI. " +
            "If a thing is not on this list, say plainly that you cannot do it yet and offer text help instead. " +
            "Tell users that the MADAD (help) button in the chat shows the full list with examples, and that they can tap an example to try it."
}
