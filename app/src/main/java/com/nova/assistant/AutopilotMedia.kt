package com.nova.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.effect.GaussianBlurWithFrameOverlaid
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * v7.158 AUTOPILOT MEDIA PREP (task C). Everything heavy runs on the AutopilotWorker thread, never the main
 * thread and never the notification-listener thread.
 *
 *  - Video truth via MediaMetadataRetriever: duration, real display size (rotation honoured), codecs.
 *  - Shorts gate: only <= 3 min videos go to YouTube as Shorts (AutopilotRules.shortOk).
 *  - Reels need MP4 H.264 + AAC: when the codec really differs, the video is RE-ENCODED with Media3
 *    Transformer - but ONLY while charging AND on Wi-Fi (owner rule: phone hang na ho). Otherwise the video
 *    is posted as-is and the honest message says so.
 *  - Horizontal videos get the 9:16 blurred-background treatment (GaussianBlurWithFrameOverlaid) under the
 *    same charging + Wi-Fi condition.
 *  - 45 MB host cap: bigger files go to YouTube only (AutopilotRules.hostVerdict) and the owner is told.
 *  - Reel cover frame: one real frame via the retriever, saved as a small JPEG (site thumbnail + cover).
 *
 * Honest status: written from the media3 1.4.1 API (verified against the real AAR signatures); NOT phone-tested.
 */
object AutopilotMedia {

    const val REENCODE_TIMEOUT_MS = 10 * 60_000L

    /** Reads the truth about a video file. null = the retriever could not read it (the caller stays honest). */
    fun videoInfo(file: File): AutopilotRules.VideoInfo? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (w <= 0 || h <= 0) return null
            val containerMime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.lowercase() ?: ""
            AutopilotRules.VideoInfo(
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                w, h,
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                containerMime,
                audioMimeOf(file)
            )
        } catch (e: Throwable) {
            ErrLog.add("AutopilotMedia", "info: $e"); null
        } finally {
            try { r.release() } catch (e: Throwable) { }
        }
    }

    /** The audio codec truth via MediaExtractor (API 21+; cheap, one pass over the track headers). */
    fun audioMimeOf(file: File): String = try {
        val ex = android.media.MediaExtractor()
        ex.setDataSource(file.absolutePath)
        var out = ""
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) { out = mime; break }
        }
        ex.release()
        out
    } catch (e: Throwable) { "" }

    // ---------------------------------------------------------------- charging / Wi-Fi (worker thread ok)

    fun charging(ctx: Context): Boolean {
        return try {
            val bm = ctx.applicationContext.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            bm?.isCharging == true || bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS) == android.os.BatteryManager.BATTERY_STATUS_CHARGING
        } catch (e: Throwable) { false }
    }

    fun onWifi(ctx: Context): Boolean {
        return try {
            val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val n = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(n) ?: return false
            cap.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Throwable) { false }
    }

    // ---------------------------------------------------------------- re-encode (task C)

    class Reencode(val ok: Boolean, val file: File?, val msg: String)

    /**
     * ONE Media3 Transformer pass that fixes everything a reel needs: H.264 + AAC output, and (when
     * [nineBySixteen]) the 9:16 canvas with a blurred copy of the frame behind the sharp video. Blocking
     * (latch) with a hard timeout - the caller is the AutopilotWorker, never the main thread.
     */
    fun reencode(ctx: Context, src: File, nineBySixteen: Boolean): Reencode {
        val app = ctx.applicationContext
        val out = File(src.parentFile, src.nameWithoutExtension + "_916.mp4")
        if (out.exists()) out.delete()
        try {
            val request = TransformationRequest.Builder()
                .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                .build()
            val videoEffects = ArrayList<androidx.media3.common.Effect>()
            if (nineBySixteen) {
                // 9:16 canvas (1080x1920), the frame zoomed to fill = the blurred layer,
                // then the sharp original overlaid on top (the classic reel look).
                videoEffects.add(Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
                videoEffects.add(GaussianBlurWithFrameOverlaid(BLUR_SIGMA, 1f, 1f))
            }
            val effects = Effects(emptyList(), videoEffects)
            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(src)))
                .setEffects(effects)
                .build()
            val latch = CountDownLatch(1)
            val code = AtomicInteger(0)   // 0 = running, 1 = done, 2 = error
            val why = arrayOf("")
            val transformer = Transformer.Builder(app)
                .setTransformationRequest(request)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                        code.set(1); latch.countDown()
                    }
                    override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                        why[0] = exportException.message ?: "export fail"; code.set(2); latch.countDown()
                    }
                })
                .build()
            transformer.start(item, out.absolutePath)
            if (!latch.await(REENCODE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                try { transformer.cancel() } catch (e: Throwable) { }
                out.delete()
                return Reencode(false, null, "re-encode ka time nikal gaya")
            }
            return when (code.get()) {
                1 -> if (out.exists() && out.length() > 0) Reencode(true, out, "re-encode ho gaya") else Reencode(false, null, "re-encode fail")
                else -> { out.delete(); Reencode(false, null, why[0].take(80).ifEmpty { "re-encode fail" }) }
            }
        } catch (e: Throwable) {
            ErrLog.add("AutopilotMedia", "reencode: $e")
            out.delete()
            return Reencode(false, null, "re-encode shuru nahi hua")
        }
    }

    private const val BLUR_SIGMA = 20f

    // ---------------------------------------------------------------- cover frame (task C)

    /** One real frame near 1/3 of the video, saved as a <=720px JPEG. null = no frame (honest, no fake cover). */
    fun coverFrame(ctx: Context, video: File, maxSide: Int = 720): File? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(video.absolutePath)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bmp = r.getFrameAtTime((dur * 1000 / 3), MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val scaled = if (bmp.width > maxSide || bmp.height > maxSide) {
                val k = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
                Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
            } else bmp
            val f = File(video.parentFile, video.nameWithoutExtension + "_cover.jpg")
            FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            if (scaled !== bmp) bmp.recycle()
            if (f.length() in 1..3_000_000) f else { f.delete(); null }
        } catch (e: Throwable) {
            ErrLog.add("AutopilotMedia", "cover: $e"); null
        } finally {
            try { r.release() } catch (e: Throwable) { }
        }
    }

    /** A small JPEG bytes of an image file, bound to [maxSide], for the vision caption call. null = no picture. */
    fun visionJpeg(ctx: Context, src: File, maxSide: Int = 768): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(src.absolutePath, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
            val bmp = BitmapFactory.decodeFile(src.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val scaled = if (bmp.width > maxSide || bmp.height > maxSide) {
                val k = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
                Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
            } else bmp
            val bos = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 84, bos)
            if (scaled !== bmp) bmp.recycle()
            val b = bos.toByteArray()
            if (b.size in 1..1_500_000) b else null
        } catch (e: Throwable) {
            ErrLog.add("AutopilotMedia", "visionJpeg: $e"); null
        }
    }
}
