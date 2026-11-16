// v7.158 sandbox compile harness (see CommonsCompressStub.kt note).
package org.apache.commons.compress.compressors.bzip2

class BZip2CompressorInputStream(ins: java.io.InputStream) : java.io.InputStream() {
    override fun read(): Int = throw UnsupportedOperationException("commons-compress sandbox stub")
    override fun read(b: ByteArray, off: Int, len: Int): Int = throw UnsupportedOperationException("commons-compress sandbox stub")
}
