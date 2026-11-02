package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v6.97 wip14 Part 1: website / app / Play Store wording rules. Pure text, no Android. */
class OpenWebRulesTest {
    private fun names(r: OpenWebRules.OpenReq?) = r?.targets?.map { it.name }

    @Test fun plainAppOpenStaysWithTheExistingPath() {
        assertNull(OpenWebRules.parseOpen("youtube kholo"))
        assertNull(OpenWebRules.parseOpen("nova open whatsapp"))
        assertNull(OpenWebRules.parseOpen("instagram khol do"))
        assertNull(OpenWebRules.parseOpen("youtube app kholo"))
    }

    @Test fun chromeBrowserItselfIsNotASite() {
        assertNull(OpenWebRules.parseOpen("chrome kholo"))
        assertNull(OpenWebRules.parseOpen("google chrome kholo"))
        assertNull(OpenWebRules.parseOpen("open chrome"))
    }

    @Test fun oneSiteInChrome() {
        val r = OpenWebRules.parseOpen("youtube chrome pe kholo")!!
        assertEquals(listOf("youtube"), names(r))
        assertTrue(r.chrome)
        assertTrue(r.targets[0].web)
        assertEquals("https://www.youtube.com", r.targets[0].url)
        assertEquals("YouTube", r.targets[0].label)
        assertEquals(listOf("youtube"), names(OpenWebRules.parseOpen("open youtube in chrome")))
        assertEquals(listOf("flipkart"), names(OpenWebRules.parseOpen("nova chrome mein flipkart kholo")))
    }

    @Test fun severalSitesEachGetOneTarget() {
        val r = OpenWebRules.parseOpen("nova youtube google aur flipkart chrome pe kholo")!!
        assertEquals(listOf("youtube", "google", "flipkart"), names(r))
        assertTrue(r.targets.all { it.web && it.url != null })
        val r2 = OpenWebRules.parseOpen("youtube, google, amazon, flipkart, myntra websites kholo")!!
        assertEquals(listOf("youtube", "google", "amazon", "flipkart", "myntra"), names(r2))
        assertFalse(r2.truncated)
    }

    @Test fun severalNamesWithoutChromeStayAppFirst() {
        val r = OpenWebRules.parseOpen("instagram aur facebook kholo")!!
        assertEquals(listOf("instagram", "facebook"), names(r))
        assertFalse(r.chrome)
        assertTrue(r.targets.none { it.web })
        assertNotNull(r.targets[0].url)   // known site stays as the fallback when the app is not installed
    }

    @Test fun domainsAndWebOnlySitesAlwaysOpenAsWebsites() {
        val r = OpenWebRules.parseOpen("flipkart.com kholo")!!
        assertEquals("https://flipkart.com", r.targets[0].url)
        assertTrue(r.targets[0].web)
        assertEquals("https://flipkart.com", OpenWebRules.parseOpen("flipkart dot com kholo")!!.targets[0].url)
        val w = OpenWebRules.parseOpen("whatsapp web kholo")!!
        assertEquals("https://web.whatsapp.com", w.targets[0].url)
        assertTrue(w.targets[0].web)
    }

    @Test fun unknownNameInWebsiteModeIsGuessedOrSearched() {
        assertEquals("https://www.xyzshop.com", OpenWebRules.parseOpen("xyzshop website kholo")!!.targets[0].url)
        val multi = OpenWebRules.parseOpen("raju ki dukan website kholo")
        assertNotNull(multi)
        assertTrue(multi!!.targets[0].url!!.startsWith("https://www.google.com/search?q="))
        assertNull(OpenWebRules.siteUrl("xyzshop", guess = false))
    }

    @Test fun morethanEightIsTruncatedHonestly() {
        val r = OpenWebRules.parseOpen("youtube google gmail facebook instagram amazon flipkart myntra netflix chrome pe kholo")!!
        assertEquals(OpenWebRules.MAX_TARGETS, r.targets.size)
        assertTrue(r.truncated)
    }

    @Test fun otherHandlersKeepTheirSentences() {
        assertNull(OpenWebRules.parseOpen("youtube chrome pe kholo aur song play karo"))
        assertNull(OpenWebRules.parseOpen("youtube me arijit search karo"))
        assertNull(OpenWebRules.parseOpen("anand ko whatsapp message bhejo"))
        assertNull(OpenWebRules.parseOpen("youtube mat kholo"))
        assertNull(OpenWebRules.parseOpen("camera kholo"))
        assertNull(OpenWebRules.parseOpen("wifi settings kholo"))
        assertNull(OpenWebRules.parseOpen("whatsapp download karo"))
        assertNull(OpenWebRules.parseOpen("kya tum website bana sakte ho"))
        assertNull(OpenWebRules.parseOpen(""))
    }

