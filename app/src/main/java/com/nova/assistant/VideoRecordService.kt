package com.nova.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Part 6B: explicit, visible-notification camera recorder. Screen-off behavior depends on the OEM and Android camera policy.
 * It must be started while NOVA's UI is visible; all failures are surfaced and no success is reported until frames/data grow. */
class VideoRecordService : Service() {
    companion object {
        const val ACTION_START = "com.nova.assistant.VIDEO_START"
        const val ACTION_STOP = "com.nova.assistant.VIDEO_STOP"
        const val EXTRA_FACING = "facing"
        @Volatile var active = false
            private set
        @Volatile var starting = false
            private set
        fun isBusy(): Boolean = active || starting
        @Volatile var lastStatus = "off"
            private set
        @Volatile var lastFileName = ""
    }

    private var camera: Camera? = null
    private var recorder: MediaRecorder? = null
    private var wake: PowerManager.WakeLock? = null
    private var temp: File? = null
    private var startedAt = 0L
    private var writerThread: Thread? = null
    private val stopping = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("nova_video", "NOVA video recording", NotificationManager.IMPORTANCE_LOW)
            )
        } catch (ignored: Exception) { ErrLog.add("VideoRecordService", ignored.toString()) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { Thread { stopVideo("user") }.start(); return START_NOT_STICKY }
            ACTION_START -> {
                val facing = intent.getStringExtra(EXTRA_FACING) ?: "back"
                Thread { startVideo(facing) }.start()
            }
            else -> return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun startVideo(facing: String) {
        if (isBusy()) { lastStatus = "already"; return }
        starting = true
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            lastStatus = "permission"; starting = false; notifyResult(lastStatus); stopSelf(); return
        }
        if (Build.VERSION.SDK_INT >= 34 && !NovaService.uiVisible) {
            lastStatus = "not_visible"; starting = false; notifyResult(lastStatus); stopSelf(); return
        }
        try {
            startForegroundCompat(notification(true))
            // Give NovaService's single-mic loop time to release AudioRecord before MediaRecorder claims the microphone.
            Thread.sleep(450L)
            val dir = RecordingManager.videoDir(this); if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("folder")
            val outFile = File(dir, "NOVA_video_${System.currentTimeMillis()}.mp4")
            temp = outFile
            val count = Camera.getNumberOfCameras(); if (count <= 0) throw IllegalStateException("no_camera")
            val desired = if (facing == "front") Camera.CameraInfo.CAMERA_FACING_FRONT else Camera.CameraInfo.CAMERA_FACING_BACK
            var id = -1; val info = Camera.CameraInfo()
            for (i in 0 until count) { Camera.getCameraInfo(i, info); if (info.facing == desired) { id = i; break } }
            if (id < 0) throw IllegalStateException(if (desired == Camera.CameraInfo.CAMERA_FACING_FRONT) "front_camera_unavailable" else "back_camera_unavailable")
            val cam = Camera.open(id); camera = cam
            // A tiny off-screen preview surface is used because legacy Camera requires a preview target.
            // Several OEM camera HALs reject this or stop when screen-off; such a phone reports a real error.
            cam.setPreviewTexture(SurfaceTexture(10))
            val p = cam.parameters
            val sizes = p.supportedVideoSizes ?: p.supportedPreviewSizes ?: emptyList()
            val size = sizes.filter { it.width <= 1280 && it.height <= 720 }.maxByOrNull { it.width.toLong() * it.height } 
                ?: sizes.maxByOrNull { it.width.toLong() * it.height }
            if (size != null) { p.setPreviewSize(size.width, size.height); try { cam.parameters = p } catch (ignored: Exception) { ErrLog.add("VideoRecordService", ignored.toString()) } }
            cam.startPreview()
            cam.unlock()
            val mr = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            recorder = mr
            mr.setCamera(cam)
            mr.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            mr.setVideoSource(MediaRecorder.VideoSource.CAMERA)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mr.setVideoFrameRate(24)
            if (size != null) mr.setVideoSize(size.width, size.height)
            mr.setVideoEncodingBitRate(2_000_000)
            mr.setAudioEncodingBitRate(96_000)
            mr.setOutputFile(outFile.absolutePath)
            mr.prepare()
            mr.start()
            startedAt = System.currentTimeMillis(); active = false; starting = true; stopping.set(false); lastStatus = "starting"; lastFileName = outFile.name
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nova:video").apply { setReferenceCounted(false); acquire(3 * 60 * 60 * 1000L + 60_000L) }
            } catch (ignored: Exception) { ErrLog.add("VideoRecordService", ignored.toString()) }
            notifyNow(notification(true))
            writerThread = Thread({
                var previousSize = 0L
                var grew = false
                while (isBusy() && !stopping.get()) {
                    try { Thread.sleep(2_000L) } catch (ignored: InterruptedException) { break }
                    val f = temp
                    val sizeNow = if (f != null && f.exists()) f.length() else 0L
                    if (sizeNow > previousSize && sizeNow >= 16_384L) grew = true
                    previousSize = sizeNow
                    if (grew && !active) {
                        // First positive file-size growth is our minimum observable evidence of encoded camera data.
                        active = true; starting = false; lastStatus = "on"
                        notifyNow(notification(true))
                    }
                    if (!grew && System.currentTimeMillis() - startedAt > 15_000L) {
                        lastStatus = "no_frames"; stopVideo("no_frames"); break
                    }
                    if (System.currentTimeMillis() - startedAt >= 3 * 60 * 60 * 1000L) { stopVideo("max"); break }
                    if (f != null && f.usableSpace < 60L * 1024L * 1024L) { stopVideo("space"); break }
                    notifyNow(notification(true))
                }
            }, "nova-video-watch").apply { isDaemon = true; start() }
        } catch (t: Throwable) {
            lastStatus = "failed:" + (t.message ?: t.javaClass.simpleName).take(80)
            active = false
            cleanupCapture(finalize = false)
            starting = false
            notifyResult(lastStatus)
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }

    private fun notification(running: Boolean): Notification {
        val stop = PendingIntent.getService(this, 63, Intent(this, VideoRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val action = Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause), "STOP VIDEO", stop).build()
        return Notification.Builder(this, "nova_video")
            .setContentTitle(if (running) "NOVA video recording" else "NOVA video")
            .setContentText(if (running) "Camera/mic indicator visible · tap STOP to save" else "Not recording")
            .setSmallIcon(android.R.drawable.ic_menu_camera).setOngoing(running).setUsesChronometer(running)
            .setWhen(if (startedAt > 0L) startedAt else System.currentTimeMillis()).addAction(action).build()
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(2, n, types)
        } else startForeground(2, n)
    }
    private fun notifyNow(n: Notification) { try { getSystemService(NotificationManager::class.java).notify(2, n) } catch (ignored: Exception) { ErrLog.add("VideoRecordService", ignored.toString()) } }

    @Synchronized
    private fun stopVideo(reason: String) {
        if (!stopping.compareAndSet(false, true)) return
        starting = true // keep NovaService's AudioRecord paused until MediaRecorder has released the mic
        val f = temp
        val elapsed = System.currentTimeMillis() - startedAt
        val stopOk = cleanupCapture(finalize = true)   // v7.12: false = MediaRecorder.stop() failed, the mp4 may be broken: never exported or called saved
        var saved = false
        if (stopOk && f != null && f.exists() && f.length() >= 16_384L) saved = exportVideo(f)
        lastStatus = when {
            reason == "no_frames" -> "no_frames"
            !stopOk -> "stop_failed"
            saved && reason == "max" -> "saved_max"
            saved && reason == "space" -> "saved_space"
            saved -> "saved"
            else -> "save_failed"
        }
        if (saved) lastFileName = f?.name ?: lastFileName
        else if (f != null && f.exists() && f.length() < 16_384L) try { f.delete() } catch (ignored: Exception) { }
        starting = false
        try { getSystemService(NotificationManager::class.java).cancel(2) } catch (ignored: Exception) { }
        notifyResult(lastStatus)   // v7.12: a visible result, also for auto-stops
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun cleanupCapture(finalize: Boolean): Boolean {
        active = false
        var stopOk = true
        try { recorder?.let { if (finalize) try { it.stop() } catch (ignored: Exception) { stopOk = false; ErrLog.add("VideoRecordService", "MediaRecorder.stop failed: " + ignored) }; it.reset(); it.release() } } catch (ignored: Exception) { }
        recorder = null
        try { camera?.stopPreview() } catch (ignored: Exception) { ErrLog.add("VideoRecordService", ignored.toString()) }
        try { camera?.lock() } catch (ignored: Exception) { ErrLog.add("VideoRecordService", ignored.toString()) }
        try { camera?.release() } catch (ignored: Exception) { }
        camera = null
        try { wake?.let { if (it.isHeld) it.release() } } catch (ignored: Exception) { }
        wake = null
        return stopOk
    }

    /** v7.12: the result of a recording (saved / failed / never started) as a normal notification that stays until swiped away. */
    private fun notifyResult(status: String) {
        val t = VideoResultRules.forStatus(status) ?: return
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel("nova_video", "NOVA video", NotificationManager.IMPORTANCE_LOW))
            val n = Notification.Builder(this, "nova_video").setContentTitle(t.title).setContentText(t.body)
                .setStyle(Notification.BigTextStyle().bigText(t.body)).setSmallIcon(android.R.drawable.ic_menu_camera).setAutoCancel(true).build()
            nm.notify(3, n)
        } catch (ignored: Exception) { ErrLog.add("VideoRecordService", ignored.toString()) }
    }

    private fun exportVideo(file: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return true // app-private file stays playable/shareable through FileProvider
        return try {
            val cr = contentResolver
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name); put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/NOVA"); put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
            var ok = false
            try { cr.openOutputStream(uri)?.use { out -> file.inputStream().use { input -> input.copyTo(out) }; ok = true } } catch (ignored: Exception) { ErrLog.add("VideoRecordService", ignored.toString()) }
            if (ok) { cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null); file.delete() }
            else cr.delete(uri, null, null)
            ok
        } catch (ignored: Exception) { false }
    }

    @Synchronized
    override fun onDestroy() {
        // `starting` matters too: the file-growth check may not have set `active` yet.
        if (isBusy()) Thread { stopVideo("destroy") }.start() else cleanupCapture(finalize = false)
        starting = false
        super.onDestroy()
    }
}
