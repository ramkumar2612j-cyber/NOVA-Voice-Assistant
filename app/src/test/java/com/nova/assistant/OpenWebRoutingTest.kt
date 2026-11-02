package com.nova.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v6.97 wip14 Part 2: NovaService.handle() runs the web-open rules AFTER these earlier specialist handlers.
 * None of them may take a "open site in Chrome" sentence, and the web rules must not take sentences those handlers own.
 */
class OpenWebRoutingTest {
    private val webPhrases = listOf(
        "youtube chrome pe kholo",
        "nova youtube google aur flipkart chrome pe kholo",
        "flipkart.com kholo",
        "whatsapp web kholo",
        "instagram aur facebook kholo",
        "amazon website kholo",
        "open youtube in chrome",
        "youtube google amazon flipkart myntra websites kholo",
        "github chrome me kholo"
    )

    @Test fun earlierHandlersDoNotStealWebSentences() {
        for (p in webPhrases) {
            assertNotNull("web rules must take: $p", OpenWebRules.parseOpen(p))
            assertNull("SiteIntent: $p", SiteIntent.parse(p))
            assertFalse("SiteIntent.isPublish: $p", SiteIntent.isPublish(p))
            assertNull("ConnectorRules: $p", ConnectorRules.parse(p))
            assertNull("ConnectorRules plan: $p", ConnectorRules.parsePlan(p))
            assertNull("GmailRules: $p", GmailRules.parse(p))
            assertNull("AppSearchRules: $p", AppSearchRules.parse(p))
            assertNull("MusicPlayRules: $p", MusicPlayRules.parse(p))
            assertNull("LearnRules: $p", LearnRules.parse(p))
            assertNull("ImageIntent: $p", ImageIntent.parse(p))
            assertNull("RecordRules: $p", RecordRules.parse(p))
            assertNull("WhatsAppCommandRules: $p", WhatsAppCommandRules.parse(p))
            assertFalse("TeachRules: $p", TeachRules.isIntent(p))
            assertFalse("HelpGuide: $p", HelpGuide.isAsk(p))
            assertFalse("LeadRules: $p", LeadRules.isLeadAcquisitionRequest(p))
        }
    }

    @Test fun webRulesDoNotStealOtherHandlersSentences() {
        val others = listOf(
            "youtube me arijit ke gaane play karo",
            "youtube kholo aur arijit play karo",
            "anand ko whatsapp bhejo kal milte hain",
            "whatsapp download karo",
            "gmail me mail bhejo",
            "wifi settings kholo",
            "camera kholo",
            "recording on karo",
            "mera ek website banao",
            "github repos dikhao"
        )
        for (p in others) assertNull("web rules must leave: $p", OpenWebRules.parseOpen(p))
    }

    @Test fun plainOpenCommandsKeepTheOldAppPath() {
        for (p in listOf("youtube kholo", "open whatsapp", "instagram khol do", "calculator kholo", "chrome kholo")) {
            assertNull("old path: $p", OpenWebRules.parseOpen(p))
            assertNotNull("Logic still reads it: $p", Logic.classify(p))
        }
    }
}
