package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherRulesTest {
    private val apps = listOf(
        LauncherRules.AppItem("YouTube", "com.yt"), LauncherRules.AppItem("WhatsApp", "com.wa"),
        LauncherRules.AppItem("Google Maps", "com.maps"), LauncherRules.AppItem("Camera", "com.cam"),
        LauncherRules.AppItem("Camera", "com.cam")
    )

    @Test fun sortedIsAlphabeticalAndDeduped() {
        val s = LauncherRules.sorted(apps)
        assertEquals(listOf("Camera", "Google Maps", "WhatsApp", "YouTube"), s.map { it.label })
    }

    @Test fun filterMatchesWordStartFirst() {
        val s = LauncherRules.sorted(apps)
        assertEquals("Google Maps", LauncherRules.filter(s, "maps").first().label)
        assertEquals("YouTube", LauncherRules.filter(s, "you").first().label)
        assertEquals(s.size, LauncherRules.filter(s, "  ").size)
    }

    @Test fun commandVsAppName() {
        val s = LauncherRules.sorted(apps)
        assertFalse(LauncherRules.looksLikeCommand("youtube", s))
        assertTrue(LauncherRules.looksLikeCommand("papa ko call karke bolo main aaunga", s))
        assertTrue(LauncherRules.looksLikeCommand("aaj ka mausam?", s))
    }

    @Test fun greetingByHour() {
        assertEquals("Suprabhat, boss", LauncherRules.greeting(8, false))
        assertEquals("Good night, boss", LauncherRules.greeting(23, true))
    }

    @Test fun weatherLabels() {
        assertEquals("Saaf dhoop", LauncherRules.weatherLabel(0))
        assertEquals("Barish", LauncherRules.weatherLabel(63))
        assertEquals("Toofan", LauncherRules.weatherLabel(95))
        assertEquals("Mausam", LauncherRules.weatherLabel(-1))
    }

    @Test fun parseWeatherAndGeo() {
        val w = LauncherRules.parseWeather("{\"current\":{\"temperature_2m\":24.6,\"weather_code\":1}}")!!
        assertEquals(25, w.tempC); assertEquals(1, w.code)
        assertEquals(null, LauncherRules.parseWeather("{\"current\":{}}"))
        assertEquals(null, LauncherRules.parseWeather("not json"))
        val g = LauncherRules.parseGeo("{\"results\":[{\"latitude\":26.85,\"longitude\":80.94}]}")!!
        assertEquals(26.85, g.first, 0.001); assertEquals(80.94, g.second, 0.001)
        assertEquals(null, LauncherRules.parseGeo("{\"generationtime_ms\":1}"))
    }

    @Test fun percentAndBarColor() {
        assertEquals(50, LauncherRules.percent(5, 10)); assertEquals(0, LauncherRules.percent(5, 0)); assertEquals(100, LauncherRules.percent(20, 10))
        assertEquals(LauncherRules.barColor(90, true), LauncherRules.barColor(10, false))   // 90% battery and 10% RAM are both "good"
        assertTrue(LauncherRules.barColor(10, true) != LauncherRules.barColor(90, true))
    }
}

