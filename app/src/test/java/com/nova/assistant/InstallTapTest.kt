package com.nova.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallTapTest {
    @Test fun offUntilArmedAndExpires() {
        InstallTap.disarm()
        assertFalse(InstallTap.armed(1000))
        InstallTap.arm(1000)
        assertTrue(InstallTap.armed(1000 + InstallTap.WINDOW_MS - 1))
        assertFalse(InstallTap.armed(1000 + InstallTap.WINDOW_MS))
        InstallTap.disarm()
        assertFalse(InstallTap.armed(2000))
    }

    @Test fun onlyRealInstallersAndExactLabels() {
        assertTrue(InstallTap.isInstaller("com.google.android.packageinstaller"))
        assertFalse(InstallTap.isInstaller("com.whatsapp"))
        assertFalse(InstallTap.isInstaller(null))
        assertTrue(InstallTap.isTapLabel("Install"))
        assertTrue(InstallTap.isTapLabel(" UPDATE "))
        assertTrue(InstallTap.isTapLabel("इंस्टॉल"))
        assertFalse(InstallTap.isTapLabel("Install anyway"))   // Play Protect warning: owner must decide
        assertFalse(InstallTap.isTapLabel("Open"))
        assertFalse(InstallTap.isTapLabel("Cancel"))
        assertFalse(InstallTap.isTapLabel(null))
    }
}
