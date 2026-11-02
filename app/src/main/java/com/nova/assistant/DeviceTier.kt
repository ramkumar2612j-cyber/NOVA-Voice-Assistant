package com.nova.assistant

/** v6.56: one plain rule to sort a phone into LOW / MID / HIGH from RAM, CPU cores and Android version. Pure Kotlin, unit tested. */
object DeviceTier {
    enum class Tier { LOW, MID, HIGH }

    fun of(ramMb: Long, cores: Int, sdk: Int): Tier = when {
        ramMb < 3000 || cores < 6 || sdk < 26 -> Tier.LOW
        ramMb >= 6000 && cores >= 8 && sdk >= 29 -> Tier.HIGH
        else -> Tier.MID
    }

    /** Offline Qwen (491 MB) only where it will not make the phone lag. */
    fun allowsOfflineBrain(t: Tier): Boolean = t != Tier.LOW

    /** Heavy visual effects (glow, blur, long splash) only on strong phones. */
    fun allowsFancyEffects(t: Tier): Boolean = t == Tier.HIGH

    fun label(t: Tier): String = when (t) {
        Tier.LOW -> "halka phone: speed pehle, offline AI band"
        Tier.MID -> "normal phone: sab chalega"
        Tier.HIGH -> "tez phone: sab effects ON"
    }
}
