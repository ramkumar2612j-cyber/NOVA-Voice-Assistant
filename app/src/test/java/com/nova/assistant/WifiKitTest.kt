package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.153 WIFI KIT: pure-rules tests (no Android). Covers security/band/bars classification, scan reply,
 * best-switch and guard cooldown, Wi-Fi QR payload round-trip + escapes, the doctor, the command grammar
 * (including everything that must NOT match) and the legal line: nothing here can attack a network.
 */
class WifiKitTest {
    private fun net(ssid: String, level: Int, freq: Int = 2437, caps: String = "[WPA2-PSK-CCMP][ESS]") =
        WifiKitRules.Net(ssid, level, freq, caps)

    // ---- classification

    @Test fun securityTypesAreClassified() {
        assertEquals("WPA3", WifiKitRules.security("[SAE][ESS]"))
        assertEquals("WPA2", WifiKitRules.security("[WPA2-PSK-CCMP][ESS]"))
        assertEquals("WEP", WifiKitRules.security("[WEP][ESS]"))
        assertEquals("WPA", WifiKitRules.security("[WPA-PSK-TKIP][ESS]"))
        assertEquals("open", WifiKitRules.security("[ESS]"))
    }

    @Test fun openNetworksAreDetected() {
        assertTrue(WifiKitRules.isOpen("[ESS]"))
        assertTrue(WifiKitRules.isOpen("[OWE][ESS]"))
        assertFalse(WifiKitRules.isOpen("[WPA2-PSK-CCMP][ESS]"))
    }

    @Test fun bandsAndBars() {
        assertEquals("2.4 GHz", WifiKitRules.band(2437))
        assertEquals("5 GHz", WifiKitRules.band(5180))
        assertEquals("6 GHz", WifiKitRules.band(6115))
        assertEquals("", WifiKitRules.band(0))
        assertEquals(4, WifiKitRules.bars(-40))
        assertEquals(3, WifiKitRules.bars(-60))
        assertEquals(2, WifiKitRules.bars(-70))
        assertEquals(1, WifiKitRules.bars(-80))
        assertEquals(0, WifiKitRules.bars(-95))
    }

    // ---- scan reply

    @Test fun scanReplySortsDedupsAndLimits() {
        val nets = listOf(net("A", -50), net("B", -70), net("A", -60), net("C", -40), net("D", -85), net("E", -90), net("F", -95), net("G", -96), net("H", -97))
        val r = WifiKitRules.scanReply(nets, en = false)
        assertTrue(r.contains("Paas ke WiFi"))
        val lines = r.split("\n").drop(1)
        assertEquals(8, lines.size)
        assertTrue(lines[0].startsWith("1. C"))
        assertTrue(lines[1].startsWith("2. A"))   // dedup keeps the strongest -60
        assertTrue(lines[1].contains("-") || lines[1].contains("3/4"))
    }

    @Test fun scanReplyEmptyIsHonest() {
        assertTrue(WifiKitRules.scanReply(emptyList(), en = false).contains("nahi mila"))
        assertTrue(WifiKitRules.scanReply(emptyList(), en = true).contains("no nearby"))
    }

    @Test fun openNetworksAreMarkedFreeInScanReply() {
        val r = WifiKitRules.scanReply(listOf(net("FreeCafe", -55, caps = "[ESS]")), en = false)
        assertTrue(r.contains("FreeCafe"))
        assertTrue(r.contains("FREE"))
    }

    // ---- best switch (owner's own networks only)

    @Test fun bestSwitchPicksStrongestCandidateWithGain() {
        val cands = listOf(net("Home", -45), net("Office", -60))
        val plan = WifiKitRules.bestSwitch("Current", -70, cands)
        assertNotNull(plan)
        assertEquals("Home", plan!!.ssid)
        assertEquals(25, plan.gainDb)
    }

    @Test fun bestSwitchRefusesSmallGain() {
        val plan = WifiKitRules.bestSwitch("Current", -60, listOf(net("Home", -55)))
        assertNull(plan)   // only 5 dB better: no flapping
    }

    @Test fun bestSwitchFromDisconnectedPicksAnyBest() {
        val plan = WifiKitRules.bestSwitch("", WifiKitRules.DISCONNECTED, listOf(net("Home", -75)))
        assertNotNull(plan)
        assertEquals("Home", plan!!.ssid)
    }

    @Test fun bestSwitchNeverReturnsTheCurrentNetwork() {
        assertNull(WifiKitRules.bestSwitch("Home", -50, listOf(net("Home", -40))))
    }

    @Test fun guardDecideRespectsTheCooldown() {
        val cands = listOf(net("Home", -40))
        assertNull(WifiKitRules.guardDecide("Current", -70, cands, lastSwitchAt = 1000, now = 1000 + 5 * 60 * 1000))
        assertNotNull(WifiKitRules.guardDecide("Current", -70, cands, lastSwitchAt = 1000, now = 1000 + 11 * 60 * 1000))
    }

