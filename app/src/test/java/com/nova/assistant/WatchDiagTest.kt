package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDiagTest {
    @Test fun noPressSaysSignalNeverArrived() {
        WatchDiag.clear()
        assertNull(WatchDiag.verdict(WatchDiag.all()))
        assertTrue(WatchDiag.reply(emptyList(), false).contains("nahi aaya"))
        assertTrue(WatchDiag.reply(emptyList(), true).contains("has not received"))
    }

    @Test fun verdictIsWhereTheLastPressEnded() {
        WatchDiag.clear()
        WatchDiag.add(WatchDiag.BUTTON, 1); WatchDiag.add(WatchDiag.BUSY, 2)
        assertEquals(WatchDiag.BUSY, WatchDiag.verdict(WatchDiag.all()))
        WatchDiag.add(WatchDiag.BUTTON, 3); WatchDiag.add(WatchDiag.MIC_PHONE, 4)
        assertEquals(WatchDiag.MIC_PHONE, WatchDiag.verdict(WatchDiag.all()))
        assertTrue(WatchDiag.reply(WatchDiag.all(), true).contains("phone mic"))
        assertTrue(WatchDiag.reply(WatchDiag.all(), false).contains("2 baar"))
    }

    @Test fun buttonOnlyIsReportedAsNothingAfter() {
        WatchDiag.clear(); WatchDiag.add(WatchDiag.BUTTON, 1)
        assertTrue(WatchDiag.reply(WatchDiag.all(), true).contains("nothing after"))
    }

    @Test fun keepsOnlyTheLastSix() {
        WatchDiag.clear()
        for (i in 1..10) WatchDiag.add(WatchDiag.BUTTON, i.toLong())
        assertEquals(6, WatchDiag.all().size)
        assertEquals(5L, WatchDiag.all().first().at)
    }
}
