package com.nova.assistant

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * v7.219 OSCAR TV: Android TV / Google TV "Remote protocol v2" ka phone-side client.
 *
 * Kaise kaam karta hai (protocol open-source references se: AOSP google-tv-pairing-protocol + AOSP TvRemote;
 * androidtvremote2 library jaisa hi — koi invented cheez nahi):
 *   1. TV aur phone ek hi Wi-Fi me hon. TV par "Android TV Remote Service" preinstalled hota hai (zyadatar TV me hota hai).
 *   2. Pairing (port 6467, TLS + humara self-signed client cert): PairingRequest -> TV ack -> Options ->
 *      Configuration -> ConfigurationAck -> TV par 6-hex ka code dikhta hai -> owner wahi code phone me daalta hai ->
 *      sha256(moduli+exponents+pin[2:]) secret bhejte hain -> SecretAck = paired (cert TV ko yaad rehta hai).
 *   3. Commands (port 6466, TLS usi cert se): Configure -> key injects (SHORT press) / app deep links.
 *
 * HONEST STATUS: ye class sandbox me real TV ke bina test NAHI ho sakti — compile + proto/DER unit tests hi
 * yahan possible hain. Owner ke TV par pehla test docs/OSCAR_TV.md ke steps se hoga.
 * Agar OSCAR Android TV/Google TV NAHI hai to ye class kaam nahi karegi — doc me alternatives hain.
 */
object TvRemote {
    const val SLOT = "tvremote"          // SecureStore: "host:apiPort:pairPort"
    const val API_PORT = 6466
    const val PAIR_PORT = 6467
    const val CLIENT_NAME = "JARVIS"
    private const val TIMEOUT_MS = 10_000

    // ---------------- storage ----------------

    fun savedHost(ctx: Context): String? {
        val v = try { SecureStore.getSlot(ctx, SLOT) } catch (e: Throwable) { "" }
        val host = v.trim().substringBefore(':')
        return if (host.contains('.') || host.contains(':')) host else null
    }

    fun saveHost(ctx: Context, host: String): Boolean {
        val h = host.trim().take(100)
        if (h.isEmpty() || h.contains(' ') || h.length < 3) return false
        return SecureStore.saveSlot(ctx, SLOT, h)
    }

    fun clear(ctx: Context) {
        try { SecureStore.clearSlot(ctx, SLOT) } catch (e: Throwable) { }
        for (f in listOf("tv_cert.pem", "tv_key.pem")) try { ctx.deleteFile(f) } catch (e: Throwable) { }
    }

    /** Cert files (filesDir). Pehli baar ban jaate hain, phir reuse. */
    private fun ensureCert(ctx: Context): TvCert.CertAndKey? = try {
        val cf = ctx.getFileStreamPath("tv_cert.pem")
        val kf = ctx.getFileStreamPath("tv_key.pem")
        if (cf != null && cf.exists() && kf != null && kf.exists()) {
            TvCert.CertAndKey(String(ctx.openFileInput("tv_cert.pem").readBytes()), String(ctx.openFileInput("tv_key.pem").readBytes()))
        } else {
            val ck = TvCert.generate(CLIENT_NAME)
            ctx.openFileOutput("tv_cert.pem", Context.MODE_PRIVATE).use { it.write(ck.certPem.toByteArray()) }
            ctx.openFileOutput("tv_key.pem", Context.MODE_PRIVATE).use { it.write(ck.keyPem.toByteArray()) }
            ck
        }
    } catch (e: Throwable) { null }

    // ---------------- TLS ----------------

