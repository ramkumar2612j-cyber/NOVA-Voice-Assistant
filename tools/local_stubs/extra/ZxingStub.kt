// v7.158 sandbox compile harness: minimal zxing stubs (the real com.google.zxing:core:3.5.3 is a Gradle
// dependency and exists in the real build; the sandbox harness has no Maven access). These stubs only make
// the local compile + JUnit classpath complete. Every function throws: nothing may silently fake a QR result.
package com.google.zxing

enum class BarcodeFormat { QR_CODE, CODE_128, EAN_13 }
enum class EncodeHintType { MARGIN, CHARACTER_SET, ERROR_CORRECTION }

class Result(val text: String)

class RGBLuminanceSource(val width: Int, val height: Int, pixels: IntArray)

class BinaryBitmap(binarizer: com.google.zxing.common.Binarizer)
