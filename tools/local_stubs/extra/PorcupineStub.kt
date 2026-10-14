package ai.picovoice.porcupine
class Porcupine {
    val frameLength: Int = 512
    fun process(f: ShortArray): Int = -1
    fun delete() {}
    class Builder {
        fun setAccessKey(k: String?) = this; fun setKeywordPath(p: String) = this; fun setSensitivity(s: Float) = this
        fun build(c: android.content.Context): Porcupine = Porcupine()
    }
}
