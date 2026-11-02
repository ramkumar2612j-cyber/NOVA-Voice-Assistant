// v7.158 sandbox compile harness: minimal media3 effect stubs (see Media3CommonStub.kt note).
package androidx.media3.effect

class Presentation private constructor() : androidx.media3.common.Effect {
    companion object {
        @JvmStatic fun createForWidthAndHeight(width: Int, height: Int, resizeMode: Int): Presentation = Presentation()
        @JvmStatic fun createForHeight(height: Int): Presentation = Presentation()
        @JvmStatic fun createForAspectRatio(aspectRatio: Float, resizeMode: Int): Presentation = Presentation()
    }
}

class GaussianBlurWithFrameOverlaid(sigma: Float, scaleX: Float, scaleY: Float) : androidx.media3.common.Effect
