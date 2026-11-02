package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class ContextMemoryTest {
    @Test fun remembersOnlyBriefSafeContext() {
        ContextMemory.clear()
        ContextMemory.remember(Logic.Cmd("torch_on"), 1000L)
        assertEquals(Logic.Cmd("torch_on"), ContextMemory.resolve("wahi dobara", 1001L))
        assertNull(ContextMemory.resolve("wahi dobara", 1000L + ContextMemory.TTL_MS + 1L))
    }

    @Test fun riskyActionsClearPriorContext() {
        ContextMemory.clear()
        ContextMemory.remember(Logic.Cmd("volume", "up"), 10L)
        ContextMemory.remember(Logic.Cmd("call", "mummy"), 11L)
        assertNull(ContextMemory.snapshot(11L))
        ContextMemory.remember(Logic.Cmd("sms", "mummy"), 12L)
        assertNull(ContextMemory.resolve("wahi dobara", 13L))
    }

    @Test fun safeFollowUpsMapToTheNormalCommandRouter() {
        ContextMemory.clear()
        ContextMemory.remember(Logic.Cmd("volume", "up"), 100L)
        assertEquals(Logic.Cmd("volume", "up", 0), ContextMemory.resolve("thoda aur tez", 101L))
        assertEquals(Logic.Cmd("volume", "down", 0), ContextMemory.resolve("aur kam", 102L))
        ContextMemory.clear()
        ContextMemory.remember(Logic.Cmd("open_app", "chrome"), 200L)
        assertEquals(Logic.Cmd("global", "back"), ContextMemory.resolve("isko band karo", 201L))
    }
}
