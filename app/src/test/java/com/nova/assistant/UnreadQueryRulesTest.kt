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
        val t = UnreadQueryRules.reply(listOf(msg("Rahul", "kal milte hain", ts = 1), msg("Bank", "", ts = 2, hidden = true)), UnreadQueryRules.Req("WhatsApp"), false)
        assertTrue(t.contains("2 naye message"))
        assertTrue(t.contains("Rahul ne kaha: kal milte hain"))
        assertTrue(t.contains("nahi padhunga"))
    }
    @Test fun many_messages_are_counted_by_sender() {
        val l = (1..5).map { msg(if (it <= 3) "Mummy" else "Papa", "hi", ts = it.toLong()) }
        val t = UnreadQueryRules.reply(l, UnreadQueryRules.Req(null), true)
        assertTrue(t.contains("5 new messages"))
        assertTrue(t.contains("Mummy 3"))
    }
    @Test fun other_app_is_filtered_out() {
        val t = UnreadQueryRules.reply(listOf(msg("Z", "x", app = "Telegram")), UnreadQueryRules.Req("WhatsApp"), false)
        assertTrue(t.contains("nahi dikh"))
    }
}
