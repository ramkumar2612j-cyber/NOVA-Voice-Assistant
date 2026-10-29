package com.nova.assistant
import org.junit.Assert.*
import org.junit.Test
class VoiceRouteAuditTest {
    private fun r(s: String) = RouteProbe.route(s)
    @Test fun whatsappNaturalOrder() {
        val ok = mapOf("Anand ko WhatsApp bhejo ki main late aaunga" to Pair("anand", "main late aaunga"),
            "mummy ko whatsapp karo ki khana ban gaya" to Pair("mummy", "khana ban gaya"),
            "papa ko whatsapp pe bolo main aa raha hu" to Pair("papa", "main aa raha hu"),
            "anand ko whatsapp pe good morning bhejo" to Pair("anand", "good morning"))
        for ((s, w) in ok) { val q = WhatsAppCommandRules.parse(s); assertNotNull(s, q); assertEquals(w.first, q!!.recipient.lowercase()); assertEquals(w.second, q.message.lowercase()); assertTrue(r(s).startsWith("whatsapp_send")) }
        for (s in listOf("mummy ko whatsapp message bhejo", "anand ko whatsapp karo", "papa ko whatsapp pe photo bhejo", "kal 5 baje anand ko whatsapp pe hello bhejo", "usko whatsapp karo ki aa jao")) assertNull(s, WhatsAppCommandRules.parse(s))
        assertTrue(r("roz subah 8 baje mummy ko whatsapp pe hello bhejo").startsWith("local:schedule_create")) // must stay a schedule
        assertEquals("Aman Biswan", WhatsAppCommandRules.parse("WhatsApp Aman Biswan ko message bhejo kab aaoge")!!.recipient)
    }
    @Test fun queueSplit() {
        assertEquals(listOf("ye gaana phir se chalao"), CmdQueue.split("ye gaana phir se chalao"))
        assertEquals(listOf("torch phir se on karo"), CmdQueue.split("torch phir se on karo"))
        assertEquals(listOf("torch on karo", "wifi on karo"), CmdQueue.split("torch on karo aur phir se wifi on karo"))
        assertEquals(listOf("wifi on karo", "bluetooth on karo"), CmdQueue.split("wifi on karo aur uske baad bluetooth on karo"))
        assertEquals(listOf("volume badhao", "torch on karo"), CmdQueue.split("volume badhao phir torch on karo"))
    }
    @Test fun playStoreDownload() {
        for (s in listOf("play store pe whatsapp download karo", "play store me instagram install karo", "play store se facebook download karo")) assertEquals(s, "app_download", r(s))
    }
    @Test fun reelOpenAppOrChrome() {
        for (x in listOf("reel kholo", "instagram reels kholo", "nova reel dekhne ka man kar raha hai", "reels chalao", "reel dekhni hai", "kuch bhi reel dikhao")) {
            assertEquals(x, "reel_open", r(x)); assertFalse(x, ReelOpenRules.parse(x)!!.chrome)
        }
        for (x in listOf("chrome me reels kholo", "instagram reels chrome pe kholo", "browser me reel dekhni hai")) assertTrue(x, ReelOpenRules.parse(x)!!.chrome)
        for (x in listOf("agli reel", "is reel ko like karo", "reel scroll karo", "rahul_sharma ki reels dekho", "reel me kya chal raha hai", "reel download karo", "reels band karo")) assertNull(x, ReelOpenRules.parse(x))
        for (x in listOf("agli reel", "is reel ko like karo", "reel scroll karo")) assertEquals(x, "reel_plan", r(x))
    }
    @Test fun verbFirstCall() {
        for ((s, who) in mapOf("phone karo papa ko" to "papa", "call karo papa ko" to "papa", "call lagao mummy ko" to "mummy", "call karo anand" to "anand")) {
            val c = Logic.classify(s); assertNotNull(s, c); assertEquals(s, "call", c!!.kind); assertEquals(s, who, c.arg)
        }
        assertEquals("papa", Logic.classify("papa ko call karo")!!.arg)   // old order keeps working
        assertEquals("papa", Logic.classify("call papa")!!.arg)
    }
    @Test fun tradeWordsAndClose() {
        for (p in listOf("stop loss kitna rakhu gold me", "usd inr ka analysis", "aaj market kaisa hai")) assertNotNull(p, TradeRules.parse(p))
        assertEquals("stoploss kitna rakhu gold me", TradeRules.parse("stop loss kitna rakhu gold me")!!.query)
        for (p in listOf("trade close karo", "gold trade band karo")) {
            val q = TradeRules.parse(p); assertNotNull(p, q); assertTrue(p, q!!.closeAsk); assertFalse(p, q.placeAsk); assertTrue(p, q.openMt5)
        }
        val place = TradeRules.parse("mt5 me demo trade lagao xauusd")!!
        assertTrue(place.placeAsk); assertFalse(place.closeAsk)
        for (p in listOf("market jaana hai", "torch band karo", "call band karo")) assertNull(p, TradeRules.parse(p))
    }
    @Test fun reelStopAndPause() {
        for (x in listOf("reel dekhna band karo", "reels band karo", "reel scroll band karo", "nova reel stop karo")) assertEquals(x, ReelStopRules.Kind.STOP, ReelStopRules.parse(x))
        for (x in listOf("reel pause karo", "reel ko pause karo")) assertEquals(x, ReelStopRules.Kind.PAUSE, ReelStopRules.parse(x))
        for (x in listOf("reel kholo", "agli reel", "is reel ko like karo", "reel scroll karo", "rahul_sharma ki reels dekho", "reel download karo", "gaana band karo", "pause karo")) assertNull(x, ReelStopRules.parse(x))
    }
    @Test fun calendarQuestions() {
        val t = CalendarQueryRules.Day.TODAY; val k = CalendarQueryRules.Day.TOMORROW; val n = CalendarQueryRules.Day.NEXT
        assertEquals(n, CalendarQueryRules.parse("meeting kab hai"))
        assertEquals(t, CalendarQueryRules.parse("aaj kya schedule hai"))
        assertEquals(n, CalendarQueryRules.parse("my next meeting"))
        assertEquals(k, CalendarQueryRules.parse("kal ki meeting dikhao"))
        for (x in listOf("kal 5 baje meeting schedule karo", "meeting cancel karo", "aaj ka schedule", "torch on karo", "meeting ka reminder lagao", "kal subah 8 baje alarm")) assertNull(x, CalendarQueryRules.parse(x))
    }
    @Test fun shortsOpenAppOrChrome() {   // v7.135
        for (x in listOf("shorts kholo", "youtube shorts kholo", "nova shorts dekhne ka man kar raha hai", "shorts chalao", "youtube shorts dekhni hai", "yt shorts lagao")) {
            assertEquals(x, "shorts_open", r(x)); assertFalse(x, ShortsOpenRules.parse(x)!!.chrome)
        }
        for (x in listOf("chrome me shorts kholo", "youtube shorts chrome pe kholo", "browser me shorts dekhni hai")) assertTrue(x, ShortsOpenRules.parse(x)!!.chrome)
        for (x in listOf("shorts scroll karo", "shorts band karo", "is short ko like karo", "short me batao", "short video banao", "shorts kya hai", "reels kholo", "instagram shorts kholo", "shorts download karo", "agli shorts", "kholo")) assertNull(x, ShortsOpenRules.parse(x))
    }
}
