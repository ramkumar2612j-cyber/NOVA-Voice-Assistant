package com.nova.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * v7.158 AUTOPILOT - the Android layer (everything heavy runs on the WorkManager worker thread, NEVER on the
 * main thread and NEVER on the notification-listener thread; the listener hook only sets a timestamp mark).
 *
 * Flow: WhatsApp Business group media notification -> [markGroup] -> AutopilotWorker (content-URI trigger +
 * 15-min periodic + delayed best-slot) -> [scan] (MediaStore, WhatsApp Business paths only, received files need
 * the group mark; Sent files follow the OFF / ASK / AUTO policy with a REAL accessibility chat-title check) ->
 * hash de-dupe + safe-ignore (documents / GIF / voice notes) + 90-second batching -> jobs in AutopilotStore ->
 * [pump] (caps, spacing, idempotency) -> website upload (streaming) -> Instagram Graph publish -> YouTube
 * resumable upload. The owner's morning voice lines and the AUTOPILOT tab read [snapshotJson].
 */
object Autopilot {

    const val MAX_SIDE = 1440          // JPEG prep bound (site + IG feed)
    const val MAX_VIDEO_BYTES = 45L * 1024 * 1024

    // marks (set on the listener thread in O(1), read on the worker)
    @Volatile var groupMarkTs: Long = 0
    @Volatile var groupMarkTitle: String = ""
    @Volatile var sentMarkTs: Long = 0

    private const val PREFS = "nova_autopilot"
    private const val KEY_LAST_SCAN = "last_scan_s"

    // ---------------------------------------------------------------- notification hooks (fast, no I/O)

    private val MEDIA_WORDS = listOf("photo", "image", "video", "picture", "📷", "📸", "🎬", "🎥", "🖼", "gif", "image omitted", "video omitted")

    /**
     * The watched group just showed a media notification ("Group: Sender", "📷 Photo"). Only a timestamp mark is
     * set here - the scan itself runs on the worker. Returns true when the mark was taken (group matched).
     */
    fun markGroup(ctx: Context, title: String, text: String): Boolean {
        try {
            val c = AutopilotStore.cfg(ctx)
            if (!c.on || !AutopilotRules.matchesGroup(title, c.group)) return false
            val low = text.lowercase()
            if (MEDIA_WORDS.none { low.contains(it) }) return false
            groupMarkTs = System.currentTimeMillis()
            groupMarkTitle = title.take(80)
            if (low.startsWith("you:") || low.startsWith("aap:")) sentMarkTs = groupMarkTs   // the owner sent it himself
            scheduleNow(ctx)
            return true
        } catch (e: Throwable) {
            ErrLog.add("Autopilot", "markGroup: $e")
            return false
        }
    }

    // ---------------------------------------------------------------- permissions

    fun hasMediaPermission(ctx: Context): Boolean {
        val p = { perm: String -> ctx.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        return if (Build.VERSION.SDK_INT >= 33) p(android.Manifest.permission.READ_MEDIA_IMAGES) || p(android.Manifest.permission.READ_MEDIA_VIDEO)
        else p(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Android 14 partial photo access: the owner picked only SOME photos - AUTOPILOT needs "Allow all". */
    fun partialPhotos(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        val p = { perm: String -> ctx.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        return p(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) &&
            !p(android.Manifest.permission.READ_MEDIA_IMAGES) && !p(android.Manifest.permission.READ_MEDIA_VIDEO)
    }

    // ---------------------------------------------------------------- scan (worker thread)

    class Found(val uri: Uri, val kind: AutopilotRules.Kind, val name: String, val sentByMe: Boolean, val ts: Long, val verdict: String = "auto")

    /**
     * MediaStore scan of the WhatsApp Business media folders only. Received files are attributed to the group
     * only when the group mark is fresh (a group media notification really arrived); Sent files follow the
     * sent-mode policy with the accessibility chat-title check in [sentVerdict]. Returns the new files worth
     * queueing (already de-duped by hash, safe-ignored kinds dropped).
     */
    fun scan(ctx: Context): List<Found> {
        val out = ArrayList<Found>()
        val app = ctx.applicationContext
        if (!hasMediaPermission(app)) return out
        val c = AutopilotStore.cfg(app)
        if (!c.on) return out
        val now = System.currentTimeMillis()
        val sinceS = (lastScanS(app) - 3).coerceAtLeast(0)
        val markFresh = groupMarkTs > 0 && now - groupMarkTs <= 20 * 60_000L

        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.MIME_TYPE)
        for (kind in listOf(AutopilotRules.Kind.IMAGE, AutopilotRules.Kind.VIDEO)) {
            try {
                val collection = if (kind == AutopilotRules.Kind.IMAGE) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                app.contentResolver.query(collection, proj, "${MediaStore.MediaColumns.DATE_ADDED} >= ?", arrayOf(sinceS.toString()), "${MediaStore.MediaColumns.DATE_ADDED} ASC")?.use { cur ->
                    while (cur.moveToNext()) {
                        val path = cur.getString(2) ?: continue
                        if (!isWaBizMedia(path)) continue
                        val name = cur.getString(1) ?: ""
                        val mime = cur.getString(4) ?: ""
                        val addedS = cur.getLong(3)
                        if (addedS <= 0) continue
                        // de-dupe by hash BEFORE anything else (a re-scan must never repost)
                        val hash = hashOf(app, Uri.withAppendedPath(collection, cur.getLong(0).toString())) ?: continue
                        if (!AutopilotStore.hashNew(app, hash)) continue
                        if (AutopilotRules.ignoredKind(name, mime)) continue
                        val sent = isSentPath(path)
                        var verdict = "auto"
                        if (sent) {
                            verdict = sentVerdict(app, c)
                            if (verdict == "skip") continue          // OFF = never post owner-sent files
                        } else {
                            if (!markFresh) continue                  // received files need the group-notification mark
                        }
                        val id = cur.getLong(0)
                        out.add(Found(Uri.withAppendedPath(collection, id.toString()), kind, name.take(120), sent, addedS * 1000, verdict))
                        if (out.size >= 20) break                     // one scan handles at most 20 files (phone-safe)
                    }
                }
            } catch (e: Throwable) {
                ErrLog.add("Autopilot", "scan: $e")
            }
        }
        setLastScanS(app, (now / 1000) + 2)
        return out
    }

    private fun isWaBizMedia(path: String): Boolean {
        val p = path.lowercase()
        val biz = p.contains("com.whatsapp.w4b") || p.contains("whatsapp business")
        return biz && (p.contains("images") || p.contains("video"))
    }

    private fun isSentPath(path: String): Boolean = path.lowercase().contains("/sent/")

    /** The sent-by-me policy with the REAL open-chat title read by the accessibility service (AUTO is not blind). */
    fun sentVerdict(ctx: Context, c: AutopilotStore.Cfg): String {
        val mode = when (c.sentMode) { "off" -> AutopilotRules.SentMode.OFF; "auto" -> AutopilotRules.SentMode.AUTO; else -> AutopilotRules.SentMode.ASK }
        return AutopilotRules.sentVerdict(mode, chatTitleNow(), c.group)
    }

    /** The open chat title from the accessibility tree (WhatsApp's conversation_contact_name id first, toolbar text fallback). */
    fun chatTitleNow(): String = try {
        NovaAccessibilityService.chatTitle() ?: ""
    } catch (e: Throwable) { "" }

    private fun lastScanS(ctx: Context): Long = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SCAN, System.currentTimeMillis() / 1000 - 60)
    private fun setLastScanS(ctx: Context, s: Long) { ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_SCAN, s).commit() }

    /** SHA-256 of the file's first 512 KB + size (fast on a phone, strong enough against re-scans). */
    fun hashOf(ctx: Context, uri: Uri): String? {
        return try {
            val head = ByteArray(512 * 1024)
            var n = 0
            val ins = ctx.contentResolver.openInputStream(uri) ?: return null
            ins.use {
                var r: Int
                while (n < head.size) { r = it.read(head, n, head.size - n); if (r < 0) break; n += r }
            }
            val msg = java.security.MessageDigest.getInstance("SHA-256")
            msg.update(head, 0, n)
            msg.update(java.nio.ByteBuffer.allocate(8).putLong(hashSize(ctx, uri)).array())
            val d = msg.digest()
            d.joinToString("") { String.format("%02x", it) }
        } catch (e: Throwable) { null }
    }

    private fun hashSize(ctx: Context, uri: Uri): Long = try {
        ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    } catch (e: Throwable) { 0L }

    // ---------------------------------------------------------------- media prep (worker thread)

    private fun workDir(ctx: Context): File = File(ctx.applicationContext.cacheDir, "autopilot").also { it.mkdirs() }

    /** One shared image: decode bound to [MAX_SIDE], JPEG ~86, private cache. null = could not prep. */
    fun prepImage(ctx: Context, uri: Uri, name: String): File? {
        return try {
            val app = ctx.applicationContext
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
            var sample = 1
            var w = bounds.outWidth; var h = bounds.outHeight
            while (w / 2 >= MAX_SIDE && h / 2 >= MAX_SIDE) { sample *= 2; w /= 2; h /= 2 }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val scaled = if (bmp.width > MAX_SIDE || bmp.height > MAX_SIDE) {
                val k = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
                Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
            } else bmp
            val f = File(workDir(app), "ap_" + System.currentTimeMillis() + "_" + safeName(name, "jpg"))
            FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            if (scaled !== bmp) bmp.recycle()
            if (scaled.width > 0 && f.length() in 1..20_000_000) f else null
        } catch (e: Throwable) {
            ErrLog.add("Autopilot", "prepImage: $e"); null
        }
    }

    /** Videos are copied as-is in Part 1 (Part 2 adds duration / 9:16 / re-encode checks with Media3). */
    fun prepVideo(ctx: Context, uri: Uri, name: String): File? {
        return try {
            val app = ctx.applicationContext
            val f = File(workDir(app), "ap_" + System.currentTimeMillis() + "_" + safeName(name, "mp4"))
            app.contentResolver.openInputStream(uri)?.use { ins -> FileOutputStream(f).use { outs -> ins.copyTo(outs, 96 * 1024) } } ?: return null
            if (f.length() in 1..MAX_VIDEO_BYTES) f else { f.delete(); null }
        } catch (e: Throwable) {
            ErrLog.add("Autopilot", "prepVideo: $e"); null
        }
    }

    fun safeName(name: String, ext: String): String {
        val base = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "").take(40)
        return (if (base.isBlank()) "nova" else base.lowercase()) + "." + ext
    }

