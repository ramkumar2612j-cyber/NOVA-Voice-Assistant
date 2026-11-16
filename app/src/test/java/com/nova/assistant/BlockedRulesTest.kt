package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedRulesTest {
    private fun snap(acc: Boolean = true, battery: Boolean = true, mic: Boolean = true, overlay: Boolean = true, notif: Boolean = true,
                     notifAccess: Boolean = true, brand: Boolean = false) =
        BlockedRules.Snap(mic, notif, overlay, acc, notifAccess, battery, true, true, true, true, true, true, brand)

    @Test fun allOnGivesNoItemsAndAnHonestLine() {
        assertTrue(BlockedRules.items(snap()).isEmpty())
        assertTrue(BlockedRules.reply(emptyList(), false).contains("Koi permission ya switch band nahi"))
        assertTrue(BlockedRules.reply(emptyList(), true).contains("No permission or switch is off"))
    }

    @Test fun importantOnesComeFirstInFixedOrder() {
        val l = BlockedRules.items(snap(acc = false, battery = false, mic = false, notifAccess = false))
        assertEquals(listOf("acc", "battery", "mic", "notifAccess"), l.map { it.id })
        assertEquals("must", l[0].level)
        assertEquals("nice", l[3].level)
    }

    @Test fun autostartIsOnlyCheckNeverOffAndOnlyForBrandsWithAScreen() {
        assertTrue(BlockedRules.items(snap(brand = false)).none { it.id == "autostart" })
        val l = BlockedRules.items(snap(brand = true))
        assertEquals(1, l.size)
        assertEquals("check", l[0].level)
        val r = BlockedRules.reply(l, false)
        assertTrue(r.contains("Koi permission ya switch band nahi"))
        assertTrue(r.contains("Autostart Android nahi batata"))
    }

    @Test fun replyCountsAndNamesAtMostFour() {
        val s = BlockedRules.Snap(false, false, false, false, false, false, false, false, false, false, false, false, false)
        val r = BlockedRules.reply(BlockedRules.items(s), true)
        assertTrue(r.startsWith("12 things are off"))
        assertTrue(r.contains("and more"))
    }

    @Test fun jsonIsEscapedAndHasTheFields() {
        val j = BlockedRules.json(BlockedRules.items(snap(acc = false)), false)
        assertTrue(j.startsWith("{\"items\":[{\"id\":\"acc\""))
        assertTrue(j.contains("\"level\":\"must\""))
        assertFalse(j.contains("\n"))
        assertEquals("{\"items\":[]}", BlockedRules.json(emptyList(), true))
    }
}
