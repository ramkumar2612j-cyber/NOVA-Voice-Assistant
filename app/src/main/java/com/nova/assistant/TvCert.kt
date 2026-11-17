package com.nova.assistant

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * v7.219 TV ke liye self-signed RSA client certificate — Android par koi public cert-generation API nahi hoti
 * (BouncyCastle hidden hai), isliye X.509 DER haath se banate hain. Ye wahi cert hai jo Android TV pairing
 * (port 6467) maangta hai; reference clients (androidtvremote2) bhi aise hi self-signed cert generate karte hain.
 *
 * Format: X.509 v3, CN=<clientName>, SHA256withRSA, RSA 2048. Koi extension nahi (TV ko nahi chahiye).
 * TvCertTest ise wapas CertificateFactory se parse karke verify karta hai — real parse, fake nahi.
 */
object TvCert {

    private val SHA256_RSA_OID = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)   // 1.2.840.113549.1.1.11
    private val RSA_OID = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)          // 1.2.840.113549.1.1.1

    // ---------------- DER primitives ----------------

    private fun len(n: Int): ByteArray {
        if (n < 0x80) return byteArrayOf(n.toByte())
        val b = ByteArrayOutputStream(); var v = n
        val tmp = java.util.ArrayDeque<Byte>()
        while (v > 0) { tmp.addFirst((v and 0xFF).toByte()); v = v shr 8 }
        b.write(0x80 or tmp.size); for (x in tmp) b.write(x.toInt())
        return b.toByteArray()
    }

    private fun tlv(tag: Int, payload: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(tag); o.write(len(payload.size)); o.write(payload)
        return o.toByteArray()
    }

    private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArrayOutputStream()) { a, p -> a.write(p); a }.toByteArray())
    private fun integer(v: BigInteger) = tlv(0x02, v.toByteArray())
    private fun intSmall(v: Int) = integer(BigInteger.valueOf(v.toLong()))
    private fun utctime(t: ZonedDateTime): ByteArray = tlv(0x17, t.format(DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")).toByteArray(Charsets.US_ASCII))
    private fun oid(raw: ByteArray) = tlv(0x06, raw)
    private fun bitString(data: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(); o.write(0x00)   // no unused bits
        o.write(data)
        return tlv(0x03, o.toByteArray())
    }

    /** CN=... Name (RDNSequence): OID 2.5.4.3 + UTF8String value. */
    private fun name(cn: String): ByteArray {
        val rdn = seq(tlv(0x06, byteArrayOf(0x55, 0x04, 0x03)), tlv(0x0C, cn.toByteArray(Charsets.UTF_8)))
        return seq(rdn)
    }

    private fun algSha256Rsa() = seq(oid(SHA256_RSA_OID), tlv(0x05, ByteArray(0)))
    private fun algRsa() = seq(oid(RSA_OID), tlv(0x05, ByteArray(0)))

    // ---------------- build ----------------

    data class CertAndKey(val certPem: String, val keyPem: String)

    /** Naya RSA-2048 keypair + self-signed cert (10 saal valid). PEM dono files me likhne ke liye. */
    fun generate(clientName: String): CertAndKey {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = sign(kp, clientName)
        return CertAndKey(pem("CERTIFICATE", cert.encoded), pem("RSA PRIVATE KEY", encodePkcs1(kp)))
    }

    /** TBS build + SHA256withRSA sign + Certificate SEQUENCE. */
    private fun sign(kp: KeyPair, clientName: String): java.security.cert.X509Certificate {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val pub = kp.public as RSAPublicKey
        val spki = seq(algRsa(), bitString(seq(integer(pub.modulus), integer(pub.publicExponent))))
        val tbs = seq(
            tlv(0xA0, intSmall(2)),                       // [0] EXPLICIT version v3
            intSmall(java.util.Random().nextInt(1_000_000) + 1),   // serial
            algSha256Rsa(),
            name(clientName),
            seq(utctime(now.minusDays(1)), utctime(now.plusDays(3650))),
            name(clientName),
            spki
        )
        val sig = Signature.getInstance("SHA256withRSA").apply { initSign(kp.private); update(tbs) }.sign()
        val certBytes = seq(tbs, algSha256Rsa(), bitString(sig))
        return CertificateFactory.getInstance("X.509").generateCertificate(java.io.ByteArrayInputStream(certBytes)) as java.security.cert.X509Certificate
    }

    // ---------------- PEM + PKCS#1 ----------------

    private fun pem(label: String, der: ByteArray): String {
        val b64 = java.util.Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(der)
        return "-----BEGIN $label-----\n$b64\n-----END $label-----\n"
    }

    /** Private key ko PKCS#1 RSAPrivateKey DER me (PEM "RSA PRIVATE KEY" ke liye). */
    private fun encodePkcs1(kp: KeyPair): ByteArray {
        val spec = java.security.KeyFactory.getInstance("RSA").getKeySpec(kp.private, java.security.spec.RSAPrivateCrtKeySpec::class.java)
        return seq(
            intSmall(0),
            integer((kp.public as RSAPublicKey).modulus),
            integer((kp.public as RSAPublicKey).publicExponent),
            integer(spec.privateExponent),
            integer(spec.primeP),
            integer(spec.primeQ),
            integer(spec.primeExponentP),
            integer(spec.primeExponentQ),
            integer(spec.crtCoefficient)
        )
    }
}
