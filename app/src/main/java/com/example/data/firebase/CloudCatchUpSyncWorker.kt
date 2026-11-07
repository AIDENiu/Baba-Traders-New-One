package com.example.data.firebase

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit

class CloudCatchUpSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val scope = CoroutineScope(Dispatchers.IO)
            val db = AppDatabase.getDatabase(applicationContext, scope)
            // Always ensure weekly backup and offline due-date alarms are up to date
            try {
                com.example.data.backup.BackupManager.checkAndRunWeeklyBackup(applicationContext, db)
                com.example.util.DueAlarmScheduler.rescheduleAllActiveAlarms(applicationContext, db)
            } catch (_: Exception) { }

            val syncService = FirestoreSyncService(
                context = applicationContext,
                roomDb = db,
                scope = scope,
                enableBackgroundMonitors = false
            )
            if (!syncService.isCloudAuthenticated() || !syncService.checkIsDeviceOnline()) {
                return Result.success()
            }
            syncService.runAutomaticBackgroundSyncCycle()
            Result.success()
        } catch (e: Exception) {
            Log.d("CloudCatchUpSyncWorker", "Periodic sync deferred: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "baba_periodic_cloud_catchup_sync"
        private const val IMMEDIATE_WORK_NAME = "baba_immediate_cloud_catchup_sync"

        fun schedulePeriodicAndImmediateSync(context: Context) {
            try {
                val networkConstraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicRequest = PeriodicWorkRequestBuilder<CloudCatchUpSyncWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(networkConstraints)
                    .build()

                val workManager = WorkManager.getInstance(context.applicationContext)
                workManager.enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicRequest
                )

                enqueueImmediateSync(context)
            } catch (e: Exception) {
                Log.d("CloudCatchUpSyncWorker", "WorkManager scheduling skipped: ${e.message}")
            }
        }

        fun enqueueImmediateSync(context: Context) {
            try {
                val networkConstraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val oneTimeRequest = OneTimeWorkRequestBuilder<CloudCatchUpSyncWorker>()
                    .setConstraints(networkConstraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    oneTimeRequest
                )
            } catch (e: Exception) {
                Log.d("CloudCatchUpSyncWorker", "Immediate sync enqueue skipped: ${e.message}")
            }
        }
    }
}
