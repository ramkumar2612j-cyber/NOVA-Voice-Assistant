package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Part 6A: background audio recording. Pure rules only. */
class RecordRulesTest {
    private fun a(s: String) = RecordRules.parse(s)?.action
    private fun lock(s: String) = RecordRules.parse(s)?.alsoLock

    @Test fun ownersExactSentenceStartsAndLocks() {
        assertEquals(RecordRules.Action.START, a("background me recording on karo aur mobile off kar do"))
        assertEquals(true, lock("background me recording on karo aur mobile off kar do"))
        assertEquals(RecordRules.Action.START, a("mobile off kar do aur background me recording on karo"))
        assertEquals(true, lock("mobile off kar do aur background me recording on karo"))
    }

    @Test fun startPhrases() {
        for (s in listOf("nova recording shuru karo", "recording on karo", "awaaz record karo", "start recording", "record my voice")) {
            assertEquals(s, RecordRules.Action.START, a(s))
            assertEquals(s, false, lock(s))
        }
        assertEquals(true, lock("recording chalu karo phone lock kar do"))
    }

    @Test fun stopPhrases() {
        for (s in listOf("recording band karo", "recording off karo", "stop recording", "recording rok do", "recording pura karo", "phone ki recording band karo")) {
            assertEquals(s, RecordRules.Action.STOP, a(s))
        }
        assertEquals(true, lock("recording band karo aur phone ko lock karo"))
    }

    @Test fun statusPhrases() {
        for (s in listOf("recording chal rahi hai kya", "recording status", "kya recording on hai", "kitni der se recording ho rahi hai")) {
            assertEquals(s, RecordRules.Action.STATUS, a(s))
            assertEquals(false, lock(s))
        }
    }

    @Test fun otherFeaturesAndUnclearSentencesNeverMatch() {
        for (s in listOf(
            "screen recording shuru karo", "camera se video record karo", "recording sunao", "recording delete karo", "call recording on karo",
            "recording band mat karo", "dont record", "recording kaise hoti hai", "recording kahan hai", "recording", "background recording",
            "torch jala do", "mobile off kar do", "recording chalu karo aur band karo", "recording share karo", ""
        )) assertNull(s, RecordRules.parse(s))
    }

    @Test fun veryLongSentenceIsIgnored() {
        assertNull(RecordRules.parse("recording on karo " + "bla ".repeat(40)))
    }

    @Test fun limits() {
        assertNull(RecordRules.autoStopReason(1000L, 500L * 1024 * 1024))
        assertEquals("max", RecordRules.autoStopReason(RecordRules.MAX_MS, 500L * 1024 * 1024))
        assertEquals("space", RecordRules.autoStopReason(1000L, RecordRules.STOP_FREE - 1))
        assertEquals("low_space", RecordRules.startCheck(RecordRules.MIN_START_FREE - 1))
        assertNull(RecordRules.startCheck(RecordRules.MIN_START_FREE))
        assertTrue(RecordRules.STOP_FREE < RecordRules.MIN_START_FREE)
    }

    @Test fun sizeAndParts() {
        assertEquals(14_400_000L, RecordRules.estimateBytes(3_600_000L))
        assertEquals(1, RecordRules.partOf(0L))
        assertEquals(1, RecordRules.partOf(RecordRules.SEGMENT_MS - 1))
        assertEquals(2, RecordRules.partOf(RecordRules.SEGMENT_MS))
        assertEquals(6, RecordRules.partOf(RecordRules.MAX_MS - 1))
        assertEquals(1_000_000L, RecordRules.ptsUs(16000L))
        assertEquals(500_000L, RecordRules.ptsUs(8000L))
    }

    @Test fun fileNames() {
        val utc = ZoneId.of("UTC")
        assertEquals("NOVA_audio_20261011_153012.m4a", RecordRules.segName(1791732612000L, utc, 1))
        assertEquals("NOVA_audio_20261011_153012_p2.m4a", RecordRules.segName(1791732612000L, utc, 2))
    }

    @Test fun durationText() {
        assertEquals("5 sec", RecordRules.fmtDur(5000L, false))
        assertEquals("2 min 3 sec", RecordRules.fmtDur(123_000L, false))
        assertEquals("1 ghanta 5 min", RecordRules.fmtDur(3_900_000L, false))
        assertEquals("1 h 5 min", RecordRules.fmtDur(3_900_000L, true))
        assertEquals("0 sec", RecordRules.fmtDur(-5L, true))
    }

    @Test fun messagesExistInBothLanguagesAndNeverClaimMoreThanTrue() {
        for (code in listOf("started", "already", "not_running", "status_on", "no_nova", "low_space", "start_failed", "saved", "saved_none", "auto_max", "auto_space", "auto_error", "lock_ok", "lock_fail")) {
            assertTrue(code, RecordRules.text(code, false, "x").isNotBlank())
            assertTrue(code, RecordRules.text(code, true, "x").isNotBlank())
        }
        assertTrue(RecordRules.text("started", false).contains("STOP"))
        assertTrue(RecordRules.text("lock_fail", true, "needs Accessibility").contains("needs Accessibility"))
        assertNotNull(RecordRules.whereMedia(2, true))
        assertTrue(RecordRules.whereApp(1, "/p", false).contains("/p"))
    }

    @Test fun recordingIsAlwaysSpokenUnlessSilentTurn() {
        assertTrue(Logic.speakReply(true, false, "rec"))
        assertFalse(SilentRules.present(false, false, listOf("rec"), "Recording shuru", "done").speak)
    }
}

class RecordWriterWarnTest {
    @org.junit.Test fun writerWarningTextExistsInBothLanguages() {
        org.junit.Assert.assertTrue(RecordRules.text("writer_err", true, "").contains("part-way"))
        org.junit.Assert.assertTrue(RecordRules.text("writer_err", false, "").contains("beech mein"))
    }
    @org.junit.Test fun savedKeepsOldConstructorAndDefaultsToNoError() {
        val s = RecordSession.Saved(1, 1, "/x", 1000L, 0)
        org.junit.Assert.assertEquals("", s.writerError)
        org.junit.Assert.assertEquals("boom", RecordSession.Saved(1, 0, "/x", 1000L, 0, "boom").writerError)
    }
}
