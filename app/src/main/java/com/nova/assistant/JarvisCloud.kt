package com.nova.assistant

import android.content.Context
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * v7.217 CENTRAL CLOUD JARVIS BRAIN, phone side (Part 1). Add-only: purane koi feature isse badal nahi jaate —
 * cloud OPTIONAL hai; bina cloud ke app bilkul pehle jaisa chalta hai.
 *
 * Server: backend/brain_server.py (owner ka apna, free, self-hosted). Contract:
 *   POST {base}/api/register   {code, name, platform}                    -> {device_id, device_token, family_key}
 *   POST {base}/api/status     Bearer <device_token> {bat, net, run}     -> {ok, now}      (heartbeat)
 *   POST {base}/api/tasks/...  Bearer <device_token>  (push/pull/claim/done)
 *   POST {base}/api/memory/... Bearer <device_token>  (push/pull; scope private = sirf is device ko wapas)
 *   POST {base}/api/sync       Bearer <device_token>  {items:[{key,val,ver,ts}]} -> {items, conflicts}  (LWW)
 *   GET  {base}/api/status                                              -> public {ok, version, devices_online}
 *
 * Pairing code (admin panel se milta hai): JARVIS-CLOUD:<base64url({"a":"https://...","p":"<one-time token>"})>
 * Register ke baad device_token + family_key SecureStore me (slot "cloudbrain" + "cloudfamily").
 * E2E: memory/tasks ka TEXT family key se AES-256-GCM me seal hota hai (LaptopLink wala hi scheme) —
 * server sirf opaque blob dekhta hai, merge metadata (ver/ts/device) se hota hai.
 * API keys (Gemini/Groq/...) kabhi cloud par nahi jaate — wo purana invariant hai aur waisa hi rahega.
 */
object JarvisCloud {
    const val SLOT = "cloudbrain"          // SecureStore: addr + " " + device_token (ek slot, space-separated)
    const val SLOT_FAMILY = "cloudfamily"  // SecureStore: E2E family key (base64url, 32 bytes)
    private const val MAX_BODY = 8_192     // hum itna hi bhejte hain; server ka cap 64 KB
    private const val CONNECT_MS = 6000
    private const val READ_MS = 10_000

    // ------------------------------------------------------------------ pure rules (unit tested)

    object Rules {
        private val CODE_KEY = Regex("^[A-Za-z0-9_=-]{20,300}$")

        /** "JARVIS-CLOUD:<b64>" -> (addr, oneTimeToken); null = galat code. */
        fun parseCode(raw: String?): Pair<String, String>? {
            if (raw == null || raw.length > 400) return null
            val t = raw.trim()
            val i = t.indexOf(':')
            if (i < 0 || !t.substring(0, i).trim().equals("JARVIS-CLOUD", ignoreCase = true)) return null
            val payload = try {
                String(Base64.getUrlDecoder().decode(t.substring(i + 1).trim()), Charsets.UTF_8)
            } catch (e: Exception) { return null }
            val j = try { JSONObject(payload) } catch (e: Exception) { return null }
            val addr = cleanBase(j.optString("a"))
            val tok = j.optString("p")
            if (addr.isEmpty() || !CODE_KEY.matches(tok)) return null
            return Pair(addr, tok)
        }

        /** JarvisVoiceApi.cleanBase jaisa hi: https-only (localhost exception), koi query/user@pass nahi. */
        fun cleanBase(raw: String?): String {
            var s = (raw ?: "").trim()
            if (s.isEmpty() || s.length > 300 || s.any { it.isWhitespace() }) return ""
            val lower = s.lowercase()
            val scheme = when { lower.startsWith("https://") -> "https"; lower.startsWith("http://") -> "http"; else -> return "" }
            var rest = s.substring(scheme.length + 3)
            if (rest.contains('?') || rest.contains('#') || rest.contains('@') || rest.contains('\\')) return ""
            while (rest.endsWith("/")) rest = rest.dropLast(1)
            if (rest.isEmpty()) return ""
            val host = rest.substringBefore('/').substringBefore(':').lowercase()
            if (host.isEmpty() || !host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return ""
            val port = rest.substringBefore('/').substringAfter(':', "")
            if (port.isNotEmpty() && (port.length > 5 || !port.all { it.isDigit() })) return ""
            if (scheme == "http" && host != "127.0.0.1" && host != "localhost") return ""
            if (scheme == "https" && !host.contains('.')) return ""
            return scheme + "://" + rest
        }

        /**
         * LWW winner: (ver, ts, deviceId) tuple compare — server, Kotlin aur JS teeno me EXACT same order
         * (tools/test_cloud_brain_contract.py fixture vectors se check hota hai). ver/ts NUMERIC compare,
         * tie-break device id STRING compare se, taaki dono taraf same winner nikle.
         */
        fun <T> lwwWinner(mine: T, myVer: Int, myTs: Long, myDevice: String,
                          theirs: T, theirVer: Int, theirTs: Long, theirDevice: String): T {
            if (myVer != theirVer) return if (myVer > theirVer) mine else theirs
            if (myTs != theirTs) return if (myTs > theirTs) mine else theirs
            return if (myDevice > theirDevice) mine else theirs   // poora tie -> theirs (server ka value already pada hai)
        }

        /** Offline queue: koshish kam se kam rehti hai — attempts ke hisaab se delay (1 min -> 15 min cap). */
        fun retryDelayMs(attempts: Int): Long =
            when {
                attempts <= 0 -> 0L
                attempts == 1 -> 60_000L
                attempts == 2 -> 5 * 60_000L
                else -> 15 * 60_000L
            }

        /** Privacy: private fact kabhi doosre device par nahi jaata — upload SE PEHLE hi filter. */
        fun uploadable(scope: String, isOwnerDevice: Boolean): Boolean =
            if (scope == "private") isOwnerDevice && false else true   // private = sirf local; server par private sirf usi device ka backup

        /** Platform label (server ko target filtering ke liye). */
        fun platform(): String = "android"
    }

