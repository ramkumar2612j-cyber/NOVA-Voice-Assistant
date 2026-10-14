package androidx.work
import android.content.Context
import java.util.concurrent.TimeUnit
class WorkerParameters
abstract class ListenableWorker(val applicationContext: Context, val workerParams: WorkerParameters) {
    abstract class Result { companion object { @JvmStatic fun retry(): Result = object : Result() {}; @JvmStatic fun success(): Result = object : Result() {} } }
}
abstract class Worker(ctx: Context, params: WorkerParameters) : ListenableWorker(ctx, params) { abstract fun doWork(): Result }
enum class NetworkType { CONNECTED }
enum class ExistingWorkPolicy { APPEND_OR_REPLACE }
enum class BackoffPolicy { EXPONENTIAL }
class Data
class Constraints { class Builder { fun setRequiredNetworkType(t: NetworkType) = this; fun build() = Constraints() } }
class OneTimeWorkRequest { class Builder { fun setConstraints(c: Constraints) = this; fun setBackoffCriteria(p: BackoffPolicy, d: Long, u: TimeUnit) = this; fun build() = OneTimeWorkRequest() } }
inline fun <reified W : ListenableWorker> OneTimeWorkRequestBuilder() = OneTimeWorkRequest.Builder()
class WorkManager {
    fun cancelUniqueWork(n: String) {}
    fun enqueueUniqueWork(n: String, p: ExistingWorkPolicy, r: OneTimeWorkRequest) {}
    companion object { @JvmStatic fun getInstance(c: Context) = WorkManager() }
}
