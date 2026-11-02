package com.nova.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Exact Android identities for the two distinct WhatsApp applications. */
object WhatsAppTarget {
    const val MESSENGER = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"

    enum class App(val packageName: String, val label: String) {
        MESSENGER(WhatsAppTarget.MESSENGER, "WhatsApp"),
        BUSINESS(WhatsAppTarget.BUSINESS, "WhatsApp Business")
    }

    fun requested(appId: String?): App? = when (appId?.lowercase()) {
        "whatsapp", "wa" -> App.MESSENGER
        "whatsapp_business", "wa_business" -> App.BUSINESS
        else -> null
    }

    fun isInstalled(pm: PackageManager, app: App): Boolean =
        try { pm.getApplicationInfo(app.packageName, 0); true }
        catch (_: PackageManager.NameNotFoundException) { false }
        catch (_: SecurityException) { false }

    fun launchIntent(pm: PackageManager, app: App): Intent? {
        if (!isInstalled(pm, app)) return null
        return pm.getLaunchIntentForPackage(app.packageName)
    }

    fun missingMessage(app: App) = "${app.label} is not installed. I won't open the other WhatsApp app instead."
}
