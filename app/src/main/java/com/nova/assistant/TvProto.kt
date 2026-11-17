package com.nova.assistant

/**
 * v7.219 OSCAR TV: Android TV / Google TV "Remote protocol v2" ka protobuf, haath se likha (koi dependency nahi).
 *
 * Wire format 100% asli open spec se — AOSP google-tv-pairing-protocol (polo.proto) + AOSP TvRemote (remotemessage.proto);
 * reference: github.com/tronikos/androidtvremote2 (wahi protocol jo Google TV app use karti hai).
 * Field numbers yahan invent NAHI kiye gaye — dono .proto files se copy kiye gaye hain:
 *
 * OuterMessage (pairing, port 6467):  protocol_version=1, status=2, pairing_request=10, pairing_request_ack=11,
 *   options=20, configuration=30, configuration_ack=31, secret=40, secret_ack=41
 * PairingRequest: service_name=1, client_name=2
 * Options: input_encodings=1 (repeated), preferred_role=3; Encoding: type=1, symbol_length=2
 * Configuration: encoding=1, client_role=2
 * Secret: secret=1 (bytes)
 *
 * RemoteMessage (commands, port 6466): remote_configure=1, remote_set_active=2, remote_key_inject=10,
 *   remote_app_link_launch_request=90
 * RemoteKeyInject: key_code=1 (enum), direction=2 (enum)
 * RemoteDirection: START_LONG=1, END_LONG=2, SHORT=3
 * RemoteConfigure: code1=1, device_info=2; RemoteDeviceInfo: model=1, vendor=2, unknown1=3, unknown2=4,
 *   package_name=5, app_version=6
 * RemoteAppLinkLaunchRequest: app_link=1
 *
 * Framing: har message se pehle uski byte-length ka protobuf varint.
 */
object TvProto {

    // ---------------- Android keycodes (AOSP keycodes.h; remotemessage.proto ka RemoteKeyCode enum) ----------------
    const val KEY_HOME = 3
    const val KEY_BACK = 4
    const val KEY_DPAD_UP = 19
    const val KEY_DPAD_DOWN = 20
    const val KEY_DPAD_LEFT = 21
    const val KEY_DPAD_RIGHT = 22
    const val KEY_DPAD_CENTER = 23
    const val KEY_VOLUME_UP = 24
    const val KEY_VOLUME_DOWN = 25
    const val KEY_POWER = 26
    const val KEY_MENU = 82
    const val KEY_SEARCH = 84
    const val KEY_MEDIA_PLAY_PAUSE = 85
    const val KEY_MEDIA_STOP = 86
    const val KEY_MEDIA_NEXT = 87
    const val KEY_MEDIA_PREVIOUS = 88
    const val KEY_ENTER = 66
    const val KEY_VOLUME_MUTE = 164
    const val KEY_SLEEP = 223
    const val KEY_WAKEUP = 224

    const val DIR_START_LONG = 1
    const val DIR_END_LONG = 2
    const val DIR_SHORT = 3

    const val STATUS_OK = 200
    const val SERVICE_NAME = "atvremote"        // polo.py: service_name = "atvremote"
    const val PROTOCOL_VERSION = 2              // _create_message(): protocol_version = 2

    // ---------------- varint ----------------

    fun writeVarint(out: java.io.ByteArrayOutputStream, v: Int) {
        var x = v
        while (true) {
            if (x and 0x7F.inv() == 0) { out.write(x); return }
            out.write((x and 0x7F) or 0x80); x = x ushr 7
        }
    }

    fun readVarint(b: ByteArray, pos: Int): Pair<Long, Int> {   // (value, next pos); -1 pos = khatam/truncated
        var result = 0L; var shift = 0; var i = pos
        while (i < b.size) {
            val byte = b[i].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return Pair(result, i + 1)
            shift += 7
            if (shift > 63) return Pair(-1L, -1)
            i++
        }
        return Pair(-1L, -1)
    }

    // ---------------- low-level field writers (protobuf wire) ----------------

