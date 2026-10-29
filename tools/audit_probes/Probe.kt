package com.nova.assistant

object ProbeMain {
    val P: List<Pair<String, String>> = listOf(
        // ---- open website
        "web" to "youtube chrome pe kholo", "web" to "youtube google aur flipkart kholo", "web" to "flipkart kholo", "web" to "amazon.in kholo",
        "web" to "google.com open karo", "web" to "chrome mein instagram kholo", "web" to "chrome me gmail aur youtube kholo", "web" to "open github in chrome",
        "web" to "naukri.com kholo", "web" to "irctc kholo", "web" to "irctc website kholo", "web" to "chatgpt kholo", "web" to "open amazon and flipkart in chrome",
        "web" to "youtube kholo", "web" to "whatsapp kholo", "web" to "wikipedia kholo", "web" to "news.google.com kholo", "web" to "youtube aur google kholo",
        "web" to "chrome me flipkart khol do", "web" to "browser me amazon kholo", "web" to "open youtube in chrome", "web" to "youtube ko chrome me kholo",
        "web" to "meesho kholo chrome par", "web" to "gmail.com kholo", "web" to "facebook aur instagram aur youtube kholo chrome mein",
        // ---- download app by name
        "dl" to "whatsapp download karo", "dl" to "instagram download kar do", "dl" to "phonepe install karo", "dl" to "telegram download karo play store se",
        "dl" to "download karo spotify", "dl" to "mx player download karo", "dl" to "paytm install kar do", "dl" to "truecaller download karo",
        "dl" to "zomato app download karo", "dl" to "snapchat install karo", "dl" to "app install karo whatsapp", "dl" to "play store se facebook download karo",
        "dl" to "arattai download karo", "dl" to "install youtube music", "dl" to "download whatsapp business", "dl" to "pubg download karo",
        "dl" to "whatsapp download karna hai", "dl" to "mujhe instagram download karna hai", "dl" to "swiggy install kardo", "dl" to "download kar do google pay",
        "dl" to "whatsapp ko download karo", "dl" to "install karo kuku fm",
        // ---- call
        "call" to "papa ko call karo", "call" to "mummy ko phone lagao", "call" to "anand ko call karo", "call" to "call anand", "call" to "rahul ko call lagao",
        "call" to "mummy ko call kar do", "call" to "100 par call karo", "call" to "9876543210 ko call karo", "call" to "call 9876543210", "call" to "bhai ko phone karo",
        "call" to "didi ko phone lagao", "call" to "whatsapp pe anand ko call karo", "call" to "anand ko dial karo", "call" to "phone karo papa ko", "call" to "papa ko phone laga do",
        "call" to "call mummy", "call" to "emergency number lagao", "call" to "usko call karo", "call" to "wo jo kal wale the unko call karo",
        // ---- whatsapp
        "wa" to "Anand ko WhatsApp bhejo ki main late aaunga", "wa" to "whatsapp pe rahul ko message bhejo hello", "wa" to "mummy ko whatsapp karo ki khana ban gaya",
        "wa" to "papa ko whatsapp pe bolo main aa raha hu", "wa" to "anand ko msg karo kal milte hain", "wa" to "whatsapp business pe anand ko message bhejo namaste",
        "wa" to "send whatsapp to rahul saying hi", "wa" to "rahul ko hi bhejo whatsapp par", "wa" to "anand ko bolo ki call kare", "wa" to "whatsapp kholo rahul ka chat",
        "wa" to "anand ko whatsapp pe good morning bhejo", "wa" to "anand ko WhatsApp bhejo", "wa" to "message bhejo anand ko ki meeting 5 baje hai",
        "wa" to "whatsapp par anand ko likho ki main aa gaya", "wa" to "anand ko sms karo main late hu", "wa" to "anand ko text karo hello",
        "wa" to "papa ko message karo ki paise bhej do", "wa" to "mummy ko whatsapp pe voice message bhejo",
        // ---- schedule / meeting / reminders
        "sch" to "kal subah 9 baje meeting set karo", "sch" to "aaj shaam 5 baje team meeting rakho", "sch" to "kal 3 baje doctor ki appointment calendar me daalo",
        "sch" to "roz subah 7 baje pani peene ka reminder lagao", "sch" to "har somvar 10 baje report bhejna yaad dilao", "sch" to "10 minute baad yaad dilana chai banani hai",
        "sch" to "parso 11 baje client call schedule karo", "sch" to "alarm laga do subah 6 baje", "sch" to "timer 5 minute ka lagao", "sch" to "meeting kab hai",
        "sch" to "aaj ki meeting dikhao", "sch" to "mere kal ke schedule me kya hai", "sch" to "remind me at 6 pm to call mom", "sch" to "set a meeting tomorrow at 4 pm with rahul",
        "sch" to "shaam ko 7 baje gym jana yaad dilao", "sch" to "roz raat 10 baje whatsapp anand ko good night bhejo", "sch" to "kal subah 8 baje anand ko call karna yaad dilao",
        "sch" to "schedule karo kal 10 baje papa ko message", "sch" to "calendar me meeting add karo kal 11 baje", "sch" to "5 baje alarm",
        // ---- trade
        "tr" to "gold ka analysis karo", "tr" to "eurusd buy lena chahiye", "tr" to "nova bitcoin kaisa lag raha hai", "tr" to "xauusd ka signal do", "tr" to "mt5 kholo",
        "tr" to "trade karo gold par", "tr" to "demo account pe eurusd buy karo", "tr" to "aaj market kaisa hai", "tr" to "nifty ka view batao", "tr" to "stop loss kitna rakhu gold me",
        "tr" to "mt5 pe practice trade lagao", "tr" to "gold sell karun ya buy", "tr" to "usd inr ka analysis", "tr" to "metatrader kholo", "tr" to "gold buy karo",
        "tr" to "silver ka analysis batao", "tr" to "btc usd trade analysis", "tr" to "gold ab buy karna sahi hai kya",
        // ---- reels
        "reel" to "agli reel", "reel" to "reel scroll karo", "reel" to "is reel ko like karo", "reel" to "rahul_sharma ki 5 reels pe like aur comment karo",
        "reel" to "reels dekho 10 minute", "reel" to "jaise hi rahul_sharma post kare mujhe batao", "reel" to "reel pe comment karo nice", "reel" to "reel save karo",
        "reel" to "is reel ko share karo anand ko", "reel" to "pichli reel pe jao", "reel" to "reels scroll karna band karo", "reel" to "instagram reels kholo",
        "reel" to "10 reels like karo", "reel" to "next reel", "reel" to "reel ko like kar do", "reel" to "reels chalao"
    )
    @JvmStatic fun main(a: Array<String>) {
        for ((f, p) in P) {
            val sp = CmdQueue.split(p)
            println(f + " | " + p + " | " + (if (sp.size > 1) "SPLIT" + sp else "") + " | " + RouteProbe.route(p))
        }
    }
}
