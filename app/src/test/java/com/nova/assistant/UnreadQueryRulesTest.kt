package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class UnreadQueryRulesTest {
    private fun msg(sender: String, text: String, app: String = "WhatsApp", ts: Long = 1, hidden: Boolean = false) =
        Driving.Msg("k$sender$ts", "com.whatsapp", app, sender, text, ts, false, hidden)

    @Test fun owner_sentence_is_a_question() {
        val r = UnreadQueryRules.parse("nova aaj whatsapp pe koi new message to nhi aya h")
        assertNotNull(r); assertEquals("WhatsApp", r!!.app)
    }
    @Test fun more_phrasings() {
        for (s in listOf("whatsapp par koi naya message aaya", "naye messages batao", "kya mere koi message aaye hain", "kisi ne message bheja kya",
            "mera koi unread message hai", "new mess aaya kya", "मेरे व्हाट्सएप पर कोई नया मैसेज आया है", "telegram pe koi message aaya")) {
            assertNotNull(s, UnreadQueryRules.parse(s))
        }
    }
    @Test fun commands_and_other_things_are_not_questions() {
        for (s in listOf("mummy ko whatsapp message bhejo", "whatsapp par hello likho", "new message likh do", "torch jala do", "gmail me naya mail aaya",
            "whatsapp kholo", "message delete karo", "kya whatsapp chalu hai", "whatsapp pe naya status dekho", "papa ko call karo", "")) {
            assertNull(s, UnreadQueryRules.parse(s))
        }
    }
    @Test fun empty_inbox_is_honest() {
        val t = UnreadQueryRules.reply(emptyList(), UnreadQueryRules.Req("WhatsApp"), false)
        assertTrue(t.contains("nahi dikh"))
    }
    @Test fun few_messages_are_read_but_codes_are_not() {
        val t = UnreadQueryRules.reply(listOf(msg("Rahul", "kal milte hain", ts = 1), msg("Bank", "", ts = 2, hidden = true)), UnreadQueryRules.Req("WhatsApp", UnreadQueryRules.MODE_READ), false)
        assertTrue(t.contains("2 naye message"))
        assertTrue(t.contains("Rahul ne kaha: kal milte hain"))
        assertTrue(t.contains("nahi padhunga"))
    }
    @Test fun many_messages_are_counted_by_sender() {
        val l = (1..5).map { msg(if (it <= 3) "Mummy" else "Papa", "hi", ts = it.toLong()) }
        val t = UnreadQueryRules.reply(l, UnreadQueryRules.Req(null), true)
        assertTrue(t.contains("5 new messages"))
        assertTrue(t.contains("Mummy (3)"))   // v7.175: en wording is "Name (count)"
    }
    @Test fun other_app_is_filtered_out() {
        val t = UnreadQueryRules.reply(listOf(msg("Z", "x", app = "Telegram")), UnreadQueryRules.Req("WhatsApp"), false)
        assertTrue(t.contains("nahi dikh"))
    }
    @Test fun default_answer_has_names_but_never_the_text() {   // v7.175
        val t = UnreadQueryRules.reply(listOf(msg("Rahul", "kal milte hain", ts = 1), msg("Mummy", "khana khaya?", ts = 2)), UnreadQueryRules.Req("WhatsApp"), false)
        assertTrue(t.contains("2 naye message")); assertTrue(t.contains("Rahul")); assertTrue(t.contains("Mummy"))
        assertFalse(t.contains("kal milte hain")); assertFalse(t.contains("khana khaya"))
        assertTrue(t.contains("dikhao"))
    }
    @Test fun show_mode_card_has_the_text() {
        val l = listOf(msg("Rahul", "kal milte hain", ts = 1))
        val card = UnreadQueryRules.screenText(l, UnreadQueryRules.Req("WhatsApp", UnreadQueryRules.MODE_SHOW), false)
        assertTrue(card.contains("Rahul")); assertTrue(card.contains("kal milte hain"))
        assertFalse(UnreadQueryRules.reply(l, UnreadQueryRules.Req("WhatsApp", UnreadQueryRules.MODE_SHOW), false).contains("kal milte hain"))
    }
    @Test fun modes_and_instagram_are_parsed() {
        assertEquals("Instagram", UnreadQueryRules.parse("instagram pe koi naya message aaya kya")!!.app)
        assertEquals(UnreadQueryRules.MODE_NAMES, UnreadQueryRules.parse("whatsapp pe naya message aaya")!!.mode)
        assertEquals(UnreadQueryRules.MODE_SHOW, UnreadQueryRules.parse("whatsapp ke naye message dikhao")!!.mode)
        assertEquals(UnreadQueryRules.MODE_READ, UnreadQueryRules.parse("whatsapp ke naye message padho")!!.mode)
        assertNull(UnreadQueryRules.parse("instagram kholo"))
    }
    @Test fun instagram_likes_are_not_messages() {
        assertTrue(UnreadQueryRules.isInstagramDm("msg", null, "Rahul", "hi"))
        assertFalse(UnreadQueryRules.isInstagramDm("social", null, "Instagram", "rahul liked your photo"))
        assertFalse(UnreadQueryRules.isInstagramDm(null, null, "rahul", "started following you"))
        assertTrue(UnreadQueryRules.isInstagramDm(null, "android.app.Notification\$MessagingStyle", "Rahul", "kal milte hain"))
    }
}
