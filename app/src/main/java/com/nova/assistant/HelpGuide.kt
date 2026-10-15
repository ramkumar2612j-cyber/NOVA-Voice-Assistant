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
        Topic("phone", "Torch, volume, wifi", "Torch, volume, wifi", listOf("torch", "flashlight", "volume", "wifi", "bluetooth", "brightness", "screenshot", "vibrate", "ringer", "rotation", "rotate"), "nova torch jala do", "Torch, volume, brightness, screenshot, vibrate ya ringer normal, screen rotation (auto rotate on/off, portrait, landscape) jaise phone ke kaam bolkar. Wifi, bluetooth, mobile data Android seedha on-off nahi karne deta, uska settings page khol deta hoon. Ringer silent ke liye Do Not Disturb access aur rotation ke liye ALLOW BRIGHTNESS permission chahiye.", "Phone basics by voice: torch, volume, brightness, screenshot, vibrate or normal ringer, screen rotation (auto rotate on/off, portrait, landscape). Android does not let apps switch wifi, bluetooth or mobile data on or off, so I open their settings page. Silent ringer needs Do Not Disturb access and rotation needs the ALLOW BRIGHTNESS permission.", "", ""),
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
        Topic("questions", "Sawal ka jawab", "Answers to questions", listOf("sawal", "question", "jawab", "answer", "pooch"), "nova solar panel kaise kaam karta hai", "Chat mein likhke poochho: likhna, code, translate, plan, padhai, analysis, sab ka poora jawab deta hoon (lists, code aur COPY button ke saath). Aaj ki khabar ya latest jaankari ke liye web se dhoondh kar Sources ke saath batata hoon. Internet chahiye.", "Type in the chat for writing, code, translation, plans, study help or analysis: I give a full answer with lists, code and a COPY button. For today's news or latest facts I search the web and list Sources. Internet needed.", "AI key se behtar jawab milta hai.", "An AI key gives better answers."),
        Topic("wabiz", "WhatsApp Business watch", "WhatsApp Business watch", listOf("business watch", "whatsapp business", "wa business"), "nova business watch 10 ghante", "Kuch ghante ke liye business chats par nazar rakhta hoon: order, payment ya complaint ho to turant batata hoon, aam sawal aapke catalogue notes se. Shuru mein reply khud nahi bhejta, sirf draft.", "For a few hours I watch your business chats: I tell you at once about orders, payments or complaints, and answer common questions only from your own catalogue notes. At first I only draft replies.", "Notification access chahiye.", "Needs Notification access."),
        Topic("autoreply", "Auto-reply", "Auto-reply", listOf("auto reply", "autoreply", "khud reply"), "Settings mein AUTO-REPLY ON karo", "Aap busy ho to WhatsApp jaise messages ka aapke apne likhe text se jawab de deta hoon aur baad mein batata hoon. AI SMART ON ho to message samajh kar apne shabdon mein jawab deta hoon (jo mujhe pata nahi wo aapse puchta hoon, apni taraf se rate ya number nahi ghadta). Bolkar ON ya OFF karne se pehle main haan ya nahi poochta hoon. Group, OTP ya password wale message ka kabhi nahi.", "When you are busy I answer WhatsApp-type messages with your own saved text and tell you afterwards. With AI SMART on I understand the message and answer in my own words (whatever I do not know I ask you, I never make up rates or numbers). Before I switch either on or off by voice I ask yes or no. Never for groups or OTP and password messages.", "Notification access chahiye. Settings mein switch ON karna padta hai.", "Needs Notification access. Turn the switch ON in Settings."),
        Topic("connectors", "Connectors (GitHub, Vercel, n8n)", "Connectors (GitHub, Vercel, n8n)", listOf("connector", "connectors", "github", "vercel", "n8n", "zapier"), "nova github repos dikhao", "GitHub, Vercel, n8n, MCP, Zapier ya Make jaisi services se judkar unka kaam bol kar karta hoon. Badalne wale kaam se pehle aapse haan poochta hoon.", "I connect to services like GitHub, Vercel, n8n, MCP, Zapier or Make and do their jobs by voice. I ask you to confirm before anything that changes data.", "Pehle connector jodna padta hai (token chahiye).", "Connect the service first (a token is needed)."),
        Topic("routines", "Routines", "Routines", listOf("routine", "routines"), "Settings mein ROUTINES kholo", "Apna naam aur bolne ka shabd rakho, phir ek bol par kai safe kaam ek ke baad ek ho jaate hain (zyada se zyada 8 step). Routine confirmation ke bina kuch risky nahi kar sakta.", "Set a name and a trigger phrase, then one phrase runs several safe jobs in a row (up to 8 steps). A routine cannot skip confirmations.", "", ""),
        Topic("lockguard", "Lock Guard (locked phone)", "Lock Guard (locked phone)", listOf("lock guard", "locked phone", "lock mode"), "Settings mein LOCK GUARD ON karo", "Phone lock ho to main chup rehta hoon aur command nahi sunta. Sirf zaroori baat bolta hoon: silent phone par call, ya Papa ya Mummy ka call ya message (message ka text kabhi nahi padhta). Volume key dabao to alert ruk jaata hai.", "While the phone is locked I stay quiet and take no commands. I only speak important things: a call while the phone is silent, or a call or message from Papa or Mummy (never the message text). Press a volume key to stop the alert.", "Notification access aur Accessibility chahiye.", "Needs Notification access and Accessibility."),
        Topic("teach", "Nickname sikhana aur ROZ KA SUDHAAR", "Teach nicknames and daily self-improve", listOf("nickname", "ka matlab", "roz ka sudhaar", "sudhaar", "self improve"), "nova yaad rakho insta ka matlab Instagram", "Aap batao ki kisi app ko kis naam se bulate ho, main yaad rakhta hoon. ROZ KA SUDHAAR din mein ek baar naye nickname sujhata hai, aapke HAAN dabane par hi lagta hai aur WAPAS se hat jaata hai.", "Tell me what you call an app and I remember it. ROZ KA SUDHAAR suggests new nicknames once a day; they apply only after you press HAAN and can be undone with WAPAS.", "ROZ KA SUDHAAR ke liye apni free AI key chahiye.", "ROZ KA SUDHAAR needs your own free AI key."),
        Topic("powerguide", "Battery aur background guide", "Battery and background guide", listOf("battery guide", "autostart", "background guide", "power guide"), "Settings mein BATTERY GUIDE kholo", "Phone ki company ke hisaab se battery aur autostart ki sahi screen kholta hoon aur batata hoon kya dabana hai, taaki NOVA band na ho. Screen na mile to Android ki battery screen khulti hai.", "I open the right battery and autostart screen for your phone brand and tell you what to tap, so NOVA is not killed. If that screen is missing, the standard Android battery screen opens.", "", ""),
        Topic("gmail", "Gmail", "Gmail", listOf("gmail", "email", "mail"), "nova important mail batao", "Gmail ke notification padhta hoon (login ya token nahi chahiye): zaroori mail batata hoon, aapki watch list ka mail (jaise Jio bill) aate hi bolta hoon, aur mail bhej sakta hoon.", "I read Gmail notifications (no login or token): I tell you important mail, announce mail from your watch list (like a Jio bill) when it arrives, and can send a mail.", "Notification access chahiye aur Gmail app ke notification ON.", "Needs Notification access and Gmail app notifications ON."),
        Topic("agent", "Lambe kaam (background jobs)", "Long jobs (background)", listOf("background job", "background jobs", "lamba kaam", "task progress"), "nova screen padh kar batao", "Lambe kaam bina atke peeche chalte hain, progress dikhta hai. Kaam adhura rah jaye to dobara try karta hoon. Maine kaam kiya bolna aur asli jaanch alag baat hai: jahan jaanch nahi ho paati wahan saaf bolta hoon.", "Long jobs run in the background with visible progress, and are retried if interrupted. A model saying done is not the same as verified, so I say plainly when I could not verify.", "Accessibility permission chahiye.", "Needs the Accessibility permission."),
        Topic("screenalert", "Screen alerts", "Screen alerts", listOf("screen alert", "screen alerts", "alert"), "nova analyze screen", "Screen par error ya warning ka dialog dikhe to kam-kam bolta hoon aur sirf poochta hoon: detail bolun? Aap haan bolo to poori detail padhta hoon, warna chup.", "When an error or warning dialog shows on screen I speak rarely and only ask: should I explain? If you say yes I read the details, otherwise I stay silent.", "Accessibility permission chahiye.", "Needs the Accessibility permission."),
        Topic("watchmic", "Watch mic (smartwatch se bulana)", "Watch mic (call NOVA from a watch)", listOf("watch", "smartwatch", "watch mic"), "Watch ka AI button dabao", "Bluetooth calling watch ka AI ya voice button dabane par NOVA sunti hai aur watch ke mic se aapki baat sunti hai. Sirf tab chalta hai jab watch voice-command signal aur Bluetooth call-audio deti ho. Asli watch par abhi test nahi hua.", "Pressing the AI or voice button of a Bluetooth calling watch wakes NOVA and it listens through the watch mic. Works only if the watch sends the voice-command signal and call audio. Not tested on a real watch yet.", "Bluetooth watch aur Microphone permission chahiye.", "Needs a Bluetooth watch and the Microphone permission."),
        Topic("quiet", "Quiet aur Silent mode", "Quiet and Silent mode", listOf("quiet", "silent", "quiet mode", "chup rehna", "bina bole"), "nova quiet mode on", "Quiet mode ON ho to jawab sirf screen par aate hain, bolte nahi. Kisi ek order ke saath chup rehna ya bina bole bolo to wo kaam chupchap hota hai. Haan ya nahi ke sawal hamesha bol kar hi poochta hoon.", "With quiet mode ON replies only show on screen and are not spoken. Add chup rehna or bina bole to a single order and it runs quietly. Yes or no questions are always spoken.", "", ""),
        Topic("offlineai", "Offline AI pack (5 free AI)", "Offline AI pack (5 free AIs)", listOf("offline ai", "offline pack", "offline model", "on device ai"), "Settings mein OFFLINE AI PACK kholo", "Phone ke andar chalne wale 5 free AI, bina key, bina account aur download ke baad bina internet ke. Pehla offline AI na chale to ye ek ek karke try hote hain.", "Five free on-device AIs that need no key, no account and no internet after the download. When the first offline AI is missing or cannot answer, these are tried one by one.", "Ek baar download chahiye (internet aur jagah).", "One-time download needed (internet and storage)."),
        Topic("leads", "Leads (business chats se)", "Leads (from business chats)", listOf("lead", "leads", "lead capture"), "nova lead capture", "Google Maps ya browser mein kisi business ki public listing kholo aur bolo, to dikhne wali public details phone par save karta hoon aur message ka draft banata hoon. Message aapki manzoori ke bina nahi jaata.", "Open a business's public listing in Google Maps or a browser and say it: I save the visible public details on this phone and write a draft. Nothing is sent without your approval.", "Accessibility permission chahiye.", "Needs the Accessibility permission."),
        Topic("limits", "Update Center aur API limits", "Update Center and API limits", listOf("api limit", "api limits", "limits", "update center", "usage"), "Settings mein API LIMITS kholo", "Dikhata hoon ki aaj har online AI service kitni baar use hui, roz ki limit kya hai aur kitni bachi hai. Ginti isi phone ki hai, doosre phone ya app ki call nahi dikhti. Update Center naya version dhundhta hai.", "Shows how many times each online AI service was used today, the daily limit and what is left. The count is from this phone only, not other devices or apps. Update Center looks for a new version.", "Internet chahiye.", "Needs internet."),
        Topic("featurestatus", "Features ka haal (bolkar poocho)", "Feature status (ask by voice)", listOf("feature status", "auto reply status", "lock guard status", "routines status", "offline pack status", "battery alert status", "kya chalu hai"), "auto reply ka status batao", "Auto-reply, Lock Guard, Routines, Battery alert, Offline pack ya API limit ON hai ya OFF, bolkar poochho. Main sirf haal batata hoon, kuch chalu ya band nahi karta; badalna ho to Settings mein jao. Home par KYA CHALTA HAI box mein bhi yahi dikhta hai.", "Ask by voice whether Auto-reply, Lock Guard, Routines, Battery alert, Offline pack or API limits is ON or OFF. I only tell the state and never switch anything; to change it, use Settings. The same is shown in the KYA CHALTA HAI box on Home.", "Kuch nahi chahiye.", "Nothing needed.")
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
        "I can: call, SMS and WhatsApp, alarms and timers, torch, volume and wifi, open apps, play songs and show routes, make pictures and websites, read the screen, remember things, learn new topics, Gmail, business chat watch, quiet mode, and answer any question. Example: \"nova call mummy\". I never work on bank, payment or password screens. $MORE_EN"
    else
        "Main ye kar sakta hoon: call, SMS aur WhatsApp, alarm aur timer, torch, volume, wifi, app kholna, gaana aur raasta, photo aur website banana, screen padhna, yaad rakhna, naya seekhna, Gmail, business chat watch, quiet mode, aur kisi bhi sawal ka jawab. Jaise bolo: \"nova mummy ko call karo\". Bank, payment ya password screen par main kaam nahi karta. $MORE_HI"

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
