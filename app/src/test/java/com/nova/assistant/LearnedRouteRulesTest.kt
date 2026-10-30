package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.nova.assistant.LearnedRouteRules as LR

class LearnedRouteRulesTest {
    private fun st(a: String, v: String = "") = LR.Step(a, v)
    private fun route(key: String, n: Int = 2, ok: Int = 1, fail: Int = 0, t: Long = 1000L) =
        LR.Route(key, List(n) { st("tap", "reels") }, ok, fail, t)

    @Test fun fingerprintIgnoresWakeWordsAndPunctuationButNotTheJob() {
        assertEquals(LR.fingerprint("Nova, instagram me reels kholo"), LR.fingerprint("instagram me reels kholo"))
        assertEquals(16, LR.fingerprint("instagram me reels kholo").length)
        assertNotEquals(LR.fingerprint("instagram me reels kholo"), LR.fingerprint("instagram me messages kholo"))
        assertEquals("", LR.fingerprint("hi"))
    }

    @Test fun genericButtonsAndOwnerWordsAreLearnedMoneyAndDigitsAreNot() {
        assertEquals("reels", LR.safeLabel("Reels", "instagram me kuch dekho"))
        assertEquals("rahul", LR.safeLabel("Rahul", "whatsapp me rahul ki chat kholo"))
        assertNull(LR.safeLabel("Rahul", "whatsapp kholo"))
        assertNull(LR.safeLabel("Send", "message send karo"))
        assertNull(LR.safeLabel("Pay now", "pay now karo"))
        assertNull(LR.safeLabel("3 new messages", "messages kholo"))
        assertNull(LR.safeLabel("Settings", "settings kholo"))
        assertNull(LR.safeLabel("a", "a b c d e f g"))
    }

    @Test fun onlyPlainStepsAreStepsEverythingElseTaints() {
        assertEquals("open_app", LR.stepFor("open_app", "Instagram", "", "", "x")!!.act)
        assertEquals("instagram", LR.stepFor("open_app", "Instagram", "", "", "x")!!.v)
        assertEquals("down", LR.stepFor("scroll", "", "", "DOWN", "x")!!.v)
        assertEquals("back", LR.stepFor("back", "", "", "", "x")!!.act)
        assertNull(LR.stepFor("type", "", "", "", "x"))
        assertNull(LR.stepFor("long_tap", "", "Reels", "", "reels"))
        assertNull(LR.stepFor("screenshot", "", "", "", "x"))
        assertNull(LR.stepFor("home", "", "", "", "x"))
        assertNull(LR.stepFor("scroll", "", "", "sideways", "x"))
        assertTrue(LR.isSkippable("wait"))
        assertFalse(LR.isSkippable("tap"))
    }

    @Test fun learnableNeedsAnOpenOrTapAndAtMostEightSteps() {
        assertFalse(LR.learnable(emptyList()))
        assertFalse(LR.learnable(listOf(st("scroll", "down"), st("back"))))
        assertTrue(LR.learnable(listOf(st("open_app", "instagram"), st("tap", "reels"))))
        assertFalse(LR.learnable(List(9) { st("tap", "reels") }))
    }

    @Test fun recordKeepsNewestFortyAndCountsSuccess() {
        var rs = emptyList<LR.Route>()
        for (i in 1..45) rs = LR.record(rs, "k%015d".format(i), listOf(st("tap", "reels")), i * 10L)
        assertEquals(LR.MAX_ROUTES, rs.size)
        assertTrue(rs.none { it.key == "k%015d".format(1) })
        val again = LR.record(rs, "k%015d".format(45), listOf(st("tap", "home")), 999L)
        assertEquals(2, again.first { it.key == "k%015d".format(45) }.ok)
        assertEquals("home", again.first { it.key == "k%015d".format(45) }.steps[0].v)
        assertEquals(rs, LR.record(rs, "", listOf(st("tap", "reels")), 5L))
    }

    @Test fun twoFailuresForgetTheRoute() {
        val a = listOf(route("a"))
        val once = LR.markFail(a, "a", 2000L)
        assertEquals(1, once.size)
        assertEquals(1, once[0].fail)
        assertTrue(LR.markFail(once, "a", 3000L).isEmpty())
        assertEquals(a.size, LR.markFail(a, "other", 5L).size)
    }

    @Test fun successClearsFailsAndOldRoutesExpire() {
        val r = route("a", fail = 1)
        assertEquals(0, LR.bumpOk(listOf(r), "a", 5000L)[0].fail)
        assertTrue(LR.usable(route("a", t = 1000L), 1000L + LR.MAX_AGE_MS))
        assertFalse(LR.usable(route("a", t = 1000L), 1001L + LR.MAX_AGE_MS))
        assertFalse(LR.usable(route("a", fail = 2), 1000L))
    }

    @Test fun findIndexNeverGuesses() {
        val items = listOf(0 to "Home", 1 to "Reels", 2 to "Search")
        assertEquals(1, LR.findIndex(items, "reels"))
        assertNull(LR.findIndex(items, "explore"))
        assertNull(LR.findIndex(items + listOf(3 to "reels"), "reels"))
    }

    @Test fun summaryIsEmptyWithoutRoutesAndHonestOtherwise() {
        assertEquals("", LR.summary(emptyList(), false))
        assertTrue(LR.summary(listOf(route("a", 2), route("b", 3)), true).startsWith("2 learned routes (5 steps"))
    }
}
