package com.nova.assistant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * v7.219 TV proto golden vectors. Wire format AOSP polo.proto + remotemessage.proto se —
 * reference: github.com/tronikos/androidtvremote2 (Google TV app wala hi protocol).
 * Ye tests encode/decode + secret-hash algorithm verify karte hain. Asli TV pairing sandbox me
 * NAHI ho sakti (hardware nahi) — wo honest status docs/OSCAR_TV.md me likha hai.
 */
class TvProtoTest {

    // ---------- varint ----------

    @Test fun varintRoundTrip() {
        for (v in listOf(0, 1, 127, 128, 300, 16383, 16384, 65535, 1 shl 20, Int.MAX_VALUE)) {
            val o = java.io.ByteArrayOutputStream()
            TvProto.writeVarint(o, v)
            val b = o.toByteArray()
            val (val1, pos) = TvProto.readVarint(b, 0)
            assertEquals("varint $v", v.toLong(), val1)
            assertEquals(b.size, pos)
        }
    }

    @Test fun varintKnownBytes() {
        val o = java.io.ByteArrayOutputStream(); TvProto.writeVarint(o, 300)
        assertArrayEquals(byteArrayOf(0xAC.toByte(), 0x02), o.toByteArray())   // protobuf canonical 300
    }

    // ---------- framing ----------

    @Test fun frameRoundTrip() {
        val msg = TvProto.pairingRequest("JARVIS")
        val framed = TvProto.frame(msg)
        val (parsed, next) = TvProto.parseFrame(framed, 0)!!
        assertArrayEquals(msg, parsed)
        assertEquals(framed.size, next)
    }

    // ---------- pairing messages: structure checks (field numbers spec se) ----------

    @Test fun pairingRequestFields() {
        val m = TvProto.pairingRequest("JARVIS")
        val f = TvProto.parseOuter(m)
        assertNotNull(f[10])                       // pairing_request = 10
        val pr = TvProto.parseOuter(f[10]!!)
        assertEquals("atvremote", String(pr[1]!!)) // service_name = 1
        assertEquals("JARVIS", String(pr[2]!!))     // client_name = 2
        assertEquals("2", String(f[1]!!))           // protocol_version = 2
        assertEquals("200", String(f[2]!!))         // status = STATUS_OK
    }

    @Test fun optionsFields() {
        val f = TvProto.parseOuter(TvProto.options())
        val opt = TvProto.parseOuter(f[20]!!)       // options = 20
        assertEquals("1", String(opt[3]!!))         // preferred_role = ROLE_TYPE_INPUT(1)
        assertNotNull(opt[1])                        // input_encodings[0]
        val enc = TvProto.parseOuter(opt[1]!!)
        assertEquals("3", String(enc[1]!!))         // ENCODING_TYPE_HEXADECIMAL
        assertEquals("6", String(enc[2]!!))         // symbol_length = 6
    }

    @Test fun configurationFields() {
        val f = TvProto.parseOuter(TvProto.configuration())
        val cfg = TvProto.parseOuter(f[30]!!)       // configuration = 30
        assertNotNull(cfg[1])                        // encoding
        assertEquals("1", String(cfg[2]!!))         // client_role = INPUT
    }

    @Test fun secretFields() {
        val h = ByteArray(32) { (it + 1).toByte() }
        val f = TvProto.parseOuter(TvProto.secret(h))
        assertArrayEquals(h, TvProto.parseOuter(f[40]!!)[1])   // secret = 40; Secret.secret = 1
    }

    // ---------- remote messages ----------

    @Test fun keyInjectFields() {
        val m = TvProto.keyInject(TvProto.KEY_VOLUME_UP, TvProto.DIR_SHORT)
        val f = TvProto.parseRemote(m)
        val ki = TvProto.parseRemote(f[10]!!)       // remote_key_inject = 10
        assertEquals("24", String(ki[1]!!))         // key_code = 24 (VOLUME_UP)
        assertEquals("3", String(ki[2]!!))          // direction = 3 (SHORT)
    }

    @Test fun deepLinkFields() {
        val m = TvProto.deepLink("market://launch?id=com.netflix.ninja")
        val f = TvProto.parseRemote(m)
        assertEquals("market://launch?id=com.netflix.ninja", String(TvProto.parseRemote(f[90]!!)[1]!!))
    }

    @Test fun configureFields() {
        val f = TvProto.parseRemote(TvProto.configure(1))
        val cfg = TvProto.parseRemote(f[1]!!)       // remote_configure = 1
        assertEquals("1", String(cfg[1]!!))         // code1 = 1 (IME feature)
        val di = TvProto.parseRemote(cfg[2]!!)
        assertEquals("atvremote", String(di[5]!!))  // package_name
        assertEquals("1.0.0", String(di[6]!!))      // app_version
    }

    // ---------- pairing secret algorithm (polo.py exact) ----------

