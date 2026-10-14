package com.nova.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Processes one queued cloud-only job while connected. Never retries phone UI agent instructions. */
class NovaCloudQueueWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        if (!OfflineJobQueue.hasInternet(applicationContext)) return Result.retry()
        val job = OfflineJobQueue.next(applicationContext) ?: return Result.success()
        if (job.attempts >= OfflineQueueRules.MAX_ATTEMPTS) {
            OfflineJobQueue.remove(applicationContext, job.id)
            notifyResult("Queued NOVA task skipped", "Retry limit reached. Bolo kaam ko dobara shuru karne ke liye.")
            OfflineJobQueue.schedule(applicationContext)
            return Result.success()
        }
        val result = try { runJob(job.kind, job.payload) } catch (e: Throwable) { null }
        if (result != null) {
            OfflineJobQueue.remove(applicationContext, job.id)
            notifyResult("NOVA queued task", result.take(220))
            OfflineJobQueue.schedule(applicationContext)
            return Result.success()
        }
        val attempts = OfflineJobQueue.markAttempt(applicationContext, job.id)
        return if (attempts in 1 until OfflineQueueRules.MAX_ATTEMPTS) Result.retry() else {
            OfflineJobQueue.remove(applicationContext, job.id)
            notifyResult("NOVA task not completed", "Network/provider error after ${OfflineQueueRules.MAX_ATTEMPTS} attempts. NOVA ne kaam complete nahi kiya.")
            OfflineJobQueue.schedule(applicationContext)
            Result.success()
        }
    }

    private fun runJob(kind: OfflineQueueRules.Kind, raw: String): String? {
        val o = JSONObject(raw)
        return when (kind) {
            OfflineQueueRules.Kind.WEBSITE -> {
                val subject = o.optString("subject").take(120); val spoken = o.optString("raw").take(350)
                if (subject.isBlank()) null else {
                    val generated = SiteGen.generate(applicationContext, subject, spoken)
                    if (!generated.ok) null else SiteGen.save(applicationContext, subject, generated.html)?.let { "Website saved: $it" }
                }
            }
            OfflineQueueRules.Kind.LEARN -> {
                val topic = o.optString("topic").take(180)
                val note = CloudTaskText.generate(applicationContext,
                    "Write a short factual educational note in 3-4 sentences. No markdown; distinguish uncertainty.", topic, 1400)
                if (topic.isBlank() || note.isNullOrBlank()) null else {
                    val safeNote = (LearnRules.cleanNote(note) ?: note).take(1200)
                    val path = OfflineJobQueue.saveDownload(applicationContext, "NOVA_learn_${SiteGen.slug(topic)}_${System.currentTimeMillis()}.txt", "text/plain", safeNote.toByteArray(Charsets.UTF_8))
                        ?: return null
                    if (ExtCfg.autoLearn(applicationContext)) {   // v6.37: auto learn = kept at once (text only, owner can delete it in KYA SEEKHA)
                        LearnStore.addKept(applicationContext, topic, safeNote, "ai-queue")
                        "Learning note kept and saved as $path; the owner can delete it in KYA SEEKHA."
                    } else {
                        LearnStore.addPending(applicationContext, topic, safeNote, "ai-queue")
                        "Learning note saved as pending and $path; owner must press RAKHO in KYA SEEKHA before it is used."
                    }
                }
            }
            OfflineQueueRules.Kind.IMAGE -> {
                val prompt = o.optString("prompt").take(450)
                if (prompt.isBlank()) null else {
                    val out = ImageGen.generate(applicationContext, prompt, null)
                    val img = out.img
                    if (!out.ok || img == null) null else {
                        val png = img.size > 4 && img[0] == 0x89.toByte() && img[1] == 0x50.toByte()
                        val ext = if (png) "png" else "jpg"
                        val mime = if (png) "image/png" else "image/jpeg"
                        OfflineJobQueue.saveDownload(applicationContext, "NOVA_image_${System.currentTimeMillis()}.$ext", mime, img)?.let { "Image saved: $it" }
                    }
                }
            }
            OfflineQueueRules.Kind.LEAD_DRAFTS -> {
                val id = o.optString("id").take(80); val lang = if (o.optString("language") == "en") "en" else "hi"
                val lead = LeadStore.list(applicationContext).firstOrNull { it.id == id } ?: return "Lead was removed; no draft created."
                val prompt = if (lang == "en") "Draft one polite, personal, low-pressure first-contact message for the public business named ${lead.name}, in ${lead.place}. No claims, no links, max 2 sentences. Output only message." else "${lead.name} ${lead.place} ke liye ek vinamra, chhota, bina jhoothe daave wala pehla contact message Hindi/Hinglish mein likho. Max 2 sentences, sirf message."
                val draft = CloudTaskText.generate(applicationContext, "Draft only. Never claim a prior relationship. No marketing tricks. Plain text.", prompt, 450)
                    ?: LeadRules.draft(lead.name, lead.place, lang)
                val path = OfflineJobQueue.saveDownload(applicationContext, "NOVA_lead_draft_${SiteGen.slug(lead.name)}_${System.currentTimeMillis()}.txt", "text/plain", draft.toByteArray(Charsets.UTF_8)) ?: return null
                if (LeadStore.createDraft(applicationContext, lang, id, draft)) "Lead draft ready for owner review: ${lead.name}; saved $path" else null
            }
        }
    }

    private fun notifyResult(title: String, message: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("nova_queue", "NOVA queued tasks", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(applicationContext, 7421, intent, PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val notification = NotificationCompat.Builder(applicationContext, "nova_queue").setSmallIcon(com.nova.assistant.R.mipmap.ic_launcher)
            .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(pi).setAutoCancel(true).build()
        nm.notify((System.currentTimeMillis() % 1000000).toInt(), notification)
    }
}
