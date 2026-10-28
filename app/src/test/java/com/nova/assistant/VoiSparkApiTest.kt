package com.nova.assistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiSparkApiTest {
    private val id = VoiSparkApi.DEFAULT_VOICE_ID

    @Test fun voiceIdFormat() {
        assertTrue(VoiSparkApi.validVoiceId(id))
        assertFalse(VoiSparkApi.validVoiceId("ultron"))
        assertFalse(VoiSparkApi.validVoiceId(id + "/x"))
    }

    @Test fun generateBodyUsesOnlyDocumentedFields() {
        val o = JSONObject(VoiSparkApi.generateBody(id, "नमस्ते"))
        assertEquals(setOf("voice_id", "model_id", "text", "sync"), o.keys().asSequence().toSet())
        assertEquals("fish-audio/drama-3", o.getString("model_id"))
        assertFalse(o.getBoolean("sync"))
    }

    @Test fun businessErrorOnHttp200IsNotOk() {
        val e = VoiSparkApi.parseEnvelope(200, """{"code":1001,"message":"insufficient credits","data":null}""")
        assertFalse(e.ok)
        assertTrue(VoiSparkApi.reason(200, e).contains("1001"))
        assertTrue(VoiSparkApi.parseEnvelope(200, """{"code":0,"message":"ok","data":{"task_id":"abc"}}""").ok)
        assertEquals(-1, VoiSparkApi.parseEnvelope(200, "<html>").code)
        assertEquals(-1, VoiSparkApi.parseEnvelope(200, """{"x":1}""").code)
    }

    @Test fun taskIdIsChecked() {
        assertEquals("abc-1", VoiSparkApi.taskId(VoiSparkApi.parseEnvelope(200, """{"code":0,"message":"","data":{"task_id":"abc-1"}}""")))
        assertNull(VoiSparkApi.taskId(VoiSparkApi.parseEnvelope(200, """{"code":0,"message":"","data":{"task_id":"a/../b"}}""")))
        assertNull(VoiSparkApi.taskId(VoiSparkApi.parseEnvelope(200, """{"code":0,"message":"","data":null}""")))
    }

    @Test fun costAndLimits() {
        assertEquals(240 * 4, VoiSparkApi.creditsFor("x".repeat(240)))
        assertTrue(VoiSparkApi.tooLong("x".repeat(241), 240))
        assertFalse(VoiSparkApi.tooLong("x".repeat(240), 240))
        assertEquals(VoiSparkApi.MIN_MAX_CHARS, VoiSparkApi.clampMaxChars(1))
    }

    @Test fun findAudioOnlyHttpsLinks() {
        val d = JSONObject("""{"status":"done","result":{"audio_url":"https://cdn.example.com/a.mp3"}}""")
        assertEquals("https://cdn.example.com/a.mp3", VoiSparkApi.findAudio(d)?.url)
        assertNull(VoiSparkApi.findAudio(JSONObject("""{"audio_url":"http://x/a.mp3"}""")))
        assertNull(VoiSparkApi.findAudio(JSONObject("""{"status":"running"}""")))
    }

    @Test fun shapeNeverShowsValues() {
        val d = JSONObject("""{"task_id":"SECRET123","audio_url":"https://x/y.mp3","n":5}""")
        val s = VoiSparkApi.shape(d)
        assertFalse(s.contains("SECRET123"))
        assertFalse(s.contains("x/y.mp3"))
        assertTrue(s.contains("audio_url:url"))
    }

    @Test fun voiceListMatchNeedsUltronName() {
        val list = JSONObject("""{"voices":[{"id":"$id","name":"Ultron"},{"id":"other","name":"Bob"}]}""")
        val h = VoiSparkApi.findVoiceInList(list, id)
        assertTrue(h.found); assertTrue(h.isUltron)
        val h2 = VoiSparkApi.findVoiceInList(JSONObject("""{"voices":[{"id":"$id","name":"Someone"}]}"""), id)
        assertTrue(h2.found); assertFalse(h2.isUltron)
        assertFalse(VoiSparkApi.findVoiceInList(JSONObject("""{"voices":[]}"""), id).found)
        assertNotNull(h.names)
    }
}