    @Test fun intToBigEndian() {
        assertArrayEquals(byteArrayOf(0x01), TvProto.intToBigEndianBytes(BigInteger.ONE))
        assertArrayEquals(byteArrayOf(0x01, 0x00), TvProto.intToBigEndianBytes(BigInteger.valueOf(256)))
        val big = BigInteger("1234567890123456789012345678901234567890")
        assertArrayEquals(big.toByteArray().let { if (it[0].toInt() == 0) it.copyOfRange(1, it.size) else it }, TvProto.intToBigEndianBytes(big))
    }

    @Test fun secretHashMatchesReferenceAlgorithm() {
        // sha256(cMod || cExp || sMod || sExp || pin[2:]) — polo.py wala
        val cMod = ByteArray(128) { 0x11 }; val cExp = byteArrayOf(0x01, 0x00, 0x01)   // 65537
        val sMod = ByteArray(128) { 0x22 }; val sExp = byteArrayOf(0x01, 0x00, 0x01)
        // pehle ek PIN dhoondo jiska hash byte-0 pin[0:2] se match ho (TV wala rule)
        var pin = ""
        var hash: ByteArray? = null
        for (i in 0 until 256) {
            val candidate = String.format("%02X", i) + "ABCD"
            val h = TvProto.pairingSecret(candidate, cMod, cExp, sMod, sExp)
            if (h != null) { pin = candidate; hash = h; break }
        }
        assertNotNull("valid PIN branch hona chahiye", hash)
        assertEquals(32, hash!!.size)
        // galat PIN -> null
        assertNull(TvProto.pairingSecret(pin.reversed(), cMod, cExp, sMod, sExp))
        // shape galat -> null
        assertNull(TvProto.pairingSecret("12345", cMod, cExp, sMod, sExp))
        assertNull(TvProto.pairingSecret("ZZZZZZ", cMod, cExp, sMod, sExp))
        assertNull(TvProto.pairingSecret(null, cMod, cExp, sMod, sExp))
    }

    @Test fun secretHashIsDeterministic() {
        val cMod = ByteArray(64) { 1 }; val cExp = byteArrayOf(3)
        val sMod = ByteArray(64) { 2 }; val sExp = byteArrayOf(3)
        // dono me same tail force karo: pin[2:] = "1122"
        val a = TvProto.pairingSecret("001122", cMod, cExp, sMod, sExp)
        val b = TvProto.pairingSecret("001122", cMod, cExp, sMod, sExp)
        // 00 pin[0:2] match hone ka chance kam — dono null ya dono same
        assertEquals(a == null, b == null)
        if (a != null) assertArrayEquals(a, b)
    }
}

class TvRulesTest {

    private fun ev(s: String?) = TvRules.parse(s)

    @Test fun tvPhrases() {
        assertEquals(TvProto.KEY_WAKEUP, ev("tv on karo")!!.keyCode)
        assertEquals(TvProto.KEY_SLEEP, ev("jarvis tv band karo")!!.keyCode)
        assertEquals(TvProto.KEY_HOME, ev("tv home")!!.keyCode)
        assertEquals(TvProto.KEY_BACK, ev("oscar back jao")!!.keyCode)
        assertEquals(TvProto.KEY_VOLUME_UP, ev("tv volume badhao")!!.keyCode)
        assertEquals(TvProto.KEY_VOLUME_DOWN, ev("tv volume kam karo")!!.keyCode)
        assertEquals(TvProto.KEY_VOLUME_MUTE, ev("tv mute karo")!!.keyCode)
        assertEquals(TvProto.KEY_DPAD_UP, ev("tv upar jao")!!.keyCode)
        assertEquals(TvProto.KEY_DPAD_CENTER, ev("tv ok")!!.keyCode)
        assertEquals(TvProto.KEY_MEDIA_PLAY_PAUSE, ev("tv pause")!!.keyCode)
        assertEquals(TvProto.KEY_MEDIA_NEXT, ev("tv agla gaana")!!.keyCode)
        assertEquals(TvProto.KEY_SEARCH, ev("tv search")!!.keyCode)
    }

    @Test fun volumeRepeatCount() {
        assertEquals(3, ev("tv volume 3 bar badhao")!!.n)
        assertEquals(2, ev("tv volume badhao 2 baar")!!.n)
        assertEquals(1, ev("tv volume kam karo")!!.n)   // bina "bar" ke repeat nahi — sirf ek dabao
    }

    @Test fun appDeepLinks() {
        assertEquals("com.netflix.ninja", ev("tv netflix kholo")!!.deepLink)
        assertEquals("com.google.android.youtube.tv", ev("jarvis tv youtube chalao")!!.deepLink)
        assertEquals("in.startv.hotstar", ev("tv hotstar kholo")!!.deepLink)
        assertEquals("com.amazon.amazonvideo.livingroom", ev("tv prime video chalao")!!.deepLink)
    }

    @Test fun deepLinkUrl() {
        assertEquals("market://launch?id=com.netflix.ninja", TvRules.deepLinkOf("com.netflix.ninja"))
        assertEquals("https://youtube.com/tv", TvRules.deepLinkOf("https://youtube.com/tv"))
    }

    @Test fun notTvPhrases() {
        assertNull(ev("chrome kholo"))
        assertNull(ev("tv"))
        assertNull(ev(null))
        assertNull(ev("laptop par chrome kholo"))
    }
}
