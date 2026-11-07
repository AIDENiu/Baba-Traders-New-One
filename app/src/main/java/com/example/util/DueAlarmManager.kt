package com.example.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.model.JobSheetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DueAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobSheetId = intent.getStringExtra("EXTRA_JOB_ID") ?: return
        val machineName = intent.getStringExtra("EXTRA_MACHINE_NAME") ?: "Machinery"
        val customerName = intent.getStringExtra("EXTRA_CUSTOMER_NAME") ?: "Customer"
        val isOverdue = intent.getBooleanExtra("EXTRA_IS_OVERDUE", false)

        showNotification(context, jobSheetId, machineName, customerName, isOverdue)
    }

    companion object {
        fun showNotification(
            context: Context,
            jobSheetId: String,
            machineName: String,
            customerName: String,
            isOverdue: Boolean
        ) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "baba_rental_due_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Rental Due Date & Overdue Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Offline alarms when equipment rentals are due or overdue"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("OPEN_JOB_ID", jobSheetId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                jobSheetId.hashCode(),
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (isOverdue) "OVERDUE RENTAL: $machineName" else "Rental Due Soon: $machineName"
            val text = if (isOverdue) {
                "Rental $jobSheetId for $customerName is OVERDUE! Collect machinery or rollover charges."
            } else {
                "Rental $jobSheetId for $customerName is due soon today."
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationId = if (isOverdue) jobSheetId.hashCode() + 1 else jobSheetId.hashCode()
            notificationManager.notify(notificationId, notification)
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext, scope)
                    DueAlarmScheduler.rescheduleAllActiveAlarms(context.applicationContext, db)
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

object DueAlarmScheduler {

    fun scheduleAlarms(context: Context, jobSheet: JobSheetEntity) {
        if (jobSheet.isDeleted || jobSheet.status == "returned" || jobSheet.status == "closed") {
            cancelAlarms(context, jobSheet.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        // 1. Pre-due alarm (2 hours prior)
        val preDueTime = jobSheet.dueTime - (2 * 60 * 60 * 1000L)
        if (preDueTime > now) {
            val preIntent = Intent(context, DueAlarmReceiver::class.java).apply {
                putExtra("EXTRA_JOB_ID", jobSheet.id)
                putExtra("EXTRA_MACHINE_NAME", jobSheet.machineName)
                putExtra("EXTRA_CUSTOMER_NAME", jobSheet.customerName)
                putExtra("EXTRA_IS_OVERDUE", false)
            }
            val prePending = PendingIntent.getBroadcast(
                context,
                jobSheet.id.hashCode(),
                preIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preDueTime, prePending)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, preDueTime, prePending)
                }
            } catch (_: Exception) { }
        }

        // 2. Overdue alarm (at exact due time)
        if (jobSheet.dueTime > now) {
            val overdueIntent = Intent(context, DueAlarmReceiver::class.java).apply {
                putExtra("EXTRA_JOB_ID", jobSheet.id)
                putExtra("EXTRA_MACHINE_NAME", jobSheet.machineName)
                putExtra("EXTRA_CUSTOMER_NAME", jobSheet.customerName)
                putExtra("EXTRA_IS_OVERDUE", true)
            }
            val overduePending = PendingIntent.getBroadcast(
                context,
                jobSheet.id.hashCode() + 1,
                overdueIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, jobSheet.dueTime, overduePending)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, jobSheet.dueTime, overduePending)
                }
            } catch (_: Exception) { }
        }
    }

    suspend fun rescheduleAllActiveAlarms(context: Context, db: AppDatabase) {
        val activeJobs = db.jobSheetDao().getActiveJobSheetsList()
        val now = System.currentTimeMillis()
        for (job in activeJobs) {
            if (job.dueTime > now) {
                scheduleAlarms(context, job)
            } else {
                // Already past due time offline/after reboot -> fire overdue notification once if not notified
                val prefs = context.getSharedPreferences("baba_due_alarm_prefs", Context.MODE_PRIVATE)
                val key = "overdue_notified_${job.id}_${job.dueTime}"
                if (!prefs.getBoolean(key, false)) {
                    DueAlarmReceiver.showNotification(
                        context = context,
                        jobSheetId = job.id,
                        machineName = job.machineName,
                        customerName = job.customerName,
                        isOverdue = true
                    )
                    prefs.edit().putBoolean(key, true).apply()
                }
            }
        }
    }

    fun cancelAlarms(context: Context, jobSheetId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Cancel Pre-due
        val preIntent = Intent(context, DueAlarmReceiver::class.java)
        val prePending = PendingIntent.getBroadcast(
            context,
            jobSheetId.hashCode(),
            preIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (prePending != null) {
            alarmManager.cancel(prePending)
            prePending.cancel()
        }

        // Cancel Overdue
        val overdueIntent = Intent(context, DueAlarmReceiver::class.java)
        val overduePending = PendingIntent.getBroadcast(
            context,
            jobSheetId.hashCode() + 1,
            overdueIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (overduePending != null) {
            alarmManager.cancel(overduePending)
            overduePending.cancel()
        }

        // Cancel active status-bar notifications
        notificationManager.cancel(jobSheetId.hashCode())
        notificationManager.cancel(jobSheetId.hashCode() + 1)
    }
}
