package com.nova.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * v7.88 CAMERA EYE - a visible full-screen "NOVA dekh raha hai" screen that takes ONE still photo with the back or front camera and hands the JPEG bytes
 * to NovaService (in memory only: nothing is written to disk or the gallery). It is started only by a spoken / typed request. It closes itself after the
 * photo, after 20 s, or when the owner taps CANCEL. Camera2 only; every failure is reported through [status] and never throws out of this class.
 */
class CameraEyeActivity : Activity() {
    companion object {
        const val EXTRA_FACING = "facing"
        @Volatile var result: ByteArray? = null
        @Volatile var status: String = ""      // "ok", "perm", "fail", "cancel" ("" = nothing happened yet)
        @Volatile private var latch: CountDownLatch? = null

        /** Service side: call BEFORE startActivity, then [await]. */
        fun begin() { result = null; status = ""; latch = CountDownLatch(1) }

        fun await(ms: Long): Boolean = try { latch?.await(ms, TimeUnit.MILLISECONDS) ?: false } catch (e: InterruptedException) { false }

        private fun done(s: String, bytes: ByteArray?) {
            if (status.isEmpty()) { status = s; result = bytes }
            latch?.countDown()
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var bg: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var jpeg: ImageReader? = null
    private var preview: ImageReader? = null
    private var finished = false
    private var label: TextView? = null
    private var front = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        front = intent?.getStringExtra(EXTRA_FACING) == CameraEyeRules.Facing.FRONT.name
        val d = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.rgb(5, 8, 12))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        val tv = TextView(this)
        tv.setTextColor(Color.rgb(53, 242, 194)); tv.textSize = 20f; tv.gravity = Gravity.CENTER
        tv.text = if (front) "NOVA dekh raha hai\n(front camera)" else "NOVA dekh raha hai\n(peeche wala camera)"
        label = tv
        col.addView(tv)
        val cancel = Button(this)
        cancel.text = "CANCEL"
        cancel.setOnClickListener { finishWith("cancel", null) }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = (28 * d).toInt()
        col.addView(cancel, lp)
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        main.postDelayed({ finishWith("fail", null) }, CameraEyeRules.CAPTURE_WAIT_MS)   // never stays open
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), 88)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        if (code != 88) return
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) openCamera() else finishWith("perm", null)
    }

    private fun openCamera() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            var id: String? = null
            for (c in cm.cameraIdList) {
                val ch = cm.getCameraCharacteristics(c)
                if (ch.get(CameraCharacteristics.LENS_FACING) == want) { id = c; break }
            }
            if (id == null) id = cm.cameraIdList.firstOrNull()
            if (id == null) { finishWith("fail", null); return }
            val ch = cm.getCameraCharacteristics(id)
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map == null) { finishWith("fail", null); return }
            val js = map.getOutputSizes(ImageFormat.JPEG)?.filter { maxOf(it.width, it.height) <= CameraEyeRules.MAX_JPEG_SIDE }?.maxByOrNull { it.width.toLong() * it.height }
                ?: map.getOutputSizes(ImageFormat.JPEG)?.minByOrNull { it.width.toLong() * it.height }
            if (js == null) { finishWith("fail", null); return }
            val ps = map.getOutputSizes(ImageFormat.YUV_420_888)?.filter { it.width <= 800 && it.height <= 800 }?.maxByOrNull { it.width.toLong() * it.height }
                ?: map.getOutputSizes(ImageFormat.YUV_420_888)?.minByOrNull { it.width.toLong() * it.height }
            if (ps == null) { finishWith("fail", null); return }
            val t = HandlerThread("nova-cam-eye"); t.start(); thread = t
            val h = Handler(t.looper); bg = h
            val jr = ImageReader.newInstance(js.width, js.height, ImageFormat.JPEG, 1)
            val pr = ImageReader.newInstance(ps.width, ps.height, ImageFormat.YUV_420_888, 2)
            jpeg = jr; preview = pr
            pr.setOnImageAvailableListener({ r -> try { r.acquireLatestImage()?.close() } catch (e: Throwable) { } }, h)   // the preview frames only let auto exposure / focus settle
            jr.setOnImageAvailableListener({ r ->
                var bytes: ByteArray? = null
                try {
                    val img = r.acquireLatestImage()
                    if (img != null) {
                        try { val buf = img.planes[0].buffer; bytes = ByteArray(buf.remaining()); buf.get(bytes) } finally { img.close() }
                    }
                } catch (e: Throwable) { ErrLog.add("cameraEye", "read") }
                if (bytes != null && bytes.size > 1000) finishWith("ok", bytes)
            }, h)
            val sensor = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cd: CameraDevice) { device = cd; startSession(cd, jr, pr, sensor, h) }
                override fun onDisconnected(cd: CameraDevice) { try { cd.close() } catch (e: Throwable) { }; finishWith("fail", null) }
                override fun onError(cd: CameraDevice, e: Int) { ErrLog.add("cameraEye", "open $e"); try { cd.close() } catch (x: Throwable) { }; finishWith("fail", null) }
            }, h)
        } catch (e: CameraAccessException) {
            ErrLog.add("cameraEye", "access " + e.reason); finishWith("fail", null)
        } catch (e: SecurityException) {
            finishWith("perm", null)
        } catch (e: Throwable) {
            ErrLog.add("cameraEye", "open"); finishWith("fail", null)
        }
    }

    @Suppress("DEPRECATION")
    private fun startSession(cd: CameraDevice, jr: ImageReader, pr: ImageReader, sensor: Int, h: Handler) {
        try {
            cd.createCaptureSession(listOf(pr.surface, jr.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    try {
                        val prev = cd.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        prev.addTarget(pr.surface)
                        prev.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        prev.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        s.setRepeatingRequest(prev.build(), null, h)
                        h.postDelayed({ takeStill(cd, s, jr, sensor) }, 1300L)   // about 1.3 s for exposure and focus to settle
                    } catch (e: Throwable) { ErrLog.add("cameraEye", "preview"); finishWith("fail", null) }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { ErrLog.add("cameraEye", "configure"); finishWith("fail", null) }
            }, h)
        } catch (e: Throwable) { ErrLog.add("cameraEye", "session"); finishWith("fail", null) }
    }

    private fun takeStill(cd: CameraDevice, s: CameraCaptureSession, jr: ImageReader, sensor: Int) {
        try {
            if (finished) return
            val rot = when (windowManager.defaultDisplay.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
            val orient = if (front) (sensor + rot) % 360 else (sensor - rot + 360) % 360
            val req = cd.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            req.addTarget(jr.surface)
            req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            req.set(CaptureRequest.JPEG_ORIENTATION, orient)
            req.set(CaptureRequest.JPEG_QUALITY, 85.toByte())
            s.capture(req.build(), null, bg)
        } catch (e: Throwable) { ErrLog.add("cameraEye", "still"); finishWith("fail", null) }
    }

    /** Any thread. Reports once, then closes the camera and the screen. */
    private fun finishWith(s: String, bytes: ByteArray?) {
        if (finished) return
        finished = true
        done(s, bytes)
        main.post {
            releaseAll()
            try { finish() } catch (e: Throwable) { }
        }
    }

    private fun releaseAll() {
        try { session?.close() } catch (e: Throwable) { }
        try { device?.close() } catch (e: Throwable) { }
        try { jpeg?.close() } catch (e: Throwable) { }
        try { preview?.close() } catch (e: Throwable) { }
        try { thread?.quitSafely() } catch (e: Throwable) { ErrLog.addOnce("quiet:CameraEyeActivity", e.toString()) }
        session = null; device = null; jpeg = null; preview = null; thread = null
    }

    override fun onStop() {
        super.onStop()
        if (!finished) finishWith("cancel", null)   // the owner left the screen: the camera is closed at once
    }

    override fun onDestroy() {
        if (!finished) { finished = true; done("cancel", null) }
        releaseAll()
        super.onDestroy()
    }
}
