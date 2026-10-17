package com.nova.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayNudgeRulesTest {
    @Test fun firstTimeIsDue() { assertTrue(OverlayNudgeRules.due(0L, 5_000L)) }
    @Test fun notAgainInsideTenMinutes() { assertFalse(OverlayNudgeRules.due(1_000L, 1_000L + 9 * 60_000L)) }
    @Test fun againAfterTenMinutes() { assertTrue(OverlayNudgeRules.due(1_000L, 1_000L + OverlayNudgeRules.RENUDGE_MS)) }
    @Test fun clockMovedBackIsDue() { assertTrue(OverlayNudgeRules.due(9_000_000L, 1_000L)) }
}
