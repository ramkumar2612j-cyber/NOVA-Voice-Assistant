package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class MatchLiveRulesTest {
    @Test fun owner_phrases_start() {
        for (s in listOf("nova is match me kaun jeetega", "nova predict karo is match ko", "match search karo aur batao kon jeetega",
            "nova live match analysis karo", "ye match kaun jitega", "is match ka prediction batao", "cricket match analysis karo"))
            assertEquals(s, MatchLiveRules.Ask.START, MatchLiveRules.parse(s)?.ask)
    }
    @Test fun stop_phrases() {
        for (s in listOf("nova match analysis band karo", "live analysis ruko", "match prediction band"))
            assertEquals(s, MatchLiveRules.Ask.STOP, MatchLiveRules.parse(s)?.ask)
    }
    @Test fun other_things_are_left_alone() {
        for (s in listOf("ye kon si movie hai", "mummy ko call karo", "whatsapp pe rahul ko match ka score bhejo", "gaana chalao", "alarm laga do", "naya message aaya kya", "stop"))
            assertNull(s, MatchLiveRules.parse(s))
    }
    @Test fun quiet_words_turn_voice_off() {
        assertFalse(MatchLiveRules.parse("nova match analysis chup karke predict karo")!!.speak)
        assertFalse(MatchLiveRules.parse("is match me kaun jeetega sirf likh ke batao")!!.speak)
        assertTrue(MatchLiveRules.parse("is match me kaun jeetega")!!.speak)
    }
    private val J = """{"safety":"ok","is_match":true,"sport":"cricket","team_a":"India","team_b":"Australia","tournament":"T20 World Cup","score_a":"145/3 (16.2)","score_b":"","status":"Target 180","confidence":"high"}"""
    @Test fun snapshot_is_read() {
        val s = MatchLiveRules.parseSnapshot("```json\n$J\n```")!!
        assertTrue(MatchLiveRules.identified(s)); assertEquals("India vs Australia", s.title()); assertTrue(s.scoreLine().contains("145/3"))
    }
    @Test fun private_or_no_match_or_low_is_not_identified() {
        assertEquals("private", MatchLiveRules.parseSnapshot("""{"safety":"private"}""")!!.safety)
        assertFalse(MatchLiveRules.identified(MatchLiveRules.parseSnapshot("""{"safety":"ok","is_match":false}""")))
        assertFalse(MatchLiveRules.identified(MatchLiveRules.parseSnapshot(J.replace("high", "low"))))
        assertFalse(MatchLiveRules.identified(MatchLiveRules.parseSnapshot(J.replace("\"team_b\":\"Australia\"", "\"team_b\":\"\""))))
        assertNull(MatchLiveRules.parseSnapshot("not json"))
    }
    @Test fun addresses_and_person_claims_are_dropped() {
        val s = MatchLiveRules.parseSnapshot(J.replace("T20 World Cup", "http://evil.example"))!!
        assertEquals("", s.tournament)
    }
    @Test fun same_match_and_score_change() {
        val a = MatchLiveRules.parseSnapshot(J)!!
        val b = MatchLiveRules.parseSnapshot(J.replace("145/3", "149/3"))!!
        val other = MatchLiveRules.parseSnapshot(J.replace("India", "England"))!!
        assertTrue(MatchLiveRules.sameMatch(a, b)); assertFalse(MatchLiveRules.sameMatch(a, other))
        assertTrue(MatchLiveRules.scoreChanged(a, b)); assertFalse(MatchLiveRules.scoreChanged(a, a))
    }
    @Test fun update_timing() {
        assertFalse(MatchLiveRules.updateDue(true, 5_000)); assertTrue(MatchLiveRules.updateDue(true, 25_000))
        assertFalse(MatchLiveRules.updateDue(false, 100_000)); assertTrue(MatchLiveRules.updateDue(false, 200_000))
    }
    @Test fun percentages_are_forced_sane() {
        val (a, b, d) = MatchLiveRules.normalize(120, 0, 0)
        assertEquals(100, a + b + d); assertTrue(a <= 96 && b >= 2)
        val p = MatchLiveRules.parsePred("""{"a_pct":"70%","b_pct":20,"draw_pct":10,"reasons":["form [1]","x"],"watch":"powerplay"}""")!!
        assertEquals(100, p.a + p.b + p.draw); assertEquals(2, p.reasons.size)
        assertNull(MatchLiveRules.parsePred("""{"reasons":[]}"""))
    }
    @Test fun one_update_cannot_flip_the_picture() {
        val prev = MatchLiveRules.Pred(60, 40, 0, emptyList(), "")
        val next = MatchLiveRules.Pred(5, 95, 0, emptyList(), "")
        val lim = MatchLiveRules.limitSwing(prev, next)
        assertTrue(lim.a >= 35); assertEquals(100, lim.a + lim.b + lim.draw)
    }
    @Test fun first_text_carries_the_honest_note() {
        val s = MatchLiveRules.parseSnapshot(J)!!
        val t = MatchLiveRules.firstText(s, MatchLiveRules.Pred(60, 40, 0, listOf("form"), "w"), 3, false)
        assertTrue(t.contains("60%")); assertTrue(t.contains("guarantee")); assertTrue(t.contains("betting"))
        assertTrue(MatchLiveRules.firstText(s, MatchLiveRules.Pred(60, 40, 0, emptyList(), ""), 0, false).contains("kaam ka data nahi"))
    }
    @Test fun search_queries_carry_only_names() {
        val q = MatchLiveRules.searchQueries(MatchLiveRules.parseSnapshot(J)!!)
        assertEquals(4, q.size); assertTrue(q.all { it.contains("India") && it.contains("Australia") && !it.contains("145") })
        assertTrue(q.any { it.contains("head to head") }); assertTrue(q.any { it.contains("recent form") }); assertTrue(q.any { it.contains("pitch") }); assertTrue(q.any { it.contains("injuries") })
        assertEquals(2, MatchLiveRules.liveQueries(MatchLiveRules.parseSnapshot(J)!!).size)
    }
    @Test fun prompt_makes_the_web_facts_the_basis_not_the_screen() {
        val sys = MatchLiveRules.composeSystem(false)
        assertTrue(sys.contains("screen alone is never a reason")); assertTrue(sys.contains("numbered"))
        assertTrue(MatchLiveRules.composeSystem(true).contains("required rate"))
    }
    @Test fun thin_search_is_called_not_real() {
        val s = MatchLiveRules.parseSnapshot(J)!!
        assertTrue(MatchLiveRules.firstText(s, MatchLiveRules.Pred(52, 48, 0, emptyList(), ""), 0, false).contains("pakka prediction nahi"))
    }
    @Test fun sources_are_listed_with_host() {
        val r = listOf(AnalysisRules.Result("India form guide", "x", "https://www.espncricinfo.com/a", ""))
        assertTrue(MatchLiveRules.sourcesText(r, false).contains("espncricinfo.com"))
    }
}
