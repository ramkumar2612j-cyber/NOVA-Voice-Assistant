package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublishTargetTest {
    @Test fun vercel() {
        assertEquals(PublishTarget.Target.VERCEL, PublishTarget.parse("nova website vercel par publish karo"))
        assertEquals(PublishTarget.Target.VERCEL, PublishTarget.parse("isko vercel par deploy karo"))
    }
    @Test fun lovable() { assertEquals(PublishTarget.Target.LOVABLE, PublishTarget.parse("nova website lovable par daalo")) }
    @Test fun notThese() {
        assertNull(PublishTarget.parse("nova website publish karo"))
        assertNull(PublishTarget.parse("vercel par website banao"))
        assertNull(PublishTarget.parse("vercel kholo"))
        assertNull(PublishTarget.parse("vercel deployments dikhao"))
        assertNull(PublishTarget.parse(""))
    }
}
