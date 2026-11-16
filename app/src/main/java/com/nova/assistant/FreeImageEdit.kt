package com.nova.assistant

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * v7.185: FREE photo EDIT (not just free photo GENERATE). Used ONLY when:
 *  (1) Gemini photo edit (editAi, Level 2) could not do it because of a technical failure or no Gemini key, AND
 *  (2) the owner turned FREE IMAGE FALLBACK on in Settings (same switch FreeImage already uses; add-only, no new switch).
 * A photo is sent here ONLY after the same native "Bhejein?" HAAN dialog editAi already shows — this file adds no new
 * consent screen, it only adds a second place the photo can go to if Gemini fails.
 * Provider: Hugging Face (free tier, owner's own token in SecureStore slot "hf" — the SAME slot FreeImage already uses
 * for text-to-image, so no new key setup for the owner).
 * HONEST LIMIT: Hugging Face's exact image-to-image request shape differs by model and changes over time; the JSON
 * shape below (base64 image + prompt) follows their published image-to-image inference docs but was never reachable
 * from this sandbox (no network here) and is NOT phone/CI tested. If HF rejects the shape, this returns a normal
 * failFor() message and the existing Level 1 phone-only edit still runs — nothing breaks, it just falls back further.
 */
object FreeImageEditRules {
    const val MAX_BYTES_IN = 8 * 1024 * 1024   // don't send a huge original photo to a free tier
}

object FreeImageEdit {
    class Outcome(val ok: Boolean, val img: ByteArray?, val msg: String)

    private const val MAX_BYTES_OUT = 12 * 1024 * 1024
    private const val MODEL = "black-forest-labs/FLUX.1-Kontext-dev"
    private const val SLOT = "hf"  // same SecureStore slot as FreeImage's Hugging Face provider

    /** srcPhoto is shrunk by the caller already (ImageGen.shrink) before this is called, same as the Gemini path does. */
    fun run(ctx: Context, rawPrompt: String, srcPhoto: ByteArray): Outcome {
        val prompt = FreeImageRules.enhancePrompt(rawPrompt)
        if (prompt.isEmpty()) return Outcome(false, null, "Prompt khaali hai")
        if (srcPhoto.size > FreeImageEditRules.MAX_BYTES_IN) return Outcome(false, null, "Photo bahut badi hai free edit ke liye")
        val key = SecureStore.getSlot(ctx, SLOT)
        if (key.isEmpty()) return Outcome(false, null, "Free edit ke liye Hugging Face token nahi hai (Settings mein wahi token daalo jo free image ke liye hai)")
        val b64 = Base64.encodeToString(srcPhoto, Base64.NO_WRAP)
        val body = JSONObject().put("inputs", b64).put("parameters", JSONObject().put("prompt", prompt)).toString().toByteArray(Charsets.UTF_8)
        val hdr = HashMap<String, String>()
        hdr["Authorization"] = "Bearer " + key
        hdr["Content-Type"] = "application/json"
        val r = http("https://router.huggingface.co/hf-inference/models/" + MODEL, "POST", hdr, body, 90000)
        ApiUsage.hit("hf_edit", r.code)
        if (r.code !in 200..299) return Outcome(false, null, FreeImageRules.failText("Hugging Face (edit)", r.code))
        val img = extractImage(r.bytes)
        if (img == null || !valid(img)) return Outcome(false, null, "Hugging Face ne edited photo nahi di")
        return Outcome(true, img, "")
    }

    /** Some HF responses are raw image bytes; some are JSON with a b64 field. Accept either, same as FreeImage does for Together. */
    private fun extractImage(raw: ByteArray): ByteArray? {
        if (FreeImageRules.looksImage(raw)) return raw
        return try {
            val o = JSONObject(String(raw, Charsets.UTF_8))
            val b64 = o.optString("image", o.optString("b64_json", ""))
            if (b64.length > 100) Base64.decode(b64, Base64.DEFAULT) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun valid(b: ByteArray): Boolean {
        if (!FreeImageRules.looksImage(b)) return false
        return try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(b, 0, b.size, o)
            o.outWidth > 0 && o.outHeight > 0
        } catch (e: Throwable) {
            false
        }
    }

    private class Http(val code: Int, val bytes: ByteArray)

    private fun http(url: String, method: String, headers: Map<String, String>, body: ByteArray?, readMs: Int): Http {
        var result = Http(599, ByteArray(0))
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = 15000
                c.readTimeout = readMs
                for (h in headers.entries) c.setRequestProperty(h.key, h.value)
                if (body != null) {
                    c.doOutput = true
                    c.outputStream.use { it.write(body) }
                }
                val code = c.responseCode
                val st = if (code in 200..299) c.inputStream else c.errorStream
                val data = if (st != null) readLimited(st, MAX_BYTES_OUT) else ByteArray(0)
                result = Http(code, data)
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            result = Http(599, ByteArray(0))
        }
        return result
    }

    private fun readLimited(st: InputStream, max: Int): ByteArray {
        val bo = ByteArrayOutputStream()
        try {
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = st.read(buf)
                if (n < 0) break
                bo.write(buf, 0, n)
                if (bo.size() > max) return ByteArray(0)
            }
        } finally {
            try { st.close() } catch (e: Exception) { }
        }
        return bo.toByteArray()
    }
}
