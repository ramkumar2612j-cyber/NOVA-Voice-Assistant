package com.nova.assistant

import com.nova.assistant.WebSources.Kind
import org.junit.Assert.*
import org.junit.Test

/** v6.97: classifier + parsers of the five key-less browsers. Payloads are samples in the documented shape; NO live call was made. */
class WebSourcesTest {
    private fun k(s: String) = WebSources.classify(s)

    @Test fun weatherCity() {
        assertEquals(Kind.WEATHER, k("jaipur ka mausam kaisa hai")!!.kind)
        assertEquals("jaipur", k("jaipur ka mausam kaisa hai")!!.query)
        assertEquals("delhi", k("weather in Delhi")!!.query)
        assertEquals("mumbai", k("nova mumbai me barish hogi kya")!!.query)
        assertEquals(1, k("kal jaipur ka mausam")!!.dayOffset)
        assertEquals("", k("mausam kaisa hai")!!.query)
        assertEquals("new delhi", k("weather of new delhi today")!!.query)
    }
    @Test fun news() {
        val a = k("aaj ki taaza khabar sunao")!!
        assertEquals(Kind.NEWS, a.kind); assertEquals("", a.query)
        assertEquals("cricket", k("cricket ki khabar")!!.query)
        assertEquals("india pakistan match score", k("india pakistan match score")!!.query)
        assertEquals("tesla", k("latest news about tesla")!!.query)
    }
    @Test fun currency() {
        val a = k("dollar ka rate kya hai")!!
        assertEquals(Kind.CURRENCY, a.kind); assertEquals("USD", a.query); assertEquals("INR", a.to)
        val b = k("100 dollar kitne rupaye hote hain")!!
        assertEquals(100.0, b.amount, 0.0); assertEquals("USD", b.query); assertEquals("INR", b.to)
        val c = k("usd to eur")!!
        assertEquals("EUR", c.to)
        assertEquals("USD", k("rupaye ka rate")!!.to)
        assertNull(k("rupaye bhejo"))
    }
    @Test fun crypto() {
        val a = k("bitcoin ka price kya hai")!!
        assertEquals(Kind.CRYPTO, a.kind); assertEquals("bitcoin", a.query)
        assertEquals("ethereum", k("eth price today")!!.query)
        assertNotEquals(Kind.CRYPTO, k("bitcoin kya hai")?.kind)
    }
    @Test fun defineAndFact() {
        assertEquals(Kind.DEFINE, k("meaning of serendipity")!!.kind)
        assertEquals("serendipity", k("serendipity ka matlab kya hai")!!.query)
        assertEquals(Kind.FACT, k("taj mahal ke baare mein batao")!!.kind)
        assertEquals(Kind.FACT, k("who is albert einstein")!!.kind)
        assertEquals(Kind.FACT, k("capital of france")!!.kind)
        assertEquals(Kind.FACT, k("shivaji maharaj kaun tha")!!.kind)
    }
    @Test fun neverHijacksCommandsOrPersonal() {
        val cmds = listOf("torch on karo", "volume badhao", "mummy ko call karo", "whatsapp pe message bhejo", "alarm laga do", "battery kitni hai",
            "time kya hua hai", "wifi on karo", "youtube pe gaana chalao", "phone lock karo", "brightness kam karo", "mera password kya hai",
            "mere phone ka mausam", "hello nova", "kaise ho", "ek joke sunao", "screenshot lo", "camera kholo", "music pause karo", "set timer ten minutes")
        for (c in cmds) assertNull("must not be a web lookup: $c", k(c))
    }
    @Test fun requestsUseAllowedHostsOnly() {
        val needs = listOf(k("jaipur ka mausam")!!, k("khabar")!!, k("dollar ka rate")!!, k("bitcoin price")!!, k("meaning of ephemeral")!!, k("who is gandhi")!!)
        for (n in needs) for (r in WebSources.requests(n)) {
            val host = java.net.URI(r.url).host
            assertTrue("$host not allowed", host in WebSources.ALLOWED_HOSTS)
            assertTrue(r.url.startsWith("https://"))
        }
    }
    @Test fun wikiParse() {
        val body = """{"batchcomplete":true,"query":{"pages":[{"pageid":1,"title":"Taj Mahal","extract":"The Taj Mahal is an ivory-white marble mausoleum on the right bank of the river Yamuna in Agra, India. It was commissioned in 1631 by Shah Jahan.","fullurl":"https://en.wikipedia.org/wiki/Taj_Mahal"}]}}"""
        val a = WebSources.parseWiki(body, true)!!
        assertTrue(a.text.startsWith("Taj Mahal: The Taj Mahal is")); assertTrue(a.text.endsWith("(Wikipedia)"))
        assertNull(WebSources.parseWiki("""{"query":{"pages":[{"title":"X","missing":true}]}}""", true))
        assertNull(WebSources.parseWiki("""{"query":{"pages":[{"title":"Mercury","extract":"Mercury may refer to: the planet, the element and a lot of other things in the list below."}]}}""", true))
        assertNull(WebSources.parseWiki("not json", true))
    }
    @Test fun wiktionaryParse() {
        val body = """{"en":[{"partOfSpeech":"Noun","language":"English","definitions":[{"definition":"The occurrence of <a href=\"x\">events</a> by chance in a &amp; happy way."},{"definition":"A pleasant surprise."}]}]}"""
        val a = WebSources.parseWiktionary(body, "serendipity", true)!!
        assertTrue(a.text.contains("(noun) The occurrence of events by chance in a & happy way.")); assertTrue(a.text.endsWith("(Wiktionary)"))
        assertNull(WebSources.parseWiktionary("""{"fr":[]}""", "x", true))
    }
    @Test fun ddgParse() {
        val a = WebSources.parseDdg("""{"Heading":"Kotlin","AbstractText":"Kotlin is a cross-platform, statically typed, general-purpose programming language with type inference.","AbstractSource":"Wikipedia","Answer":""}""", true)!!
        assertTrue(a.text.startsWith("Kotlin: Kotlin is a cross-platform")); assertTrue(a.text.endsWith("(Wikipedia)"))
        assertEquals("4 (DuckDuckGo)", WebSources.parseDdg("""{"Answer":"4","AbstractText":""}""", true)!!.text)
        assertNull(WebSources.parseDdg("""{"Heading":"","AbstractText":"","RelatedTopics":[{"Text":"something loosely related that is long enough to look real","FirstURL":"x"}]}""", true))
        assertNull(WebSources.parseDdg("", true))
    }
    @Test fun weatherParse() {
        val geo = WebSources.parseGeo("""{"results":[{"id":1,"name":"Jaipur","latitude":26.9196,"longitude":75.7878,"country":"India","admin1":"Rajasthan"}]}""")!!
        assertEquals("Jaipur", geo.name); assertEquals(26.9196, geo.lat, 1e-6)
        assertNull(WebSources.parseGeo("""{"generationtime_ms":0.5}"""))
        val body = """{"current":{"temperature_2m":29.4,"relative_humidity_2m":55,"apparent_temperature":31.2,"weather_code":2,"wind_speed_10m":8.2},"daily":{"temperature_2m_max":[33.1,34.0],"temperature_2m_min":[22.4,23.0],"precipitation_probability_max":[10,40],"weather_code":[2,61]}}"""
        val a = WebSources.parseForecast(body, geo, 0, false)!!
        assertEquals("Jaipur (Rajasthan) me abhi 29°C, mehsoos 31°C, halke badal. Aaj 22°C se 33°C tak, barish ka chance 10%. Nami 55%, hawa 8 km/h. (Open-Meteo)", a.text)
        val t = WebSources.parseForecast(body, geo, 1, true)!!
        assertEquals("Jaipur (Rajasthan) tomorrow: 23°C to 34°C, light rain, rain chance 40%. (Open-Meteo)", t.text)
        assertNull(WebSources.parseForecast("""{"daily":{}}""", geo, 0, true))
    }
    @Test fun newsParse() {
        val rss = """<?xml version="1.0"?><rss><channel><title>x</title>
<item><title>India wins the series 3-1 against Australia - The Hindu</title><link>https://n/1</link><source url="https://thehindu.com">The Hindu</source></item>
<item><title><![CDATA[Markets close higher &amp; Nifty gains 1% - Mint]]></title><source url="https://livemint.com">Mint</source></item>
<item><title>India wins the series 3-1 against Australia - The Hindu</title><source url="a">The Hindu</source></item>
<item><title>short</title></item></channel></rss>"""
        val a = WebSources.parseNews(rss, "", true)!!
        assertEquals("Top headlines: 1) India wins the series 3-1 against Australia (The Hindu). 2) Markets close higher & Nifty gains 1% (Mint). (Google News)", a.text)
        assertTrue(WebSources.parseNews(rss, "cricket", false)!!.text.startsWith("cricket ki taaza khabrein:"))
        assertNull(WebSources.parseNews("<rss></rss>", "", true))
    }
    @Test fun fxParse() {
        val need = WebSources.Need(Kind.CURRENCY, "USD", "INR", 100.0)
        val a = WebSources.parseFx("""{"amount":1.0,"base":"USD","date":"2026-10-14","rates":{"INR":88.125}}""", need, true)!!
        assertTrue(a.text.startsWith("100 USD = 8,812.5 INR")); assertTrue(a.text.endsWith("(Frankfurter)"))
        assertNull(WebSources.parseFx("""{"rates":{"EUR":0.9}}""", need, true))
        assertNull(WebSources.parseFx("""{"message":"not found"}""", need, true))
    }
    @Test fun coinParse() {
        val a = WebSources.parseCoin("""{"bitcoin":{"inr":9712345.0,"inr_24h_change":1.234,"usd":111234.5,"usd_24h_change":1.1}}""", "bitcoin", true)!!
        assertEquals("Bitcoin: Rs 9,712,345 (USD 111,234.5), last 24h +1.2%. (CoinGecko)", a.text)
        assertNull(WebSources.parseCoin("""{}""", "bitcoin", true))
    }
}
