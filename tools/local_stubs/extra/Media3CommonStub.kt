// v7.158 sandbox compile harness: minimal media3 stubs (the real androidx.media3:*:1.4.1 are Gradle
// dependencies; the sandbox has no Maven). Signatures verified against the REAL 1.4.1 AARs
// (Transformer.Builder.setTransformationRequest/addListener/start(EditedMediaItem,String),
// TransformationRequest.Builder.setVideoMimeType/setAudioMimeType, EditedMediaItem.Builder(MediaItem).setEffects,
// Effects(List<Effect>, List<AudioProcessor>), Transformer.Listener.onCompleted(Composition,ExportResult) /
// onError(Composition,ExportResult,ExportException), Presentation.createForWidthAndHeight(Int,Int,Int),
// GaussianBlurWithFrameOverlaid(Float,Float,Float)). Everything throws at runtime: nothing may fake an export.
package androidx.media3.common

class MediaItem private constructor(val localUri: android.net.Uri?) {
    class Builder {
        private var uri: android.net.Uri? = null
        fun setUri(u: android.net.Uri): Builder { uri = u; return this }
        fun build(): MediaItem = MediaItem(uri)
    }
    companion object { @JvmStatic fun fromUri(u: android.net.Uri): MediaItem = MediaItem.Builder().setUri(u).build() }
}

object MimeTypes {
    const val VIDEO_H264 = "video/avc"
    const val AUDIO_AAC = "audio/mp4a-latm"
}

object C {
    const val RESIZE_MODE_FIT = 2
    const val RESIZE_MODE_FIXED_WIDTH = 1
    const val RESIZE_MODE_FIXED_HEIGHT = 3
}

interface Effect
