package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** v7.06: more known apps resolve to their own Play package, so "<app> download karo" opens that exact page. Pure text, no Android. */
class MoreAppsTest {
    private fun pkg(s: String) = OpenWebRules.parseDownload(s)?.pkg

    @Test fun newKnownAppsResolve() {
        assertEquals("com.niksoftware.snapseed", pkg("snapseed download karo"))
        assertEquals("com.lemon.lvoverseas", pkg("capcut install karo"))
        assertEquals("com.king.candycrushsaga", pkg("candy crush saga download karo"))
        assertEquals("com.king.candycrushsaga", pkg("download candy crush"))
        assertEquals("com.google.android.apps.photos", pkg("google photos download karo"))
        assertEquals("com.supercell.clashofclans", pkg("clash of clans download kar do"))
        assertEquals("org.videolan.vlc", pkg("vlc download karo"))
    }

    @Test fun oldAppsStillResolve() {
        assertEquals("com.whatsapp", pkg("whatsapp download karo"))
        assertEquals("com.instagram.android", pkg("instagram download karo"))
    }

    @Test fun unknownAppStillOnlyGetsSearch() {
        val a = OpenWebRules.parseDownload("xyz game download karo")
        assertNotNull(a)
        assertEquals(null, a!!.pkg)
    }
}
