package com.nova.assistant

object ProbeMain2 {
    val P = listOf(
        "112 call karo", "call 100", "100 par call karo", "police ko call karo", "ambulance bulao", "108 lagao", "phone karo papa ko", "call karo papa ko", "papa ko call", "mummy ko call karo abhi",
        "install youtube music", "install whatsapp", "whatsapp install", "app download karo whatsapp", "whatsapp app download karo", "play store pe whatsapp download karo", "whatsapp ko play store se install kar do",
        "papa ko whatsapp karo ki main aa raha hu", "anand ko whatsapp pe hello bhejo", "nova anand ko whatsapp bhejo ki main late aaunga", "rahul ko whatsapp karo kal milte hain", "anand ko whatsapp karo",
        "mummy ko whatsapp message bhejo", "papa ko whatsapp pe photo bhejo", "roz subah 8 baje mummy ko whatsapp pe hello bhejo", "kal 5 baje anand ko whatsapp pe hello bhejo",
        "remind me to drink water in 10 minutes", "remind me tomorrow at 9 am to call rahul", "mujhe kal 9 baje yaad dilana", "meeting 5 baje rakho", "kal ki meeting dikhao", "aaj kya schedule hai", "agli meeting kab hai",
        "my next meeting", "reminder lagao", "meeting cancel karo", "alarm band karo", "saare alarm hatao", "timer band karo",
        "usd inr ka analysis", "aaj market kaisa hai", "gold ka rate kya hai", "stop loss kitna rakhu gold me", "eurusd par trade lagao", "mt5 me gold buy karo", "gold trade band karo", "trade close karo", "mera trade kaisa chal raha hai",
        "jaise hi rahul_sharma post kare like karo", "rahul_sharma ki post par nazar rakho", "reels chalao", "reel dekhna band karo", "instagram reels kholo", "reel ko pause karo", "reels me scroll karo agle pe jao", "agle reel pe jao",
        "ye gaana phir se chalao", "torch phir se on karo", "phir se try karo", "volume badhao phir torch on karo", "phir bhi kaam nahi kiya", "wifi on karo aur uske baad bluetooth on karo", "wifi on karo then torch on karo",
        "1. torch on 2. wifi off 3. volume badhao", "torch on; wifi off; volume badhao", "alarm 5.30 baje lagao", "mujhe 5 baje utha dena 2 baje bhi",
        "torch on karo aur phir se wifi on karo"
    )
    @JvmStatic fun main(a: Array<String>) {
        for (p in P) {
            val sp = CmdQueue.split(p)
            println(p + " | " + (if (sp.size > 1) "SPLIT" + sp else "") + " | " + RouteProbe.route(p))
        }
        val ten = "torch on; wifi off; volume badhao; brightness kam karo; youtube kholo; chrome kholo; gmail kholo; camera kholo; settings kholo; torch off; wifi on; bluetooth on"
        println("TEN: " + CmdQueue.split(ten).size + " dropped=" + CmdQueue.splitDropped(ten))
    }
}
