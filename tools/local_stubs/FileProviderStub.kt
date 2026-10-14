package androidx.core.content
open class FileProvider : android.content.ContentProvider() {
    override fun onCreate() = true
    override fun query(u: android.net.Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): android.database.Cursor? = null
    override fun getType(u: android.net.Uri): String? = null
    override fun insert(u: android.net.Uri, v: android.content.ContentValues?): android.net.Uri? = null
    override fun delete(u: android.net.Uri, s: String?, a: Array<String>?) = 0
    override fun update(u: android.net.Uri, v: android.content.ContentValues?, s: String?, a: Array<String>?) = 0
    companion object { @JvmStatic fun getUriForFile(c: android.content.Context, auth: String, f: java.io.File): android.net.Uri = android.net.Uri.EMPTY }
}
