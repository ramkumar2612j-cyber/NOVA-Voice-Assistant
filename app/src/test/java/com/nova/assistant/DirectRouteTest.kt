package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DirectRouteTest {
    private fun k(s: String) = DirectRoute.parse(s)

    @Test fun youtubeSearchPhrases() {
        assertEquals(DirectRoute.Kind.YT_SEARCH, k("nova youtube par arijit singh songs search karo")!!.kind)
        assertEquals("arijit singh songs", k("nova youtube par arijit singh songs search karo")!!.arg)
        assertEquals("lofi music", k("youtube search lofi music")!!.arg)
        assertEquals("cricket highlights", k("search cricket highlights on youtube")!!.arg)
        assertEquals("python tutorial", k("youtube pe python tutorial dhundo")!!.arg)
    }

    @Test fun mapsPhrases() {
        assertEquals(DirectRoute.Kind.MAPS_NAV, k("navigate to india gate")!!.kind)
        assertEquals("jaipur", k("nova jaipur ka rasta batao")!!.arg)
        assertEquals("airport", k("mujhe airport jane ka rasta batao")!!.arg)
        assertEquals("connaught place", k("take me to connaught place")!!.arg)
        assertEquals("petrol pump near me", k("petrol pump near me dhundo")!!.arg)
        assertEquals("atm near me", k("nearby atm dikhao")!!.arg)
        assertEquals(DirectRoute.Kind.MAPS_SHOW, k("show hospital on maps")!!.kind)
    }

    @Test fun playOpenAndChainedJobsStayWithTheAgent() {
        assertNull(k("youtube kholo"))
        assertNull(k("youtube par arijit chalao"))
        assertNull(k("youtube par x search karo aur pehla video chalao"))
        assertNull(k("youtube search karo"))
    }

    @Test fun negationAndOtherCommandsAreNotTaken() {
        assertNull(k("navigate mat karo"))
        assertNull(k("call mom"))
        assertNull(k("whatsapp rahul hello"))
        assertNull(k("alarm 7 baje"))
        assertNull(k("music chalao"))
    }

    @Test fun urlsAreEncodedByTheCaller() {
        val e = { s: String -> s.replace(" ", "+") }
        assertEquals("https://www.youtube.com/results?search_query=a+b", DirectRoute.youtubeUrl("a b", e))
        assertNotNull(DirectRoute.mapsNavUrl("x", e))
        assertEquals("https://www.google.com/maps/search/?api=1&query=atm+near+me", DirectRoute.mapsShowUrl("atm near me", e))
    }
}
