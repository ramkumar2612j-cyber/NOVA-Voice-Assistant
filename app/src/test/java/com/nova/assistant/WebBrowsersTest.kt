package com.nova.assistant

import com.nova.assistant.WebSources.Kind
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** v6.97: engine rules with a FAKE fetcher (no network): cache, parallel preference, breaker, offline, honest failures. */
class WebBrowsersTest {
    private var now = 1_000_000L
    private val calls = AtomicInteger(0)

    @Before fun setUp() { WebBrowsers.clearForTest(); WebBrowsers.clock = { now }; calls.set(0) }
    @After fun tearDown() { WebBrowsers.clock = { System.currentTimeMillis() }; WebBrowsers.fetcher = WebBrowsers.Fetcher { u, m -> WebBrowsers.httpGet(u, m) } }

    private val wikiBody = """{"query":{"pages":[{"title":"Taj Mahal","extract":"The Taj Mahal is an ivory-white marble mausoleum on the right bank of the river Yamuna in Agra, India."}]}}"""
    private val ddgBody = """{"Heading":"Taj Mahal","AbstractText":"A mausoleum in Agra built by Shah Jahan in memory of his wife Mumtaz Mahal.","AbstractSource":"Wikipedia"}"""
    private val geoBody = """{"results":[{"name":"Jaipur","latitude":26.9,"longitude":75.8,"admin1":"Rajasthan"}]}"""
    private val fcBody = """{"current":{"temperature_2m":30.0,"relative_humidity_2m":50,"apparent_temperature":31.0,"weather_code":0,"wind_speed_10m":5.0},"daily":{"temperature_2m_max":[33.0,33.0],"temperature_2m_min":[22.0,22.0],"precipitation_probability_max":[0,0],"weather_code":[0,0]}}"""

    private fun fact() = WebSources.Need(Kind.FACT, "taj mahal")

    @Test fun factUsesWikiFirstAndCaches() {
        WebBrowsers.fetcher = WebBrowsers.Fetcher { u, _ -> calls.incrementAndGet(); if (u.contains("wikipedia")) wikiBody else ddgBody }
        val a = WebBrowsers.run(fact(), true, true)!!
        assertTrue(a.ok); assertTrue(a.text.endsWith("(Wikipedia)")); assertEquals(WebSources.Browser.WIKI, a.browser)
        val before = calls.get()
        val b = WebBrowsers.run(fact(), true, true)!!
        assertTrue(b.cached); assertEquals(before, calls.get())
    }
    @Test fun factFallsBackToDdgWhenWikiEmpty() {
        WebBrowsers.fetcher = WebBrowsers.Fetcher { u, _ -> if (u.contains("wikipedia")) """{"query":{"pages":[{"title":"x","missing":true}]}}""" else ddgBody }
        val a = WebBrowsers.run(fact(), true, true)!!
        assertEquals(WebSources.Browser.INSTANT, a.browser)
    }
    @Test fun factMissReturnsNullSoChainContinues() {
        WebBrowsers.fetcher = WebBrowsers.Fetcher { _, _ -> null }
        assertNull(WebBrowsers.run(fact(), true, true))
    }
    @Test fun offline() {
        WebBrowsers.fetcher = WebBrowsers.Fetcher { _, _ -> calls.incrementAndGet(); "x" }
        assertNull(WebBrowsers.run(fact(), true, false))
        val w = WebBrowsers.run(WebSources.Need(Kind.WEATHER, "jaipur"), false, false)!!
        assertFalse(w.ok); assertTrue(w.text.contains("internet")); assertEquals(0, calls.get())
    }
    @Test fun weatherTwoSteps() {
        WebBrowsers.fetcher = WebBrowsers.Fetcher { u, _ -> if (u.contains("geocoding")) geoBody else fcBody }
        val a = WebBrowsers.run(WebSources.Need(Kind.WEATHER, "jaipur"), false, true)!!
        assertTrue(a.text.startsWith("Jaipur (Rajasthan) me abhi 30°C"))
    }
    @Test fun weatherNeedsACityOrHomeCity() {
        val r = WebBrowsers.run(WebSources.Need(Kind.WEATHER, ""), false, true)!!
        assertFalse(r.ok); assertTrue(r.text.contains("shehar"))
        WebBrowsers.fetcher = WebBrowsers.Fetcher { u, _ -> if (u.contains("geocoding")) geoBody else fcBody }
        assertTrue(WebBrowsers.run(WebSources.Need(Kind.WEATHER, ""), false, true, "Jaipur")!!.ok)
    }
    @Test fun liveFailureIsHonestNotNull() {
        WebBrowsers.fetcher = WebBrowsers.Fetcher { _, _ -> null }
        val r = WebBrowsers.run(WebSources.Need(Kind.NEWS, ""), true, true)!!
        assertFalse(r.ok); assertTrue(r.text.contains("news"))
        val c = WebBrowsers.run(WebSources.Need(Kind.CURRENCY, "AED", "INR"), true, true)!!
        assertTrue(c.text.contains("AED"))
    }
    @Test fun cacheExpires() {
        WebBrowsers.fetcher = WebBrowsers.Fetcher { _, _ -> calls.incrementAndGet(); """{"bitcoin":{"inr":100.0,"usd":1.0}}""" }
        val n = WebSources.Need(Kind.CRYPTO, "bitcoin")
        WebBrowsers.run(n, true, true); WebBrowsers.run(n, true, true)
        assertEquals(1, calls.get())
        now += 3 * 60_000L
        WebBrowsers.run(n, true, true)
        assertEquals(2, calls.get())
    }
    @Test fun breakerSkipsADeadBrowser() {
        WebBrowsers.fetcher = WebBrowsers.Fetcher { _, _ -> calls.incrementAndGet(); null }
        for (i in 1..3) WebBrowsers.run(WebSources.Need(Kind.NEWS, "q$i"), true, true)
        val before = calls.get()
        val r = WebBrowsers.run(WebSources.Need(Kind.NEWS, "q4"), true, true)!!
        assertEquals(before, calls.get()); assertFalse(r.ok)
        assertTrue(WebBrowsers.statusJson().contains("\"state\":\"paused\""))
        now += 100_000L
        WebBrowsers.run(WebSources.Need(Kind.NEWS, "q5"), true, true)
        assertTrue(calls.get() > before)
    }
    @Test fun currencyMirrorUsed() {
        WebBrowsers.fetcher = WebBrowsers.Fetcher { u, _ -> if (u.contains("frankfurter.dev")) null else """{"base":"USD","date":"2026-10-14","rates":{"INR":88.0}}""" }
        val a = WebBrowsers.run(WebSources.Need(Kind.CURRENCY, "USD", "INR", 2.0), true, true)!!
        assertTrue(a.text.startsWith("2 USD = 176 INR"))
    }
    @Test fun neverCallsAHostOutsideTheList() {
        assertNull(WebBrowsers.httpGet("https://evil.example.com/x", 1000))
        assertNull(WebBrowsers.httpGet("http://en.wikipedia.org/x", 1000))
    }
}
