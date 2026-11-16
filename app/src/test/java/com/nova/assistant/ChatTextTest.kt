package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class ChatTextTest {
    @Test fun rawJsonNeverReachesUser() {
        assertEquals("Theek hai, kaam ho gaya.", ChatText.clean("{\"status\":\"success\"}"))
        assertEquals("JARVIS ready", ChatText.clean("{\"message\":\"JARVIS ready\",\"status\":\"success\"}"))
    }
    @Test fun normalTextStaysUntouched() {
        assertEquals("JARVIS ready", ChatText.clean("JARVIS ready"))
    }
}
