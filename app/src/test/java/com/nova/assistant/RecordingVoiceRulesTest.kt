package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class RecordingVoiceRulesTest {
    @Test fun onlyReadOnlyRecordingIntentsMatch() {
        assertEquals(RecordingVoiceRules.Action.COUNT, RecordingVoiceRules.parse("kitni recordings hain")?.action)
        assertEquals(RecordingVoiceRules.Action.COUNT, RecordingVoiceRules.parse("how many recordings are there")?.action)
        assertEquals(RecordingVoiceRules.Action.PLAY_LAST, RecordingVoiceRules.parse("last recording sunao")?.action)
        assertEquals(RecordingVoiceRules.Action.PLAY_LAST, RecordingVoiceRules.parse("play latest recording")?.action)
    }

    @Test fun neverDeletesOrSharesFromVoice() {
        assertNull(RecordingVoiceRules.parse("last recording delete karo"))
        assertNull(RecordingVoiceRules.parse("share recording"))
        assertNull(RecordingVoiceRules.parse("recording band karo"))
        assertNull(RecordingVoiceRules.parse("recording chal rahi hai kya"))
    }
}
