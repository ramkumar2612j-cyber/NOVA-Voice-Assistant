package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiTranscriptTest {
    @Test fun realCommandIsKept() {
        assertEquals("gallery kholo aur paanchvi photo chuno", ListenGuard.cleanCloudTranscript("gallery kholo aur paanchvi photo chuno"))
    }

    @Test fun noiseNotesBecomeEmpty() {
        assertEquals("", ListenGuard.cleanCloudTranscript("[silence]"))
        assertEquals("", ListenGuard.cleanCloudTranscript("(inaudible)"))
        assertEquals("", ListenGuard.cleanCloudTranscript("*background noise*"))
        assertEquals("", ListenGuard.cleanCloudTranscript("No speech"))
        assertEquals("", ListenGuard.cleanCloudTranscript("   "))
    }

    @Test fun transcriptLabelAndQuotesAreCut() {
        assertEquals("papa ko call karo", ListenGuard.cleanCloudTranscript("Transcript: \"papa ko call karo\""))
    }

    @Test fun noiseNoteInsideSentenceIsCut() {
        assertEquals("youtube kholo", ListenGuard.cleanCloudTranscript("youtube kholo [noise]"))
    }

    @Test fun echoedInstructionIsNeverACommand() {
        assertEquals("", ListenGuard.cleanCloudTranscript("Write down exactly what the speaker said, word for word."))
    }

    @Test fun normalBracketWordsAreKept() {
        assertEquals("call mummy (home) now", ListenGuard.cleanCloudTranscript("call mummy (home) now"))
    }
}