    private fun tag(out: java.io.ByteArrayOutputStream, field: Int, wire: Int) = writeVarint(out, (field shl 3) or wire)
    private fun varintField(out: java.io.ByteArrayOutputStream, field: Int, v: Int) { tag(out, field, 0); writeVarint(out, v) }
    private fun bytesField(out: java.io.ByteArrayOutputStream, field: Int, v: ByteArray) { tag(out, field, 2); writeVarint(out, v.size); out.write(v) }
    private fun strField(out: java.io.ByteArrayOutputStream, field: Int, v: String) = bytesField(out, field, v.toByteArray(Charsets.UTF_8))

    private fun lenDelimited(field: Int, payload: ByteArray): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        tag(o, field, 2); writeVarint(o, payload.size); o.write(payload)
        return o.toByteArray()
    }

    // ---------------- pairing messages (polo / OuterMessage) ----------------

    /** Step 1: PairingRequest{service_name="atvremote", client_name}. */
    fun pairingRequest(clientName: String): ByteArray {
        val inner = java.io.ByteArrayOutputStream()
        strField(inner, 1, SERVICE_NAME)
        strField(inner, 2, clientName.take(40))
        return outerMessage(10, inner.toByteArray())
    }

    /** Step 2 (TV ne PairingRequestAck bheja): hum Options{preferred_role=INPUT, input_encodings=[HEXADECIMAL,6]} bhejte hain. */
    fun options(): ByteArray {
        val enc = java.io.ByteArrayOutputStream()
        varintField(enc, 1, 3)     // ENCODING_TYPE_HEXADECIMAL
        varintField(enc, 2, 6)     // symbol_length = 6
        val inner = java.io.ByteArrayOutputStream()
        val e = enc.toByteArray()
        // repeated field = same tag multiple times
        val o2 = java.io.ByteArrayOutputStream()
        tag(o2, 1, 2); writeVarint(o2, e.size); o2.write(e)
        inner.write(o2.toByteArray())
        varintField(inner, 3, 1)   // preferred_role = ROLE_TYPE_INPUT
        return outerMessage(20, inner.toByteArray())
    }

    /** Step 3 (TV ne Options bheja): Configuration{encoding=HEX,6, client_role=INPUT}. */
    fun configuration(): ByteArray {
        val enc = java.io.ByteArrayOutputStream()
        varintField(enc, 1, 3)
        varintField(enc, 2, 6)
        val inner = java.io.ByteArrayOutputStream()
        inner.write(lenDelimited(1, enc.toByteArray()))
        varintField(inner, 2, 1)   // client_role = ROLE_TYPE_INPUT
        return outerMessage(30, inner.toByteArray())
    }

    /** Step 5: Secret{secret=sha256(moduli+exponents+pin[2:])}. */
    fun secret(hash: ByteArray): ByteArray {
        val inner = java.io.ByteArrayOutputStream()
        bytesField(inner, 1, hash)
        return outerMessage(40, inner.toByteArray())
    }

    private fun outerMessage(field: Int, payload: ByteArray): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        varintField(o, 1, PROTOCOL_VERSION)
        varintField(o, 2, STATUS_OK)
        o.write(lenDelimited(field, payload))
        return o.toByteArray()
    }

    // ---------------- remote messages (remotemessage / RemoteMessage) ----------------

    /** Connect hone par bhejte hain: Configure{code1=features bit, device_info}. */
    fun configure(activeFeatures: Int = 1): ByteArray {   // 1 = IME (current app pata chalta hai); voice bit 2
        val di = java.io.ByteArrayOutputStream()
        strField(di, 4, "1")                    // unknown2 = "1" (reference client jaisa)
        strField(di, 5, SERVICE_NAME)           // package_name = "atvremote"
        strField(di, 6, "1.0.0")                // app_version
        varintField(di, 3, 1)                   // unknown1 = 1
        val inner = java.io.ByteArrayOutputStream()
        varintField(inner, 1, activeFeatures)
        inner.write(lenDelimited(2, di.toByteArray()))
        return remoteMessage(1, inner.toByteArray())
    }

    /** Key press: RemoteKeyInject{key_code, direction}. */
    fun keyInject(keyCode: Int, direction: Int = DIR_SHORT): ByteArray {
        val inner = java.io.ByteArrayOutputStream()
        varintField(inner, 1, keyCode)
        varintField(inner, 2, direction)
        return remoteMessage(10, inner.toByteArray())
    }

    /** App deep link: RemoteAppLinkLaunchRequest{app_link}. App id ho to "market://launch?id=" lagta hai. */
    fun deepLink(appLink: String): ByteArray {
        val inner = java.io.ByteArrayOutputStream()
        strField(inner, 1, appLink.take(300))
        return remoteMessage(90, inner.toByteArray())
    }

    private fun remoteMessage(field: Int, payload: ByteArray): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        o.write(lenDelimited(field, payload))
        return o.toByteArray()
    }

    // ---------------- framing: varint length + message ----------------

    fun frame(msg: ByteArray): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        writeVarint(o, msg.size)
        o.write(msg)
        return o.toByteArray()
    }

    // ---------------- decoder (TV se aane wale messages ke liye — sirf jitna chahiye) ----------------

    /** Ek frame parse karo: (message bytes, next pos) ya null. */
    fun parseFrame(b: ByteArray, pos: Int = 0): Pair<ByteArray, Int>? {
        val (len, p) = readVarint(b, pos)
        if (len < 0 || p < 0 || p + len > b.size) return null
        return Pair(b.copyOfRange(p, p + len.toInt()), p + len.toInt())
    }

    /** OuterMessage se (status, fieldNumber->payload) — pairing state machine isse chalti hai. */
    fun parseOuter(msg: ByteArray): Map<Int, ByteArray> {
        val out = HashMap<Int, ByteArray>()
        var pos = 0
        while (pos < msg.size) {
            val (t, p1) = readVarint(msg, pos)
            if (t < 0 || p1 < 0) break
            val field = (t ushr 3).toInt()
            val wire = (t and 7).toInt()
            if (wire == 0) {
                val (v, p2) = readVarint(msg, p1)
                if (v < 0 || p2 < 0) break
                out[field] = v.toString().toByteArray()   // varint ko string bytes me (status ke liye)
                pos = p2
            } else if (wire == 2) {
                val (l, p2) = readVarint(msg, p1)
                if (l < 0 || p2 < 0 || p2 + l > msg.size) break
                out[field] = msg.copyOfRange(p2, p2 + l.toInt())
                pos = p2 + l.toInt()
            } else break   // humming length-delimited aur varint hi use hota hai in protos me
        }
        return out
    }

    /** RemoteMessage parse (TV ka configure/error). Sirf field->payload. */
    fun parseRemote(msg: ByteArray): Map<Int, ByteArray> = parseOuter(msg)

    /** PIN validation + secret hash — polo.py ka exact algorithm:
     *  pin 6 hex chars; sha256(clientMod || clientExp || serverMod || serverExp || pin[2:] bytes);
     *  hash ka pehla byte pin[0:2] (hex) ke barabar hona chahiye, warna PIN galat. */
    fun pairingSecret(pin: String?, clientModulus: ByteArray, clientExponent: ByteArray,
                      serverModulus: ByteArray, serverExponent: ByteArray): ByteArray? {
        if (pin == null) return null
        val p = pin.trim().uppercase()
        if (p.length != 6 || !p.all { it.isDigit() || it in 'A'..'F' }) return null
        val tail = try {
            hexToBytes(p.substring(2))
        } catch (e: Exception) { return null }
        val h = java.security.MessageDigest.getInstance("SHA-256")
        h.update(clientModulus); h.update(clientExponent); h.update(serverModulus); h.update(serverExponent); h.update(tail)
        val digest = h.digest()
        val first = try { p.substring(0, 2).toInt(16) } catch (e: Exception) { return null }
        return if (digest[0].toInt() and 0xFF == first) digest else null
    }

    fun intToBigEndianBytes(v: java.math.BigInteger): ByteArray {
        var b = v.toByteArray()
        if (b.isNotEmpty() && b[0].toInt() == 0) b = b.copyOfRange(1, b.size)   // leading zero hatao ( polo _int_to_bytes jaisa)
        return b
    }

    fun hexToBytes(s: String): ByteArray {
        val o = ByteArray(s.length / 2)
        for (i in o.indices) o[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return o
    }
}
