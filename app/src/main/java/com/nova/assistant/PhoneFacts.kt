package com.nova.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * v6.99 Part 4: what NOVA knows about THIS phone and about itself, as a short fact list for the online AI chain.
 * Facts: phone model, Android version, which permissions / accesses are allowed right now, and the exact tap path to allow each one.
 * Only yes/no status goes out; no key, no contact, no message text. describe() is pure (testable); read() asks Android.
 * Evidence level: written. Not compiled, not phone tested.
 */
object PhoneFacts {
    /** key -> (readable name, tap path to allow it) */
    val ACCESS: List<Triple<String, String, String>> = listOf(
        Triple("mic", "Microphone", "Phone Settings > Apps > NOVA > Permissions > Microphone > Allow"),
        Triple("contacts", "Contacts", "Phone Settings > Apps > NOVA > Permissions > Contacts > Allow"),
        Triple("call", "Phone calls", "Phone Settings > Apps > NOVA > Permissions > Phone > Allow"),
        Triple("answer", "Answer calls", "Phone Settings > Apps > NOVA > Permissions > Phone > Allow"),
        Triple("calendar", "Calendar", "Phone Settings > Apps > NOVA > Permissions > Calendar > Allow"),
        Triple("camera", "Camera", "Phone Settings > Apps > NOVA > Permissions > Camera > Allow"),
        Triple("notif", "Notifications", "Phone Settings > Apps > NOVA > Notifications > Allow"),
        Triple("accessibility", "Accessibility (tap and type in other apps)", "Phone Settings > Accessibility > Installed apps > NOVA > ON"),
        Triple("listener", "Notification access (read chat messages)", "Phone Settings > Notifications > Notification access > NOVA > ON"),
        Triple("overlay", "Display over other apps", "Phone Settings > Apps > NOVA > Display over other apps > Allow"),
        Triple("writesettings", "Modify system settings (rotation)", "Phone Settings > Apps > NOVA > Modify system settings > Allow")
    )

    private const val RULES =
        "HOW TO ACT: when the user orders a task, do it first and tell the result after; do not ask for permission in words for ordinary tasks. " +
            "Android itself shows each permission box only once, so if something is NOT allowed yet, say in one short line which one and the tap path from the list, then do the rest. " +
            "Never say a task is done unless NOVA really did it. "

    /** Pure: builds the fact text from a status map (missing key = not allowed). */
    fun describe(model: String, sdk: Int, allowed: Map<String, Boolean>): String {
        val ok = ACCESS.filter { allowed[it.first] == true }.map { it.second }
        val no = ACCESS.filter { allowed[it.first] != true }
        val sb = StringBuilder()
        sb.append("ABOUT THIS PHONE: ").append(model.ifBlank { "Android phone" }).append(", Android API ").append(sdk).append(". ")
        sb.append("Allowed for NOVA now: ").append(if (ok.isEmpty()) "nothing yet" else ok.joinToString(", ")).append(". ")
        if (no.isNotEmpty()) {
            sb.append("NOT allowed yet: ").append(no.joinToString("; ") { it.second + " -> " + it.third }).append(". ")
        }
        sb.append(RULES)
        return sb.toString()
    }

    private fun granted(ctx: Context, p: String): Boolean = try {
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun read(ctx: Context): Map<String, Boolean> {
        val m = HashMap<String, Boolean>()
        m["mic"] = granted(ctx, Manifest.permission.RECORD_AUDIO)
        m["contacts"] = granted(ctx, Manifest.permission.READ_CONTACTS)
        m["call"] = granted(ctx, Manifest.permission.CALL_PHONE)
        m["answer"] = Build.VERSION.SDK_INT < 28 || granted(ctx, Manifest.permission.ANSWER_PHONE_CALLS)
        m["calendar"] = granted(ctx, Manifest.permission.READ_CALENDAR)
        m["camera"] = granted(ctx, Manifest.permission.CAMERA)
        m["notif"] = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
        m["accessibility"] = try { NovaAccessibilityService.isEnabled(ctx) } catch (e: Throwable) { false }
        m["listener"] = try {
            (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "").contains(ctx.packageName)
        } catch (e: Throwable) { false }
        m["overlay"] = try { Settings.canDrawOverlays(ctx) } catch (e: Throwable) { false }
        m["writesettings"] = try { Settings.System.canWrite(ctx) } catch (e: Throwable) { false }
        return m
    }

    fun aiFacts(ctx: Context): String = try {
        describe(Build.MODEL ?: "", Build.VERSION.SDK_INT, read(ctx))
    } catch (e: Throwable) { RULES }
}
