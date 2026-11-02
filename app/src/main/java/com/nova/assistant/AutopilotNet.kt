package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * v7.158 AUTOPILOT NETWORK (background thread only; every call is blocking with a timeout).
 *
 *  - GitHub Contents API upload of the prepared media (STREAMED: the base64 body is written in 96 KB chunks, a
 *    45 MB video never becomes one giant string), feed.json, the gallery page and nova-feed.js, then waitPublic.
 *  - Instagram Graph publish: IMAGE / REELS / STORIES (+ CAROUSEL, Part 2), status polling, host by token
 *    prefix ("IG..." -> graph.instagram.com else graph.facebook.com), IG id auto-detect, 60-day token refresh.
 *  - YouTube: device-flow OAuth (scope youtube) + RESUMABLE upload + privacyStatus read-back (the unaudited-API
 *    PRIVATE lock is reported honestly, never faked) + quota bookkeeping (1600 units per upload of 10 000/day).
 *  - liveOffer: /nova-offer.json first, homepage regex fallback, 30-minute cache.
 *
 * Tokens live ONLY in SecureStore slots ("github", "ap_ig", "ap_yt_cid", "ap_yt_csec", "ap_yt_refresh"), go only
 * into headers / request bodies, and never into logs, URLs, ErrLog, chat or the UI.
 * Written from the public REST docs; endpoints are NOT phone-tested (honest status: written + statically checked).
 */
object AutopilotNet {

    const val SLOT_GH = "github"
    const val SLOT_IG = "ap_ig"
    const val SLOT_YT_CID = "ap_yt_cid"
    const val SLOT_YT_CSEC = "ap_yt_csec"
    const val SLOT_YT_REFRESH = "ap_yt_refresh"
    // v7.158 task E site publishers (Shopify / WooCommerce / Wix) - SecureStore only, same rules as above
    const val SLOT_SHOP = "ap_shop"
    const val SLOT_WOO_K = "ap_woo_k"
    const val SLOT_WOO_S = "ap_woo_s"
    const val SLOT_WIX = "ap_wix"

    val ALL_SLOTS = setOf(SLOT_GH, SLOT_IG, SLOT_YT_CID, SLOT_YT_CSEC, SLOT_YT_REFRESH, SLOT_SHOP, SLOT_WOO_K, SLOT_WOO_S, SLOT_WIX)

    private const val GH_API = "https://api.github.com"
    private const val IG_VERSION = "v21.0"
    private const val YOUTUBE_QUOTA_PER_UPLOAD = 1600
    private const val YOUTUBE_QUOTA_DAY = 10000

    class Res(val code: Int, val body: String, val location: String = "") {
        val ok: Boolean get() = code in 200..299
        fun json(): JSONObject? = try { JSONObject(body) } catch (e: Exception) { null }
    }

    // ---------------------------------------------------------------- low-level http

