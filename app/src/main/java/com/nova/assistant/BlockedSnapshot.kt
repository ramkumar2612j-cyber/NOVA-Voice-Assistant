package com.nova.assistant

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * v7.147 STEP 7: reads (never changes) the phone's real permission / special-access state for [BlockedRules]. Every read is wrapped:
 * when Android refuses to answer, that switch counts as "on" (we do not claim a problem we did not see).
 */
object BlockedSnapshot {
    private fun <T> safe(default: T, f: () -> T): T = try { f() } catch (e: Throwable) { default }

    private fun granted(ctx: Context, p: String): Boolean = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun read(ctx: Context): BlockedRules.Snap = BlockedRules.Snap(
        mic = safe(true) { granted(ctx, Manifest.permission.RECORD_AUDIO) },
        notif = safe(true) { Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS) },
        overlay = safe(true) { Settings.canDrawOverlays(ctx) },
        acc = safe(true) { NovaAccessibilityService.isEnabled(ctx) },
        notifAccess = safe(true) { DrivingBridge.listenerEnabled(ctx) },
        battery = safe(true) { (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName) },
        write = safe(true) { Settings.System.canWrite(ctx) },
        dnd = safe(true) { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted },
        alarm = safe(true) { Build.VERSION.SDK_INT < 31 || (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() },
        install = safe(true) { ctx.packageManager.canRequestPackageInstalls() },
        contacts = safe(true) { granted(ctx, Manifest.permission.READ_CONTACTS) },
        calendar = safe(true) { granted(ctx, Manifest.permission.READ_CALENDAR) },
        brandHasAutostart = safe(false) { PowerGuideRules.targets(Build.MANUFACTURER ?: "").isNotEmpty() }
    )
}
