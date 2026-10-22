package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class PlaybookRulesTest {
    @Test fun classifiesCommonJobsAndFallsBackToGeneric() {
        assertEquals(PlaybookRules.Kind.VIDEO, PlaybookRules.classify("youtube par gaana lagao"))
        assertEquals(PlaybookRules.Kind.MAIL, PlaybookRules.classify("gmail me mail dekho"))
        assertEquals(PlaybookRules.Kind.MESSAGE, PlaybookRules.classify("whatsapp pe message bhejo"))
        assertEquals(PlaybookRules.Kind.GENERIC, PlaybookRules.classify("kuch bhi"))
        assertEquals(PlaybookRules.Kind.GENERIC, PlaybookRules.classify(""))
    }

    @Test fun cleanAppRejectsUrlsEmailsNumbersAndLongText() {
        assertEquals("youtube", PlaybookRules.cleanApp(" YouTube "))
        assertEquals("", PlaybookRules.cleanApp("https://x.com/a?b=1"))
        assertEquals("", PlaybookRules.cleanApp("a@b.com"))
        assertEquals("", PlaybookRules.cleanApp("12345678"))
        assertEquals("", PlaybookRules.cleanApp("x".repeat(30)))
        assertEquals("", PlaybookRules.cleanApp(""))
    }

    @Test fun successCreditsAllAppsAndFailureChargesOnlyTheLast() {
        val k = PlaybookRules.Kind.VIDEO
        val a = PlaybookRules.record(emptyList(), k, listOf("chrome", "youtube"), true, 100L)
        val routes = a.single().routes.associateBy { it.app }
        assertEquals(1, routes["chrome"]!!.ok)
        assertEquals(1, routes["youtube"]!!.ok)
        val b = PlaybookRules.record(a, k, listOf("chrome", "youtube"), false, 200L)
        val r2 = b.single().routes.associateBy { it.app }
        assertEquals(0, r2["chrome"]!!.fail)
        assertEquals(1, r2["youtube"]!!.fail)
        assertEquals(200L, r2["youtube"]!!.lastAtMs)
    }

    @Test fun noAppMeansNothingIsRecorded() {
        val old = PlaybookRules.record(emptyList(), PlaybookRules.Kind.MAIL, listOf("gmail"), true, 1L)
        assertEquals(old, PlaybookRules.record(old, PlaybookRules.Kind.MAIL, listOf("", "http://x.com/p"), true, 2L))
    }

    @Test fun routesStayBoundedAndHopelessRoutesArePruned() {
        var e: List<PlaybookRules.Entry> = emptyList()
        for (i in 1..10) e = PlaybookRules.record(e, PlaybookRules.Kind.SEARCH, listOf("app$i"), true, i.toLong())
        assertTrue(e.single().routes.size <= PlaybookRules.MAX_ROUTES_PER_KIND)
        var f: List<PlaybookRules.Entry> = emptyList()
        for (i in 1..4) f = PlaybookRules.record(f, PlaybookRules.Kind.FORM, listOf("badapp"), false, i.toLong())
        assertEquals(4, f.single().routes.single().fail)
        f = PlaybookRules.record(f, PlaybookRules.Kind.FORM, listOf("badapp"), false, 5L)
        assertTrue(f.isEmpty())
    }

    @Test fun hintPrefersWorkingRouteWarnsAboutFailingOneAndStaysShort() {
        var e: List<PlaybookRules.Entry> = emptyList()
        e = PlaybookRules.record(e, PlaybookRules.Kind.VIDEO, listOf("youtube"), true, 1L)
        e = PlaybookRules.record(e, PlaybookRules.Kind.VIDEO, listOf("youtube"), true, 2L)
        e = PlaybookRules.record(e, PlaybookRules.Kind.VIDEO, listOf("oldplayer"), true, 3L)
        e = PlaybookRules.record(e, PlaybookRules.Kind.VIDEO, listOf("oldplayer"), false, 4L)
        e = PlaybookRules.record(e, PlaybookRules.Kind.VIDEO, listOf("oldplayer"), false, 5L)
        e = PlaybookRules.record(e, PlaybookRules.Kind.VIDEO, listOf("oldplayer"), false, 6L)
        val h = PlaybookRules.hint(e, PlaybookRules.Kind.VIDEO, true)
        assertTrue(h.contains("youtube"))
        assertTrue(h.contains("often failed: oldplayer"))
        assertTrue(h.contains("never overrides"))
        assertTrue(h.length <= PlaybookRules.MAX_HINT_CHARS)
        assertEquals("", PlaybookRules.hint(e, PlaybookRules.Kind.MAIL, true))
        assertEquals("", PlaybookRules.hint(emptyList(), PlaybookRules.Kind.VIDEO))
    }

    @Test fun oldHistoryDecaysSoNewBehaviourCanWin() {
        var e: List<PlaybookRules.Entry> = emptyList()
        for (i in 1..60) e = PlaybookRules.record(e, PlaybookRules.Kind.MUSIC, listOf("player"), true, i.toLong())
        assertTrue(e.single().routes.single().ok <= PlaybookRules.DECAY_AT + 1)
    }
}
