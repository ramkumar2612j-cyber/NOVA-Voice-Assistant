package androidx.core.app
class NotificationCompat {
    open class Style
    class BigTextStyle : Style() { fun bigText(t: CharSequence?) = this }
    class Builder(c: android.content.Context, ch: String) {
        fun setSmallIcon(i: Int) = this; fun setContentTitle(t: CharSequence?) = this; fun setContentText(t: CharSequence?) = this
        fun setStyle(s: Style?) = this; fun setContentIntent(p: android.app.PendingIntent?) = this; fun setAutoCancel(b: Boolean) = this
        fun build(): android.app.Notification = android.app.Notification()
    }
}