    @Test fun strongestOpenPicksTheStrongestOpenNetwork() {
        val nets = listOf(net("Secured", -30), net("Cafe", -60, caps = "[ESS]"), net("Park", -70, caps = "[ESS]"))
        assertEquals("Cafe", WifiKitRules.strongestOpen(nets)!!.ssid)
        assertNull(WifiKitRules.strongestOpen(listOf(net("Secured", -30))))
    }

    // ---- Wi-Fi QR

    @Test fun qrPayloadRoundTripWithEscapes() {
        val payload = WifiKitRules.qrPayload("My;Home,Net", "pass\\word:123")
        val qr = WifiKitRules.parseQr(payload)
        assertNotNull(qr)
        assertEquals("My;Home,Net", qr!!.ssid)
        assertEquals("pass\\word:123", qr.pass)
        assertFalse(qr.open)
    }

    @Test fun qrOpenNetworkUsesNopass() {
        val qr = WifiKitRules.parseQr(WifiKitRules.qrPayload("Cafe", ""))
        assertNotNull(qr)
        assertTrue(qr!!.open)
        assertEquals("Cafe", qr.ssid)
    }

    @Test fun parseQrRejectsNonWifiText() {
        assertNull(WifiKitRules.parseQr("hello world"))
        assertNull(WifiKitRules.parseQr("WIFI:T:WPA;P:onlypass;;"))   // no S field
    }

    @Test fun parseQrReadsARealWorldPayload() {
        val qr = WifiKitRules.parseQr("WIFI:T:WPA;S:JioFiber;P:bigsecret99;;")
        assertNotNull(qr)
        assertEquals("JioFiber", qr!!.ssid)
        assertEquals("bigsecret99", qr.pass)
    }

    // ---- doctor

    @Test fun doctorExplainsEveryHonestState() {
        fun s(wifiOn: Boolean, ssid: String, has: Boolean, valid: Boolean, portal: Boolean? = null) =
            WifiKitRules.DoctorState(wifiOn, ssid, -50, has, valid, portal)
        assertTrue(WifiKitRules.doctorReply(s(false, "", false, false), false).contains("WiFi band"))
        assertTrue(WifiKitRules.doctorReply(s(true, "", false, false), false).contains("juda nahi"))
        assertTrue(WifiKitRules.doctorReply(s(true, "X", false, false), false).contains("Router"))
        assertTrue(WifiKitRules.doctorReply(s(true, "X", true, false, true), false).contains("captive portal") || WifiKitRules.doctorReply(s(true, "X", true, false, true), false).contains("login"))
        assertTrue(WifiKitRules.doctorReply(s(true, "X", true, false, false), false).contains("Airplane") || WifiKitRules.doctorReply(s(true, "X", true, false, false), false).contains("airplane") || WifiKitRules.doctorReply(s(true, "X", true, false, false), false).contains("verify nahi"))
        assertTrue(WifiKitRules.doctorReply(s(true, "X", true, true), false).contains("Sab theek"))
        assertTrue(WifiKitRules.doctorReply(WifiKitRules.DoctorState(true, "X", -85, true, true, null), false).contains("kamzor"))
    }

    // ---- validation

    @Test fun ssidAndPassValidation() {
        assertTrue(WifiKitRules.validSsid("JioFiber"))
        assertFalse(WifiKitRules.validSsid(""))
        assertFalse(WifiKitRules.validSsid(" ".repeat(33)))
        assertTrue(WifiKitRules.validPass("12345678"))
        assertFalse(WifiKitRules.validPass("1234567"))   // 7 chars
        assertFalse(WifiKitRules.validPass("x".repeat(64)))
        assertTrue(WifiKitRules.validSsidPass("Cafe", ""))   // open network
        assertFalse(WifiKitRules.validSsidPass("Cafe", "short"))
    }

    // ---- command grammar

    private fun kind(text: String): String? = WifiKitRules.parse(text)?.kind

    @Test fun scanCommandsParse() {
        assertEquals(WifiKitRules.K_SCAN, kind("nova wifi scan karo"))
        assertEquals(WifiKitRules.K_SCAN, kind("wifi kaunse hain"))
        assertEquals(WifiKitRules.K_SCAN, kind("nearby wifi dikhao"))
        assertEquals(WifiKitRules.K_SCAN, kind("wifi ki list batao"))
    }

    @Test fun bestAndSwitchCommandsParse() {
        assertEquals(WifiKitRules.K_BEST, kind("best wifi pe jao"))
        assertEquals(WifiKitRules.K_BEST, kind("sabse strong wifi pe le chalo"))
        assertEquals(WifiKitRules.K_SWITCH, kind("wifi switch karo jiofiber"))
        assertEquals("jiofiber", WifiKitRules.parse("wifi switch karo jiofiber")!!.arg)
        assertEquals(WifiKitRules.K_SWITCH, kind("wifi badlo jiofiber"))
        assertEquals(WifiKitRules.K_SWITCH, kind("jiofiber wifi pe jao"))
        assertEquals(WifiKitRules.K_SWITCH, kind("wifi jiofiber pe switch karo"))
    }

