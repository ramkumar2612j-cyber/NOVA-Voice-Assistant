package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class ChatTextTest {
    @Test fun rawJsonNeverReachesUser() {
        assertEquals("Theek hai, kaam ho gaya.", ChatText.clean("{\"status\":\"success\"}"))
        assertEquals("NOVA ready", ChatText.clean("{\"message\":\"NOVA ready\",\"status\":\"success\"}"))
    }
    @Test fun normalTextStaysUntouched() {
        assertEquals("NOVA ready", ChatText.clean("NOVA ready"))
    }
}
