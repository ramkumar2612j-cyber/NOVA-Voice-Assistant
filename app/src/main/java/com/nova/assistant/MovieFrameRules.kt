package com.nova.assistant

/**
 * v7.64 PART 14B-2 - pure rules (no Android, no network; JUnit-able) for the LIVE SCREEN CAPTURE of the movie scan.
 * Decisions made HERE:
 *  - 2..4 frames, GAP_MS apart (so a moving reel shows different scenes). Frames are small JPEGs kept ONLY in memory: never saved, never sent anywhere in this hissa.
 *  - A protected screen (bank / payment / password / OTP / settings / installer / NOVA itself) is checked BEFORE EVERY frame (AnalysisRules.gateSource). If it shows up at any point,
 *    every frame taken so far is thrown away and the scan stops: PROTECTED.
 *  - Identical frames (paused video) are kept once; one distinct frame is enough to go on.
 *  - Nothing here names a movie (that is 14B-3) and nothing here reads a face.
 */
object MovieFrameRules {
    const val MIN_FRAMES = 2
    const val MAX_FRAMES = 4
    const val GAP_MS = 1500L
    const val MAX_FRAME_BYTES = 250_000
    const val MAX_TOTAL_BYTES = 800_000

    enum class Stop { OK, PROTECTED, NO_SERVICE, OLD_ANDROID, NO_FRAMES, CANCELLED }

    /** How many frames to take: always inside MIN..MAX. */
    fun frameCount(requested: Int): Int = requested.coerceIn(MIN_FRAMES, MAX_FRAMES)

    /** The same gate as every other analysis: a protected screen is refused. */
    fun gate(protectedScreen: Boolean): AnalysisRules.Gate = AnalysisRules.gateSource(protectedScreen)

    /** A frame is usable when it is not empty and not larger than MAX_FRAME_BYTES. */
    fun usable(frame: ByteArray?): Boolean = frame != null && frame.isNotEmpty() && frame.size <= MAX_FRAME_BYTES

    /** True when [frame] equals one already kept (paused video / static screen). */
    fun isDuplicate(kept: List<ByteArray>, frame: ByteArray): Boolean = kept.any { it.contentEquals(frame) }

    /** True while another frame still fits in the memory budget. */
    fun fitsBudget(kept: List<ByteArray>, frame: ByteArray): Boolean = kept.sumOf { it.size } + frame.size <= MAX_TOTAL_BYTES

    private fun t(en: Boolean, hi: String, e: String): String = if (en) e else hi

    /** Honest line for every way the capture can end. Never names a movie, never says a guess was made. */
    fun statusText(stop: Stop, frames: Int, en: Boolean): String = when (stop) {
        Stop.OK -> t(en,
            "Screen se $frames frame liye (sirf memory me, save nahi kiye). Movie ka naam pehchanne wala hissa (Part 14B-3) abhi ban raha hai, isliye abhi naam nahi bata sakta.",
            "I captured $frames frame(s) from the screen (memory only, not saved). The part that guesses the movie name (Part 14B-3) is still being built, so I cannot name it yet.")
        Stop.PROTECTED -> t(en,
            "Ye screen protected hai (bank, password, settings ya NOVA khud), isliye maine kuch capture nahi kiya.",
            "This screen is protected (bank, password, settings or NOVA itself), so I captured nothing.")
        Stop.NO_SERVICE -> t(en,
            "Screen padhne ke liye Accessibility service chalu nahi hai. Usse chalu karo, phir dobara bolo.",
            "The Accessibility service is not running, so I cannot capture the screen. Turn it on and ask again.")
        Stop.OLD_ANDROID -> t(en,
            "Is phone ka Android screenshot lene wali suvidha (Android 11+) nahi deta, isliye live scan nahi ho sakta.",
            "This phone's Android has no screenshot support for apps (Android 11+), so a live scan is not possible.")
        Stop.NO_FRAMES -> t(en,
            "Screen se koi frame nahi mila (shayad ye screen screenshot hone se roki gayi hai). Dobara try karo.",
            "I could not get any frame from the screen (it may block screenshots). Try again.")
        Stop.CANCELLED -> t(en, "Screen scan roka gaya, kuch save nahi hua.", "Screen scan stopped, nothing was saved.")
    }

    fun startedText(en: Boolean): String = t(en,
        "Theek hai, screen se kuch frame le raha hoon. Video chalne do, kuch second lagenge.",
        "Okay, I am capturing a few frames from the screen. Let the video keep playing, it takes a few seconds.")
}