    @Test fun openConnectCommandsParse() {
        assertEquals(WifiKitRules.K_OPEN, kind("free wifi pe connect karo"))
        assertEquals(WifiKitRules.K_OPEN, kind("open wifi dhundo"))
        assertEquals(WifiKitRules.K_OPEN, kind("khula wifi connect karo"))
    }

    @Test fun qrCommandsParse() {
        assertEquals(WifiKitRules.K_QR_MAKE, kind("wifi qr banao"))
        assertEquals(WifiKitRules.K_QR_MAKE, kind("wifi share karo"))
        assertEquals(WifiKitRules.K_QR_MAKE, kind("jiofiber wifi ka qr banao"))
        assertEquals("jiofiber", WifiKitRules.parse("jiofiber wifi ka qr banao")!!.arg)
        assertEquals(WifiKitRules.K_QR_READ, kind("wifi qr scan karo"))
        assertEquals(WifiKitRules.K_QR_READ, kind("wifi qr kholo"))
    }

    @Test fun doctorCommandsParseWithAndWithoutTheWordWifi() {
        assertEquals(WifiKitRules.K_FIX, kind("wifi theek karo"))
        assertEquals(WifiKitRules.K_FIX, kind("wifi problem hai"))
        assertEquals(WifiKitRules.K_FIX, kind("wifi slow hai"))
        assertEquals(WifiKitRules.K_FIX, kind("internet kyun nahi chal raha"))
        assertEquals(WifiKitRules.K_FIX, kind("net nahi chal raha"))
    }

    @Test fun passwordSaveParsesAndKeepsCase() {
        val c = WifiKitRules.parse("wifi password save karo JioFiber-5G jio@12345")
        assertEquals(WifiKitRules.K_SAVE_PASS, c!!.kind)
        assertEquals("JioFiber-5G", c.arg.substringBefore(WifiKitRules.SEP))
        assertEquals("jio@12345", c.arg.substringAfter(WifiKitRules.SEP))
        val c2 = WifiKitRules.parse("JioFiber ka wifi password MySecret99")
        assertEquals(WifiKitRules.K_SAVE_PASS, c2!!.kind)
        assertEquals("MySecret99", c2.arg.substringAfter(WifiKitRules.SEP))
        val c3 = WifiKitRules.parse("wifi password jiofiber 12345678")
        assertEquals("12345678", c3!!.arg.substringAfter(WifiKitRules.SEP))
    }

    @Test fun passwordQuestionsNeverSave() {
        assertNull(WifiKitRules.parse("wifi password kya hai"))
        assertNull(WifiKitRules.parse("wifi password kaun batayega"))
    }

    @Test fun forgetAndGuardParse() {
        assertEquals(WifiKitRules.K_FORGET, kind("wifi password jiofiber bhool jao"))
        assertEquals("jiofiber", WifiKitRules.parse("wifi password jiofiber bhool jao")!!.arg)
        assertEquals(WifiKitRules.K_GUARD_ON, kind("wifi guard on karo"))
        assertEquals(WifiKitRules.K_GUARD_OFF, kind("wifi guard off karo"))
        assertEquals(WifiKitRules.K_GUARD_ON, kind("wifi auto switch on karo"))
    }

    @Test fun statusCommandsParse() {
        assertEquals(WifiKitRules.K_STATUS, kind("kaunsa wifi laga hai"))
        assertEquals(WifiKitRules.K_STATUS, kind("kaun sa wifi chal raha hai"))
        assertEquals(WifiKitRules.K_STATUS, kind("wifi ka haal batao"))
    }

    @Test fun plainWifiOnOffAndSettingsNeverMatchTheKit() {
        assertNull(WifiKitRules.parse("wifi on karo"))
        assertNull(WifiKitRules.parse("wifi off karo"))
        assertNull(WifiKitRules.parse("wifi band karo"))
        assertNull(WifiKitRules.parse("wifi kholo"))
        assertNull(WifiKitRules.parse("wifi settings kholo"))
        assertNull(WifiKitRules.parse("wifi settings"))
    }

    @Test fun negatedAndUnrelatedSentencesNeverMatch() {
        assertNull(WifiKitRules.parse("wifi scan mat karo"))
        assertNull(WifiKitRules.parse("gaana chalao"))
        assertNull(WifiKitRules.parse("battery kitni hai"))
        assertNull(WifiKitRules.parse(""))
    }

    // ---- the legal line

    @Test fun rulesSourceStaysFreeOfAttackWords() {
        val src = java.io.File("src/main/java/com/nova/assistant/WifiKitRules.kt").readText().lowercase()
        for (bad in listOf("crack", "brute", "wordlist", "aircrack", "reaver", "wifite", "pin attack", "wps attack")) {
            assertFalse("forbidden word in rules: $bad", src.contains(bad))
        }
    }
}
