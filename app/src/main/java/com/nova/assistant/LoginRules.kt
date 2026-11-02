package com.nova.assistant

/**
 * v7.108 PART 5: saved-login jobs (Gmail app, Gmail / any site in Chrome). Pure Kotlin (no Android classes), unit-testable.
 * NOVA works only with accounts ALREADY signed in on the phone (the Gmail app, Chrome's saved sign-in, the account chooser "Continue as ...").
 * It NEVER types a password, OTP, card number or UPI PIN, never stores one, and never solves a CAPTCHA.
 * When the screen is a real login wall (password / code / phone approval / CAPTCHA), NOVA tries safe routes for a few steps and then stops
 * as NEEDS_OWNER with the exact thing the owner must do. It never fakes success. Screen text is only inspected, never stored.
 */
object LoginRules {
    /** Consecutive steps on the same blocking wall before the job stops as NEEDS_OWNER. */
    const val WALL_STEPS = 3
    const val MAX_LINE_CHARS = 320

    enum class State { NONE, CHOOSER, PASSWORD, CODE, CAPTCHA }

    private val CAPTCHA_WORDS = listOf(
        "captcha", "i m not a robot", "im not a robot", "i am not a robot", "verify you are human", "verify you re human",
        "select all images", "select all squares", "prove you are human"
    )
    /** Login-only prompts: enough on their own (a mailbox line such as "Your verification code" is NOT in this list). */
    private val CODE_STRONG = listOf(
        "2 step verification", "two step verification", "enter the code", "enter code", "tap yes on your phone",
        "approve this sign in", "confirm it s you", "verify it s you"
    )
    /** Words that also appear in normal mail text: they count only together with a code / password text box on screen. */
    private val CODE_WEAK = listOf("verification code", "6 digit code", "six digit code", "one time password", "one time code", "otp", "check your phone")
    private val PASSWORD_WORDS = listOf("enter your password", "type your password", "enter password", "password required", "wrong password")
    private val CHOOSER_WORDS = listOf("choose an account", "select an account", "pick an account", "continue as", "sign in with google", "use another account")

    private val DANGER = listOf(
        "remove account", "remove google account", "delete account", "delete your google account", "delete google account",
        "recovery", "forgot email", "2 step", "two step", "security key", "security keys", "passkey", "passkeys", "backup code", "backup codes", "sign out of all"
    )

    private val HINT_WORDS = setOf("gmail", "mail", "email", "inbox", "login", "signin", "sign", "account", "chrome", "drive", "youtube", "google")

    private fun flat(s: String): String = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * What the screen is. `lines` = visible text lines, `secretEditable` = a text box labelled password / code / card / PIN is on screen.
     * Order: CAPTCHA, CODE, PASSWORD, CHOOSER. A merely long page that mentions a word does not count: each line must be short (<= 140 chars).
     */
    fun detect(lines: List<String>, secretEditable: Boolean): State {
        val f = lines.map { it.trim() }.filter { it.isNotEmpty() && it.length <= 140 }.map { flat(it) }
        fun hit(words: List<String>): Boolean = f.any { l -> words.any { w -> hasWords(l, w) } }
        if (hit(CAPTCHA_WORDS)) return State.CAPTCHA
        if (hit(CODE_STRONG) || (secretEditable && hit(CODE_WEAK))) return State.CODE
        if (secretEditable || hit(PASSWORD_WORDS)) return State.PASSWORD
        if (hit(CHOOSER_WORDS)) return State.CHOOSER
        return State.NONE
    }

    /** Whole-word(s) match so "otp" does not hit "hotpot". */
    private fun hasWords(line: String, words: String): Boolean = " $line ".contains(" $words ")

    /** Only password / code / CAPTCHA walls stop a job. The account chooser is a normal step. */
    fun blocks(s: State): Boolean = s == State.PASSWORD || s == State.CODE || s == State.CAPTCHA

    /** Taps that would change account security or remove a saved account: never allowed. */
    fun isAccountDanger(label: String): Boolean {
        val l = flat(label)
        if (l.isEmpty()) return false
        return DANGER.any { hasWords(l, it) }
    }

    /** True when the job text suggests signing in / mail / Google, so the login rules are added to the job. */
    fun wantsHint(task: String): Boolean {
        val t = flat(task.take(400)).split(" ").toSet()
        return t.any { it in HINT_WORDS }
    }

    fun hint(): String =
        ("LOGIN RULES: use only accounts already signed in on this phone (Gmail app, Chrome saved sign-in). If an account chooser appears, " +
            "tap the owner's account or 'Continue as ...'. NEVER type a password, OTP, card number or UPI PIN, never tap remove/recovery/2-step/security items. " +
            "If a password, code, phone approval or CAPTCHA is required, finish every other step you can, then stop and tell the owner the exact thing needed. Never claim it worked.").take(520)

    fun wallLine(s: State): String = when (s) {
        State.PASSWORD -> "LOGIN WALL: a password is required. Do NOT type one. Try the account chooser / another already signed-in route; otherwise stop with ask or fail and say a password is needed."
        State.CODE -> "LOGIN WALL: a verification code or phone approval is required. Do NOT guess it. Try another already signed-in route; otherwise stop and say the code is needed."
        State.CAPTCHA -> "LOGIN WALL: a CAPTCHA is shown. Do NOT try to solve it. Stop and tell the owner to solve it."
        else -> ""
    }.take(MAX_LINE_CHARS)

    fun chooserLine(): String = "LOGIN: an account chooser is visible. Tap the owner's account or 'Continue as ...'; never add or remove accounts."

    fun wallNote(s: State, en: Boolean): String = when (s) {
        State.PASSWORD -> if (en) "A password is needed here; I never type passwords" else "Yahan password chahiye; main password kabhi type nahi karta"
        State.CODE -> if (en) "A verification code / phone approval is needed here" else "Yahan verification code ya phone par approve chahiye"
        State.CAPTCHA -> if (en) "A CAPTCHA is shown; I cannot solve it" else "Yahan CAPTCHA hai; main use solve nahi kar sakta"
        else -> ""
    }

    /** Exact thing the owner must do (final message of the NEEDS_OWNER stop). */
    fun ownerMessage(s: State, en: Boolean): String = when (s) {
        State.PASSWORD -> if (en) "Sign-in needs your password. I never type passwords. Please sign in yourself, then ask me again."
        else "Sign-in ke liye aapka password chahiye. Main password type nahi karta. Aap khud sign in kar lo, phir mujhe dobara bolo."
        State.CODE -> if (en) "Sign-in needs a verification code or an approval on your phone. Please do that yourself, then ask me again."
        else "Sign-in ke liye verification code ya phone par approve chahiye. Aap khud kar do, phir mujhe dobara bolo."
        State.CAPTCHA -> if (en) "A CAPTCHA is shown. Please solve it yourself, then ask me again."
        else "CAPTCHA aaya hai. Aap khud solve kar do, phir mujhe dobara bolo."
        else -> ""
    }
}
