package com.nova.assistant

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Persistent queue for cloud-only work. Screen/UI agent commands are intentionally not accepted here. */
object OfflineJobQueue {
    data class Job(val id: String, val kind: OfflineQueueRules.Kind, val payload: String, val attempts: Int, val createdAt: Long)
    private const val PREF = "nova_cloud_queue"
    private const val ITEMS = "items_v1"
    private const val MAX = OfflineQueueRules.MAX_PENDING
    private const val NAME = "nova-cloud-jobs"

    fun hasInternet(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(n) ?: return false
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) { false }
    }

    @Synchronized private fun read(ctx: Context): MutableList<Job> {
        val raw = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(ITEMS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw); val result = ArrayList<Job>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = try { OfflineQueueRules.Kind.valueOf(o.optString("kind")) } catch (e: Exception) { continue }
                val payload = OfflineQueueRules.cleanPayload(o.optString("payload"))
                if (payload.isNotBlank()) result.add(Job(o.optString("id"), kind, payload, o.optInt("attempts", 0).coerceIn(0, OfflineQueueRules.MAX_ATTEMPTS), o.optLong("created")))
            }
            result.takeLast(MAX).toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    @Synchronized private fun write(ctx: Context, jobs: List<Job>): Boolean {
        val arr = JSONArray()
        jobs.takeLast(MAX).forEach { j -> arr.put(JSONObject().put("id", j.id).put("kind", j.kind.name).put("payload", j.payload).put("attempts", j.attempts).put("created", j.createdAt)) }
        return ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(ITEMS, arr.toString()).commit()
    }

    @Synchronized fun enqueueWebsite(ctx: Context, subject: String, raw: String): Boolean {
        val payload = JSONObject().put("subject", OfflineQueueRules.cleanPayload(subject).take(120)).put("raw", OfflineQueueRules.cleanPayload(raw).take(350)).toString()
        return enqueue(ctx, OfflineQueueRules.Kind.WEBSITE, payload)
    }
    @Synchronized fun enqueueLearn(ctx: Context, topic: String): Boolean = enqueue(ctx, OfflineQueueRules.Kind.LEARN,
        JSONObject().put("topic", OfflineQueueRules.cleanPayload(topic).take(180)).toString())
    @Synchronized fun enqueueImage(ctx: Context, prompt: String): Boolean = enqueue(ctx, OfflineQueueRules.Kind.IMAGE,
        JSONObject().put("prompt", OfflineQueueRules.cleanPayload(prompt).take(450)).toString())
    @Synchronized fun enqueueLeadDraft(ctx: Context, leadId: String, language: String): Boolean = enqueue(ctx, OfflineQueueRules.Kind.LEAD_DRAFTS,
        JSONObject().put("id", leadId.take(80)).put("language", if (language == "en") "en" else "hi").toString())

    @Synchronized private fun enqueue(ctx: Context, kind: OfflineQueueRules.Kind, payloadRaw: String): Boolean {
        if (!OfflineQueueRules.mayQueue(kind)) return false
        if (payloadRaw.contains(Regex("(?i)(api[_ -]?key|password|passwd|otp|verification code|whatsapp message|notification text)"))) return false
        val payload = OfflineQueueRules.cleanPayload(payloadRaw)
        if (payload.isBlank()) return false
        val jobs = read(ctx)
        if (jobs.size >= MAX) return false
        if (jobs.any { it.kind == kind && it.payload == payload }) { schedule(ctx); return true }
        val ok = write(ctx, jobs + Job(UUID.randomUUID().toString(), kind, payload, 0, System.currentTimeMillis()))
        if (ok) schedule(ctx)
        return ok
    }

    @Synchronized fun next(ctx: Context): Job? = read(ctx).firstOrNull()
    @Synchronized fun markAttempt(ctx: Context, id: String): Int {
        val jobs = read(ctx); val i = jobs.indexOfFirst { it.id == id }; if (i < 0) return -1
        val n = jobs[i].attempts + 1; jobs[i] = jobs[i].copy(attempts = n); write(ctx, jobs); return n
    }
    @Synchronized fun remove(ctx: Context, id: String): Boolean {
        val jobs = read(ctx); val next = jobs.filterNot { it.id == id }
        return write(ctx, next)
    }
    @Synchronized fun status(ctx: Context): String {
        val jobs = read(ctx)
        if (jobs.isEmpty()) return "Queue khaali hai. Internet na hone par website, seekho, photo aur lead drafts yahan save honge. Screen agent task kabhi auto-replay nahi hota."
        return "Queue mein ${jobs.size}/$MAX kaam: " + jobs.joinToString(", ") { "${it.kind.name.lowercase()} (try ${it.attempts}/$MAX)" }
    }
    @Synchronized fun clear(ctx: Context): Int {
        val jobs = read(ctx)
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(ITEMS).commit()
        try { WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(NAME) } catch (e: Exception) { ErrLog.add("OfflineJobQueue", e.toString()) }
        return jobs.size
    }

    fun schedule(ctx: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<NovaCloudQueueWorker>().setConstraints(constraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** MediaStore Downloads > NOVA. Safe for arbitrary file names; never uses a path supplied by a cloud model. */
    fun saveDownload(ctx: Context, fileName: String, mime: String, bytes: ByteArray): String? {
        return try {
            val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "NOVA_result.txt" }
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, safe); put(MediaStore.Downloads.MIME_TYPE, mime); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NOVA"); put(MediaStore.Downloads.IS_PENDING, 1) }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
                ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                "Downloads/NOVA/$safe"
            } else {
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "NOVA") } ?: return null
                if (!dir.exists() && !dir.mkdirs()) return null
                File(dir, safe).writeBytes(bytes); "App Downloads/NOVA/$safe"
            }
        } catch (e: Exception) { null }
    }
}
