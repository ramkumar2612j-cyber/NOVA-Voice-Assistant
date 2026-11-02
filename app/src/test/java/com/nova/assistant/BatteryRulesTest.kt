package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryRulesTest {
    @Test fun alertOnceBelowFifty() {
        var s = BatteryRules.State()
        var d = BatteryRules.decide(s, 52, false); assertNull(d.alertStep); s = d.state
        d = BatteryRules.decide(s, 49, false); assertEquals(50, d.alertStep); s = d.state
        d = BatteryRules.decide(s, 48, false); assertNull(d.alertStep); s = d.state
        d = BatteryRules.decide(s, 39, false); assertEquals(40, d.alertStep)
    }
    @Test fun jumpAnnouncesOnlyLowestStep() {
        val d = BatteryRules.decide(BatteryRules.State(), 38, false)
        assertEquals(40, d.alertStep)
    }
    @Test fun chargingNeverAlertsAndResets() {
        var d = BatteryRules.decide(BatteryRules.State(), 4, true)
        assertNull(d.alertStep); assertFalse(d.sos)
        assertEquals(BatteryRules.State(), d.state)
    }
    @Test fun sosOnceAtFive() {
        var d = BatteryRules.decide(BatteryRules.State(), 5, false)
        assertTrue(d.sos)
        d = BatteryRules.decide(d.state, 4, false)
        assertFalse(d.sos)
    }
    @Test fun sosNotAtSix() {
        assertFalse(BatteryRules.decide(BatteryRules.State(), 6, false).sos)
    }
    @Test fun sosRearmsAfterRecovery() {
        var d = BatteryRules.decide(BatteryRules.State(), 5, false)
        d = BatteryRules.decide(d.state, 20, false)
        d = BatteryRules.decide(d.state, 5, false)
        assertTrue(d.sos)
    }
}
