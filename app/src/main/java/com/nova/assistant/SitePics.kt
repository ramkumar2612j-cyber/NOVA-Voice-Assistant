package com.nova.assistant

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * v7.140 WEBSITE PICTURE FALLBACK. When the AI picture for a website slot fails (quota, no key, slow), the slot used to stay a
 * 1x1 pixel, i.e. an empty page. Now: AI picture -> a public picture from Wikipedia (free, no key) -> a clean title card.
 * Nothing here taps, sends or installs anything. The title card is pure Kotlin (no Android) so it is unit-testable.
 */
object SitePics {
    private val PALETTE = listOf(
        "#0b1f4d" to "#27e1ff", "#2a0b3d" to "#ff4d8d", "#0b3d2a" to "#4dffb0",
        "#3d1f0b" to "#ffb04d", "#1a0b3d" to "#8d6bff", "#3d0b14" to "#ff5c5c"
    )

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    /** Pure: a gradient picture with the title, as a data URI (never blank, never a broken link). */
    fun card(desc: String, idx: Int): String {
        val (a, b) = PALETTE[Math.floorMod(idx, PALETTE.size)]
        val words = desc.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(6)
        val l1 = esc(words.take(3).joinToString(" ").take(28))
        val l2 = esc(words.drop(3).joinToString(" ").take(28))
        val svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 800 600' preserveAspectRatio='xMidYMid slice'>" +
            "<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'><stop offset='0' stop-color='$a'/><stop offset='1' stop-color='#03060f'/></linearGradient></defs>" +
            "<rect width='800' height='600' fill='url(#g)'/>" +
            "<circle cx='640' cy='140' r='170' fill='$b' fill-opacity='0.16'/><circle cx='120' cy='520' r='120' fill='$b' fill-opacity='0.12'/>" +
            "<rect x='40' y='40' width='720' height='520' rx='28' fill='none' stroke='$b' stroke-opacity='0.55' stroke-width='3'/>" +
            "<text x='400' y='" + (if (l2.isEmpty()) "320" else "290") + "' font-family='sans-serif' font-size='54' font-weight='700' fill='#ffffff' text-anchor='middle'>$l1</text>" +
            (if (l2.isEmpty()) "" else "<text x='400' y='360' font-family='sans-serif' font-size='54' font-weight='700' fill='$b' text-anchor='middle'>$l2</text>") +
            "</svg>"
        return "data:image/svg+xml;utf8," + URLEncoder.encode(svg, "UTF-8").replace("+", "%20")
    }

    /** Pure: the Wikipedia search address for a picture description. */
    fun wikiUrl(q: String): String =
        "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=" + URLEncoder.encode(q, "UTF-8") +
            "&gsrnamespace=0&gsrlimit=5&prop=pageimages&piprop=thumbnail&pithumbsize=900&format=json"

    private fun get(url: String, max: Int): ByteArray? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "NOVA-Assistant/7.140 (Android; website pictures)")
            if (c.responseCode != 200) return null
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            c.inputStream.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > max) return null
                }
            }
            out.toByteArray()
        } catch (e: Throwable) { null } finally { try { c?.disconnect() } catch (e: Throwable) { } }
    }

    /** A public Wikipedia picture for [desc] as a data URI, or null. Tries the whole line, then its first 3 words. */
    fun wiki(desc: String): String? {
        val words = desc.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val queries = listOf(words.take(8).joinToString(" "), words.take(3).joinToString(" ")).filter { it.isNotBlank() }.distinct()
        for (q in queries) {
            try {
                val body = get(wikiUrl(q), 300_000) ?: continue
                val pages = org.json.JSONObject(String(body, Charsets.UTF_8)).optJSONObject("query")?.optJSONObject("pages") ?: continue
                var bestIdx = Int.MAX_VALUE
                var bestSrc: String? = null
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val p = pages.optJSONObject(keys.next()) ?: continue
                    val src = p.optJSONObject("thumbnail")?.optString("source", "") ?: ""
                    val idx = p.optInt("index", 99)
                    if (src.isNotEmpty() && !src.endsWith(".svg", true) && idx < bestIdx) { bestIdx = idx; bestSrc = src }
                }
                val s = bestSrc ?: continue
                val raw = get(s, 3_000_000) ?: continue
                val small = ImageGen.shrink(raw, 1024) ?: raw
                return "data:image/jpeg;base64," + ImageGen.b64(small)
            } catch (e: Throwable) { ErrLog.addOnce("quiet:SitePics", e.toString()) }
        }
        return null
    }
}
