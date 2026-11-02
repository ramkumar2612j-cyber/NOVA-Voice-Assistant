// v7.158 sandbox compile harness: minimal zxing qrcode stubs (see ZxingStub.kt note). Everything throws.
package com.google.zxing.qrcode

class QRCodeWriter {
    fun encode(contents: String, format: com.google.zxing.BarcodeFormat, width: Int, height: Int, hints: Map<com.google.zxing.EncodeHintType, *>): com.google.zxing.common.BitMatrix =
        throw UnsupportedOperationException("zxing sandbox stub")
}

class QRCodeReader {
    fun decode(image: com.google.zxing.BinaryBitmap): com.google.zxing.Result =
        throw UnsupportedOperationException("zxing sandbox stub")
}
