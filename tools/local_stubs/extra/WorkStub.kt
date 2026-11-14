package androidx.work
import android.content.Context
import android.net.Uri
import java.util.concurrent.TimeUnit
class WorkerParameters
abstract class ListenableWorker(val applicationContext: Context, val workerParams: WorkerParameters) {
    abstract class Result { companion object { @JvmStatic fun retry(): Result = object : Result() {}; @JvmStatic fun success(): Result = object : Result() {}; @JvmStatic fun failure(): Result = object : Result() {} } }
}
abstract class Worker(ctx: Context, params: WorkerParameters) : ListenableWorker(ctx, params) { abstract fun doWork(): Result }
enum class NetworkType { CONNECTED }
enum class ExistingWorkPolicy { APPEND_OR_REPLACE, REPLACE, KEEP, APPEND, UPDATE }
enum class ExistingPeriodicWorkPolicy { KEEP, REPLACE, UPDATE, CANCEL_AND_REENQUEUE }
enum class BackoffPolicy { EXPONENTIAL }
class Data
class Constraints {
    class ContentUriTrigger(val uri: Uri, val triggeredFromMediaStore: Boolean)
    class Builder {
        fun setRequiredNetworkType(t: NetworkType) = this
        fun setRequiresBatteryNotLow(b: Boolean) = this
        fun setRequiresCharging(b: Boolean) = this
        fun setRequiresStorageNotLow(b: Boolean) = this
        fun addContentUriTrigger(uri: Uri, triggerForDescendants: Boolean) = this   // real WorkManager 2.9.1 signature
        fun setTriggerContentMaxDelay(d: Long, u: TimeUnit) = this
        fun setTriggerContentUpdateDelay(d: Long, u: TimeUnit) = this
        fun build() = Constraints()
    }
}
class OneTimeWorkRequest {
    class Builder {
        fun setConstraints(c: Constraints) = this
        fun setInitialDelay(d: Long, u: TimeUnit) = this
        fun setBackoffCriteria(p: BackoffPolicy, d: Long, u: TimeUnit) = this
        fun build() = OneTimeWorkRequest()
    }
}
class PeriodicWorkRequest {
    class Builder(d: Long, u: TimeUnit) {
        fun setConstraints(c: Constraints) = this
        fun setInitialDelay(delay: Long, u2: TimeUnit) = this
        fun build() = PeriodicWorkRequest()
    }
}
inline fun <reified W : ListenableWorker> OneTimeWorkRequestBuilder() = OneTimeWorkRequest.Builder()
inline fun <reified W : ListenableWorker> PeriodicWorkRequestBuilder(d: Long, u: TimeUnit) = PeriodicWorkRequest.Builder(d, u)
class WorkManager {
    fun getWorkInfosForUniqueWork(n: String): java.util.concurrent.Future<List<Any?>> = java.util.concurrent.CompletableFuture.completedFuture(emptyList<Any?>())
    fun cancelUniqueWork(n: String) {}
    fun enqueueUniqueWork(n: String, p: ExistingWorkPolicy, r: OneTimeWorkRequest) {}
    fun enqueueUniquePeriodicWork(n: String, p: ExistingPeriodicWorkPolicy, r: PeriodicWorkRequest) {}
    fun enqueue(r: OneTimeWorkRequest) {}
    companion object { @JvmStatic fun getInstance(c: Context) = WorkManager() }
}
