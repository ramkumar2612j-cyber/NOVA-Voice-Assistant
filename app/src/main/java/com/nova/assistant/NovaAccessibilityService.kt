package com.nova.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * What this service does (and nothing else):
 *  1. Global navigation (home/back/recents/notifications/quick settings/lock) on a local voice command.
 *  2. Press the green SEND button in WhatsApp after the user said "yes" (WhatsApp packages only,
 *     only when the text box holds exactly the confirmed message, 15 s time limit).
 *  3. Read the visible accessibility TEXT when the user says "analyze screen", or - only if the user
 *     switched Screen Monitoring ON - scan it locally (debounced) for obvious error/warning wording.
 *  4. TASK AGENT (v28): only after the owner said "yes" to a spoken task, it lists the visible items (no
 *     password fields, no text typed into boxes) and taps / types by index. NovaService decides what is allowed
 *     (AgentRules + Control). On protected screens (settings, payment, bank) the list is empty.
 * It never stores screen text. Screen text can leave the phone only after the owner explicitly starts a screen session
 * or confirms an agent task; the configured provider and its disclosure/consent settings apply. Protected screens are refused.
 */
class NovaAccessibilityService : AccessibilityService() {

    class Snap(val pkg: String, val lines: List<String>)

    companion object {
        @Volatile var instance: NovaAccessibilityService? = null
        private val PKGS = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val SEND_WORDS = setOf("send", "भेजें", "भेजे", "enviar", "senden", "envoyer")
        private const val TIMEOUT_MS = 15000L
        private const val DEBOUNCE_MS = 1000L
        private const val MIN_SCAN_GAP_MS = 3000L
        private const val MAX_LINES = 300

        fun armSend(message: String, cb: (Boolean) -> Unit) {
            val s = instance
            if (s == null) cb(false) else s.arm(message, cb)
        }

        /**
         * Visible text of the active window. Password fields are never read. Editable fields (what the
         * user is typing) are only included when [includeEditable] is true (local use only).
         * Returns null when Accessibility is not connected or Android exposes no window.
         */
        fun readScreen(includeEditable: Boolean): Snap? {
            val s = instance ?: return null
            var root = s.rootInActiveWindow
            if (root == null || root.packageName?.toString() == s.packageName) {
                val alt = otherAppRoot(s)
                if (alt != null) root = alt
            }
            if (root == null) return null
            val pkg = root.packageName?.toString() ?: ""
            // Never collect text from bank/payment/installer/settings/NOVA screens; package is enough to refuse.
            if (ScreenSession.isProtected(pkg, s.packageName)) return Snap(pkg, emptyList())
            val out = ArrayList<String>()
            collect(root, 0, out, includeEditable)
            return Snap(pkg, out)
        }

        /** Top application window that is NOT NOVA (used when NOVA's own card or chat has the focus). */
        private fun otherAppRoot(s: NovaAccessibilityService): AccessibilityNodeInfo? {
            return try {
                s.windows
                    .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
                    .sortedByDescending { it.layer }
                    .mapNotNull { it.root }
                    .firstOrNull { it.packageName?.toString() != s.packageName }
            } catch (e: Exception) { null }
        }

        fun foregroundPackage(): String? = instance?.rootInActiveWindow?.packageName?.toString()

        /**
         * v7.64 PART 14B-2: movie scan. null = service not running; true = the app on screen is protected (bank, payment, settings, installer ...) or cannot be told;
         * false = fine to capture. NOVA's own window is skipped (the user speaks to NOVA while the reel is on top), the same way readScreen() does it.
         * Fail closed: no window at all counts as protected.
         */
        fun movieProtected(): Boolean? {
            val s = instance ?: return null
            var root = s.rootInActiveWindow
            if (root == null || root.packageName?.toString() == s.packageName) {
                val alt = otherAppRoot(s)
                if (alt != null) root = alt
            }
            val pkg = root?.packageName?.toString() ?: return true
            return ScreenSession.isProtected(pkg, s.packageName)
        }

        /** v7.64 PART 14B-2: one small in-memory JPEG for the movie scan; refuses (null) on a protected screen. Never saved to disk. */
        fun movieShot(): ByteArray? {
            if (movieProtected() != false) return null
            return agentScreenshot()
        }

        /** v6.78: second way to start an activity when the foreground service is blocked from the background: the same intent from the Accessibility service's own context. false = service not running or Android refused. */
        fun startFromService(i: Intent): Boolean {
            val svc = instance ?: return false
            return try { svc.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
        }

        fun performGlobalAction(action: Int): Boolean = instance?.performGlobalAction(action) ?: false

        /** v6.53: Android's own screenshot action: the picture goes to the gallery, no storage permission is needed. Android 9+. */
        fun screenshotToGallery(): Ctl {
            if (Build.VERSION.SDK_INT < 28) return Ctl(false, "old_android")
            return if (instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) == true) Ctl(true, "ok") else Ctl(false, "failed")
        }

        /** v6.53: two-finger pinch in the middle of the screen (zoom a photo / profile picture / map). */
        fun pinch(zoomIn: Boolean): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            val dm = s.resources.displayMetrics
            val cx = dm.widthPixels / 2f
            val cy = dm.heightPixels / 2f
            val near = dm.widthPixels * 0.06f
            val far = dm.widthPixels * 0.30f
            val a = Path()
            val b = Path()
            if (zoomIn) {
                a.moveTo(cx - near, cy); a.lineTo(cx - far, cy)
                b.moveTo(cx + near, cy); b.lineTo(cx + far, cy)
            } else {
                a.moveTo(cx - far, cy); a.lineTo(cx - near, cy)
                b.moveTo(cx + far, cy); b.lineTo(cx + near, cy)
            }
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(a, 0, 450))
                .addStroke(GestureDescription.StrokeDescription(b, 0, 450))
                .build()
            val latch = CountDownLatch(1)
            var done = false
            val accepted = s.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { done = true; latch.countDown() }
                override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
            }, null)
            if (!accepted) return Ctl(false, "failed")
            try { latch.await(2000, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { ErrLog.add("NovaAccessibilityService", e.toString()) }
            return if (done) Ctl(true, "ok") else Ctl(false, "failed")
        }

        // ------------------------------------------------------------------ voice screen control
        // Rules (what is allowed) live in Control.kt. These must run on a worker thread (they may wait).

        /** code: ok, no_acc, no_window, blocked, not_found, ambiguous:N, no_focus, password, failed. */
        class Ctl(val ok: Boolean, val code: String)

        /** A cheap fingerprint of the visible text, to tell whether an action changed the screen. */
        fun screenSignature(): Int = readScreen(false)?.lines?.hashCode() ?: 0

        fun tapLabel(label: String): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            val root = s.rootInActiveWindow ?: return Ctl(false, "no_window")
            if (Control.blocked(root.packageName?.toString(), s.packageName, "tap")) return Ctl(false, "blocked")
            val exact = ArrayList<AccessibilityNodeInfo>()
            val part = ArrayList<AccessibilityNodeInfo>()
            findLabel(root, 0, label, exact, part)
            val list = if (exact.isNotEmpty()) exact else part
            if (list.isEmpty()) return Ctl(false, "not_found")
            if (list.size > 1) return Ctl(false, "ambiguous:" + list.size)
            return if (s.clickNode(list[0])) Ctl(true, "ok") else Ctl(false, "failed")
        }

        fun typeText(text: String): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            val root = s.rootInActiveWindow ?: return Ctl(false, "no_window")
            if (Control.blocked(root.packageName?.toString(), s.packageName, "type")) return Ctl(false, "blocked")
            val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return Ctl(false, "no_focus")
            if (node.isPassword) return Ctl(false, "password")
            if (!node.isEditable) return Ctl(false, "no_focus")
            val old = node.text?.toString() ?: ""
            val hint = node.hintText?.toString()
            val base = if (hint != null && old == hint) "" else old      // an empty box shows its hint as text
            return setTextChecked(node, base + text)
        }

        /**
         * v6.92: inside the app that is now in front: find (or open) its search box, type [query], press search,
         * and when [tapResult] is true tap the first result that mentions the query. Never types into password
         * fields and never works in banking / settings / installer screens (Control.blocked).
         */
        fun searchInApp(query: String, tapResult: Boolean): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            if (Control.blocked(s.rootInActiveWindow?.packageName?.toString(), s.packageName, "type")) return Ctl(false, "blocked")
            var box = findSearchBox(s.rootInActiveWindow, 0)
            if (box == null) {
                for (w in listOf("Search", "Search YouTube", "Find", "खोजें", "सर्च")) {
                    if (tapLabel(w).ok) break
                }
                try { Thread.sleep(900) } catch (e: InterruptedException) { ErrLog.add("NovaAccessibilityService", e.toString()) }
                box = findSearchBox(s.rootInActiveWindow, 0)
            }
            if (box == null) return Ctl(false, "no_box")
            val typed = setTextChecked(box, query)
            if (!typed.ok) return Ctl(false, "not_typed")
            var submitted = false
            if (Build.VERSION.SDK_INT >= 30) {
                submitted = box.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }
            if (!submitted) submitted = tapLabel("Search").ok
            if (!submitted) return Ctl(false, "typed_only")
            if (!tapResult) return Ctl(true, "searched")
            try { Thread.sleep(2500) } catch (e: InterruptedException) { ErrLog.add("NovaAccessibilityService", e.toString()) }
            return if (tapFirstResult(s, query)) Ctl(true, "played") else Ctl(true, "searched")
        }

        private fun findSearchBox(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (n == null || depth > 25) return null
            try {
                if (n.isEditable && n.isVisibleToUser && !n.isPassword) return n
                for (i in 0 until n.childCount) {
                    val r = findSearchBox(n.getChild(i), depth + 1)
                    if (r != null) return r
                }
            } catch (e: Exception) {
                // node went away while walking: ignore
            }
            return null
        }

        private fun collectResultHits(n: AccessibilityNodeInfo?, depth: Int, word: String, out: ArrayList<AccessibilityNodeInfo>) {
            if (n == null || depth > 30 || out.size > 30) return
            try {
                if (!n.isVisibleToUser) return
                if (!n.isEditable && !n.isPassword) {
                    val t = n.text?.toString()
                    val d = n.contentDescription?.toString()
                    val hit = (!t.isNullOrBlank() && Control.matchScore(t, word) > 0) || (!d.isNullOrBlank() && Control.matchScore(d, word) > 0)
                    if (hit) out.add(n)
                }
                for (i in 0 until n.childCount) collectResultHits(n.getChild(i), depth + 1, word, out)
            } catch (e: Exception) {
                // node went away while walking: ignore
            }
        }

        private fun tapFirstResult(s: NovaAccessibilityService, query: String): Boolean {
            val word = query.trim().split(" ").firstOrNull { it.length >= 3 } ?: query.trim()
            val root = s.rootInActiveWindow ?: return false
            val hits = ArrayList<AccessibilityNodeInfo>()
            collectResultHits(root, 0, word, hits)
            val sorted = hits.sortedBy { h -> val r = Rect(); h.getBoundsInScreen(r); r.top }
            var tries = 0
            for (h in sorted) {
                if (tries++ >= 4) break
                if (s.clickNode(h)) return true
            }
            return false
        }

        fun scrollDir(dir: String): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            val path = Control.swipePath(dir) ?: return Ctl(false, "failed")
            val dm = s.resources.displayMetrics
            val p = Path()
            p.moveTo(path[0] * dm.widthPixels, path[1] * dm.heightPixels)
            p.lineTo(path[2] * dm.widthPixels, path[3] * dm.heightPixels)
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 320)).build()
            val latch = CountDownLatch(1)
            var done = false
            val accepted = s.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { done = true; latch.countDown() }
                override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
            }, null)
            if (!accepted) return Ctl(false, "failed")
            try { latch.await(1500, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { ErrLog.add("NovaAccessibilityService", e.toString()) }
            return if (done) Ctl(true, "ok") else Ctl(false, "failed")
        }


        // ------------------------------------------------------------------ v7.114 REEL helpers (used only by ReelRunner)
        // Short, bounded actions on the video app that is in front. Every one refuses protected apps (Control.blocked), never reads or types into password fields,
        // never waits more than a few seconds, and returns a Ctl so the runner can say honestly what worked.

        /** The window of the app in front: NOVA's own window is skipped (the owner talks to NOVA while the reel is on top). */
        private fun reelRoot(): AccessibilityNodeInfo? {
            val s = instance ?: return null
            var root = s.rootInActiveWindow
            if (root == null || root.packageName?.toString() == s.packageName) { val alt = otherAppRoot(s); if (alt != null) root = alt }
            return root
        }

        /** Package name of the app in front (NOVA skipped), or null. */
        fun reelPackage(): String? = try { reelRoot()?.packageName?.toString() } catch (e: Exception) { null }

        /** Visible node whose description or text IS one of [labels] (case-insensitive, trimmed). First match in tree order. */
        private fun reelFind(root: AccessibilityNodeInfo?, labels: List<String>, depth: Int = 0): AccessibilityNodeInfo? {
            if (root == null || depth > 30) return null
            try {
                if (!root.isVisibleToUser) return null
                if (!root.isPassword) {
                    val d = root.contentDescription?.toString()?.trim()?.lowercase()
                    val tx = root.text?.toString()?.trim()?.lowercase()
                    if ((!d.isNullOrEmpty() && d in labels) || (!tx.isNullOrEmpty() && tx in labels)) return root
                }
                for (i in 0 until root.childCount) { val r = reelFind(root.getChild(i), labels, depth + 1); if (r != null) return r }
            } catch (e: Exception) { /* node went away while walking */ }
            return null
        }

        /** True when a visible node with exactly one of these labels exists ("unlike", "liked" ...). */
        fun reelHas(labels: List<String>): Boolean = try { reelFind(reelRoot(), labels.map { it.lowercase() }) != null } catch (e: Exception) { false }

        /** Click the first visible node that is exactly one of [labels]. code: ok, no_acc, no_window, blocked, not_found, failed. */
        fun reelTap(labels: List<String>): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            val root = reelRoot() ?: return Ctl(false, "no_window")
            if (Control.blocked(root.packageName?.toString(), s.packageName, "tap")) return Ctl(false, "blocked")
            val n = reelFind(root, labels.map { it.lowercase() }) ?: return Ctl(false, "not_found")
            return if (s.clickNode(n)) Ctl(true, "ok") else Ctl(false, "failed")
        }

        /** Visible text lines of the app in front (for checks like "is my comment on the screen"). Password fields are never read. */
        fun reelLines(): List<String> {
            val root = reelRoot() ?: return emptyList()
            val s = instance ?: return emptyList()
            if (Control.blocked(root.packageName?.toString(), s.packageName, "tap")) return emptyList()
            val out = ArrayList<String>()
            try { collect(root, 0, out, false) } catch (e: Exception) { /* ignore */ }
            return out
        }

        /** Click the first visible, NOT editable node whose description or text CONTAINS one of [parts] (profile grid tiles, a name in a share list). */
        fun reelTapContains(parts: List<String>): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            val root = reelRoot() ?: return Ctl(false, "no_window")
            if (Control.blocked(root.packageName?.toString(), s.packageName, "tap")) return Ctl(false, "blocked")
            val low = parts.map { it.lowercase() }.filter { it.isNotEmpty() }
            var found: AccessibilityNodeInfo? = null
            fun walk(n: AccessibilityNodeInfo?, depth: Int) {
                if (n == null || depth > 30 || found != null) return
                try {
                    if (!n.isVisibleToUser) return
                    if (!n.isPassword && !n.isEditable) {
                        val d = n.contentDescription?.toString()?.lowercase() ?: ""
                        val tx = n.text?.toString()?.lowercase() ?: ""
                        if (low.any { d.contains(it) || tx.contains(it) }) { found = n; return }
                    }
                    for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
                } catch (e: Exception) { /* ignore */ }
            }
            walk(root, 0)
            val n = found ?: return Ctl(false, "not_found")
            return if (s.clickNode(n)) Ctl(true, "ok") else Ctl(false, "failed")
        }

        /** True when a visible text box (comment / search) is open. Password boxes do not count. */
        fun reelHasEditable(): Boolean {
            val root = reelRoot() ?: return false
            var yes = false
            fun walk(n: AccessibilityNodeInfo?, depth: Int) {
                if (n == null || depth > 30 || yes) return
                try {
                    if (!n.isVisibleToUser) return
                    if (n.isEditable && !n.isPassword) { yes = true; return }
                    for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
                } catch (e: Exception) { /* ignore */ }
            }
            walk(root, 0)
            return yes
        }

        private fun reelGesture(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long, taps: Int = 1): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            val dm = s.resources.displayMetrics
            val b = GestureDescription.Builder()
            for (k in 0 until taps) {
                val p = Path()
                p.moveTo(x1 * dm.widthPixels, y1 * dm.heightPixels)
                if (x1 != x2 || y1 != y2) p.lineTo(x2 * dm.widthPixels, y2 * dm.heightPixels)
                b.addStroke(GestureDescription.StrokeDescription(p, k * 120L, ms))
            }
            val latch = CountDownLatch(1)
            var done = false
            val accepted = s.dispatchGesture(b.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { done = true; latch.countDown() }
                override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
            }, null)
            if (!accepted) return Ctl(false, "failed")
            try { latch.await(2000, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { ErrLog.add("NovaAccessibilityService", e.toString()) }
            return if (done) Ctl(true, "ok") else Ctl(false, "failed")
        }

        /** Next reel (finger moves up) or previous reel (finger moves down). A quick swipe so the pager really moves. */
        fun reelSwipe(next: Boolean): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            if (Control.blocked(reelPackage(), s.packageName, "scroll")) return Ctl(false, "blocked")
            return if (next) reelGesture(0.5f, 0.80f, 0.5f, 0.25f, 180) else reelGesture(0.5f, 0.30f, 0.5f, 0.78f, 180)
        }

        /** Double tap in the middle of the video = like (the app's own gesture). Used when the heart button cannot be found. */
        fun reelDoubleTap(): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            if (Control.blocked(reelPackage(), s.packageName, "tap")) return Ctl(false, "blocked")
            return reelGesture(0.5f, 0.42f, 0.5f, 0.42f, 40, taps = 2)
        }

        /** Type [text] into the visible comment / search box (the one whose hint or description says "comment" / "search" first, else the first editable). */
        fun reelType(text: String, hints: List<String>): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            val root = reelRoot() ?: return Ctl(false, "no_window")
            if (Control.blocked(root.packageName?.toString(), s.packageName, "type")) return Ctl(false, "blocked")
            val boxes = ArrayList<AccessibilityNodeInfo>()
            fun walk(n: AccessibilityNodeInfo?, depth: Int) {
                if (n == null || depth > 30 || boxes.size > 8) return
                try {
                    if (!n.isVisibleToUser) return
                    if (n.isEditable && !n.isPassword) boxes.add(n)
                    for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
                } catch (e: Exception) { /* ignore */ }
            }
            walk(root, 0)
            if (boxes.isEmpty()) return Ctl(false, "no_focus")
            val pick = boxes.firstOrNull { b ->
                val h = ((b.hintText?.toString() ?: "") + " " + (b.contentDescription?.toString() ?: "") + " " + (b.text?.toString() ?: "")).lowercase()
                hints.any { h.contains(it) }
            } ?: boxes[0]
            return setTextChecked(pick, Control.cleanTyped(text))
        }

        // ------------------------------------------------------------------ task agent (v28)
        // Items of the last agentSnapshot(), so a later tap-by-index hits the same node. Worker thread only.
        private val agentRefs = ArrayList<AccessibilityNodeInfo>()

        /** Package + numbered visible items of the top app window. null = Accessibility off or no readable window. */
        fun agentSnapshot(): Pair<String, List<AgentRules.Node>>? {
            val s = instance ?: return null
            var root = s.rootInActiveWindow
            if (root == null || root.packageName?.toString() == s.packageName) {
                val alt = otherAppRoot(s)
                if (alt != null) root = alt
            }
            if (root == null) return null
            val pkg = root.packageName?.toString() ?: ""
            // Fail closed: the task-agent snapshot must obey the same protected-screen policy as readScreen().
            if (ScreenSession.isProtected(pkg, s.packageName)) {
                synchronized(agentRefs) { agentRefs.clear() }
                return Pair(pkg, emptyList<AgentRules.Node>())
            }
            val refs = ArrayList<AccessibilityNodeInfo>()
            val out = ArrayList<AgentRules.Node>()
            collectAgent(root, 0, refs, out)
            synchronized(agentRefs) {
                agentRefs.clear()
                agentRefs.addAll(refs)
            }
            return Pair(pkg, out)
        }

        private fun collectAgent(n: AccessibilityNodeInfo?, depth: Int, refs: ArrayList<AccessibilityNodeInfo>, out: ArrayList<AgentRules.Node>) {
            if (n == null || depth > 30 || out.size >= 120) return
            try {
                if (!n.isVisibleToUser) return
                if (n.isPassword) return
                val edit = n.isEditable
                // Editable controls may expose sensitive content through either text OR contentDescription.
                // Read text only to determine empty/filled state; never include its value in the exported snapshot.
                val hint = if (edit) n.hintText?.toString()?.trim() ?: "" else ""
                val editValue = if (edit) n.text?.toString()?.trim() ?: "" else ""
                val t0 = if (edit) "" else n.text?.toString()?.trim() ?: ""
                val d0 = if (edit) "" else n.contentDescription?.toString()?.trim() ?: ""
                // What the owner typed in a box is never listed: only its hint and whether it is empty or filled.
                val text = if (edit) {
                    val filled = editValue.isNotEmpty() && editValue != hint
                    (if (filled) "[text box, filled]" else "[text box, empty]") + (if (hint.isNotEmpty()) " " + hint.take(40) else "")
                } else t0
                val keep = text.isNotEmpty() || d0.isNotEmpty() || n.isClickable || edit || n.isChecked || n.isSelected
                if (keep) {
                    val r = Rect()
                    n.getBoundsInScreen(r)
                    if (r.width() > 0 && r.height() > 0) {
                        val cls = n.className?.toString()?.substringAfterLast('.') ?: ""
                        out.add(AgentRules.Node(out.size, text.take(80), d0.take(80), cls.take(20), n.isClickable, edit, n.isChecked || n.isSelected))
                        refs.add(n)
                    }
                }
                for (i in 0 until n.childCount) collectAgent(n.getChild(i), depth + 1, refs, out)
            } catch (e: Exception) {
                // node went away while walking: ignore
            }
        }

        private fun agentNode(index: Int): AccessibilityNodeInfo? {
            val n = synchronized(agentRefs) { agentRefs.getOrNull(index) } ?: return null
            return try { if (n.refresh()) n else null } catch (e: Exception) { null }
        }

        /** Tap (or long press) item [index] of the last snapshot. Clicks the node; if Android refuses, taps its centre. */
        fun agentTap(index: Int, long: Boolean): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            val n = agentNode(index) ?: return Ctl(false, "not_found")
            if (n.isPassword) return Ctl(false, "password")
            if (!long && s.clickNode(n)) return Ctl(true, "ok")
            val r = Rect()
            n.getBoundsInScreen(r)
            if (r.width() <= 0 || r.height() <= 0) return Ctl(false, "failed")
            return gestureTap(s, r.exactCenterX(), r.exactCenterY(), if (long) 650L else 70L)
        }

        /** Types into item [index] (its content is replaced), or appends to the focused box when index < 0. */
        fun agentType(index: Int, text: String): Ctl {
            if (index < 0) return typeText(text)
            val n = agentNode(index) ?: return Ctl(false, "not_found")
            if (n.isPassword) return Ctl(false, "password")
            if (!n.isEditable) return Ctl(false, "no_focus")
            return setTextChecked(n, text)
        }

        /**
         * v6.18d: type [text] into the editable box [n] and CHECK that it really arrived. Some apps (Compose screens) answer
         * ACTION_SET_TEXT with "true" or "false" without the text showing up. Order: focus + click, SET_TEXT, read back;
         * if the read-back shows the text is missing, clear the box and paste through the clipboard, read back again.
         * Only called for an editable, non-password box in a screen the safety check already allowed.
         */
        private fun setTextChecked(n: AccessibilityNodeInfo, text: String): Ctl {
            n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            try { Thread.sleep(250) } catch (e: InterruptedException) { ErrLog.add("NovaAccessibilityService", e.toString()) }
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val set = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            try { Thread.sleep(200) } catch (e: InterruptedException) { ErrLog.add("NovaAccessibilityService", e.toString()) }
            val seen = textSeen(n, text)
            if (set && seen != false) return Ctl(true, "ok")   // true = landed, null = this app does not let us read it back
            if (pasteInto(n, text) && textSeen(n, text) != false) return Ctl(true, "ok")
            return Ctl(false, if (set) "not_typed" else "failed")
        }

        /** true = the box now holds the start of [text], false = it does not, null = the box text cannot be read. */
        private fun textSeen(n: AccessibilityNodeInfo, text: String): Boolean? {
            try { n.refresh() } catch (e: Exception) { ErrLog.add("NovaAccessibilityService", e.toString()) }
            val t = n.text?.toString() ?: return null
            val hint = n.hintText?.toString()
            if (hint != null && t == hint) return false
            return t.contains(text.trim().take(12))
        }

        /** Fallback: empty the box, put [text] on the clipboard and paste it. This replaces what was on the clipboard. */
        private fun pasteInto(n: AccessibilityNodeInfo, text: String): Boolean {
            val s = instance ?: return false
            return try {
                val clr = Bundle()
                clr.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clr)
                val cm = s.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("NOVA", text))
                n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val ok = n.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                try { Thread.sleep(250) } catch (e: InterruptedException) { ErrLog.add("NovaAccessibilityService", e.toString()) }
                ok
            } catch (e: Exception) {
                false
            }
        }


        /**
         * v28: a small JPEG of the screen (Android 11+, needs canTakeScreenshot). null when not possible
         * (old Android, secure window, service not re-enabled yet): the agent then continues with text only.
         */
        @android.annotation.TargetApi(30)
        fun agentScreenshot(): ByteArray? {
            val s = instance ?: return null
            if (Build.VERSION.SDK_INT < 30) return null
            val latch = CountDownLatch(1)
            val holder = arrayOfNulls<ByteArray>(1)
            try {
                s.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, s.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                        try {
                            val hb = r.hardwareBuffer
                            val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                            val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hb.close()
                            if (soft != null) {
                                val w = minOf(soft.width, 640)
                                val h = maxOf(1, soft.height * w / soft.width)
                                val small = Bitmap.createScaledBitmap(soft, w, h, true)
                                val bos = ByteArrayOutputStream()
                                small.compress(Bitmap.CompressFormat.JPEG, 60, bos)
                                holder[0] = bos.toByteArray()
                            }
                        } catch (e: Exception) {
                            holder[0] = null
                        }
                        latch.countDown()
                    }

                    override fun onFailure(errorCode: Int) { latch.countDown() }
                })
                latch.await(3000, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                return null
            }
            return holder[0]
        }

        private fun gestureTap(s: NovaAccessibilityService, x: Float, y: Float, ms: Long): Ctl {
            val p = Path()
            p.moveTo(x, y)
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build()
            val latch = CountDownLatch(1)
            var done = false
            val accepted = s.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { done = true; latch.countDown() }
                override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
            }, null)
            if (!accepted) return Ctl(false, "failed")
            try { latch.await(2000, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { ErrLog.add("NovaAccessibilityService", e.toString()) }
            return if (done) Ctl(true, "ok") else Ctl(false, "failed")
        }

        private fun findLabel(
            n: AccessibilityNodeInfo?, depth: Int, label: String,
            exact: ArrayList<AccessibilityNodeInfo>, part: ArrayList<AccessibilityNodeInfo>
        ) {
            if (n == null || depth > 30 || exact.size + part.size > 20) return
            try {
                if (!n.isVisibleToUser) return
                if (!n.isPassword) {
                    var best = 0
                    val t = n.text?.toString()
                    val d = n.contentDescription?.toString()
                    if (!t.isNullOrBlank()) best = maxOf(best, Control.matchScore(t, label))
                    if (!d.isNullOrBlank()) best = maxOf(best, Control.matchScore(d, label))
                    if (best == 2) exact.add(n) else if (best == 1) part.add(n)
                }
                for (i in 0 until n.childCount) findLabel(n.getChild(i), depth + 1, label, exact, part)
            } catch (e: Exception) {
                // node went away while walking: ignore
            }
        }

        // ------------------------------------------------------------------ v6.97 wip14 (Part 4): Play Store install = a SEPARATE narrow path
        // Control.blocked() still refuses every voice "tap" / "type" in com.android.vending (nothing above changed).
        // Only the two functions below may act there, and only as PlayInstallRules allows:
        // armed for this key (60 s), the page title is the asked app, and the pressed node's text is exactly "Install".
        private const val PLAY_PKG = "com.android.vending"

        /** The Play Store window, or null (never NOVA's own window, never another app). */
        private fun playRoot(s: NovaAccessibilityService): AccessibilityNodeInfo? {
            val r = s.rootInActiveWindow
            if (r != null && r.packageName?.toString() == PLAY_PKG) return r
            val alt = otherAppRoot(s)
            return if (alt != null && alt.packageName?.toString() == PLAY_PKG) alt else null
        }

        private fun playLines(root: AccessibilityNodeInfo): List<String> {
            val out = ArrayList<String>()
            collect(root, 0, out, false)
            return out
        }

        private fun topOf(n: AccessibilityNodeInfo): Int {
            val r = Rect()
            n.getBoundsInScreen(r)
            return r.top
        }

        /** The top-most visible, enabled node whose whole text is exactly "Install" ("Install anyway", "Installs", "Uninstall" never match). */
        private fun exactInstallNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val found = ArrayList<AccessibilityNodeInfo>()
            try {
                val list = root.findAccessibilityNodeInfosByText("Install") ?: return null
                for (n in list) {
                    val t = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()
                    if (t.equals("Install", ignoreCase = true) && n.isVisibleToUser && n.isEnabled && !n.isPassword) found.add(n)
                }
            } catch (e: Exception) {
                return null
            }
            return found.minByOrNull { topOf(it) }
        }

        /**
         * Presses Install on the Play Store page of ONE app and reports what it really saw. Blocking (up to about 20 s): call from a worker thread.
         * [key] = the key NovaService armed in PlayInstallRules (the package id, or "q:<name>" for an unknown app). One press at most.
         * Codes: started / installed (ok) | need_login / paid / no_acc / not_armed / no_page / tap_failed / unconfirmed / interrupted (not ok).
         */
        fun playInstall(key: String, label: String, cancel: () -> Boolean = { false }): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            if (!PlayInstallRules.armedFor(key, System.currentTimeMillis())) return Ctl(false, "not_armed")
            var tapped = false
            val t0 = SystemClock.elapsedRealtime()
            loop@ while (SystemClock.elapsedRealtime() - t0 < 14_000L) {
                if (cancel()) return Ctl(false, "interrupted")
                val root = playRoot(s)
                if (root != null) {
                    val lines = playLines(root)
                    if (!PlayInstallRules.pageMatches(lines, label)) {
                        // Not the asked app's page (yet). Only a sign-in screen is worth reporting.
                        if (PlayInstallRules.decide(lines, false) == PlayInstallRules.Decision.NEED_LOGIN) return Ctl(false, "need_login")
                    } else {
                        when (PlayInstallRules.decide(lines, false)) {
                            PlayInstallRules.Decision.INSTALLED -> return Ctl(true, "installed")
                            PlayInstallRules.Decision.NEED_LOGIN -> return Ctl(false, "need_login")
                            PlayInstallRules.Decision.PAID -> return Ctl(false, "paid")
                            PlayInstallRules.Decision.TAP -> {
                                if (!PlayInstallRules.mayTap(key, label, lines, System.currentTimeMillis())) return Ctl(false, "not_armed")
                                val node = exactInstallNode(root)
                                if (node != null) {
                                    PlayInstallRules.disarm(key)          // one press only, whatever happens next
                                    if (!s.clickNode(node)) return Ctl(false, "tap_failed")
                                    tapped = true
                                    break@loop
                                }
                            }
                            else -> {}
                        }
                    }
                }
                try { Thread.sleep(500) } catch (e: InterruptedException) { return Ctl(false, "interrupted") }
            }
            if (!tapped) return Ctl(false, "no_page")
            val v0 = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - v0 < 6_000L) {
                if (cancel()) return Ctl(false, "interrupted")
                try { Thread.sleep(500) } catch (e: InterruptedException) { return Ctl(false, "interrupted") }
                val root = playRoot(s) ?: continue
                val lines = playLines(root)
                if (!PlayInstallRules.pageMatches(lines, label)) continue
                when (PlayInstallRules.decide(lines, true)) {
                    PlayInstallRules.Decision.STARTED -> return Ctl(true, "started")
                    PlayInstallRules.Decision.NEED_LOGIN -> return Ctl(false, "need_login")
                    else -> {}
                }
            }
            return Ctl(false, "unconfirmed")
        }

        private fun collectPlayHits(n: AccessibilityNodeInfo?, depth: Int, spoken: String, out: ArrayList<AccessibilityNodeInfo>) {
            if (n == null || depth > 30 || out.size >= 10) return
            try {
                if (!n.isVisibleToUser) return
                val t = n.text?.toString()
                if (!t.isNullOrBlank() && !n.isEditable && !n.isPassword && PlayInstallRules.searchResultMatches(t, spoken)) out.add(n)
                for (i in 0 until n.childCount) collectPlayHits(n.getChild(i), depth + 1, spoken, out)
            } catch (e: Exception) {
                // node went away while walking: ignore
            }
        }

        /**
         * Unknown app name: in the Play SEARCH results tap the first result whose title is exactly the spoken name (PlayInstallRules.searchResultMatches).
         * The search box can show the same words, so when two nodes match the second one (the first is the box) is used. Wrong tap is harmless:
         * playInstall then finds no matching page title and presses nothing. Blocking (up to about 10 s): worker thread only.
         */
        fun playOpenResult(spoken: String, cancel: () -> Boolean = { false }): Ctl {
            val s = instance ?: return Ctl(false, "no_acc")
            val t0 = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - t0 < 10_000L) {
                if (cancel()) return Ctl(false, "interrupted")
                val root = playRoot(s)
                if (root != null) {
                    val hits = ArrayList<AccessibilityNodeInfo>()
                    collectPlayHits(root, 0, spoken, hits)
                    if (hits.isNotEmpty()) {
                        val sorted = hits.sortedBy { topOf(it) }
                        val pick = if (sorted.size >= 2) sorted[1] else sorted[0]
                        return if (s.clickNode(pick)) Ctl(true, "ok") else Ctl(false, "tap_failed")
                    }
                }
                try { Thread.sleep(500) } catch (e: InterruptedException) { return Ctl(false, "interrupted") }
            }
            return Ctl(false, "not_found")
        }

        /** Cancels pending scans at once when monitoring is switched off. */
        fun monitorChanged() { instance?.onMonitorChanged() }

        fun isEnabled(ctx: Context): Boolean {
            if (instance != null) return true
            val s = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return s.split(':').any {
                val c = ComponentName.unflattenFromString(it)
                c != null && c.packageName == ctx.packageName && c.className.endsWith("NovaAccessibilityService")
            }
        }

        private fun collect(n: AccessibilityNodeInfo?, depth: Int, out: ArrayList<String>, includeEditable: Boolean) {
            if (n == null || depth > 30 || out.size >= MAX_LINES) return
            try {
                if (!n.isVisibleToUser) return
                if (!n.isPassword && (includeEditable || !n.isEditable)) {
                    val t = n.text?.toString()?.trim()
                    val d = n.contentDescription?.toString()?.trim()
                    val s = if (!t.isNullOrBlank()) t else d
                    if (!s.isNullOrBlank()) out.add(s.take(200))
                }
                for (i in 0 until n.childCount) collect(n.getChild(i), depth + 1, out, includeEditable)
            } catch (e: Exception) {
                // node went away while walking: ignore
            }
        }
    }

    private class Job(val message: String, val cb: (Boolean) -> Unit, val deadline: Long)

    private val h = Handler(Looper.getMainLooper())
    private val cfg by lazy { Cfg(this) }
    private var job: Job? = null
    private var scanQueued = false
    private var lastScanAt = -MIN_SCAN_GAP_MS
    private val alertGate = ScreenAlert.Gate()

    private val tick = object : Runnable {
        override fun run() {
            try {
                val j = job ?: return
                if (SystemClock.elapsedRealtime() > j.deadline) { done(false); return }
                if (!attempt()) h.postDelayed(this, 500)
            } catch (e: Exception) {
                // v6.24: an exception on the main thread would kill the whole app and Android would then call the service "malfunctioning"
                ErrLog.add("accessibility", "tick " + e.javaClass.simpleName)
            }
        }
    }

    private val scan = Runnable { try { scanBody() } catch (e: Exception) { ErrLog.add("accessibility", "scan " + e.javaClass.simpleName) } }

    private fun scanBody() {
        scanQueued = false
        if (!cfg.screenMonitor) return
        lastScanAt = SystemClock.elapsedRealtime()
        val snap = readScreen(false) ?: return
        if (snap.pkg == packageName || snap.pkg in PKGS && job != null) return
        // v33 Part 2: only a real dialog counts (ScreenAlert), never while the screen is off, then a strict rate limit.
        val hit = ScreenAlert.detect(snap.pkg, snap.lines, packageName) ?: return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null && !pm.isInteractive) return
        val now = SystemClock.elapsedRealtime()
        if (!alertGate.allow(now, hit.key)) return
        // NovaService only asks if it is idle (not busy, not listening, not waiting for a yes, not driving, no screen session open).
        if (NovaService.instance?.screenAlertAsk(snap, hit) == true) alertGate.record(now, hit.key)
    }

    /**
     * v6.97-wip9 LOCK GUARD: while a lock alert plays, ONE press of a volume key stops it (and is consumed, so the volume does not change).
     * Without an active alert the key is passed on untouched. Needs flagRequestFilterKeyEvents (accessibility_config.xml).
     */
    override fun onKeyEvent(event: android.view.KeyEvent?): Boolean {
        try {
            if (event != null && LockAlert.active && LockGuard.isStopKey(event.keyCode)) {
                if (event.action == android.view.KeyEvent.ACTION_DOWN) LockAlert.stop()
                return true
            }
        } catch (e: Exception) { /* never block the hardware keys because of an error here */ }
        return false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateMask()
        CrashNote.event(applicationContext, "connected")
    }

    override fun onDestroy() {
        CrashNote.event(applicationContext, "destroyed")
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        CrashNote.event(applicationContext, "unbound")
        instance = null
        h.removeCallbacksAndMessages(null)
        scanQueued = false
        val j = job
        job = null
        j?.cb?.invoke(false)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        try { handleEvent(e) } catch (x: Exception) { ErrLog.add("accessibility", "event " + x.javaClass.simpleName) }
    }

    private fun handleEvent(e: AccessibilityEvent?) {
        if (job != null) attempt()
        if (InstallTap.armed(System.currentTimeMillis())) { installTries = 0; tryInstallTap() }
        if (!cfg.screenMonitor) return
        if (e?.packageName?.toString() == packageName) return
        if (scanQueued) return
        scanQueued = true
        val wait = maxOf(DEBOUNCE_MS, lastScanAt + MIN_SCAN_GAP_MS - SystemClock.elapsedRealtime())
        h.postDelayed(scan, wait)
    }

    private fun onMonitorChanged() {
        h.post {
            if (!cfg.screenMonitor) {
                h.removeCallbacks(scan)
                scanQueued = false
            }
            updateMask()
        }
    }

    /** Content-change events are only requested while they are actually needed. */
    private fun updateMask() {
        try {
            val info = serviceInfo ?: return
            val wantContent = cfg.screenMonitor || job != null
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                (if (wantContent) AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED else 0)
            serviceInfo = info
        } catch (e: Exception) {
            // service is shutting down
        }
    }

    private fun arm(message: String, cb: (Boolean) -> Unit) {
        h.post {
            val old = job
            job = Job(message, cb, SystemClock.elapsedRealtime() + TIMEOUT_MS)
            old?.cb?.invoke(false) // a newer message replaces (and cancels) the older one
            updateMask()
            h.removeCallbacks(tick)
            h.postDelayed(tick, 700)
        }
    }

    private fun done(ok: Boolean) {
        val j = job
        job = null
        h.removeCallbacks(tick)
        updateMask()
        j?.cb?.invoke(ok)
    }

    /** True when the job is finished (sent). False = keep waiting. */
    private fun attempt(): Boolean {
        val j = job ?: return true
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: return false
        if (pkg !in PKGS) return false
        // Only press send when the text box holds exactly the message the user confirmed.
        val entry = root.findAccessibilityNodeInfosByViewId("$pkg:id/entry")?.firstOrNull()
            ?: findEditable(root, 0) ?: return false
        val typed = entry.text?.toString() ?: return false
        if (!Logic.sameMessage(typed, j.message)) return false
        val send = findSend(root, pkg) ?: return false
        if (clickNode(send)) { done(true); return true }
        return false
    }

    private fun findEditable(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (n == null || depth > 14) return null
        if (n.isEditable && n.className?.toString()?.endsWith("EditText") == true) return n
        for (i in 0 until n.childCount) {
            val r = findEditable(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    private fun findSend(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByViewId("$pkg:id/send")?.firstOrNull()?.let { return it }
        return search(root, 0)
    }

    private fun search(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (n == null || depth > 12) return null
        val d = n.contentDescription?.toString()?.trim()?.lowercase()
        if (d != null && d in SEND_WORDS && n.isClickable) return n
        for (i in 0 until n.childCount) {
            val r = search(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    private var installTries = 0

    /** Presses Install/Update on the system installer, only inside the 90 s window opened by a verified NOVA update. */
    private fun tryInstallTap() {
        try {
            if (!InstallTap.armed(System.currentTimeMillis())) return
            val root = rootInActiveWindow
            val pkg = root?.packageName?.toString()
            if (root != null && InstallTap.isInstaller(pkg)) {
                var node: AccessibilityNodeInfo? = null
                for (w in listOf("Install", "Update", "इंस्टॉल", "अपडेट")) {
                    node = root.findAccessibilityNodeInfosByText(w)?.firstOrNull { InstallTap.isTapLabel(it.text?.toString()) }
                    if (node != null) break
                }
                if (node != null && clickNode(node)) { InstallTap.disarm(); return }
            }
            if (++installTries <= InstallTap.MAX_TRIES) h.postDelayed({ tryInstallTap() }, 600)
        } catch (x: Exception) {
            ErrLog.add("accessibility", "installtap " + x.javaClass.simpleName)
        }
    }

    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        var depth = 0
        while (cur != null && depth < 4) {
            if (cur.isClickable && cur.isEnabled) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            cur = cur.parent
            depth++
        }
        return false
    }
}
