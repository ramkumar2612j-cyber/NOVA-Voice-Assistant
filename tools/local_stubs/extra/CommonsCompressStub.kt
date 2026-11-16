// v7.158 sandbox compile harness: minimal commons-compress stubs (the real org.apache.commons:commons-compress:1.26.2
// is a Gradle dependency; the sandbox has no Maven). Extraction itself never runs in unit tests; the stub only
// completes the compile + classpath. Every method throws.
package org.apache.commons.compress.archivers.tar

class TarArchiveEntry {
    val name: String = ""
    val size: Long = 0L
    val isDirectory: Boolean = false
    val isFile: Boolean = false
    val isLink: Boolean = false
    val isSymbolicLink: Boolean = false
    val linkName: String? = null
}

class TarArchiveInputStream(ins: java.io.InputStream) : java.io.InputStream() {
    val nextTarEntry: TarArchiveEntry? = null
    override fun read(): Int = throw UnsupportedOperationException("commons-compress sandbox stub")
    override fun read(b: ByteArray, off: Int, len: Int): Int = throw UnsupportedOperationException("commons-compress sandbox stub")
}
