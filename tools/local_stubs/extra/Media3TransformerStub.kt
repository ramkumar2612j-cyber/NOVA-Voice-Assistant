// v7.158 sandbox compile harness: minimal media3 transformer stubs (see Media3CommonStub.kt note).
package androidx.media3.transformer

import android.content.Context
import android.os.Looper

class TransformationRequest private constructor() {
    class Builder {
        fun setVideoMimeType(m: String): Builder = this
        fun setAudioMimeType(m: String): Builder = this
        fun build(): TransformationRequest = TransformationRequest()
    }
}

class ExportResult
class ExportException(message: String) : Exception(message)
class Composition

interface AudioProcessorStub
class Effects(audioProcessors: List<AudioProcessorStub>, videoEffects: List<androidx.media3.common.Effect>)   // real order: audio first, then video

class EditedMediaItem private constructor() {
    class Builder(mediaItem: androidx.media3.common.MediaItem) {
        fun setEffects(e: Effects): Builder = this
        fun build(): EditedMediaItem = EditedMediaItem()
    }
}

class Transformer private constructor() {
    fun start(item: EditedMediaItem, outputPath: String) { throw UnsupportedOperationException("media3 sandbox stub") }
    fun start(composition: Composition, outputPath: String) { throw UnsupportedOperationException("media3 sandbox stub") }
    fun cancel() { }

    interface Listener {
        fun onCompleted(composition: Composition, exportResult: ExportResult) { }
        fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { }
    }

    class Builder(private val context: Context) {
        fun setTransformationRequest(request: TransformationRequest): Builder = this
        fun addListener(listener: Listener): Builder = this   // real media3 1.4.1: one argument
        fun build(): Transformer = Transformer()
    }
}