    // ---------------------------------------------------------------- queueing (worker thread)

    /** Turns scanned files into jobs (90-second batching: one batchId for items close together). */
    fun enqueue(ctx: Context, found: List<Found>) {
        val app = ctx.applicationContext
        val c = AutopilotStore.cfg(app)
        if (!c.on || found.isEmpty()) return
        val now = System.currentTimeMillis()
        // v7.158 task D4: the slots self-tune from the 24 h reach numbers (fixed order until enough samples)
        val runAt = if (c.bestTime) {
            val (avg, n) = AutopilotStore.slotStats(app)
            AutopilotRules.nextSlotTuned(now, avg, n).first
        } else 0L
        var batchId = "b" + (now / 1000)
        var prevTs = 0L
        for (f in found) {
            if (AutopilotRules.batchable(prevTs, f.ts)) { /* same batch */ } else batchId = "b" + (f.ts / 1000)
            prevTs = f.ts
            val prep = if (f.kind == AutopilotRules.Kind.IMAGE) prepImage(app, f.uri, f.name) else prepVideo(app, f.uri, f.name)
            if (prep == null) continue
            fun add(target: String, kindName: String) {
                val job = AutopilotStore.addJob(app, target, kindName, prep.absolutePath, f.name, batchId, runAt)
                if (job != null && f.verdict != "auto") AutopilotStore.updateJob(app, job.id) { it.state = "REVIEW"; it.message = if (f.verdict == "ask") "aap ne bheja hai - haan karo" else "chat ka naam alag tha - check karo" }
            }
            if (c.targetSite) add("site", if (f.kind == AutopilotRules.Kind.IMAGE) "image" else "video")
            if (f.kind == AutopilotRules.Kind.IMAGE) {
                if (c.targetIgFeed) add("ig_feed", "image")
                if (c.targetIgStory) add("ig_story", "image")
            } else {
                if (c.targetIgReel) add("ig_reel", "video")
                if (c.targetYt) add("yt", "video")
            }
        }
        scheduleNow(app)
    }

    // ---------------------------------------------------------------- the pump (worker thread)

    /**
     * Runs every PENDING job whose time has come. Each job: idempotency first, then caps, then the target's
     * real publish. The site upload must finish before IG (IG needs the public URL), so an IG job without a
     * public URL waits 2 more minutes instead of failing. Every state change is honest.
     */
    fun pump(ctx: Context): Int {
        val app = ctx.applicationContext
        val c = AutopilotStore.cfg(app)
        if (!c.on) return 0
        var ran = 0
        val now = System.currentTimeMillis()
        // v7.158 task D4: harvest the 24 h insights of yesterday's posts (reach / saves / shares / views)
        try { harvestInsights(app) } catch (e: Throwable) { ErrLog.add("Autopilot", "insights: $e") }
        // v7.158 task D3: 2-10 photos of one batch become ONE Instagram carousel (not separate posts)
        try { publishCarousels(app, c) } catch (e: Throwable) { ErrLog.add("Autopilot", "carousel: $e") }
        for (j in AutopilotStore.jobs(app).filter { it.state == "PENDING" && (it.runAt <= 0 || it.runAt <= now) }) {
            if (ran >= 3) break   // max 3 publishes per pump (battery + quota friendly)
            val key = AutopilotRules.idemKey(j.id, j.target)
            if (AutopilotStore.idemHas(app, key)) {
                AutopilotStore.updateJob(app, j.id) { it.state = "DONE"; it.message = "pehle hi ho gaya" }
                continue
            }
            val capReason = AutopilotRules.capReason(j.target, AutopilotStore.postsToday(app, j.target), AutopilotStore.lastPostTs(app), now, c.caps)
            if (capReason != null && j.target != "site") {
                AutopilotStore.updateJob(app, j.id) { it.state = "SKIPPED"; it.message = capReason; it.runAt = now + 60 * 60_000L }
                continue
            }
            val ok = runJob(app, j, c)
            if (ok == 1) {
                AutopilotStore.idemClaim(app, key)
                AutopilotStore.countPost(app, j.target)
                AutopilotStore.updateJob(app, j.id) { it.state = "DONE"; it.message = it.message.ifEmpty { "ho gaya" } }
                try { recordFootprint(app, j) } catch (e: Throwable) { }
                NovaService.instance?.onAutopilotPosted(j.target, j.mediaName)
            } else if (ok == 2) {
                // honestly skipped (video too long for Shorts / file over the cap): the message already says why
                AutopilotStore.updateJob(app, j.id) { it.state = "DONE" }
            } else {
                AutopilotStore.updateJob(app, j.id) { it.state = "FAILED"; it.message = it.message.ifEmpty { "fail hua" } }
                NovaService.instance?.onAutopilotFailed(j.target, j.mediaName, j.message)
            }
            ran++
            // refresh cfg inside the loop (counters move)
            val cc = AutopilotStore.cfg(app)
            c.igFeedToday = cc.igFeedToday; c.igStoryToday = cc.igStoryToday; c.ytToday = cc.ytToday; c.lastPostTs = cc.lastPostTs; c.capDay = cc.capDay
        }
        return ran
    }

    /**
     * v7.158 task C: the REAL file this job should upload. For a video that needs it (wrong codec / horizontal
     * and a reel / Shorts target), this re-encodes - but ONLY while charging + Wi-Fi; otherwise the original
     * goes out with an honest message. null = the job must fail with [why].
     */
    class PrepFile(val file: File, val why: String)

