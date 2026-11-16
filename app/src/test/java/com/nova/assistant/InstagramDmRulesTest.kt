package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class InstagramDmRulesTest {
    @Test fun app_name_message_verb() {
        val r = InstagramDmRules.parse("instagram pe rahul ko kal milte hain bhejo")!!
        assertEquals("rahul", r.name); assertEquals("kal milte hain", r.message)
    }
    @Test fun insta_dm_form() {
        val r = InstagramDmRules.parse("jarvis insta dm priya sharma ko happy birthday likho")!!
        assertEquals("priya sharma", r.name); assertEquals("happy birthday", r.message)
    }
    @Test fun app_after_name() {
        val r = InstagramDmRules.parse("rahul ko instagram pe hello bhai bhejo")!!
        assertEquals("rahul", r.name); assertEquals("hello bhai", r.message)
    }
    @Test fun no_app_word_is_not_ours() { assertNull(InstagramDmRules.parse("rahul ko hello bhai bhejo")) }
    @Test fun whatsapp_and_mail_are_left_alone() {
        assertNull(InstagramDmRules.parse("instagram aur whatsapp pe rahul ko hello bhejo"))
        assertNull(InstagramDmRules.parse("rahul ko instagram link mail karo hello"))
    }
    @Test fun question_is_not_a_send() { assertNull(InstagramDmRules.parse("instagram pe naya message aaya kya")) }
    @Test fun missing_message_or_name() {
        assertNull(InstagramDmRules.parse("instagram pe ko bhejo"))
        assertNull(InstagramDmRules.parse("instagram pe rahul ko bhejo"))
    }
    @Test fun task_names_exactly_one_chat() {
        val t = InstagramDmRules.task(InstagramDmRules.Req("rahul", "hi"))
        assertTrue(t.contains("\"rahul\"")); assertTrue(t.contains("\"hi\"")); assertTrue(t.contains("Sirf isi ek chat"))
    }
}
