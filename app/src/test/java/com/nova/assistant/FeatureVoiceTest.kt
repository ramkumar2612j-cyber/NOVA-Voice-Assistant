package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureVoiceTest {
    @Test fun statusQuestionsAreRecognised() {
        assertEquals("autoreply", FeatureVoice.key("auto reply ka status batao"))
        assertEquals("autoreply", FeatureVoice.key("auto-reply on hai ya off"))
        assertEquals("lockguard", FeatureVoice.key("lock guard chalu hai kya"))
        assertEquals("offlinepack", FeatureVoice.key("offline pack install hai kya, batao"))
        assertEquals("apilimits", FeatureVoice.key("api limit kitni bachi"))
        assertEquals("routines", FeatureVoice.key("meri routines ka status batao"))
        assertEquals("batteryalert", FeatureVoice.key("battery alert on hai ya off"))
    }
    @Test fun ordersAreNeverCaptured() {
        assertNull(FeatureVoice.key("auto reply on karo"))
        assertNull(FeatureVoice.key("lock guard band karo"))
        assertNull(FeatureVoice.key("routine delete karo"))
        assertNull(FeatureVoice.key("mummy ko call karo auto reply status"))
    }
    @Test fun plainCommandsAndChatAreNotCaptured() {
        assertNull(FeatureVoice.key("battery kitni hai"))
        assertNull(FeatureVoice.key("torch on karo"))
        assertNull(FeatureVoice.key("batao mausam kaisa hai"))
        assertNull(FeatureVoice.key(""))
    }
    @Test fun repliesAreHonestAboutState() {
        assertTrue(FeatureVoice.reply("autoreply", FeatureVoice.State(true), false).contains("ON"))
        assertTrue(FeatureVoice.reply("autoreply", FeatureVoice.State(false), false).contains("OFF"))
        assertTrue(FeatureVoice.reply("apilimits", FeatureVoice.State(true, "Gemini"), true).contains("Gemini"))
    }
}
