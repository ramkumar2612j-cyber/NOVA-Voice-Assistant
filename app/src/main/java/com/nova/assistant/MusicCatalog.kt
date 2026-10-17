package com.nova.assistant

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * wip13: key-less public song CATALOG (Apple iTunes Search) used ONLY to tell similar songs apart (title + artist) before NOVA asks the music app to play.
 * It never plays anything and is not Spotify's own catalogue; the music app still does its own search. null from [lookup] = catalogue unreachable (offline),
 * which simply means NOVA skips the "which song?" popup and asks the music app directly.
 */
object MusicCatalog {
    data class Track(val title: String, val artist: String) {
        val label: String get() = "$title — $artist"
    }

    private const val MAX_BODY = 262144

    fun parse(json: String): List<Track> {
        val out = ArrayList<Track>()
        try {
            val arr = JSONObject(json).optJSONArray("results") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val t = o.optString("trackName").trim()
                val a = o.optString("artistName").trim()
                if (t.isEmpty() || a.isEmpty()) continue
                out.add(Track(t, a))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun lookup(query: String, timeoutMs: Int = 3500): List<Track>? {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        var conn: HttpURLConnection? = null
        try {
            val url = "https://itunes.apple.com/search?media=music&entity=song&limit=12&term=" + URLEncoder.encode(q, "UTF-8")
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "GET"
            c.connectTimeout = minOf(timeoutMs, 2500)
            c.readTimeout = timeoutMs
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) return null
            val out = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (out.size() < MAX_BODY) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                }
            }
            return parse(out.toString("UTF-8"))
        } catch (e: Exception) {
            return null
        } finally {
            try { conn?.disconnect() } catch (e: Exception) { }
        }
    }
}
