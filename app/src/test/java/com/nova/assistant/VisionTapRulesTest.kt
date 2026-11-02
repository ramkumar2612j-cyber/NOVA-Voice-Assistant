package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionTapRulesTest {
    private val own = "com.nova.assistant"
    private val app = "com.example.canvasapp"

    @Test fun weakScreenMeansAlmostNothingTappable() {
        assertTrue(VisionTapRules.weakScreen(0))
        assertTrue(VisionTapRules.weakScreen(2))
        assertFalse(VisionTapRules.weakScreen(3))
    }

    @Test fun perMilleBecomesAPixelAlwaysInsideTheScreen() {
        assertEquals(540, VisionTapRules.toPixel(500, 1080))
        assertEquals(1, VisionTapRules.toPixel(0, 1080))
        assertEquals(1078, VisionTapRules.toPixel(1000, 1080))
        assertEquals(1078, VisionTapRules.toPixel(5000, 1080))
        assertEquals(1, VisionTapRules.toPixel(-40, 1080))
        assertEquals(0, VisionTapRules.toPixel(500, 2))
    }

    @Test fun edgesAreNotTapped() {
        assertTrue(VisionTapRules.insideSafeArea(500, 500))
        assertTrue(VisionTapRules.insideSafeArea(10, 990))
        assertFalse(VisionTapRules.insideSafeArea(5, 500))
        assertFalse(VisionTapRules.insideSafeArea(500, 995))
    }

    @Test fun blockOrderAndGameRule() {
        assertNull(VisionTapRules.block(true, true, false, 0))
        assertEquals("is step me screen ki picture nahi hai", VisionTapRules.block(false, true, false, 0))
        assertTrue(VisionTapRules.block(true, false, false, 0)!!.contains("normal items"))
        assertTrue(VisionTapRules.block(true, true, true, 0)!!.startsWith("game"))
        assertTrue(VisionTapRules.block(true, true, null, 0)!!.startsWith("game"))
        assertTrue(VisionTapRules.block(true, true, false, VisionTapRules.MAX_TAPS)!!.contains("bahut"))
        assertTrue(VisionTapRules.block(false, true, false, 0, en = true)!!.contains("no picture"))
    }

    @Test fun hintDescribesTheActionAndForbidsPlayingGames() {
        val h = VisionTapRules.hint()
        assertTrue(h.contains("tap_xy"))
        assertTrue(h.contains("0 to 1000"))
        assertTrue(h.contains("GAME"))
    }

    @Test fun parseReadsTheVisionTapAndRejectsBadOnes() {
        val a = AgentRules.parse("{\"act\":\"tap_xy\",\"x\":420,\"y\":\"610\",\"text\":\"Start\"}")
        assertNotNull(a)
        assertEquals(420, a!!.x)
        assertEquals(610, a.y)
        assertEquals("Start", a.text)
        assertEquals(500, AgentRules.parse("{\"act\":\"tap_xy\",\"x\":0.5,\"y\":0.5,\"text\":\"Play\"}")!!.x)
        assertNull(AgentRules.parse("{\"act\":\"tap_xy\",\"x\":420,\"y\":610}"))
        assertNull(AgentRules.parse("{\"act\":\"tap_xy\",\"x\":1500,\"y\":610,\"text\":\"Start\"}"))
        assertNull(AgentRules.parse("{\"act\":\"tap_xy\",\"y\":610,\"text\":\"Start\"}"))
    }

    @Test fun checkVetsTheNamedTarget() {
        fun v(label: String, x: Int = 500, y: Int = 500) = AgentRules.check(AgentRules.Action("tap_xy", text = label, x = x, y = y), null, app, own).v
        assertEquals(AgentRules.Verdict.OK, v("Start"))
        assertEquals(AgentRules.Verdict.DENY, v(""))
        assertEquals(AgentRules.Verdict.DENY, v("Start", x = 3))
        assertEquals(AgentRules.Verdict.DENY, v("Pay now"))
        assertEquals(AgentRules.Verdict.DENY, v("Enter password"))
        assertEquals(AgentRules.Verdict.CONFIRM, v("Delete"))
    }

    @Test fun aVisionTapIsNeverALearnedStep() {
        assertNull(LearnedRouteRules.stepFor("tap_xy", "", "Start", "", "start karo"))
    }
}
