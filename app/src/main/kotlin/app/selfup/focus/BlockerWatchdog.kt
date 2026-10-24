package app.selfup.focus

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Restarts the app lock if the phone killed it. Phones that clean up background apps often
 * leave its process dead without the restart Android promises a sticky service.
 */
class BlockerWatchdog(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        BlockerService.start(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "app-lock-watchdog"

        /** Every 15 minutes, Android's shortest period. Idempotent, so calling it on every launch is fine. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BlockerWatchdog>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