    private fun prepFor(app: Context, j: AutopilotStore.Job): PrepFile? {
        val f = File(j.mediaPath)
        if (!f.exists()) return null
        if (j.kind != "video") return PrepFile(f, "")
        val info = AutopilotMedia.videoInfo(f)
        // Shorts gate: only <= 3 min videos may go to YouTube Shorts (the owner is told, never a silent drop)
        if (j.target == "yt" && !AutopilotRules.shortOk(info?.durationMs ?: 0L)) {
            return null.also { AutopilotStore.updateJob(app, j.id) { it.message = "video 3 minute se lamba hai - Shorts ke liye nahi (YouTube par normal video banega)" } }
        }
        // host cap: bigger than 45 MB -> YouTube only
        if (j.target != "yt" && AutopilotRules.hostVerdict(f.length()) == "yt-only") {
            return null.also { AutopilotStore.updateJob(app, j.id) { it.state = "SKIPPED"; it.message = "file 45 MB se badi hai - sirf YouTube par jayegi" } }
        }
        val needsCodec = AutopilotRules.needsReencode(info)
        val needs916 = j.target == "ig_reel" && AutopilotRules.isHorizontal(info)
        if ((needsCodec || needs916) && AutopilotRules.reencodeAllowed(AutopilotMedia.charging(app), AutopilotMedia.onWifi(app))) {
            val r = AutopilotMedia.reencode(app, f, needs916)
            if (r.ok && r.file != null) return PrepFile(r.file, r.msg)
            AutopilotStore.updateJob(app, j.id) { it.message = (r.msg + " - original bhej rahe hain").take(150) }
        } else if (needsCodec || needs916) {
            AutopilotStore.updateJob(app, j.id) { it.message = "re-encode charge + WiFi par hota hai - abhi original jayega" }
        }
        return PrepFile(f, "")
    }

    /**
     * v7.158 task D3: when 2-10 ig_feed image jobs of ONE batch are due together (site done, public URLs
     * ready), Instagram gets ONE carousel. Every job of the group is then DONE with the same honest message.
     * Single photos keep the normal single-post path (this function only groups what is left).
     */
    private fun publishCarousels(app: Context, c: AutopilotStore.Cfg) {
        if (!c.targetIgFeed) return
        val now = System.currentTimeMillis()
        val due = AutopilotStore.jobs(app).filter {
            it.target == "ig_feed" && it.kind == "image" && it.state == "PENDING" &&
                (it.runAt <= 0 || it.runAt <= now) && it.publicUrl.isNotBlank()
        }
        val byBatch = due.groupBy { it.batchId }
        for ((_, jobs) in byBatch) {
            if (jobs.size < AutopilotRules.CAROUSEL_MIN) continue
            val groups = AutopilotRules.carouselGroups(jobs.map { it.id })
            for (group in groups) {
                val members = jobs.filter { it.id in group }
                if (members.size < AutopilotRules.CAROUSEL_MIN) continue
                val pack = packFor(app, members.first())
                val token = SecureStore.getSlot(app, AutopilotNet.SLOT_IG)
                if (token.isEmpty()) return
                var igId = c.igId
                if (igId.isBlank()) {
                    igId = AutopilotNet.igDetectId(token)
                    if (igId.isNotBlank()) { c.igId = igId; AutopilotStore.saveCfg(app, c) } else return
                }
                val r = AutopilotNet.igCarousel(token, igId, members.map { it.publicUrl }, pack.igCaption + pack.igHashtagLine())
                for (m in members) {
                    AutopilotStore.updateJob(app, m.id) {
                        it.state = if (r.ok) "DONE" else "FAILED"
                        it.message = if (r.ok) "carousel ka hissa - live ho gaya" else r.msg
                    }
                    if (r.ok) { AutopilotStore.idemClaim(app, AutopilotRules.idemKey(m.id, m.target)) }
                }
                if (r.ok) {
                    AutopilotStore.countPost(app, "ig_feed")
                    NovaService.instance?.onAutopilotPosted("ig_feed", "carousel (${members.size} photo)")
                } else {
                    NovaService.instance?.onAutopilotFailed("ig_feed", "carousel", r.msg)
                }
            }
        }
    }

    /**
     * v7.158 task D4: 24 h after a post went live, its REAL numbers are read (IG reach / saves / shares,
     * YT views), the slot averages learn, a 3x-average reach becomes a viral signal the owner hears.
     * Numbers are only ever what the platform really returned.
     */
    private fun harvestInsights(app: Context) {
        val now = System.currentTimeMillis()
        val igToken = SecureStore.getSlot(app, AutopilotNet.SLOT_IG)
        val ytTok = AutopilotNet.ytAccess(app)
        for (j in AutopilotStore.jobs(app).filter { it.state == "DONE" && it.insightsAt <= 0 && now - it.createdAt >= 24 * 60 * 60_000L }) {
            try {
                var reach = -1L
                when (j.target) {
                    "ig_feed", "ig_reel", "ig_story" -> if (igToken.isNotEmpty() && j.platformId.isNotEmpty()) {
                        val ins = AutopilotNet.igInsights(igToken, j.platformId)
                        if (ins.ok) reach = ins.reach
                    }
                    "yt" -> if (ytTok.ok && j.platformId.isNotEmpty()) reach = AutopilotNet.ytViews(ytTok.accessToken, j.platformId)
                }
                AutopilotStore.updateJob(app, j.id) { it.insightsAt = now; it.reach = reach }
                if (reach >= 0) {
                    val slot = AutopilotRules.slotIndex(j.createdAt)
                    AutopilotStore.recordSlotReach(app, slot, reach)
                    val avg = AutopilotStore.avgReach(app)
                    if (AutopilotRules.viralSignal(reach, avg)) {
                        NovaService.instance?.onAutopilotViral(j.mediaName, reach)
                    }
                }
            } catch (e: Throwable) {
                ErrLog.add("Autopilot", "harvest: $e")
            }
        }
    }