    @Test fun voiceSpellingsAreRepaired() {
        assertEquals(listOf("youtube"), names(OpenWebRules.parseOpen("you tube crome pe kholo")))
        assertEquals(listOf("facebook", "instagram"), names(OpenWebRules.parseOpen("face book aur insta gram chrome me kholo")))
    }

    @Test fun downloadKnownApps() {
        val a = OpenWebRules.parseDownload("whatsapp download karo")!!
        assertEquals("com.whatsapp", a.pkg)
        assertEquals("WhatsApp", a.label)
        assertEquals("com.instagram.android", OpenWebRules.parseDownload("nova instagram download kar do play store se")!!.pkg)
        assertEquals("com.whatsapp.w4b", OpenWebRules.parseDownload("download whatsapp business")!!.pkg)
        assertEquals("org.telegram.messenger", OpenWebRules.parseDownload("install karo telegram")!!.pkg)
        assertEquals("com.google.android.youtube", OpenWebRules.parseDownload("youtube download karo google play store se")!!.pkg)
    }

    @Test fun downloadUnknownAppHasNoPackage() {
        val a = OpenWebRules.parseDownload("xyz game download karo")!!
        assertNull(a.pkg)
        assertEquals("Xyz Game", a.label)
    }

    @Test fun downloadSeveralAppsDoesTheFirstAndKeepsTheRest() {
        val a = OpenWebRules.parseDownload("whatsapp aur instagram download karo")!!
        assertEquals("com.whatsapp", a.pkg)
        assertEquals(listOf("Instagram"), a.rest)
    }

    @Test fun downloadLeavesOtherDownloadsAlone() {
        assertNull(OpenWebRules.parseDownload("nova update download karo"))
        assertNull(OpenWebRules.parseDownload("offline pack download karo"))
        assertNull(OpenWebRules.parseDownload("ye song download karo"))
        assertNull(OpenWebRules.parseDownload("model download karo"))
        assertNull(OpenWebRules.parseDownload("whatsapp uninstall karo"))
        assertNull(OpenWebRules.parseDownload("whatsapp download mat karo"))
        assertNull(OpenWebRules.parseDownload("youtube kholo"))
        assertNull(OpenWebRules.parseDownload("download"))
    }

    @Test fun siteUrlHelpers() {
        assertEquals("https://example.org", OpenWebRules.siteUrl("example.org", guess = false))
        assertEquals("https://mail.google.com", OpenWebRules.siteUrl("gmail", guess = false))
        assertEquals("YouTube", OpenWebRules.labelFor("youtube"))
        assertEquals("Raju Shop", OpenWebRules.labelFor("raju shop"))
    }

    // ---- v6.99 Part 3: speech-to-text spellings of download / install (written, NOT RUN)
    @Test fun downloadSpeechSpellingsAreUnderstood() {
        for (s in listOf(
            "whatsapp daunlod karo", "whatsapp dawnload karo", "whatsapp down load karo", "whatsapp down lod kar do",
            "instol karo whatsapp", "nova whatsapp downlod karo",
            "whatsapp \u0921\u093e\u0909\u0928\u0932\u094b\u0921 \u0915\u0930\u094b",
            "whatsapp \u0907\u0902\u0938\u094d\u091f\u0949\u0932 \u0915\u0930\u094b"
        )) assertEquals(s, "com.whatsapp", OpenWebRules.parseDownload(s)?.pkg)
    }

    @Test fun misspelledDownloadIsNeverTakenAsOpenOrOtherDownloads() {
        assertNull(OpenWebRules.parseOpen("whatsapp daunlod karo"))
        for (s in listOf("nova update daunlod karo", "video daunlod karo", "mat daunlod karo whatsapp", "gaana down load karo"))
            assertNull(s, OpenWebRules.parseDownload(s))
    }

    @Test fun helpUnderstandsMisspelledDownloadQuestion() {
        assertEquals("download", HelpGuide.topicFor("kya tum app daunlod kar sakte ho")?.id)
    }
}
