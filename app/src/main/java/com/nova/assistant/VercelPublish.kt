package com.nova.assistant

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v7.05 H: publish the newest website on Vercel with the owner's OWN Vercel token (CONNECT tab). One POST to the deployments API
 * with index.html inline (media is already embedded as data: URIs). The token goes in a request header only (never a URL or a log).
 * The result URL comes ONLY from Vercel's answer, never invented. Called only after the owner's yes (askFirst) and only when the
 * Vercel connector has WRITE permission. Evidence: written + compiled with stubs + unit tested body/URL parsing; real Vercel call NOT TESTED.
 */
object VercelPublish {
    class Out(val ok: Boolean, val url: String = "", val msg: String = "")

    const val MAX_BYTES = 12_000_000

    /** Pure: Vercel project names are lower-case letters, digits and '-'. */
    fun projectName(subject: String, stamp: Long): String {
        val s = SiteGen.slug(subject).replace('_', '-')
        return ("nova-" + s + "-" + (stamp % 10000)).take(60)
    }

    /** Pure: the deployment request body. */
    fun buildBody(name: String, html: String): String {
        val file = JSONObject().put("file", "index.html").put("data", html).put("encoding", "utf-8")
        return JSONObject()
            .put("name", name)
            .put("files", org.json.JSONArray().put(file))
            .put("projectSettings", JSONObject().put("framework", JSONObject.NULL))
            .put("target", "production")
            .toString()
    }

    /** Pure: "https://..." from Vercel's answer (first alias when there is one, else url). Empty when the answer has none. */
    fun parseUrl(body: String): String {
        return try {
            val o = JSONObject(body)
            var host = ""
            val al = o.optJSONArray("alias")
            if (al != null && al.length() > 0) host = al.optString(0)
            if (host.isBlank()) host = o.optString("url")
            host = host.trim()
            if (host.isEmpty() || !host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) "" else "https://$host"
        } catch (e: Exception) { "" }
    }

    private fun failFor(code: Int): String = when (code) {
        401, 403 -> "Vercel token galat hai ya permission nahi hai. CONNECT tab mein dobara jodo"
        400, 409, 422 -> "Vercel ne website nahi li. Thodi der baad dobara try karo"
        429 -> "Vercel abhi busy hai (limit). Thodi der baad try karo"
        598 -> "Internet nahi mil raha"
        else -> "Vercel par abhi publish nahi ho paya. Thodi der baad dobara try karo"
    }

    fun run(ctx: Context, subject: String, html: String): Out {
        if (!ConnectorStore.connected(ctx, ConnectorCatalog.VERCEL)) return Out(false, "", "Vercel connect nahi hai. CONNECT tab mein token jodo")
        if (!ConnectorCatalog.allows(ConnectorStore.perm(ctx, ConnectorCatalog.VERCEL), true)) return Out(false, "", "Vercel par sirf padhne ki ijazat hai. CONNECT tab mein WRITE chalu karo")
        val token = ConnectorStore.token(ctx, ConnectorCatalog.VERCEL)
        if (token.isEmpty()) return Out(false, "", "Vercel token nahi mila. CONNECT tab mein jodo")
        if (html.length < 200 || html.length > MAX_BYTES) return Out(false, "", "Website file theek nahi hai")
        return try {
            val c = URL("https://api.vercel.com/v13/deployments?skipAutoDetectionConfirmation=1").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 90000
            c.instanceFollowRedirects = false
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("User-Agent", "NOVA-assistant")
            c.outputStream.use { it.write(buildBody(projectName(subject, System.currentTimeMillis()), html).toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            if (code !in 200..299) return Out(false, "", failFor(code))
            val url = parseUrl(text)
            if (url.isEmpty()) Out(false, "", "Vercel ne link nahi diya. Vercel dashboard mein dekho") else Out(true, url, "")
        } catch (e: java.net.UnknownHostException) {
            Out(false, "", failFor(598))
        } catch (e: Exception) {
            Out(false, "", failFor(599))
        }
    }
}
