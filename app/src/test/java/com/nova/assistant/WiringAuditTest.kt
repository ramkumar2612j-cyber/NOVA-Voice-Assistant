package com.nova.assistant

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.98 WIRING AUDIT: every MADAD / SKILLS topic has an example sentence. For each one at least ONE real recognizer
 * (not the AI fallback) must understand it, so a skill that is listed can also be reached by voice / typing.
 * Pure-JVM check of the routing rules; the real phone work (permissions, hardware) is NOT covered here.
 */
class WiringAuditTest {
    private fun n(s: String) = try { VoiceText.normalize(s) } catch (e: Throwable) { s }

    private fun understood(raw: String): String? {
        val s = n(raw).lowercase().replace(Regex("^(nova|hey nova)\\s+"), "")
        fun <T> t(name: String, f: () -> T?): String? = try { if (f() != null) name else null } catch (e: Throwable) { null }
        return t("classify") { Logic.classify(s) }
            ?: t("trade") { TradeRules.parse(s) }
            ?: t("unread") { UnreadQueryRules.parse(s) }
            ?: t("profile") { ProfileSearchRules.parse(s) }
            ?: t("camera") { CameraEyeRules.parse(s) }
            ?: t("gmail") { GmailRules.parse(s) }
            ?: t("appsearch") { AppSearchRules.parse(s) }
            ?: t("meeting") { MeetingEventRules.parse(s) }
            ?: t("featurefix") { if (FeatureFixRules.matches(s)) true else null }
            ?: t("scan") { if (QuickScan.wantsLook(s) || QuickScan.wantsScan(s)) true else null }
            ?: t("moviescan") { MovieScanRules.parse(s).takeIf { it != MovieScanRules.Ask.NONE } }
            ?: t("agent") { if (AgentRules.isTask(s)) true else null }
            ?: t("music") { MusicPlayRulesPlus.parseBest(s) }
            ?: t("site") { SiteIntent.parse(s) }
            ?: t("photoedit") { PhotoEditIntent.parse(s) }
            ?: t("schedule") { ScheduledTaskRules.parse(s) }
            ?: t("teach") { TeachRules.parse(s) }
            ?: t("smartreply") { SmartReplyRules.parse(s) }
            ?: t("help") { if (HelpGuide.isAsk(s)) true else null }
            ?: t("featurevoice") { FeatureVoice.key(s) }
            ?: t("auto") { AutoIntent.detect(s, false).takeIf { it.kind != AutoIntent.Kind.NONE } }
    }

    @Test fun everyListedSkillHasARouteForItsExample() {
        val missing = ArrayList<String>()
        for (tp in HelpGuide.TOPICS) {
            if (understood(tp.example) == null) missing.add(tp.id + " <- " + tp.example)
        }
        println("WIRING-MISSING: " + missing)
        assertTrue("no recognizer for: $missing", missing.isEmpty())
    }

    @Test fun newSkillsAreInTheHelpGuide() {
        val ids = HelpGuide.TOPICS.map { it.id }.toSet()
        for (id in listOf("trade", "unreadmsg", "featuresfix", "meetingadd", "cameraeye", "profilesearch", "scan")) assertTrue(id, id in ids)
    }

    @Test fun tradeAndUnreadAndFixPhrasesAreRouted() {
        assertTrue(TradeRules.parse("gold ka analysis karo") != null)
        assertTrue(UnreadQueryRules.parse("ye btao insta pe koi naya message aaya h") != null)
        assertTrue(FeatureFixRules.matches("band features set karo"))
        assertTrue(Logic.classify("3:30 ka schedule banao rss meeting me jana hai")?.kind == "calendar_add")
    }
}