    // ------------------------------------------------------------------ E2E payload seal (LaptopLink wala scheme, family key)

    private fun sealText(familyKey: String, topic: String, text: String): String = try {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(Base64.getUrlDecoder().decode(familyKey), "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(topic.toByteArray())
        val o = JSONObject().put("t", text.take(800)).put("ts", System.currentTimeMillis())
        Base64.getUrlEncoder().withoutPadding().encodeToString(iv + c.doFinal(o.toString().toByteArray()))
    } catch (e: Exception) { text }   // family key kharab ho to plaintext (server owner ka hai) — sync rukta nahi

    fun openText(familyKey: String, topic: String, env: String): String = try {
        val raw = Base64.getUrlDecoder().decode(env)
        if (raw.size < 29) env
        else {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(Base64.getUrlDecoder().decode(familyKey), "AES"), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            c.updateAAD(topic.toByteArray())
            JSONObject(String(c.doFinal(raw.copyOfRange(12, raw.size)))).optString("t").ifBlank { env }
        }
    } catch (e: Exception) { env }

    // ------------------------------------------------------------------ saved state (SecureStore)

    /** (addr, deviceToken) ya null. */
    fun saved(ctx: Context): Pair<String, String>? {
        val v = try { SecureStore.getSlot(ctx, SLOT) } catch (e: Throwable) { "" }
        val p = v.trim().split(" ")
        if (p.size != 2 || Rules.cleanBase(p[0]).isEmpty() || p[1].length < 20) return null
        return Pair(p[0], p[1])
    }

    fun familyKey(ctx: Context): String = try { SecureStore.getSlot(ctx, SLOT_FAMILY) } catch (e: Throwable) { "" }

    /** Pairing code se register. Ok = token save ho gaya. Error text owner ki bhasha me. */
    fun register(ctx: Context, code: String, name: String): String {
        val (addr, one) = Rules.parseCode(code) ?: return "invalid"
        val body = JSONObject().put("code", "JARVIS-CLOUD:" + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(JSONObject().put("a", addr).put("p", one).toString().toByteArray()))
            .put("name", name.take(40).ifBlank { "phone" }).put("platform", Rules.platform()).toString()
        val (code2, resp) = post("$addr/api/register", body, null)
        if (code2 != 200) return "fail"
        return try {
            val tok = resp.getString("device_token")
            val fam = resp.optString("family_key")
            if (tok.length < 20) return "fail"
            SecureStore.saveSlot(ctx, SLOT, addr + " " + tok)
            if (fam.length >= 40) SecureStore.saveSlot(ctx, SLOT_FAMILY, fam)
            "ok"
        } catch (e: Exception) { "fail" }
    }

    fun clear(ctx: Context) {
        try { SecureStore.clearSlot(ctx, SLOT); SecureStore.clearSlot(ctx, SLOT_FAMILY) } catch (e: Throwable) { }
    }

    // ------------------------------------------------------------------ API calls (Harmony: jaruri error hi bolta hai)

    /** (httpCode, json) — 0 = koi network error. Body cap, https-only, token header me. */
    private fun post(url: String, body: String, token: String?): Pair<Int, JSONObject> = try {
        val h = URL(url).openConnection() as HttpURLConnection
        h.requestMethod = "POST"; h.doOutput = true
        h.connectTimeout = CONNECT_MS; h.readTimeout = READ_MS
        h.setRequestProperty("Content-Type", "application/json")
        if (token != null) h.setRequestProperty("Authorization", "Bearer $token")
        h.setFixedLengthStreamingMode(minOf(body.length, MAX_BODY).toLong())
        h.outputStream.use { os: OutputStream -> os.write(body.take(MAX_BODY).toByteArray()) }
        val code = h.responseCode
        val txt = try { (if (code in 200..299) h.inputStream else h.errorStream)?.bufferedReader()?.use { it.readText() } } catch (e: Exception) { null }
        h.disconnect()
        val j = try { JSONObject(txt ?: "") } catch (e: Exception) { JSONObject() }
        Pair(code, j)
    } catch (e: Exception) { Pair(0, JSONObject()) }

    private fun get(url: String): Pair<Int, JSONObject> = try {
        val h = URL(url).openConnection() as HttpURLConnection
        h.connectTimeout = CONNECT_MS; h.readTimeout = READ_MS
        val code = h.responseCode
        val txt = try { (if (code in 200..299) h.inputStream else h.errorStream)?.bufferedReader()?.use { it.readText() } } catch (e: Exception) { null }
        h.disconnect()
        Pair(code, try { JSONObject(txt ?: "") } catch (e: Exception) { JSONObject() })
    } catch (e: Exception) { Pair(0, JSONObject()) }

    /** Heartbeat: bat/net/run. true = server ne liya. */
    fun sendStatus(ctx: Context, bat: Int, net: String, run: Boolean): Boolean {
        val s = saved(ctx) ?: return false
        val body = JSONObject().put("bat", bat).put("net", net.take(10)).put("run", run).toString()
        return post(s.first + "/api/status", body, s.second).first == 200
    }

    /** Public status (admin/login ke bina) — website bhi yahi dikhata hai. */
    fun publicStatus(addr: String): JSONObject? {
        val a = Rules.cleanBase(addr) ?: return null
        val (c, j) = get("$a/api/status")
        return if (c == 200 && j.optBoolean("ok")) j else null
    }

    /** Task push (E2E seal ke saath). */
    fun taskPush(ctx: Context, id: String, title: String, target: String): Boolean {
        val s = saved(ctx) ?: return false
        val fam = familyKey(ctx)
        val sealed = if (fam.length >= 40) sealText(fam, "task:$id", title) else title
        val body = JSONObject().put("id", id.take(64)).put("title", sealed.take(800))
            .put("target", target.take(20)).put("ver", 1).put("ts", System.currentTimeMillis() / 1000).toString()
        return post(s.first + "/api/tasks/push", body, s.second).first == 200
    }

    /** Task pull + unseal — list of (id, title, target, state, claimedBy). */
    fun taskPull(ctx: Context, since: Long): List<Array<String>> {
        val s = saved(ctx) ?: return emptyList()
        val fam = familyKey(ctx)
        val body = JSONObject().put("since", since).toString()
        val (c, j) = post(s.first + "/api/tasks/pull", body, s.second)
        if (c != 200) return emptyList()
        val out = ArrayList<Array<String>>()
        val arr = j.optJSONArray("tasks") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val id = t.optString("id")
            var title = t.optString("title")
            if (fam.length >= 40) title = openText(fam, "task:$id", title)
            out.add(arrayOf(id, title, t.optString("target"), t.optString("state"), t.optString("claimed_by")))
        }
        return out
    }