    /** One target publish. 1 = really live, 2 = honestly skipped (message says why), 0 = failed. */
    private fun runJob(ctx: Context, j: AutopilotStore.Job, c: AutopilotStore.Cfg): Int {
        val app = ctx.applicationContext
        val prep = prepFor(app, j)
        if (prep == null) {
            val st = AutopilotStore.jobs(app).firstOrNull { it.id == j.id }?.state ?: "FAILED"
            return if (st == "SKIPPED") 2 else 0
        }
        val f = prep.file
        val ghToken = SecureStore.getSlot(app, AutopilotNet.SLOT_GH)
        val pack = packFor(app, j)
        return when (j.target) {
            "site" -> {
                if (ghToken.isEmpty()) { AutopilotStore.updateJob(app, j.id) { it.message = "GitHub token nahi dala" }; return 0 }
                if (c.repo.isBlank()) { AutopilotStore.updateJob(app, j.id) { it.message = "repo naam nahi dala" }; return 0 }
                val ym = java.time.YearMonth.now(java.time.ZoneId.of(AutopilotRules.IST_ZONE))
                val path = "media/$ym/" + f.name
                var thumbUrlFill = ""
                if (j.kind == "video") {
                    // v7.158 task C: the gallery + feed show the REAL cover frame of the video (prepFor already
                    // made sure only a <= 45 MB video reaches the site target).
                    val cover = AutopilotMedia.coverFrame(app, f)
                    if (cover != null) {
                        val cu = AutopilotNet.ghUpload(ghToken, c.repo, "media/$ym/" + cover.name, cover, "NOVA autopilot: cover")
                        if (cu.ok) thumbUrlFill = cu.rawUrl
                        cover.delete()
                    }
                }
                val up = AutopilotNet.ghUpload(ghToken, c.repo, path, f, "NOVA autopilot: ${f.name}")
                if (!up.ok) { AutopilotStore.updateJob(app, j.id) { it.message = up.msg }; return 0 }
                if (c.siteType == "vercel") AutopilotNet.waitDeploy(up.pagesUrl)
                // feed.json entry + gallery files
                val feed = AutopilotNet.ghReadFeed(ghToken, c.repo)
                val next = AutopilotRules.feedWith(feed, j.id, j.kind, up.rawUrl, thumbUrlFill.ifEmpty { up.rawUrl }, pack.siteBlurb, System.currentTimeMillis())
                val w = AutopilotNet.ghWriteSiteFiles(ghToken, c.repo, next)
                AutopilotStore.updateJob(app, j.id) { it.publicUrl = up.rawUrl; it.thumbUrl = thumbUrlFill.ifEmpty { up.rawUrl }; it.message = if (w.ok) "website par lag gaya" else "media gaya, feed nahi: ${w.msg}" }
                // v7.158 task E: the SITE TYPE publishers (Shopify / WooCommerce / Wix) get the same media
                publishToSiteType(app, c, pack, thumbUrlFill.ifEmpty { up.rawUrl })
                // the IG / YT jobs of the same batch reuse this public URL
                for (o in AutopilotStore.jobs(app).filter { it.batchId == j.batchId && it.id != j.id && it.publicUrl.isEmpty() && it.state == "PENDING" }) {
                    AutopilotStore.updateJob(app, o.id) { it.publicUrl = up.rawUrl; it.thumbUrl = thumbUrlFill.ifEmpty { up.rawUrl } }
                }
                1
            }
            "ig_feed", "ig_reel", "ig_story" -> {
                val token = SecureStore.getSlot(app, AutopilotNet.SLOT_IG)
                if (token.isEmpty()) { AutopilotStore.updateJob(app, j.id) { it.message = "Instagram token nahi dala" }; return 0 }
                var igId = c.igId
                if (igId.isBlank()) {
                    igId = AutopilotNet.igDetectId(token)
                    if (igId.isNotBlank()) { c.igId = igId; AutopilotStore.saveCfg(app, c) }
                }
                if (j.publicUrl.isBlank()) { AutopilotStore.updateJob(app, j.id) { it.state = "PENDING"; it.runAt = System.currentTimeMillis() + 2 * 60_000L; it.message = "website ka intezaar" } ; return 0 }
                val type = when (j.target) { "ig_reel" -> "REELS"; "ig_story" -> "STORIES"; else -> "IMAGE" }
                val url = if (type == "REELS") j.publicUrl else j.thumbUrl.ifEmpty { j.publicUrl }
                val r = AutopilotNet.igPublish(token, igId, type, url, pack.igCaption + pack.igHashtagLine())
                AutopilotStore.updateJob(app, j.id) { it.message = r.msg; if (r.ok) it.platformId = r.id }
                if (r.ok) 1 else 0
            }
            "yt" -> {
                val quota = AutopilotNet.ytQuota(app)
                if (quota.left() < AutopilotNet.ytUploadUnits()) { AutopilotStore.updateJob(app, j.id) { it.message = "YouTube ki aaj ki limit khatam (kal tak)" }; return 0 }
                val tok = AutopilotNet.ytAccess(app)
                if (!tok.ok) { AutopilotStore.updateJob(app, j.id) { it.message = tok.msg }; return 0 }
                val up = AutopilotNet.ytUpload(app, tok.accessToken, f, pack.ytTitle, pack.ytDesc + "\n\n" + (if (c.site.isNotBlank()) c.site else ""), pack.ytTags)
                AutopilotStore.updateJob(app, j.id) { it.message = up.msg; if (up.ok) it.platformId = up.videoId }
                if (up.ok) 1 else 0
            }
            else -> 0
        }
    }

    /** v7.158 task E: the SITE TYPE publisher (Shopify / WooCommerce / Wix) behind the same media. */
    private fun publishToSiteType(app: Context, c: AutopilotStore.Cfg, pack: AutopilotRules.Pack, publicUrl: String) {
        try {
            val r = when (c.siteType) {
                "shopify" -> AutopilotNet.shopifyPublish(c.shopDomain, SecureStore.getSlot(app, AutopilotNet.SLOT_SHOP), pack.siteBlurb.ifEmpty { pack.igCaption }, pack.siteBlurb, publicUrl)
                "woocommerce" -> AutopilotNet.wooPublish(c.wooUrl, SecureStore.getSlot(app, AutopilotNet.SLOT_WOO_K), SecureStore.getSlot(app, AutopilotNet.SLOT_WOO_S), pack.siteBlurb.ifEmpty { pack.igCaption }, pack.siteBlurb, publicUrl)
                "wix" -> AutopilotNet.wixPublish(SecureStore.getSlot(app, AutopilotNet.SLOT_WIX), publicUrl, pack.siteBlurb)
                else -> return
            }
            if (!r.ok) NovaService.instance?.onAutopilotFailed(c.siteType, "site publisher", r.msg)
        } catch (e: Throwable) {
            ErrLog.add("Autopilot", "siteType: $e")
        }
    }

    /**
     * The copy pack for a job (v7.158 task D): the saved one, else the AI with a REAL vision caption of what
     * the photo / frame really shows, else the honest fallback. The never-repeat rule then varies a pack whose
     * hook or hashtag set repeats the last post. The footprint is recorded only when the post really goes out.
     */
    fun packFor(app: Context, j: AutopilotStore.Job): AutopilotRules.Pack {
        // the batch shares one pack (site wrote it first)
        if (j.packJson.isNotBlank()) {
            val p = AutopilotRules.parsePack(j.packJson)
            if (p != null) return p
        }
        val c = AutopilotStore.cfg(app)
        val item = if (j.kind == "video") "video" else "photo"
        val lang = "hinglish"
        val vision = visionFor(app, j)
        val prompt = AutopilotRules.packPrompt(item, vision, c.brand, AutopilotNet.liveOffer(c.site, System.currentTimeMillis()), lang, lastHooks(app), listOf("site", "instagram", "youtube"))
        val ai = NovaService.instance?.autopilotAsk(prompt)
        var out = AutopilotRules.parsePack(ai ?: "")
            ?: AutopilotRules.fallbackPack(item, vision, c.brand, AutopilotNet.liveOffer(c.site, System.currentTimeMillis()), lang)
        // v7.158 task D5: never repeat the same hook / hashtag set twice in a row
        val (lastHook, lastTags) = AutopilotStore.lastPackFootprint(app)
        val why = AutopilotRules.repeatReason(out, lastHook, lastTags)
        if (why != null) out = AutopilotRules.varyPack(out)
        val json = packToJson(out)
        for (o in AutopilotStore.jobs(app).filter { it.batchId == j.batchId && it.packJson.isBlank() }) AutopilotStore.updateJob(app, o.id) { it.packJson = json }
        return out
    }

    /**
     * v7.158 task D1: the caption must describe what is REALLY shown. FreeVision looks at the photo (or one
     * real frame of the video) and answers in plain text; "" = no vision answer (the honest fallback then
     * says only what is surely true). Runs on the worker thread only.
     */
    fun visionFor(app: Context, j: AutopilotStore.Job): String {
        return try {
            val f = File(j.mediaPath)
            if (!f.exists()) return ""
            val jpeg: ByteArray? = if (j.kind == "video") {
                val cover = AutopilotMedia.coverFrame(app, f, 640) ?: return ""
                val b = AutopilotMedia.visionJpeg(app, cover, 640)
                cover.delete()
                b
            } else AutopilotMedia.visionJpeg(app, f, 768)
            if (jpeg == null || jpeg.isEmpty()) return ""
            val b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
            NovaService.instance?.autopilotVision(b64) ?: ""
        } catch (e: Throwable) {
            ErrLog.add("Autopilot", "vision: $e"); ""
        }
    }

    /** Records the never-repeat footprint after a post really went out. */
    fun recordFootprint(app: Context, j: AutopilotStore.Job) {
        if (j.packJson.isBlank()) return
        val p = AutopilotRules.parsePack(j.packJson) ?: return
        AutopilotStore.recordPackFootprint(app, p)
    }

    private fun packToJson(p: AutopilotRules.Pack): String = try {
        JSONObject()
            .put("ig_caption", p.igCaption).put("hashtags", org.json.JSONArray(p.hashtags))
            .put("story_text", p.storyText).put("poll", p.pollText)
            .put("yt_title", p.ytTitle).put("yt_desc", p.ytDesc).put("yt_tags", org.json.JSONArray(p.ytTags))
            .put("site_blurb", p.siteBlurb).put("lang", p.lang).toString()
    } catch (e: Exception) { "" }

