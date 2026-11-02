package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class LoginRulesTest {
    private fun d(vararg l: String, secret: Boolean = false) = LoginRules.detect(l.toList(), secret)

    @Test fun passwordBoxOrPromptIsAPasswordWall() {
        assertEquals(LoginRules.State.PASSWORD, d("Welcome", "Enter your password"))
        assertEquals(LoginRules.State.PASSWORD, d("Sign in", secret = true))
        assertTrue(LoginRules.blocks(LoginRules.State.PASSWORD))
    }

    @Test fun codeAndPhoneApprovalAreCodeWalls() {
        assertEquals(LoginRules.State.CODE, d("2-Step Verification", "Tap Yes on your phone"))
        assertEquals(LoginRules.State.CODE, d("Enter the code"))
        assertEquals(LoginRules.State.CODE, d("Verification code", secret = true))
        assertTrue(LoginRules.blocks(LoginRules.State.CODE))
    }

    @Test fun mailboxLinesAboutCodesAreNotAWall() {
        assertEquals(LoginRules.State.NONE, d("Your verification code is 482910", "Inbox", "Compose"))
        assertEquals(LoginRules.State.NONE, d("Amazon: your OTP for delivery", "Primary"))
    }

    @Test fun captchaIsAWallAndAccountChooserIsNot() {
        assertEquals(LoginRules.State.CAPTCHA, d("I'm not a robot"))
        assertEquals(LoginRules.State.CAPTCHA, d("Select all images with traffic lights"))
        assertEquals(LoginRules.State.CHOOSER, d("Choose an account", "a@b.com"))
        assertEquals(LoginRules.State.CHOOSER, d("Continue as Sam"))
        assertFalse(LoginRules.blocks(LoginRules.State.CHOOSER))
        assertFalse(LoginRules.blocks(LoginRules.State.NONE))
    }

    @Test fun wholeWordsOnlyAndLongLinesIgnored() {
        assertEquals(LoginRules.State.NONE, d("hotpot recipes", "otp", "Home"))   // "otp" alone needs a code box
        assertEquals(LoginRules.State.NONE, d("x".repeat(60) + " enter the code " + "y".repeat(100)))
        assertEquals(LoginRules.State.NONE, d())
    }

    @Test fun accountSecurityTapsAreDangerous() {
        listOf("Remove account", "Delete your Google Account", "Recovery email", "2-Step Verification", "Add passkey", "Backup codes", "Sign out of all devices")
            .forEach { assertTrue(it, LoginRules.isAccountDanger(it)) }
        listOf("Continue as Sam", "Inbox", "Compose", "Next", "Sign in", "Search mail", "").forEach { assertFalse(it, LoginRules.isAccountDanger(it)) }
    }

    @Test fun hintOnlyForLoginOrMailJobs() {
        assertTrue(LoginRules.wantsHint("gmail me naya mail dekho"))
        assertTrue(LoginRules.wantsHint("chrome mein login karo"))
        assertFalse(LoginRules.wantsHint("calculator kholo aur 5 jodo"))
        val h = LoginRules.hint()
        assertTrue(h.contains("NEVER type a password"))
        assertTrue(h.length <= 520)
    }

    @Test fun ownerMessagesNameTheExactThing() {
        assertTrue(LoginRules.ownerMessage(LoginRules.State.PASSWORD, true).contains("password"))
        assertTrue(LoginRules.ownerMessage(LoginRules.State.CODE, true).contains("code"))
        assertTrue(LoginRules.ownerMessage(LoginRules.State.CAPTCHA, true).contains("CAPTCHA"))
        assertTrue(LoginRules.ownerMessage(LoginRules.State.PASSWORD, false).contains("password"))
        assertEquals(3, LoginRules.WALL_STEPS)
    }
}
