package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v6.76 (Part 6): AgentRules.check DENY / CONFIRM / OK cases for banking, payment, install and risky screens. Added on top of AgentRulesTest (none removed). */
class AgentRulesPart6Test {
    private val own = "com.nova.assistant"
    private fun node(i: Int, text: String, desc: String = "", editable: Boolean = false) =
        AgentRules.Node(i, text, desc, "Button", true, editable, false)
    private val tap = AgentRules.Action("tap", 0)

    @Test fun bankingPaymentAndInstallerScreensAreDeniedForTapAndType() {
        val pkgs = listOf(
            "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
            "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.vending",
            "com.android.settings", "com.google.android.permissioncontroller", "com.example.mybank.app", "com.some.wallet", "com.x.upi.pay"
        )
        for (p in pkgs) {
            assertEquals(p, AgentRules.Verdict.DENY, AgentRules.check(tap, node(0, "Continue"), p, own).v)
            assertEquals(p, AgentRules.Verdict.DENY, AgentRules.check(AgentRules.Action("type", 0, "hello"), node(0, "Box", editable = true), p, own).v)
        }
        // NOVA's own screen is never driven by the agent either
        assertEquals(AgentRules.Verdict.DENY, AgentRules.check(tap, node(0, "Continue"), own, own).v)
    }

    @Test fun moneyAndSecurityWordsAreDeniedInAnyApp() {
        for (p in listOf("com.whatsapp", "com.openai.chatgpt", "com.instagram.android")) {
            for (l in listOf("Pay", "Buy now", "Purchase", "Checkout", "Subscribe", "Upgrade", "Transfer", "Order now", "Uninstall", "Factory reset", "Password", "Enter OTP", "Donate")) {
                assertEquals("$p / $l", AgentRules.Verdict.DENY, AgentRules.check(tap, node(0, l), p, own).v)
            }
        }
    }

    @Test fun riskyButNotMoneyAsksFirst() {
        for (l in listOf("Send", "Delete", "Delete chat", "Remove", "Post", "Submit", "Confirm", "bhejo", "hatao")) {
            assertEquals(l, AgentRules.Verdict.CONFIRM, AgentRules.check(tap, node(0, l), "com.whatsapp", own).v)
        }
    }

    @Test fun plainSendIsFreeOnlyInAiAppsAndOnlyPlain() {
        for (p in AgentRules.AI_APPS) {
            for (l in listOf("Send", "Send message", "Send prompt", "bhejo")) {
                assertEquals("$p / $l", AgentRules.Verdict.OK, AgentRules.check(tap, node(0, l), p, own).v)
            }
            // not plain: asks (v6.76: used to pass because only the risky word was looked at)
            assertEquals(AgentRules.Verdict.CONFIRM, AgentRules.check(tap, node(0, "Send file to everyone"), p, own).v)
            assertEquals(AgentRules.Verdict.CONFIRM, AgentRules.check(tap, node(0, "Send"), "com.whatsapp", own).v)
            assertEquals(AgentRules.Verdict.CONFIRM, AgentRules.check(tap, node(0, "Delete chat"), p, own).v)
        }
        assertTrue(AgentRules.isAiSend("Send prompt"))
        assertFalse(AgentRules.isAiSend("Send money"))
        assertFalse(AgentRules.isAiSend("Search"))
    }

    @Test fun missingOrNonEditableTargetsAreDenied() {
        assertEquals(AgentRules.Verdict.DENY, AgentRules.check(tap, null, "com.whatsapp", own).v)
        assertEquals(AgentRules.Verdict.DENY, AgentRules.check(AgentRules.Action("type", 0, "hi"), node(0, "Send"), "com.whatsapp", own).v)
        assertEquals(AgentRules.Verdict.DENY, AgentRules.check(AgentRules.Action("type", -1, ""), null, "com.whatsapp", own).v)
        assertEquals(AgentRules.Verdict.DENY, AgentRules.check(AgentRules.Action("open_app"), null, "com.whatsapp", own).v)
        assertEquals(AgentRules.Verdict.OK, AgentRules.check(AgentRules.Action("open_app", app = "chrome"), null, "com.whatsapp", own).v)
    }

    @Test fun harmlessActionsStayOk() {
        for (a in listOf("done", "fail", "ask", "wait", "back", "home", "scroll")) {
            assertEquals(a, AgentRules.Verdict.OK, AgentRules.check(AgentRules.Action(a), null, "com.whatsapp", own).v)
        }
        assertEquals(AgentRules.Verdict.OK, AgentRules.check(tap, node(0, "Photos"), "com.whatsapp", own).v)
    }
}
