package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class TradeRulesTest {
    @Test fun matches() {
        for (p in listOf("nova gold ka analysis karo", "eurusd buy lena chahiye ya sell", "bitcoin ka trend batao", "nifty ka signal do", "mt5 me demo trade lagao xauusd"))
            assertNotNull(p, TradeRules.parse(p))
    }
    @Test fun doesNotMatch() {
        for (p in listOf("mt5 kholo", "nova mt5 open karo", "bitcoin kya hai", "gold ka gaana chalao", "whatsapp par share market ka message bhejo", "torch on karo"))
            assertNull(p, TradeRules.parse(p))
    }
    @Test fun placeAsk_opensMt5() {
        val r = TradeRules.parse("mt5 me demo trade lagao xauusd")!!
        assertTrue(r.openMt5); assertTrue(r.placeAsk)
    }
    @Test fun neverPromisesProfit() {
        val s = TradeRules.systemPrompt(false).lowercase()
        assertTrue(s.contains("never promise profit")); assertTrue(TradeRules.disclaimer(false).contains("guarantee"))
    }
}