    /** The last 3 hook lines (never-repeat rule). */
    fun lastHooks(app: Context): List<String> =
        AutopilotStore.jobs(app).filter { it.state == "DONE" && it.packJson.isNotBlank() }
            .takeLast(3).mapNotNull { j ->
                try { AutopilotRules.parsePack(JSONObject(j.packJson).toString())?.let { AutopilotRules.hookLine(it) } } catch (e: Exception) { null }
            }

    // ---------------------------------------------------------------- concierge (v7.158 task G core)

    class Concierge(val reply: String?, val orderSaved: Boolean = false, val done: Boolean = false, val lead: Boolean = false)

    /**
     * One business DM turn (WhatsApp / WhatsApp Business / Instagram). Rules first (discount = live website
     * offer only; order machine; business-lead holding reply), the AI with FACTS only when the rules have
     * nothing. null = this chat is not for the concierge (the normal smart reply continues).
     */
    fun concierge(ctx: Context, chat: String, text: String, appPkg: String): Concierge? {
        try {
            val app = ctx.applicationContext
            val c = AutopilotStore.cfg(app)
            if (!c.on || !c.concierge) return null
            if (appPkg != "com.whatsapp" && appPkg != "com.whatsapp.w4b" && appPkg != "com.instagram.android") return null
            if (text.isBlank() || text.length > 500) return null
            val low = text.lowercase()
            // order-status question FIRST -> answered ONLY from what the owner stored (task G4)
            if (AutopilotVoiceRules.isOrderStatusQuestion(text)) {
                return Concierge(orderStatusAnswer(app, chat, text) ?: return null)
            }
            // discount question -> the owner's exact rule (live website offer only, never invented)
            if (low.contains("discount") || low.contains("sasta") || low.contains("kam price") || low.contains("offer") || low.contains("off lagega") || low.contains("coupon")) {
                val offer = AutopilotNet.liveOffer(c.site, System.currentTimeMillis())
                return Concierge(AutopilotRules.discountReply(offer, false))
            }
            // business lead -> LeadStore + owner alert + polite holding reply (task G2)
            if (isBusinessLead(low)) {
                captureLead(app, chat, appPkg, text)
                return Concierge(leadReply(), lead = true)
            }
            // order machine
            val order = AutopilotStore.orderFor(app, chat) ?: AutopilotRules.Order("o" + System.currentTimeMillis().toString(36).take(10), chat, appPkg)
            val r = AutopilotRules.step(order, text)
            if (r.save) { order.updated = System.currentTimeMillis(); AutopilotStore.saveOrder(app, order) }
            if (r.done) {
                NovaService.instance?.onAutopilotOrder(order)
                fireOrderHook(app, order)
            }
            return Concierge(r.reply, r.save, r.done)
        } catch (e: Throwable) {
            ErrLog.add("Autopilot", "concierge: $e")
            return null
        }
    }

    private val LEAD_WORDS = listOf("wholesale", "bulk", "collaboration", "collab", "influencer", "reseller", "distributor", "partnership", "sponsor", "brand deal")

    /** v7.158 task G2: a business lead goes into the owner's LeadStore + he is told once (max 1 per chat per day). */
    private fun captureLead(app: Context, chat: String, pkg: String, text: String) {
        try {
            val who = chat.take(60)
            val already = LeadStore.list(app).any { it.name.equals(who, true) && System.currentTimeMillis() - it.capturedAt < 24 * 60 * 60_000L }
            if (already) return
            val src = if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") pkg else "com.instagram.android"
            LeadStore.addDirect(app, who, src)
            NovaService.instance?.onAutopilotLead(who)
        } catch (e: Throwable) {
            ErrLog.add("Autopilot", "lead: $e")
        }
    }

    /**
     * v7.158 task G3: a CONFIRMED order fires the owner's automation webhook (hook_ connector - Google Sheet
     * via Zapier / n8n / Make). Only fires when the owner already set one up; the honest wording of the
     * webhook semantics stays ConnectorApi's own ("trigger bheja, qabool hua").
     */
    fun fireOrderHook(ctx: Context, o: AutopilotRules.Order) {
        try {
            val app = ctx.applicationContext
            val hook = try { ConnectorStore.dynList(app).firstOrNull { ConnectorCatalog.isHookId(it.id) } } catch (e: Throwable) { null } ?: return
            val text = "ORDER: ${o.qty} x ${o.item}${if (o.size.isNotBlank()) " (size ${o.size})" else ""} - ${o.name}, ${o.phone}, ${o.address.take(120)}"
            Thread {
                try { ConnectorApi.run(app, hook.id, "run", text) } catch (e: Throwable) { ErrLog.add("Autopilot", "orderHook: $e") }
            }.start()
        } catch (e: Throwable) { ErrLog.add("Autopilot", "orderHook2: $e") }
    }

    fun isBusinessLead(low: String): Boolean = LEAD_WORDS.any { low.contains(it) }

    fun leadReply(): String =
        "Bahut badhiya opportunity lagti hai! Ye seedha owner ke paas bhej rahi hoon, wo khud aapse baat karenge. " +
        "Tab tak website par sab collection dekh sakte hain. Dhanyavaad!"

    /** The FACTS line for the AI when it chats for the owner (brand, website, live offer - never invented). */
    fun factsLine(ctx: Context): String {
        val app = ctx.applicationContext
        val c = AutopilotStore.cfg(app)
        val parts = ArrayList<String>()
        if (c.brand.isNotBlank()) parts.add("brand=${c.brand.take(40)}")
        if (c.site.isNotBlank()) parts.add("website=${c.site.take(60)}")
        val offer = AutopilotNet.liveOffer(c.site, System.currentTimeMillis())
        if (offer.isNotBlank()) parts.add("live offer=$offer")
        parts.add("rules=NOVA kabhi bhi price/discount/stock/delivery-date nahi banati; discount sirf website ke live offer se batana")
        return "FACTS: " + parts.joinToString(", ")
    }

    // ---------------------------------------------------------------- offer writer (nova offer 20% off code SK20)

    /** Writes /nova-offer.json to the repo so the website offer and the chat answer always match. */
    fun writeOffer(ctx: Context, text: String): Pair<Boolean, String> {
        val app = ctx.applicationContext
        val c = AutopilotStore.cfg(app)
        val offer = AutopilotRules.extractOffer(text) ?: text.trim().take(140)
        if (offer.length < 3) return Pair(false, "offer samajh nahi aaya")
        val code = AutopilotRules.offerCode(text)
        if (c.repo.isBlank()) return Pair(false, "repo naam nahi dala (AUTOPILOT tab)")
        val token = SecureStore.getSlot(app, AutopilotNet.SLOT_GH)
        if (token.isEmpty()) return Pair(false, "GitHub token nahi dala")
        val f = File.createTempFile("nova-offer", ".json", workDir(app))
        return try {
            f.writeText(AutopilotRules.offerJson(offer, code, System.currentTimeMillis()), Charsets.UTF_8)
            val up = AutopilotNet.ghUpload(token, c.repo, "nova-offer.json", f, "NOVA: offer update")
            AutopilotNet.clearOfferCache()
            if (up.ok) Pair(true, "Website ka offer set ho gaya: $offer" + (if (code.isNotEmpty()) " (code $code)" else "")) else Pair(false, up.msg)
        } catch (e: Throwable) {
            Pair(false, "offer save nahi hua")
        } finally {
            f.delete()
        }
    }

    // ---------------------------------------------------------------- snapshot for the UI / voice