    /** SSLSocketFactory: humara client cert + server cert accept (pairing-secret hi asli security hai). */
    private fun sslFactory(ck: TvCert.CertAndKey): SSLSocketFactory? {   // block body: expression body me return allowed nahi
        return try {
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(ck.certPem.toByteArray()))
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        val b64 = ck.keyPem.replace(Regex("-----[A-Z ]+-----|\\s"), "")
        val der = java.util.Base64.getDecoder().decode(b64)
        val priv = parsePkcs1Private(der) ?: return null
        ks.setKeyEntry("tv", priv, CharArray(1), arrayOf(cert))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, CharArray(1))
        val tm = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) { }
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) { }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, tm, null) }.socketFactory
    } catch (e: Throwable) { null }
    }

    /** PKCS#1 RSAPrivateKey DER -> PrivateKey (KeyFactory RSAPrivateCrtKeySpec se). */
    private fun parsePkcs1Private(der: ByteArray): java.security.PrivateKey? {   // block body: expression body me return allowed nahi
        return try {
        val ints = parseDerIntegers(der)
        if (ints.size < 9) return null
        val spec = java.security.spec.RSAPrivateCrtKeySpec(ints[1], ints[2], ints[3], ints[4], ints[5], ints[6], ints[7], ints[8])
        java.security.KeyFactory.getInstance("RSA").generatePrivate(spec)
        } catch (e: Throwable) { null }
    }

    /** Simple DER SEQUENCE-of-INTEGERs parser (PKCS#1 key ke liye kaafi). */
    private fun parseDerIntegers(der: ByteArray): List<java.math.BigInteger> {
        val out = ArrayList<java.math.BigInteger>()
        var i = 0
        fun readLen(): Int {
            val l = der[i].toInt() and 0xFF; i++
            if (l and 0x80 == 0) return l
            val n = l and 0x7F; var v = 0
            repeat(n) { v = (v shl 8) or (der[i].toInt() and 0xFF); i++ }
            return v
        }
        if (i >= der.size || der[i].toInt() != 0x30) return out
        i++; readLen()
        while (i < der.size) {
            if (der[i].toInt() != 0x02) break
            i++; val l = readLen()
            out.add(java.math.BigInteger(1, der.copyOfRange(i, i + l))); i += l
        }
        return out
    }

    private fun connect(factory: SSLSocketFactory, host: String, port: Int): SSLSocket? = try {
        val s = factory.createSocket() as SSLSocket
        s.soTimeout = TIMEOUT_MS
        s.connect(InetSocketAddress(host, port), TIMEOUT_MS)
        s.startHandshake()
        s
    } catch (e: Throwable) { try { null } finally { } }

    // ---------------- pairing ----------------

    /**
     * Pairing chalao. onPin TV ke configuration_ack ke BAAD poll hota hai (300ms, max 30s) — owner ko TV par
     * dikh raha 6-hex code phone ke PIN box me daalne ka time milta hai. null/space PIN = timeout.
     * Return: "ok" / "connect_fail" / "pin_invalid" / "tv_refused" / "pin_timeout" / "no_cert".
     * BACKGROUND THREAD SE HI BULAO (network + TLS).
     */
    fun pair(ctx: Context, host: String, onPin: () -> String?): String {
        val ck = ensureCert(ctx) ?: return "no_cert"
        val factory = sslFactory(ck) ?: return "no_cert"
        val sock = connect(factory, host, PAIR_PORT) ?: return "connect_fail"
        try {
            val inp = sock.inputStream; val out = sock.outputStream
            // 1) PairingRequest
            writeFrame(out, TvProto.pairingRequest(CLIENT_NAME))
            var msg = readFrame(inp) ?: return "tv_refused"
            var f = TvProto.parseOuter(msg)
            if (f[11] == null) return "tv_refused"                 // pairing_request_ack chahiye
            // 2) Options
            writeFrame(out, TvProto.options())
            msg = readFrame(inp) ?: return "tv_refused"
            f = TvProto.parseOuter(msg)
            if (f[20] == null) return "tv_refused"                 // options chahiye
            // 3) Configuration
            writeFrame(out, TvProto.configuration())
            msg = readFrame(inp) ?: return "tv_refused"
            f = TvProto.parseOuter(msg)
            if (f[31] == null) return "tv_refused"                 // configuration_ack = TV par PIN dikh raha hai
            // 4) PIN ka wait (owner box me bharta hai) + secret
            var pin = ""
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline) {
                val p = (onPin() ?: "").trim()
                if (p.length == 6) { pin = p; break }
                try { Thread.sleep(300) } catch (e: InterruptedException) { return "pin_timeout" }
            }
            if (pin.isEmpty()) return "pin_timeout"
            sock.soTimeout = 15_000
            val serverCert = try { sock.session.peerCertificates[0] as X509Certificate } catch (e: Throwable) { return "tv_refused" }
            val clientPub = try {
                CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(ck.certPem.toByteArray())).publicKey as RSAPublicKey
            } catch (e: Throwable) { return "no_cert" }
            val serverPub = try { serverCert.publicKey as RSAPublicKey } catch (e: Throwable) { return "tv_refused" }
            val hash = TvProto.pairingSecret(pin,
                TvProto.intToBigEndianBytes(clientPub.modulus), TvProto.intToBigEndianBytes(clientPub.publicExponent),
                TvProto.intToBigEndianBytes(serverPub.modulus), TvProto.intToBigEndianBytes(serverPub.publicExponent))
                ?: return "pin_invalid"
            writeFrame(out, TvProto.secret(hash))
            msg = readFrame(inp) ?: return "tv_refused"
            f = TvProto.parseOuter(msg)
            if (f[41] == null) return "tv_refused"                 // secret_ack = paired!
            saveHost(ctx, host)
            return "ok"
        } finally {
            try { sock.close() } catch (e: Throwable) { }
        }
    }

    // ---------------- commands ----------------

    /** Ek ya kai events bhejo (background thread). Return friendly result text ya error. */
    fun send(ctx: Context, events: List<TvRules.TvEvent>): String {
        if (events.isEmpty()) return ""
        val host = savedHost(ctx) ?: return "not_paired"
        val ck = ensureCert(ctx) ?: return "no_cert"
        val factory = sslFactory(ck) ?: return "no_cert"
        val sock = connect(factory, host, API_PORT) ?: return "connect_fail"
        try {
            val out = sock.outputStream
            writeFrame(out, TvProto.configure(1))
            var sent = 0
            for (ev in events) {
                if (ev.deepLink.isNotEmpty()) {
                    writeFrame(out, TvProto.deepLink(TvRules.deepLinkOf(ev.deepLink)))
                    sent++
                } else {
                    repeat(ev.n.coerceIn(1, 10)) { writeFrame(out, TvProto.keyInject(ev.keyCode, TvProto.DIR_SHORT)); sent++ }
                }
                Thread.sleep(30)   // TV ko events alag alag samajhne do
            }
            return "ok:$sent"
        } catch (e: Throwable) {
            return "send_fail"
        } finally {
            try { sock.close() } catch (e: Throwable) { }
        }
    }

    // ---------------- io ----------------

    private fun writeFrame(out: OutputStream, msg: ByteArray) {
        out.write(TvProto.frame(msg))
        out.flush()
    }

    /** Ek varint-framed message read karo (timeout socket ka). null = band/galat. */
    private fun readFrame(inp: InputStream): ByteArray? {   // block body: expression body me return allowed nahi
        return try {
        var len = 0L; var shift = 0; var b: Int
        while (true) {
            b = inp.read()
            if (b < 0) return null
            len = len or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 28) return null
        }
        if (len <= 0 || len > 1_000_000) return null
        val buf = ByteArray(len.toInt())
        var off = 0
        while (off < buf.size) {
            val r = inp.read(buf, off, buf.size - off)
            if (r < 0) return null
            off += r
        }
        buf
        } catch (e: Throwable) { null }
    }
}
