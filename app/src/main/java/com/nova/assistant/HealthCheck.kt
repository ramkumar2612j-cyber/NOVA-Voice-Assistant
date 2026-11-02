package com.nova.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Self-check data for the automatic AutoTest run. It only reads permission/service state and
 * builds a report; it never opens dialogs automatically when the app resumes.
 */
object HealthCheck {
    class Item(val name: String, val ok: Boolean, val fix: String, val important: Boolean = true)

    fun run(ctx: Context): List<Item> {
        val out = ArrayList<Item>()
        val mic = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        out.add(Item("Mic permission", mic, "Settings > Apps > NOVA > Permissions > Microphone: Allow"))
        val notif = Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        out.add(Item("Notification permission", notif, "Settings > Apps > NOVA > Notifications: Allow"))
        val accOn = NovaAccessibilityService.isEnabled(ctx)
        val accLive = NovaAccessibilityService.instance != null
        val accFix = if (accOn && !accLive)
            "Switch is ON but Android did not connect it (malfunctioning). Force stop NOVA, then Accessibility > NOVA Phone Control OFF and ON. Also set Autostart ON and Battery: No restrictions"
        else
            "Settings > Accessibility > NOVA Phone Control: ON"
        out.add(Item("Phone Control (Accessibility)", accLive, accFix))
        out.add(Item("Display over other apps", Settings.canDrawOverlays(ctx),
            "Settings > Apps > NOVA > Display over other apps: Allow"))
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val bat = pm != null && pm.isIgnoringBatteryOptimizations(ctx.packageName)
        out.add(Item("Battery unrestricted", bat,
            "Settings > Apps > NOVA > Battery: No restrictions (and Autostart ON)"))
        out.add(Item("Gemini key (phone ke kaam)", SecureStore.hasKeys(ctx), "NOVA Settings me Gemini key daalo", false))
        out.add(Item("Groq key (awaaz aur backup AI)", SecureStore.hasGroq(ctx), "NOVA Settings me free Groq key daalo", false))
        out.add(Item("OpenRouter key (backup AI)", SecureStore.hasSlot(ctx, "openrouter"), "NOVA Settings me free OpenRouter key daalo", false))
        out.add(Item("GLM Z.ai key (backup AI)", SecureStore.hasSlot(ctx, "zai"), "NOVA Settings me free Z.ai key daalo", false))
        out.add(Item("NOVA listening", NovaService.running, "Press ACTIVATE in NOVA", false))
        return out
    }

    fun problems(items: List<Item>): List<Item> = items.filter { !it.ok && it.important }

    fun report(ctx: Context, items: List<Item>): String {
        val vName = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (e: Exception) { "?" }
        val sb = StringBuilder()
        sb.append("NOVA HEALTH REPORT\n")
        sb.append("App ").append(vName).append(" | ").append(Build.MANUFACTURER).append(' ')
            .append(Build.MODEL).append(" | Android ").append(Build.VERSION.SDK_INT).append('\n')
        for (i in items) {
            sb.append(if (i.ok) "OK    " else "FAIL  ").append(i.name)
            if (!i.ok) sb.append("  -> ").append(i.fix)
            sb.append('\n')
        }
        sb.append(ErrLog.dump(System.currentTimeMillis())).append('\n')
        sb.append(AutoTrace.summary()).append('\n')   // v7.34 PART 5: what the auto routing did
        sb.append(FeatureVerify.healthLines()).append('\n')   // v7.35: result of the automatic feature + wiring check
        return sb.toString()
    }

    // v6.21: health problems are reported only inside the automatic AutoTest report.
    // Do not show dialogs on Activity resume; they reappeared on every cold launch.

}
