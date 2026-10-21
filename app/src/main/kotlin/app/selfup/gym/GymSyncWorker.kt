package app.selfup.gym

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.selfup.SelfUpApplication
import java.util.concurrent.TimeUnit

/** Pulls from Gym while SelfUp is closed, so goals tick off and records update on their own. */
class GymSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as SelfUpApplication).gym.sync()
        return Result.success()
    }

    companion object {
        private const val NAME = "gym-sync"

        /** Every 15 minutes, Android's shortest period. Idempotent, so calling it on every launch is fine. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GymSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
