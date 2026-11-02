package com.nova.assistant

/**
 * v7.144 STEP 4 OF 10: vision tap for weak-Accessibility screens (games, Flutter, custom canvas) where the screen exposes almost no tappable items.
 * The agent already gets a small picture of the screen; on such a screen it may answer {"act":"tap_xy","x":..,"y":..,"text":"what the target says"}
 * with x / y in per-mille of the picture (0..1000). Pure Kotlin (no Android), unit-testable. This file only DECIDES and converts; it taps nothing.
 *
 * Fixed limits (owner rules): never in a game (NOVA only looks at a game and describes it, it does not play), never when the screen has normal
 * tappable items (use the item numbers), never without a picture in THIS step, never at the screen edge (status / navigation bar), at most
 * [MAX_TAPS] per job, and the named target goes through the same money / account / security word check as every other tap (AgentRules.check).
 * A job that used a vision tap is never saved as a learned route.
 */
object VisionTapRules {
    const val MAX_TAPS = 5
    const val EDGE_PM = 10

    /** A screen is "weak" when Accessibility shows at most two tappable items. */
    fun weakScreen(clickableCount: Int): Boolean = clickableCount <= 2

    /** Per-mille (0..1000) of the picture -> pixel of the real screen, always inside the screen. */
    fun toPixel(pm: Int, size: Int): Int {
        if (size < 4) return 0
        return (pm.coerceIn(0, 1000).toLong() * size / 1000).toInt().coerceIn(1, size - 2)
    }

    /** True when the point is not too close to an edge (status bar / gesture bar). */
    fun insideSafeArea(xPm: Int, yPm: Int): Boolean =
        xPm in EDGE_PM..(1000 - EDGE_PM) && yPm in EDGE_PM..(1000 - EDGE_PM)

    /**
     * Why a vision tap must NOT run now, or null when it may (the word check on the target is separate). [isGame]: true = game, false = not a game,
     * null = could not find out (treated like a game: owner rule is that NOVA does not play games).
     */
    fun block(offeredThisStep: Boolean, weak: Boolean, isGame: Boolean?, tapsSoFar: Int, en: Boolean = false): String? = when {
        !offeredThisStep -> if (en) "no picture in this step" else "is step me screen ki picture nahi hai"
        !weak -> if (en) "this screen has normal items, use their numbers" else "is screen par normal items hain, unke number se tap karo"
        isGame != false -> if (en) "game: I only look and describe, I do not play" else "game: main sirf dekh kar bata sakta hoon, khelta nahi"
        tapsSoFar >= MAX_TAPS -> if (en) "too many vision taps in one job" else "ek kaam me bahut zyada picture-taps ho gaye"
        else -> null
    }

    /** Extra lines appended to the prompt of a step that has a picture AND a weak screen. */
    fun hint(): String =
        "\nVISION TAP (this screen exposes almost no tappable items, e.g. a game, Flutter or canvas app): use the PICTURE. " +
        "To tap something you can clearly see, answer {\"act\":\"tap_xy\",\"x\":N,\"y\":N,\"text\":\"what the target says or shows\"} " +
        "where x and y are whole numbers 0 to 1000 across the picture width and height (500,500 is the centre). " +
        "Name the target in \"text\" truthfully. If you cannot see the target clearly, do NOT guess: answer ask or fail. " +
        "Never tap anything about money, payment, login, permissions or installing. " +
        "If this is a GAME, do not play it: answer done with a short description of what is on screen.\n"
}