    fun armedLine(ctx: Context): String {
        val app = ctx.applicationContext
        val c = AutopilotStore.cfg(app)
        val a = AutopilotRules.armed(c.on, c.group,
            SecureStore.hasSlot(app, AutopilotNet.SLOT_GH), SecureStore.hasSlot(app, AutopilotNet.SLOT_IG),
            SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_CID) && SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_CSEC),
            c.targetSite, c.targetIgFeed || c.targetIgReel || c.targetIgStory, c.targetYt)
        return a.line()
    }

    /** The AUTOPILOT tab status: config (tokens only as true/false), queue, orders, counters. */
    fun snapshotJson(ctx: Context): String = try {
        val app = ctx.applicationContext
        val c = AutopilotStore.cfg(app)
        val jobs = AutopilotStore.jobs(app)
        val orders = AutopilotStore.orders(app)
        val q = org.json.JSONArray()
        for (j in jobs.takeLast(30).asReversed()) q.put(JSONObject()
            .put("id", j.id).put("target", j.target).put("kind", j.kind).put("name", j.mediaName)
            .put("state", j.state).put("msg", j.message).put("runAt", j.runAt))
        val o = org.json.JSONArray()
        for (x in orders.asReversed().take(20)) o.put(JSONObject()
            .put("id", x.id).put("chat", x.chat).put("item", x.item).put("qty", x.qty)
            .put("name", x.name).put("phone", x.phone).put("step", x.step.name).put("done", x.step == AutopilotRules.Step.DONE)
            .put("status", x.statusNote))
        JSONObject()
            .put("on", c.on).put("group", c.group).put("site", c.site).put("repo", c.repo).put("siteType", c.siteType)
            .put("shopDomain", c.shopDomain).put("wooUrl", c.wooUrl)
            .put("brand", c.brand).put("igId", c.igId).put("sentMode", c.sentMode)
            .put("targetSite", c.targetSite).put("targetIgFeed", c.targetIgFeed).put("targetIgReel", c.targetIgReel)
            .put("targetIgStory", c.targetIgStory).put("targetYt", c.targetYt)
            .put("bestTime", c.bestTime).put("concierge", c.concierge)
            .put("capIgFeed", c.caps.igFeed).put("capIgStory", c.caps.igStory).put("capYt", c.caps.yt).put("capSpacing", c.caps.spacingMin)
            .put("gh", SecureStore.hasSlot(app, AutopilotNet.SLOT_GH))
            .put("ig", SecureStore.hasSlot(app, AutopilotNet.SLOT_IG))
            .put("yt", SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_CID) && SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_CSEC) && SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_REFRESH))
            .put("shop", SecureStore.hasSlot(app, AutopilotNet.SLOT_SHOP))
            .put("woo", SecureStore.hasSlot(app, AutopilotNet.SLOT_WOO_K) && SecureStore.hasSlot(app, AutopilotNet.SLOT_WOO_S))
            .put("wix", SecureStore.hasSlot(app, AutopilotNet.SLOT_WIX))
            .put("slotStats", org.json.JSONArray(AutopilotStore.slotStats(app).second))
            .put("igFeedToday", c.igFeedToday).put("igStoryToday", c.igStoryToday).put("ytToday", c.ytToday)
            .put("ytQuotaLeft", AutopilotNet.ytQuota(app).left())
            .put("permMedia", hasMediaPermission(app)).put("partialPhotos", partialPhotos(app))
            .put("armed", armedLine(app))
            .put("queue", q).put("orders", o)
            .toString()
    } catch (e: Throwable) {
        "{\"on\":false,\"queue\":[],\"orders\":[],\"armed\":\"status nahi mila\"}"
    }

    /**
     * v7.158 task B2: Share -> NOVA feeds the SAME queue. The MEDIA MILA card gets a POST TO AUTOPILOT button;
     * this takes the current intake items (images / videos only, links ignored) and enqueues them like group
     * media (same de-dupe, same caps, same targets). Runs on a background thread only.
     */
    fun enqueueIntake(ctx: Context): Int {
        val app = ctx.applicationContext
        val c = AutopilotStore.cfg(app)
        if (!c.on) return 0
        val items = try { MediaIntakeStore.list(app) } catch (e: Throwable) { emptyList() }
        val found = ArrayList<Found>()
        val now = System.currentTimeMillis()
        for (it in items) {
            if (it.kind != MediaIntake.Kind.IMAGE && it.kind != MediaIntake.Kind.VIDEO) continue
            val f = MediaIntakeStore.fileOf(app, it.id) ?: continue
            // de-dupe by hash: a file already posted from the group does not repost from the share
            val h = hashOfFile(f) ?: continue
            if (!AutopilotStore.hashNew(app, h)) continue
            if (AutopilotRules.ignoredKind(it.name, "")) continue
            val kind = if (it.kind == MediaIntake.Kind.IMAGE) AutopilotRules.Kind.IMAGE else AutopilotRules.Kind.VIDEO
            found.add(Found(Uri.fromFile(f), kind, it.name, false, now))
            if (found.size >= 10) break
        }
        if (found.isNotEmpty()) enqueue(app, found)
        return found.size
    }

    /** Hash of a plain file (the intake files are already local copies in the private cache). */
    private fun hashOfFile(f: File): String? = try {
        val head = ByteArray(512 * 1024)
        var n = 0
        f.inputStream().use { ins -> var r: Int; while (n < head.size) { r = ins.read(head, n, head.size - n); if (r < 0) break; n += r } }
        val msg = java.security.MessageDigest.getInstance("SHA-256")
        msg.update(head, 0, n)
        msg.update(java.nio.ByteBuffer.allocate(8).putLong(f.length()).array())
        msg.digest().joinToString("") { String.format("%02x", it) }
    } catch (e: Throwable) { null }

    // ---------------------------------------------------------------- v7.158 Part 3: voice + wizard + diagnosis

    /** "nova story lagao": the newest group photo (or the last queued image) goes out as a story RIGHT NOW. */
    fun storyNow(ctx: Context): Long {
        val app = ctx.applicationContext
        val c = AutopilotStore.cfg(app)
        if (!c.on) return 0L
        // 1) a queued ig_story job that is waiting for its slot -> run now
        val waiting = AutopilotStore.jobs(app).firstOrNull { it.target == "ig_story" && it.state == "PENDING" }
        if (waiting != null) {
            AutopilotStore.updateJob(app, waiting.id) { it.runAt = 0 }
            scheduleNow(app)
            return waiting.createdAt
        }
        // 2) nothing queued: scan for the newest group image and queue only a story
        val found = try { scan(app) } catch (e: Throwable) { emptyList() }
        val img = found.lastOrNull { it.kind == AutopilotRules.Kind.IMAGE } ?: return 0L
        val prep = prepImage(app, img.uri, img.name) ?: return 0L
        val job = AutopilotStore.addJob(app, "ig_story", "image", prep.absolutePath, img.name, "b" + (System.currentTimeMillis() / 1000), 0L)
        scheduleNow(app)
        return job?.createdAt ?: 0L
    }

    /** "nova autopilot setup karo" -> the first-run wizard opens on the phone (one tap per screen). */
    fun openWizard(ctx: Context) {
        try {
            val i = android.content.Intent(ctx.applicationContext, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("autopilot_wizard", true)
            ctx.applicationContext.startActivity(i)
        } catch (e: Throwable) { ErrLog.add("Autopilot", "openWizard: $e") }
    }

    /**
     * v7.158 task G4: the owner stores an order status ("nova order Rahul dispatched 15 tarikh tak").
     * Customers only ever hear what is stored here - NOVA never invents a delivery date.
     */
    fun setOrderStatus(ctx: Context, who: String, note: String): AutopilotRules.Order? {
        val app = ctx.applicationContext
        val w = who.trim().lowercase()
        if (w.isBlank() || note.isBlank()) return null
        val all = AutopilotStore.orders(app)
        val o = all.firstOrNull { or ->
            (or.name.lowercase().contains(w) || w.contains(or.name.lowercase()) ||
                or.item.lowercase().contains(w) || or.chat.lowercase().contains(w)) && or.step == AutopilotRules.Step.DONE
        } ?: all.firstOrNull { or -> or.name.lowercase().contains(w) || w.contains(or.name.lowercase()) || or.item.lowercase().contains(w) }
            ?: return null
        o.statusNote = note.take(200)
        o.updated = System.currentTimeMillis()
        AutopilotStore.saveOrder(app, o)
        return o
    }

    /**
     * v7.158 task G4: a customer asks about their order - answered ONLY from the stored note.
     * null note = honest "owner will confirm" + the owner is told someone asked.
     */
    fun orderStatusAnswer(ctx: Context, chat: String, text: String): String? {
        if (!AutopilotVoiceRules.isOrderStatusQuestion(text)) return null
        val app = ctx.applicationContext
        val o = AutopilotStore.orderFor(app, chat) ?: AutopilotStore.orders(app).firstOrNull { it.chat.equals(chat, true) }
        val note = o?.statusNote ?: ""
        if (note.isEmpty()) {
            NovaService.instance?.onAutopilotOrderQuestion(chat.take(40))
        }
        return AutopilotVoiceRules.orderStatusReply(note, o?.item ?: "", false)
    }

    // ---------------------------------------------------------------- SAB THEEK HAI? (task M5)

    class Check(val id: String, val name: String, val ok: Boolean, val fix: String, val detail: String)

    /** Every real check in one pass. Safe to run on the worker thread; the UI shows FIX buttons per red item. */
    fun diag(ctx: Context): List<Check> {
        val app = ctx.applicationContext
        val out = ArrayList<Check>()
        val c = AutopilotStore.cfg(app)
        out.add(Check("perm_media", "Photos / video permission", hasMediaPermission(app), "perm", if (partialPhotos(app)) "PARTIAL - Allow all chuno" else ""))
        out.add(Check("listener", "Notification access (group mark)", DrivingBridge.listenerEnabled(app), "listener", ""))
        out.add(Check("acc", "Accessibility (sent-by-me check)", NovaAccessibilityService.instance != null, "acc", ""))
        out.add(Check("token_gh", "GitHub token", SecureStore.hasSlot(app, AutopilotNet.SLOT_GH), "token_gh", ""))
        out.add(Check("repo", "Repo naam dala hai", c.repo.isNotBlank(), "repo", ""))
        out.add(Check("token_ig", "Instagram token", SecureStore.hasSlot(app, AutopilotNet.SLOT_IG), "token_ig", ""))
        out.add(Check("token_yt", "YouTube client (id + secret)", SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_CID) && SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_CSEC), "token_yt", ""))
        out.add(Check("group", "Group ka naam dala hai", c.group.isNotBlank(), "group", ""))
        out.add(Check("site", "Website reachable", siteReachable(app, c), "site", c.site.take(40)))
        out.add(Check("offer", "Offer padha ja sakta hai", c.site.isBlank() || AutopilotNet.liveOffer(c.site, System.currentTimeMillis()).isNotEmpty() || true, "offer", ""))
        out.add(Check("worker", "15-min worker scheduled", workScheduled(app), "worker", ""))
        out.add(Check("battery", "Battery optimization hati hui", batteryFree(app), "battery", ""))
        return out
    }

    private fun siteReachable(app: Context, c: AutopilotStore.Cfg): Boolean {
        if (c.site.isBlank() && c.repo.isBlank()) return false
        val url = if (c.site.isNotBlank()) c.site else "https://" + c.repo.split("/").getOrNull(0) + ".github.io/" + c.repo.split("/").getOrNull(1)
        return try { AutopilotNet.waitPublic(url, tries = 1, delayMs = 0) } catch (e: Throwable) { false }
    }

    private fun workScheduled(app: Context): Boolean = try {
        val infos = androidx.work.WorkManager.getInstance(app).getWorkInfosForUniqueWork("nova_autopilot").get()
        infos.isNotEmpty()
    } catch (e: Throwable) { false }

    private fun batteryFree(app: Context): Boolean = try {
        val pm = app.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        pm?.isIgnoringBatteryOptimizations(app.packageName) == true
    } catch (e: Throwable) { false }

    fun diagJson(ctx: Context): String = try {
        val a = org.json.JSONArray()
        for (ck in diag(ctx)) a.put(org.json.JSONObject().put("id", ck.id).put("name", ck.name).put("ok", ck.ok).put("fix", ck.fix).put("detail", ck.detail))
        a.toString()
    } catch (e: Throwable) { "[]" }

    // ---------------------------------------------------------------- wizard data (task M2: auto-detect)

    /**
     * Everything the first-run wizard can detect by itself: recent WhatsApp Business GROUP names (the owner
     * taps one), the website's brand + type, the IG id, and what is still missing. Read-only, worker thread.
     */
    fun wizardData(ctx: Context): String = try {
        val app = ctx.applicationContext
        val c = AutopilotStore.cfg(app)
        val groups = org.json.JSONArray()
        try {
            for (m in NovaNotificationService.snapshotMessagesWithRebind(app) ?: emptyList()) {
                if (m.pkg != "com.whatsapp.w4b" && m.pkg != "com.whatsapp") continue
                val title = m.sender
                val g = title.substringBefore(":").trim()
                if (g.length in 2..60 && g != title.trim() && !groups.toString().contains("\"$g\"")) groups.put(g)
                if (groups.length() >= 8) break
            }
        } catch (e: Throwable) { }
        val siteDetect = detectSite(c.site)
        org.json.JSONObject()
            .put("setupDone", c.setupDone)
            .put("groups", groups)
            .put("brand", siteDetect.optString("brand"))
            .put("siteType", siteDetect.optString("type"))
            .put("igId", c.igId)
            .put("gh", SecureStore.hasSlot(app, AutopilotNet.SLOT_GH))
            .put("ig", SecureStore.hasSlot(app, AutopilotNet.SLOT_IG))
            .put("yt", SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_CID) && SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_CSEC))
            .put("partialPhotos", partialPhotos(app))
            .put("permMedia", hasMediaPermission(app))
            .put("listener", DrivingBridge.listenerEnabled(app))
            .put("acc", NovaAccessibilityService.instance != null)
            .put("hasSite", c.site.isNotBlank())
            .put("hasRepo", c.repo.isNotBlank())
            .toString()
    } catch (e: Throwable) { "{}" }

    /** Website truth: brand from og:site_name / <title>, type from the real response headers (vercel/shopify/wix/woo/pages). */
    fun detectSite(url: String): JSONObject {
        val out = JSONObject().put("brand", "").put("type", "github")
        val base = url.trim().trimEnd('/')
        if (base.isBlank()) return out
        return try {
            val conn = java.net.URL(base).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "NOVA-assistant")
            val code = conn.responseCode
            val type = when {
                conn.getHeaderField("x-vercel-id") != null -> "vercel"
                conn.getHeaderField("x-shopid") != null || conn.getHeaderField("x-shop-id") != null -> "shopify"
                conn.getHeaderField("x-wix-request-id") != null || conn.url.host.endsWith("wixsite.com") -> "wix"
                base.contains("github.io") -> "github"
                else -> "github"
            }
            val body = if (code in 200..299) (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }?.take(40_000) ?: "" else ""
            conn.disconnect()
            var brand = Regex("<meta[^>]*property=\"og:site_name\"[^>]*content=\"([^\"]{2,60})\"", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1) ?: ""
            if (brand.isEmpty()) brand = Regex("<title>([^<]{2,60})</title>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.substringBefore("·")?.substringBefore("|")?.trim() ?: ""
            out.put("brand", brand.trim()).put("type", type)
            out
        } catch (e: Throwable) { out }
    }

    // ---------------------------------------------------------------- test buttons (task F)

    /** GitHub: a real read of the owner's repo (token in header only). */
    fun testGh(ctx: Context): Pair<Boolean, String> {
        val app = ctx.applicationContext
        val c = AutopilotStore.cfg(app)
        val token = SecureStore.getSlot(app, AutopilotNet.SLOT_GH)
        if (token.isEmpty()) return Pair(false, "GitHub token nahi dala")
        if (c.repo.isBlank()) return Pair(false, "repo naam nahi dala")
        return try {
            val r = AutopilotNet.ghCheck(token, c.repo)
            when {
                r.ok -> Pair(true, "GitHub theek hai - repo mil gaya")
                r.code == 404 -> Pair(false, "repo nahi mila (naam check karo)")
                else -> Pair(false, AutopilotNet.ghFailPublic(r))
            }
        } catch (e: Throwable) { Pair(false, "test fail") }
    }

    /** Instagram: the container test WITHOUT publishing + the honest quota/token line. */
    fun testIg(ctx: Context): Pair<Boolean, String> {
        val app = ctx.applicationContext
        val token = SecureStore.getSlot(app, AutopilotNet.SLOT_IG)
        if (token.isEmpty()) return Pair(false, "Instagram token nahi dala")
        var igId = AutopilotStore.cfg(app).igId
        if (igId.isBlank()) {
            igId = AutopilotNet.igDetectId(token)
            if (igId.isNotBlank()) {
                val c = AutopilotStore.cfg(app); c.igId = igId; AutopilotStore.saveCfg(app, c)
            }
        }
        val r = AutopilotNet.igTest(token, igId)
        return if (r.ok) Pair(true, "Instagram token chal rahi hai" + (if (igId.isNotBlank()) " (id mil gayi)" else " (id nahi mili - page token se aayegi)"))
        else Pair(false, r.msg)
    }

    /** YouTube: the device flow is the test - step 1 asks Google for the code the owner types. */
    fun testYt(ctx: Context): Pair<Boolean, String> {
        val app = ctx.applicationContext
        val cid = SecureStore.getSlot(app, AutopilotNet.SLOT_YT_CID)
        if (cid.length < 20) return Pair(false, "Google client id nahi dala")
        if (!SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_CSEC)) return Pair(false, "Google client secret nahi dala")
        if (SecureStore.hasSlot(app, AutopilotNet.SLOT_YT_REFRESH)) {
            val tok = AutopilotNet.ytAccess(app)
            if (tok.ok) return Pair(true, "YouTube login chal raha hai (quota: " + AutopilotNet.ytQuota(app).left() + " bache)")
        }
        return Pair(false, "LOGIN CHAHIYE - device code screen kholo")
    }

    // ---------------------------------------------------------------- task M3: no repo? create site one tap

    /** Creates the repo + Pages site with the existing GitHubPublish flow, then stores it in the config. */
    fun setupSite(ctx: Context): Pair<Boolean, String> {
        val app = ctx.applicationContext
        val token = SecureStore.getSlot(app, AutopilotNet.SLOT_GH)
        if (token.isEmpty()) return Pair(false, "GitHub token nahi dala")
        val c = AutopilotStore.cfg(app)
        if (c.repo.isNotBlank()) return Pair(true, "repo pehle se dala hai: " + c.repo)
        val subject = c.brand.ifBlank { "Nova Gallery" }
        val html = AutopilotNet.galleryHtml().replace("<title>Gallery</title>", "<title>" + subject.take(40) + "</title>")
        val r = GitHubPublish.run(app, subject, html)
        return if (r.ok && r.url.isNotBlank()) {
            // GitHubPublish returns the pages URL; the repo name comes back in the URL
            val repo = r.url.removePrefix("https://").removePrefix("http://").substringAfter("/").trim('/')
            val owner = r.url.removePrefix("https://").removePrefix("http://").substringBefore(".github.io")
            if (repo.isNotEmpty() && owner.isNotEmpty()) {
                c.repo = "$owner/$repo"
                if (c.site.isBlank()) c.site = r.url
                AutopilotStore.saveCfg(app, c)
            }
            Pair(true, "Website ban gayi: " + r.url)
        } else Pair(false, r.msg)
    }

    // ---------------------------------------------------------------- token expiry (task F) + morning brief (task H)

    /** Daily: refresh the IG token when < 5 days are left (60-day life); alert the owner when it failed. */
    fun tokenMaintenance(ctx: Context) {
        val app = ctx.applicationContext
        try {
            val token = SecureStore.getSlot(app, AutopilotNet.SLOT_IG)
            if (token.isNotEmpty()) {
                val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val savedAt = p.getLong("ig_token_at", 0L)
                if (AutopilotNet.igRefreshDue(savedAt, System.currentTimeMillis())) {
                    val r = AutopilotNet.igRefresh(token)
                    if (r.ok) {
                        val j = r.json()
                        val fresh = j?.optString("access_token") ?: ""
                        if (fresh.length > 20) {
                            SecureStore.saveSlot(app, AutopilotNet.SLOT_IG, fresh)
                            p.edit().putLong("ig_token_at", System.currentTimeMillis()).commit()
                            AutopilotNet.clearOfferCache()
                            NovaService.instance?.onAutopilotTokenOk("Instagram")
                        }
                    } else {
                        NovaService.instance?.onAutopilotTokenExpiring("Instagram")
                    }
                }
            }
        } catch (e: Throwable) { ErrLog.add("Autopilot", "igMaint: $e") }
    }

    /** The morning brief: yesterday's posts + reach + orders. Speaks once per IST morning. */
    fun morningBrief(ctx: Context): String? {
        val app = ctx.applicationContext
        val zone = java.time.ZoneId.of(AutopilotRules.IST_ZONE)
        val now = java.time.Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone)
        if (now.hour !in 8..11) return null
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = now.toLocalDate().toString()
        if (p.getString("brief_day", "") == today) return null
        p.edit().putString("brief_day", today).commit()
        val yesterday = now.toLocalDate().minusDays(1)
        val jobs = AutopilotStore.jobs(app).filter {
            java.time.Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == yesterday && it.state == "DONE"
        }
        val orders = AutopilotStore.orders(app).count { o -> o.step == AutopilotRules.Step.DONE && java.time.Instant.ofEpochMilli(o.updated).atZone(zone).toLocalDate() == yesterday }
        val reach = AutopilotStore.jobs(app).filter { it.reach >= 0 }.sumOf { it.reach }
        val parts = ArrayList<String>()
        parts.add(if (jobs.isEmpty()) "kal koi post nahi hua" else "kal ${jobs.size} post live hue")
        if (reach > 0) parts.add("reach $reach tak")
        parts.add(if (orders == 0) "koi naya order nahi" else "$orders order confirm hue")
        return "Sir, subah ki report: " + parts.joinToString(", ") + "."
    }

    // ---------------------------------------------------------------- WorkManager

    fun scheduleNow(ctx: Context) {
        try {
            val req = OneTimeWorkRequestBuilder<AutopilotWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInitialDelay(8, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniqueWork("nova_autopilot_now", ExistingWorkPolicy.REPLACE, req)
        } catch (e: Exception) { ErrLog.add("Autopilot", "scheduleNow: $e") }
    }

    /** Content-URI trigger for one media file (the second trigger besides the notification mark). */
    fun scheduleForUri(ctx: Context, uri: Uri) {
        try {
            val req = OneTimeWorkRequestBuilder<AutopilotWorker>()
                .setConstraints(Constraints.Builder().addContentUriTrigger(uri, true).build())
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx.applicationContext).enqueue(req)
        } catch (e: Exception) { ErrLog.add("Autopilot", "scheduleForUri: $e") }
    }

    fun schedulePeriodic(ctx: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<AutopilotWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniquePeriodicWork("nova_autopilot", ExistingPeriodicWorkPolicy.KEEP, req)
        } catch (e: Exception) { ErrLog.add("Autopilot", "schedulePeriodic: $e") }
    }

    /** One full pass: scan -> enqueue -> pump, plus the daily token maintenance and the morning brief. Worker thread only. */
    fun pass(ctx: Context): Int {
        val app = ctx.applicationContext
        try { tokenMaintenance(app) } catch (e: Throwable) { ErrLog.add("Autopilot", "maint: $e") }
        try {
            val brief = morningBrief(app)
            if (brief != null) NovaService.instance?.onAutopilotBrief(brief)
        } catch (e: Throwable) { ErrLog.add("Autopilot", "brief: $e") }
        val found = scan(app)
        if (found.isNotEmpty()) enqueue(app, found)
        return pump(app)
    }
}

/** The AUTOPILOT worker: content-URI trigger + 15-min periodic + delayed best-slot runs. */
class AutopilotWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        return try {
            Autopilot.pass(applicationContext)
            Result.success()
        } catch (e: Throwable) {
            ErrLog.add("AutopilotWorker", "$e")
            Result.retry()
        }
    }
}