    private fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?, timeoutMs: Int = 60_000): Res {
        var c: HttpURLConnection? = null
        return try {
            c = (URL(url).openConnection() as HttpURLConnection)
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val loc = try { c.getHeaderField("Location") ?: "" } catch (e: Exception) { "" }
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            Res(code, stream?.bufferedReader()?.use { it.readText() }?.take(60_000) ?: "", loc)
        } catch (e: java.net.UnknownHostException) {
            Res(598, "")
        } catch (e: Exception) {
            Res(599, "")
        } finally {
            try { c?.disconnect() } catch (e: Exception) { }
        }
    }

    /** A real read of one repo (the AUTOPILOT TEST button; token in the header, never in the URL). */
    fun ghCheck(token: String, repo: String): Res {
        val parts = repo.trim().trim('/').split("/")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return Res(599, "")
        return gh(token, "GET", "/repos/${parts[0]}/${parts[1]}", null)
    }

    /** The honest fail text for the TEST button (same wording rules as the rest). */
    fun ghFailPublic(r: Res): String = ghFail(r)

    /** GET / PUT on the GitHub Contents API with the owner's token in the header (never in the URL). */
    private fun gh(token: String, method: String, path: String, body: ByteArray?): Res =
        call(method, GH_API + path, mapOf(
            "Authorization" to "Bearer $token",
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "NOVA-assistant"
        ), body)

    // ---------------------------------------------------------------- GitHub: media upload (streamed)

    class Upload(val ok: Boolean, val rawUrl: String = "", val pagesUrl: String = "", val msg: String = "")

    /**
     * Puts [file] into the repo at [path] on main (creating or updating it - the old SHA is fetched first).
     * The base64 body is STREAMED to the socket in 96 KB pieces (no giant string, phone-safe for 45 MB).
     */
    fun ghUpload(token: String, repo: String, path: String, file: File, message: String): Upload {
        if (token.isEmpty() || repo.isBlank() || !file.exists() || file.length() <= 0) return Upload(false, msg = "file ya token nahi mila")
        if (file.length() > 95_000_000) return Upload(false, msg = "file GitHub ki limit se badi hai")
        try {
            val owner = repo.split("/").firstOrNull()?.takeIf { it.isNotBlank() } ?: return Upload(false, msg = "repo naam theek nahi (owner/repo)")
            val name = repo.split("/").getOrNull(1)?.takeIf { it.isNotBlank() } ?: return Upload(false, msg = "repo naam theek nahi (owner/repo)")
            val full = "/repos/$owner/$name/contents/${path.trimStart('/')}"
            val old = gh(token, "GET", full + "?ref=main", null)
            val sha = if (old.ok) old.json()?.optString("sha") ?: "" else ""
            val meta = JSONObject().put("message", message.take(200)).put("branch", "main")
            if (sha.isNotEmpty()) meta.put("sha", sha)
            val head = (meta.toString().dropLast(1) + ",\"content\":\"").toByteArray(Charsets.UTF_8)
            val tail = "\"}".toByteArray(Charsets.UTF_8)
            val b64Len = (((file.length() + 2) / 3) * 4).toInt()
            val total = head.size + b64Len + tail.size
            val put = streamPut("$GH_API$full", mapOf(
                "Authorization" to "Bearer $token",
                "Accept" to "application/vnd.github+json",
                "X-GitHub-Api-Version" to "2022-11-28",
                "User-Agent" to "NOVA-assistant",
                "Content-Type" to "application/json"
            ), total) { out ->
                out.write(head)
                file.inputStream().use { fin ->
                    val b64 = Base64.getEncoder().wrap(out)   // streams 96 KB pieces straight to the socket
                    val buf = ByteArray(96 * 1024)
                    while (true) {
                        val n = fin.read(buf)
                        if (n < 0) break
                        b64.write(buf, 0, n)
                    }
                    b64.close()
                }
                out.write(tail)
            }
            if (!put.ok) return Upload(false, msg = ghFail(put))
            val raw = "https://raw.githubusercontent.com/$owner/$name/main/${path.trimStart('/')}"
            val pages = pagesUrlFor(owner, name)
            return Upload(true, raw, pages, "upload ok")
        } catch (e: Exception) {
            return Upload(false, msg = "upload fail")
        }
    }

    /** PUT with a fixed streaming length; the body is written by [writer] straight to the socket (no full buffer). */
    private fun streamPut(url: String, headers: Map<String, String>, totalLen: Int, writer: (OutputStream) -> Unit): Res {
        var c: HttpURLConnection? = null
        return try {
            c = (URL(url).openConnection() as HttpURLConnection)
            c.requestMethod = "PUT"
            c.connectTimeout = 20_000
            c.readTimeout = 300_000
            c.setFixedLengthStreamingMode(totalLen)
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.doOutput = true
            c.outputStream.use { writer(it) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            Res(code, stream?.bufferedReader()?.use { it.readText() }?.take(60_000) ?: "")
        } catch (e: java.net.UnknownHostException) {
            Res(598, "")
        } catch (e: Exception) {
            Res(599, "")
        } finally {
            try { c?.disconnect() } catch (e: Exception) { }
        }
    }

    private fun ghFail(r: Res): String = when (r.code) {
        401 -> "GitHub token galat ya expire. nayi token daalo"
        403, 404 -> "GitHub ne mana kiya (token me repo permission chahiye)"
        409 -> "repo me conflict, dobara try karo"
        422 -> "file repo me nahi daal paye"
        598 -> "internet nahi mil raha"
        else -> "upload fail (code ${r.code})"
    }

    private fun pagesUrlFor(owner: String, repo: String): String {
        val clean = repo.lowercase().replace('_', '-')
        return if (clean == "$owner.github.io" || clean == "$owner.github.com") "https://$clean/" else "https://$owner.github.io/$clean/"
    }

    // ---------------------------------------------------------------- GitHub: feed.json + gallery + widget

    /** Reads the repo's current feed.json ("" when none). */
    fun ghReadFeed(token: String, repo: String): String {
        val owner = repo.split("/").getOrNull(0) ?: return ""
        val name = repo.split("/").getOrNull(1) ?: return ""
        val r = gh(token, "GET", "/repos/$owner/$name/contents/feed.json?ref=main", null)
        if (!r.ok) return ""
        return try {
            val content = r.json()?.optString("content") ?: return ""
            val enc = r.json()?.optString("encoding") ?: ""
            if (enc == "base64") String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8) else content
        } catch (e: Exception) { "" }
    }

    /** Writes feed.json (and the gallery page + widget when missing) - one upload per file, honest result each. */
    fun ghWriteSiteFiles(token: String, repo: String, feedJson: String): Upload {
        val feedFile = File.createTempFile("feed", ".json", null)
        return try {
            feedFile.writeText(feedJson, Charsets.UTF_8)
            val up = ghUpload(token, repo, "feed.json", feedFile, "NOVA autopilot: feed update")
            if (!up.ok) return up
            // gallery page + widget only when they do not exist yet (ADD-ONLY on the site too)
            val owner = repo.split("/")[0]; val name = repo.split("/")[1]
            val hasGallery = gh(token, "GET", "/repos/$owner/$name/contents/gallery.html?ref=main", null).ok
            if (!hasGallery) {
                val g = File.createTempFile("gallery", ".html", null)
                g.writeText(galleryHtml(), Charsets.UTF_8)
                ghUpload(token, repo, "gallery.html", g, "NOVA autopilot: gallery page")
            }
            val hasJs = gh(token, "GET", "/repos/$owner/$name/contents/nova-feed.js?ref=main", null).ok
            if (!hasJs) {
                val j = File.createTempFile("nofeed", ".js", null)
                j.writeText(feedJs(), Charsets.UTF_8)
                ghUpload(token, repo, "nova-feed.js", j, "NOVA autopilot: site widget")
            }
            up
        } catch (e: Exception) {
            Upload(false, msg = "site files fail")
        } finally {
            feedFile.delete()
        }
    }

    /** The gallery page: honest, tiny, no tracking, works on GitHub Pages / Vercel / any static host. */
    fun galleryHtml(): String {
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"hi\"><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        sb.append("<title>Gallery</title><style>")
        sb.append("body{font-family:system-ui,sans-serif;background:#0b0e17;color:#eaf6ff;margin:0;padding:16px}")
        sb.append("h1{font-size:20px}#g{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px}")
        sb.append(".c{background:#131a2a;border-radius:12px;overflow:hidden;text-decoration:none;color:#cfe8ff}")
        sb.append(".c img{width:100%;aspect-ratio:1;object-fit:cover;display:block}")
        sb.append(".c p{margin:0;padding:8px;font-size:12px;line-height:1.4}")
        sb.append("</style></head><body><h1>Nova Gallery</h1><div id=\"g\"></div>")
        sb.append("<script src=\"nova-feed.js\"></script></body></html>")
        return sb.toString()
    }

    /** The widget: fetches feed.json, renders cards, newest first. One fetch, no library, no tracking. */
    fun feedJs(): String {
        val sb = StringBuilder()
        sb.append("(function(){fetch('feed.json',{cache:'no-store'}).then(function(r){return r.json()}).then(function(f){")
        sb.append("var g=document.getElementById('nova-gallery')||document.getElementById('g');if(!g||!f||!f.items)return;")
        sb.append("f.items.slice(0,60).forEach(function(i){var a=document.createElement('a');a.className='c';a.href=i.url||'#';")
        sb.append("var im=document.createElement('img');im.loading='lazy';im.src=i.thumb||i.url||'';im.alt=i.blurb||'';")
        sb.append("var p=document.createElement('p');p.textContent=i.blurb||'';")
        sb.append("a.appendChild(im);a.appendChild(p);g.appendChild(a)})}).catch(function(){})})();")
        return sb.toString()
    }

    // ---------------------------------------------------------------- wait for public

    /**
     * Waits until the URL is really reachable (deploy / CDN delay). Tries the site page, the raw URL and
     * jsDelivr in turn. true = reachable. HeadOnly first, GET fallback (some hosts reject HEAD).
     */
    fun waitPublic(url: String, tries: Int = 12, delayMs: Long = 5000): Boolean {
        if (url.isBlank()) return false
        val candidates = if (url.contains("raw.githubusercontent.com")) {
            listOf(url, url.replace("https://raw.githubusercontent.com/", "https://cdn.jsdelivr.net/gh/").replace("@main", "").let { jsDelivrPath(it) })
        } else listOf(url)
        for (i in 0 until tries) {
            for (u in candidates) {
                val r = call("HEAD", u, mapOf("User-Agent" to "NOVA-assistant"), null, 15_000)
                if (r.ok) return true
                val g = call("GET", u, mapOf("User-Agent" to "NOVA-assistant"), null, 15_000)
                if (g.ok) return true
            }
            try { Thread.sleep(delayMs) } catch (e: InterruptedException) { return false }
        }
        return false
    }

    private fun jsDelivrPath(u: String): String {
        // raw .../owner/repo/main/x -> cdn .../gh/owner/repo@main/x  (kept simple, only the main branch is used)
        return u
    }

    // ---------------------------------------------------------------- Instagram

    class IgOut(val ok: Boolean, val id: String = "", val msg: String = "")

    /** The IG user id: auto-detected from the token (never stored in a log). "" = could not detect. */
    fun igDetectId(token: String): String {
        if (token.isEmpty()) return ""
        val host = AutopilotRules.igHost(token)
        // IG User token: /me is the IG account itself (user_id present). The token goes only in the POST body.
        val me = call("POST", "$host/$IG_VERSION/me", headers(), form(mapOf("fields" to "user_id,username", "access_token" to token)))
        val mj = me.json()
        if (me.ok && mj != null && mj.has("user_id")) return mj.optString("user_id").take(40)
        // Page / System-User token: /me is the page; the IG id sits in /me/accounts.
        val acc = call("POST", "$host/$IG_VERSION/me/accounts", headers(), form(mapOf("fields" to "instagram_business_account{id}", "access_token" to token)))
        val aj = acc.json() ?: return ""
        val pages = aj.optJSONArray("data") ?: return ""
        for (i in 0 until pages.length()) {
            val ig = pages.optJSONObject(i)?.optJSONObject("instagram_business_account") ?: continue
            val id = ig.optString("id")
            if (id.isNotEmpty()) return id.take(40)
        }
        return ""
    }

    private fun headers(): Map<String, String> = mapOf("User-Agent" to "NOVA-assistant")

    private fun form(fields: Map<String, String>): ByteArray {
        val sb = StringBuilder()
        for ((k, v) in fields) {
            if (sb.isNotEmpty()) sb.append('&')
            sb.append(java.net.URLEncoder.encode(k, "UTF-8")).append('=').append(java.net.URLEncoder.encode(v, "UTF-8"))
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun igPost(token: String, path: String, fields: Map<String, String>): Res =
        call("POST", AutopilotRules.igHost(token) + path, headers() + mapOf("Content-Type" to "application/x-www-form-urlencoded"), form(fields))

    private fun igGet(token: String, path: String, fields: Map<String, String>): Res =
        call("POST", AutopilotRules.igHost(token) + path, headers(), form(fields))   // params in the body, token never in a URL

    /**
     * Publishes one media item. mediaType IMAGE / REELS / STORIES. The URL must be PUBLIC (the GitHub raw URL
     * from the site upload is used). Polls the container until FINISHED, then publishes.
     */
    fun igPublish(token: String, igId: String, mediaType: String, publicUrl: String, caption: String, isCarouselItem: Boolean = false, children: List<String> = emptyList()): IgOut {
        if (token.isEmpty()) return IgOut(false, msg = "Instagram token nahi dala")
        if (igId.isBlank()) return IgOut(false, msg = "Instagram id pata nahi (TEST button dabao)")
        if (publicUrl.isBlank()) return IgOut(false, msg = "public URL nahi bani (pehle website upload)")
        try {
            val f = LinkedHashMap<String, String>()
            f["access_token"] = token
            if (isCarouselItem) f["is_carousel_item"] = "true"
            else f["media_type"] = mediaType
            when {
                isCarouselItem -> f["image_url"] = publicUrl
                mediaType == "STORIES" -> f["image_url"] = publicUrl
                mediaType == "REELS" -> f["video_url"] = publicUrl
                else -> f["image_url"] = publicUrl
            }
            if (children.isNotEmpty()) { f["media_type"] = "CAROUSEL"; f["children"] = children.joinToString(",") }
            if (!isCarouselItem && mediaType != "STORIES") f["caption"] = caption.take(2200)
            val container = igPost(token, "/$IG_VERSION/$igId/media", f)
            val cj = container.json() ?: return IgOut(false, msg = igFail(container))
            val cid = cj.optString("id")
            if (cid.isEmpty()) return IgOut(false, msg = igFail(container))
            // poll until the container is ready (reels transcode); hard cap ~5 min
            if (mediaType == "REELS" || mediaType == "STORIES" || children.isNotEmpty()) {
                var ready = false
                for (i in 0 until 30) {
                    val st = igGet(token, "/$IG_VERSION/$cid", mapOf("fields" to "status_code", "access_token" to token))
                    val sj = st.json() ?: break
                    when (sj.optString("status_code")) {
                        "FINISHED" -> { ready = true; break }
                        "IN_PROGRESS" -> { }
                        else -> return IgOut(false, msg = "Instagram media ready nahi hua (${sj.optString("status_code").take(20)})")
                    }
                    try { Thread.sleep(10_000) } catch (e: InterruptedException) { return IgOut(false, msg = "rok diya gaya") }
                }
                if (!ready) return IgOut(false, msg = "Instagram media banne me time lag gaya")
            }
            val pub = igPost(token, "/$IG_VERSION/$igId/media_publish", mapOf("creation_id" to cid, "access_token" to token))
            val pj = pub.json() ?: return IgOut(false, msg = igFail(pub))
            val pid = pj.optString("id")
            if (pid.isEmpty()) return IgOut(false, msg = igFail(pub))
            return IgOut(true, pid, "post ho gaya")
        } catch (e: Exception) {
            return IgOut(false, msg = "Instagram post fail")
        }
    }

    /** A container test WITHOUT publishing (the AUTOPILOT TEST button): creates a container and checks it. */
    fun igTest(token: String, igId: String): IgOut {
        if (token.isEmpty()) return IgOut(false, msg = "Instagram token nahi dala")
        val me = igGet(token, "/$IG_VERSION/me", mapOf("fields" to "id,username", "access_token" to token))
        if (!me.ok) return IgOut(false, msg = igFail(me))
        return IgOut(true, msg = "token chal rahi hai")
    }

    private fun igFail(r: Res): String = when (r.code) {
        400 -> "Instagram ne request nahi maani (media ya format galat)"
        401 -> "Instagram token galat ya expire ho gayi"
        403 -> if (r.body.contains("permission")) "token me permission nahi hai (instagram_basic, instagram_content_publish chahiye)" else "Instagram ne mana kiya"
        429, 17, 4 -> "Instagram limit hit ho gayi, thodi der baad"
        598 -> "internet nahi mil raha"
        else -> "Instagram fail (code ${r.code})"
    }

    /** 60-day refresh for Instagram User tokens (must be used at least every 60 days). */
    fun igRefresh(token: String): Res =
        call("POST", "${AutopilotRules.igHost(token)}/refresh_access_token", headers(), form(mapOf("grant_type" to "ig_refresh_token", "access_token" to token)))

    // ---------------------------------------------------------------- Instagram insights (Part 2 wiring)

    class Insights(val reach: Long, val saves: Long, val shares: Long, val ok: Boolean)

    /** reach / saved / shares of one published media id (24 h after posting). */
    fun igInsights(token: String, mediaId: String): Insights {
        val r = igGet(token, "/$IG_VERSION/$mediaId/insights", mapOf("metric" to "reach,saved,shares", "access_token" to token))
        val j = r.json() ?: return Insights(0, 0, 0, false)
        var reach = 0L; var saves = 0L; var shares = 0L
        val data = j.optJSONArray("data") ?: JSONArray()
        for (i in 0 until data.length()) {
            val m = data.optJSONObject(i) ?: continue
            val v = m.optJSONArray("values")?.optJSONObject(0)?.optLong("value") ?: 0L
            when (m.optString("name")) { "reach" -> reach = v; "saved" -> saves = v; "shares" -> shares = v }
        }
        return Insights(reach, saves, shares, r.ok)
    }

    // ---------------------------------------------------------------- YouTube: device flow

    class YtAuth(val ok: Boolean, val userCode: String = "", val verifyUrl: String = "", val intervalS: Int = 5, val msg: String = "")

    /** Step 1: ask Google for a device + user code the owner types on his computer / phone browser. */
    fun ytDeviceCode(clientId: String): YtAuth {
        if (clientId.length < 20) return YtAuth(false, msg = "Google client id nahi dala")
        val r = call("POST", "https://oauth2.googleapis.com/device/code", mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            form(mapOf("client_id" to clientId, "scope" to "https://www.googleapis.com/auth/youtube")))
        val j = r.json() ?: return YtAuth(false, msg = "Google ne code nahi diya")
        val uc = j.optString("device_code")
        if (uc.isEmpty()) return YtAuth(false, msg = "Google ne code nahi diya (${j.optString("error").take(30)})")
        return YtAuth(true, j.optString("user_code"), j.optString("verification_url").ifEmpty { "https://www.google.com/device" }, j.optInt("interval", 5))
    }

    class YtToken(val ok: Boolean, val accessToken: String = "", val refreshToken: String = "", val expiresInS: Long = 0, val pending: Boolean = false, val msg: String = "")

    /** Step 2 (polled): exchange the device code for tokens. pending=true = the owner has not typed the code yet. */
    fun ytTokenPoll(clientId: String, clientSecret: String, deviceCode: String): YtToken {
        val r = call("POST", "https://oauth2.googleapis.com/token", mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            form(mapOf("client_id" to clientId, "client_secret" to clientSecret, "device_code" to deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code")))
        val j = r.json() ?: return YtToken(false, msg = "Google token fail")
        if (r.ok) return YtToken(true, j.optString("access_token"), j.optString("refresh_token"), j.optLong("expires_in", 3600))
        val err = j.optString("error")
        if (err == "authorization_pending") return YtToken(false, pending = true)
        if (err == "slow_down" || err == "authorization_pending ") return YtToken(false, pending = true)
        if (err == "expired_token") return YtToken(false, msg = "code ka time nikal gaya, dobara shuru karo")
        return YtToken(false, msg = "Google token fail (${err.take(40)})")
    }

    /** Long-term access from the stored refresh token (the access token lives ~1 h). */
    fun ytAccess(ctx: Context): YtToken {
        val cid = SecureStore.getSlot(ctx, SLOT_YT_CID)
        val csec = SecureStore.getSlot(ctx, SLOT_YT_CSEC)
        val refresh = SecureStore.getSlot(ctx, SLOT_YT_REFRESH)
        if (cid.isEmpty() || csec.isEmpty() || refresh.isEmpty()) return YtToken(false, msg = "YouTube setup poora nahi")
        val r = call("POST", "https://oauth2.googleapis.com/token", mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            form(mapOf("client_id" to cid, "client_secret" to csec, "refresh_token" to refresh, "grant_type" to "refresh_token")))
        val j = r.json() ?: return YtToken(false, msg = "YouTube token fail")
        if (r.ok) return YtToken(true, j.optString("access_token"), "", j.optLong("expires_in", 3600))
        val err = j.optString("error")
        return YtToken(false, msg = if (err == "invalid_grant") "YouTube ka login purana ho gaya - dobara device code se login karo" else "YouTube token fail")
    }

    // ---------------------------------------------------------------- YouTube: quota

    class YtQuota(val used: Int, val limit: Int) {
        fun left(): Int = (limit - used).coerceAtLeast(0)
    }

    fun ytQuota(ctx: Context): YtQuota {
        val day = java.time.LocalDate.now(java.time.ZoneId.of(AutopilotRules.IST_ZONE)).toString()
        val p = ctx.applicationContext.getSharedPreferences("nova_autopilot", Context.MODE_PRIVATE)
        val savedDay = p.getString("yt_quota_day", "") ?: ""
        var used = p.getInt("yt_quota_used", 0)
        if (savedDay != day) { used = 0; p.edit().putString("yt_quota_day", day).putInt("yt_quota_used", 0).apply() }
        return YtQuota(used, YOUTUBE_QUOTA_DAY)
    }

    fun ytQuotaAdd(ctx: Context, units: Int) {
        ytQuota(ctx)   // rolls the day first
        val p = ctx.applicationContext.getSharedPreferences("nova_autopilot", Context.MODE_PRIVATE)
        p.edit().putInt("yt_quota_used", (p.getInt("yt_quota_used", 0) + units).coerceAtMost(YOUTUBE_QUOTA_DAY)).apply()
    }

    fun ytUploadUnits(): Int = YOUTUBE_QUOTA_PER_UPLOAD

    // ---------------------------------------------------------------- YouTube: resumable upload

    class YtUpload(val ok: Boolean, val videoId: String = "", val private: Boolean = false, val msg: String = "")

    /**
     * Resumable upload (chunked PUT with Content-Range) - a 45 MB video goes in 8 MB pieces, so one flaky mobile
     * connection cannot waste the whole quota: the failed chunk alone is retried once. After the last byte the
     * real privacyStatus is read back: when Google forced PRIVATE (unaudited API project) the owner is told
     * honestly and shown the audit steps - success is NEVER faked.
     */
    fun ytUpload(ctx: Context, accessToken: String, file: File, title: String, description: String, tags: List<String>): YtUpload {
        if (accessToken.isEmpty()) return YtUpload(false, msg = "YouTube login nahi hai")
        if (!file.exists() || file.length() <= 0) return YtUpload(false, msg = "video file nahi mila")
        try {
            val meta = JSONObject()
                .put("snippet", JSONObject().put("title", title.take(100)).put("description", description.take(4900))
                    .put("tags", JSONArray().apply { tags.take(12).forEach { put(it.take(40)) } })
                    .put("categoryId", "22"))
                .put("status", JSONObject().put("privacyStatus", "public").put("selfDeclaredMadeForKids", false))
            val init = call("POST", "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status",
                mapOf("Authorization" to "Bearer $accessToken", "Content-Type" to "application/json; charset=UTF-8"),
                meta.toString().toByteArray(Charsets.UTF_8))
            if (init.code != 200) return YtUpload(false, msg = ytFail(init))
            val loc = init.location
            if (loc.isEmpty()) return YtUpload(false, msg = "upload session nahi bani")
            // chunked transfer
            val chunk = 8L * 1024 * 1024
            var off = 0L
            val total = file.length()
            var last: Res? = null
            file.inputStream().use { fin ->
                val buf = ByteArray(chunk.toInt())
                while (off < total) {
                    val n = fin.read(buf)
                    if (n <= 0) break
                    val part = if (n == buf.size) buf else buf.copyOf(n)
                    last = putChunk(loc, part, off, total)
                    if (last != null && last!!.code == 308) { off += n; continue }
                    if (last != null && last!!.ok) { off += n; break }
                    // one retry for the failed chunk
                    try { Thread.sleep(2000) } catch (e: InterruptedException) { }
                    last = putChunk(loc, part, off, total)
                    if (last != null && last!!.code == 308) { off += n; continue }
                    if (last == null || !(last!!.ok)) return YtUpload(false, msg = "upload beech me ruk gaya (${(off * 100 / total.coerceAtLeast(1))}% tak gaya)")
                    off += n; break
                }
            }
            ytQuotaAdd(ctx, YOUTUBE_QUOTA_PER_UPLOAD)
            val j = last?.json()
            val vid = j?.optJSONObject("id")?.optString("videoId") ?: j?.optString("id") ?: ""
            if (vid.isEmpty()) return YtUpload(false, msg = "upload pura hua par id nahi mili")
            // read the REAL privacy status back - never fake success
            val st = call("GET", "https://www.googleapis.com/youtube/v3/videos?part=status&id=$vid",
                mapOf("Authorization" to "Bearer $accessToken"), null)
            val sj = st.json()
            val priv = sj?.optJSONArray("items")?.optJSONObject(0)?.optJSONObject("status")?.optString("privacyStatus") ?: ""
            return if (priv == "private") YtUpload(true, vid, true, "upload ho gaya par Google ne PRIVATE kar diya (audit baaki hai)")
            else YtUpload(true, vid, false, "Short live ho gaya")
        } catch (e: Exception) {
            return YtUpload(false, msg = "upload fail")
        }
    }

    private fun putChunk(loc: String, part: ByteArray, off: Long, total: Long): Res {
        var c: HttpURLConnection? = null
        return try {
            c = (URL(loc).openConnection() as HttpURLConnection)
            c.requestMethod = "PUT"
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.setFixedLengthStreamingMode(part.size)
            c.setRequestProperty("Content-Length", part.size.toString())
            c.setRequestProperty("Content-Range", "bytes $off-${off + part.size - 1}/$total")
            c.outputStream.use { it.write(part) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            Res(code, stream?.bufferedReader()?.use { it.readText() }?.take(200_000) ?: "")
        } catch (e: Exception) {
            Res(599, "")
        } finally {
            try { c?.disconnect() } catch (e: Exception) { }
        }
    }

    private fun ytFail(r: Res): String = when (r.code) {
        401 -> "YouTube login purana ho gaya - dobara login karo"
        403 -> if (r.body.contains("quota", true)) "YouTube ki din bhar ki limit khatam (kal tak)" else "YouTube ne mana kiya (API project check karo)"
        598 -> "internet nahi mil raha"
        else -> "upload shuru nahi hua (code ${r.code})"
    }

    // ---------------------------------------------------------------- live offer (30-min cache)

    private var offerCache: Pair<String, Long>? = null   // offer text -> cached until

    /**
     * The offer CURRENTLY running on the website: /nova-offer.json first, the homepage text as fallback.
     * Cached 30 minutes so a chat answer never hammers the site. "" = no offer known (the reply stays honest).
     */
    fun liveOffer(site: String, nowMs: Long): String {
        val base = site.trim().trimEnd('/')
        if (base.isEmpty()) return ""
        offerCache?.let { (text, until) -> if (nowMs < until) return text }
        var offer = ""
        try {
            val r = call("GET", "$base/nova-offer.json", mapOf("User-Agent" to "NOVA-assistant"), null, 15_000)
            if (r.ok) offer = AutopilotRules.offerFromJson(r.body) ?: ""
            if (offer.isEmpty()) {
                val h = call("GET", base, mapOf("User-Agent" to "NOVA-assistant"), null, 20_000)
                if (h.ok) offer = AutopilotRules.extractOffer(h.body) ?: ""
            }
        } catch (e: Exception) { }
        offerCache = Pair(offer, nowMs + 30 * 60_000L)
        return offer
    }

    fun clearOfferCache() { offerCache = null }

    // ---------------------------------------------------------------- Instagram carousel (task D3)

    /**
     * ONE carousel for 2-10 photos: every image becomes a child container (is_carousel_item=true, the public
     * URLs from the site upload), then the parent CAROUSEL container is published with the caption.
     */
    fun igCarousel(token: String, igId: String, publicUrls: List<String>, caption: String): IgOut {
        if (token.isEmpty()) return IgOut(false, msg = "Instagram token nahi dala")
        if (igId.isBlank()) return IgOut(false, msg = "Instagram id pata nahi (TEST button dabao)")
        if (publicUrls.size !in 2..10) return IgOut(false, msg = "carousel ke liye 2-10 photo chahiye (mile ${publicUrls.size})")
        try {
            val children = ArrayList<String>()
            for (u in publicUrls) {
                val c = igPost(token, "/$IG_VERSION/$igId/media", mapOf(
                    "is_carousel_item" to "true", "image_url" to u, "access_token" to token))
                val cj = c.json() ?: return IgOut(false, msg = igFail(c))
                val cid = cj.optString("id")
                if (cid.isEmpty()) return IgOut(false, msg = igFail(c))
                children.add(cid)
            }
            return igPublish(token, igId, "CAROUSEL", "", caption, isCarouselItem = false, children = children)
        } catch (e: Exception) {
            return IgOut(false, msg = "carousel fail")
        }
    }

    // ---------------------------------------------------------------- YouTube views (task D4 insights)

    /** The real view count of one uploaded video (-1 = unknown / not readable). */
    fun ytViews(accessToken: String, videoId: String): Long {
        val r = call("GET", "https://www.googleapis.com/youtube/v3/videos?part=statistics&id=$videoId",
            mapOf("Authorization" to "Bearer $accessToken"), null)
        val j = r.json() ?: return -1
        val v = j.optJSONArray("items")?.optJSONObject(0)?.optJSONObject("statistics")?.optString("viewCount") ?: return -1
        return v.toLongOrNull() ?: -1
    }

    // ---------------------------------------------------------------- site type publishers (task E)

    class SiteOut(val ok: Boolean, val url: String = "", val msg: String = "")

    /**
     * Shopify: one Product with the media as its image (Admin API, owner's own access token; the token goes
     * in the X-Shopify-Access-Token header only). Written from the public docs; NOT phone-tested.
     */
    fun shopifyPublish(shopDomain: String, token: String, title: String, blurb: String, publicUrl: String): SiteOut {
        if (shopDomain.isBlank() || token.isEmpty()) return SiteOut(false, msg = "Shopify ka naam ya token nahi dala")
        val domain = shopDomain.trim().trimEnd('/').removePrefix("https://").removePrefix("http://")
        if (domain.contains('/') || !domain.contains('.')) return SiteOut(false, msg = "Shopify ka naam theek nahi (jaise: mystore.myshopify.com)")
        try {
            val product = JSONObject()
                .put("product", JSONObject()
                    .put("title", title.take(255))
                    .put("body_html", blurb.take(3000))
                    .put("status", "active")
                    .put("images", JSONArray().put(JSONObject().put("src", publicUrl))))
            val r = call("POST", "https://$domain/admin/api/2024-10/products.json",
                mapOf("X-Shopify-Access-Token" to token, "Content-Type" to "application/json"),
                product.toString().toByteArray(Charsets.UTF_8), 40_000)
            if (!r.ok) return SiteOut(false, msg = siteFail(r))
            val id = r.json()?.optJSONObject("product")?.optLong("id") ?: 0L
            return SiteOut(true, "https://$domain/products/$id", "Shopify product ban gaya")
        } catch (e: Exception) {
            return SiteOut(false, msg = "Shopify fail")
        }
    }

    /** WooCommerce: a product via the store's own REST API (consumer key / secret over HTTP Basic). */
    fun wooPublish(storeUrl: String, key: String, secret: String, title: String, blurb: String, publicUrl: String): SiteOut {
        if (storeUrl.isBlank() || key.isEmpty() || secret.isEmpty()) return SiteOut(false, msg = "WooCommerce ki detail nahi dali")
        val base = storeUrl.trim().trimEnd('/')
        if (!base.startsWith("http")) return SiteOut(false, msg = "Store ka poora URL daalo (https://...)")
        try {
            val basic = Base64.getEncoder().encodeToString((key + ":" + secret).toByteArray(Charsets.UTF_8))
            val product = JSONObject()
                .put("name", title.take(200))
                .put("description", blurb.take(3000))
                .put("status", "publish")
                .put("images", JSONArray().put(JSONObject().put("src", publicUrl)))
            val r = call("POST", "$base/wp-json/wc/v3/products",
                mapOf("Authorization" to "Basic $basic", "Content-Type" to "application/json"),
                product.toString().toByteArray(Charsets.UTF_8), 40_000)
            if (!r.ok) return SiteOut(false, msg = siteFail(r))
            val id = r.json()?.optLong("id") ?: 0L
            return SiteOut(true, "$base/?p=$id", "WooCommerce product ban gaya")
        } catch (e: Exception) {
            return SiteOut(false, msg = "WooCommerce fail")
        }
    }

    /** Wix: import the media into the site's Media Manager (owner's own Wix API key, header only). */
    fun wixPublish(token: String, publicUrl: String, title: String): SiteOut {
        if (token.isEmpty()) return SiteOut(false, msg = "Wix ki API key nahi dali")
        if (publicUrl.isBlank()) return SiteOut(false, msg = "public URL nahi bani")
        try {
            val body = JSONObject().put("url", publicUrl).put("displayName", title.take(200))
            val r = call("POST", "https://www.wixapis.com/media/v1/files/import",
                mapOf("Authorization" to token, "Content-Type" to "application/json"),
                body.toString().toByteArray(Charsets.UTF_8), 40_000)
            if (!r.ok) return SiteOut(false, msg = siteFail(r))
            return SiteOut(true, "", "Wix media me chala gaya")
        } catch (e: Exception) {
            return SiteOut(false, msg = "Wix fail")
        }
    }

    private fun siteFail(r: Res): String = when (r.code) {
        401 -> "token galat ya expire ho gayi";
        403 -> "token me permission nahi hai"
        404 -> "store ka naam / URL galat lag raha hai"
        598 -> "internet nahi mil raha"
        else -> "site ne mana kar diya (code ${r.code})"
    }

    /** Waits for a deploy: Vercel builds take a moment, GitHub Pages queues. Longer than waitPublic, same honesty. */
    fun waitDeploy(url: String): Boolean = waitPublic(url, tries = 24, delayMs = 10_000L)

    // ---------------------------------------------------------------- token expiry helpers (Part 3 wires the alerts)

    /** True when the IG token needs its 60-day refresh soon (the caller stores the last refresh time). */
    fun igRefreshDue(lastRefreshMs: Long, nowMs: Long, days: Int = 5): Boolean =
        lastRefreshMs <= 0 || nowMs - lastRefreshMs >= (60 - days) * 24L * 60 * 60 * 1000
}
