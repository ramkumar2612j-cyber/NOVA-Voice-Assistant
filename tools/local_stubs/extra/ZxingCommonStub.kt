// v7.158 sandbox compile harness: minimal zxing common/qrcode stubs (see ZxingStub.kt note). Everything throws.
package com.google.zxing.common

class BitMatrix(val width: Int, val height: Int) {
    fun get(x: Int, y: Int): Boolean = throw UnsupportedOperationException("zxing sandbox stub")
}

abstract class Binarizer

class HybridBinarizer(source: com.google.zxing.RGBLuminanceSource) : Binarizer()
class GlobalHistogramBinarizer(source: com.google.zxing.RGBLuminanceSource) : Binarizer()
