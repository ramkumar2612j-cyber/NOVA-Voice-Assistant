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

    // v7.152: the popup theme question ("popup light hai ya dark?") is a STATUS question, not an order.
    @Test fun popupThemeQuestionIsAStatusQuestion() {
        assertEquals("popuptheme", FeatureVoice.key("popup light hai ya dark"))
        assertEquals("popuptheme", FeatureVoice.key("popup ka rang kya hai"))
        assertEquals("popuptheme", FeatureVoice.key("popup kaun sa theme hai batao"))
        assertNull(FeatureVoice.key("light popup karo"))          // order: HudTheme switches it, FeatureVoice never does
        assertNull(FeatureVoice.key("popup ki permission on hai")) // a permission question is not the theme question
    }

    @Test fun popupThemeReplyNamesTheStateAndTheNextTap() {
        val light = FeatureVoice.reply("popuptheme", FeatureVoice.State(true), false)
        assertTrue(light.contains("LIGHT") && light.contains("nova dark popup karo"))
        val dark = FeatureVoice.reply("popuptheme", FeatureVoice.State(false), false)
        assertTrue(dark.contains("DARK") && dark.contains("nova light popup karo"))
    }
}