    fun taskClaim(ctx: Context, id: String): Boolean {
        val s = saved(ctx) ?: return false
        return post(s.first + "/api/tasks/claim", JSONObject().put("id", id.take(64)).toString(), s.second).first == 200
    }

    fun taskDone(ctx: Context, id: String, result: String): Boolean {
        val s = saved(ctx) ?: return false
        val fam = familyKey(ctx)
        val sealed = if (fam.length >= 40) sealText(fam, "task:$id:r", result) else result
        return post(s.first + "/api/tasks/done", JSONObject().put("id", id.take(64)).put("result", sealed.take(800)).toString(), s.second).first == 200
    }

    /** Memory fact push. scope "private" = sirf yahi device. */
    fun memoryPush(ctx: Context, id: String, text: String, scope: String): Boolean {
        val s = saved(ctx) ?: return false
        val fam = familyKey(ctx)
        val sealed = if (fam.length >= 40) sealText(fam, "mem:$id", text) else text
        val body = JSONObject().put("id", id.take(64)).put("text", sealed.take(800))
            .put("scope", if (scope == "private") "private" else "all")
            .put("ver", 1).put("ts", System.currentTimeMillis() / 1000).toString()
        return post(s.first + "/api/memory/push", body, s.second).first == 200
    }

    /** Memory pull + unseal — list of (id, text, scope). */
    fun memoryPull(ctx: Context, since: Long): List<Array<String>> {
        val s = saved(ctx) ?: return emptyList()
        val fam = familyKey(ctx)
        val (c, j) = post(s.first + "/api/memory/pull", JSONObject().put("since", since).toString(), s.second)
        if (c != 200) return emptyList()
        val out = ArrayList<Array<String>>()
        val arr = j.optJSONArray("facts") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val id = f.optString("id")
            var text = f.optString("text")
            if (fam.length >= 40) text = openText(fam, "mem:$id", text)
            out.add(arrayOf(id, text, f.optString("scope")))
        }
        return out
    }

    /** Generic KV sync (LWW). Server jeeta hua wapas bhejta hai; conflicts me hamara purana hota hai. */
    fun sync(ctx: Context, items: List<Pair<String, String>>): JSONObject? {
        val s = saved(ctx) ?: return null
        val arr = org.json.JSONArray()
        for ((k, v) in items.take(50)) arr.put(JSONObject().put("key", k.take(64)).put("val", v.take(4000))
            .put("ver", 1).put("ts", System.currentTimeMillis() / 1000))
        val (c, j) = post(s.first + "/api/sync", JSONObject().put("items", arr).toString(), s.second)
        return if (c == 200) j else null
    }
}
