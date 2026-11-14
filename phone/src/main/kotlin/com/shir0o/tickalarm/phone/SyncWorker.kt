package com.shir0o.tickalarm.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shir0o.tickalarm.core.AlarmPlanner
import com.shir0o.tickalarm.ring.AlarmScheduler
import com.shir0o.tickalarm.ring.AlarmStore
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Every 15 minutes: read TickTick, plan the next 48 hours of alarms, arm the phone, update the watch. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val creds = Credentials(ctx)
        val token = creds.accessToken
        if (token.isNullOrBlank()) {
            AlarmStore.setStatus(ctx, "Not connected to TickTick yet.")
            return Result.success()
        }
        return try {
            val fetched = fetchWithRefresh(creds, token)
            val alarms = AlarmPlanner.plan(fetched.tasks, Instant.now(), ZoneId.systemDefault())
            AlarmScheduler.replaceAll(ctx, alarms)
            val watchOk = WatchLink.pushAlarms(ctx, alarms)
            val skipped = if (fetched.failedProjects > 0) " ${fetched.failedProjects} list(s) couldn't be read." else ""
            val watch = if (watchOk) "" else " Couldn't reach the watch app."
            AlarmStore.setSynced(ctx, "${alarms.size} alarm(s) in the next 48 hours.$skipped$watch")
            Result.success()
        } catch (e: AuthExpiredException) {
            AlarmStore.setStatus(ctx, "TickTick sign-in expired. Tap Connect TickTick.")
            notifyReconnect(ctx)
            Result.success()
        } catch (e: Exception) {
            AlarmStore.setStatus(ctx, "Last sync failed: ${e.message ?: e.javaClass.simpleName}")
            Result.retry()
        }
    }

    private suspend fun fetchWithRefresh(creds: Credentials, token: String): TickTickClient.FetchResult =
        try {
            TickTickClient(token).allTasks()
        } catch (e: AuthExpiredException) {
            val refresh = creds.refreshToken ?: throw e
            val renewed = runCatching { TickTickAuth.refresh(creds, refresh) }.getOrNull() ?: throw e
            creds.saveToken(renewed)
            TickTickClient(renewed.accessToken).allTasks()
        }

    private fun notifyReconnect(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL, "Sync problems", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(ctx, STATUS_CHANNEL)
            .setSmallIcon(com.shir0o.tickalarm.ring.R.drawable.tickalarm_ic_alarm)
            .setContentTitle("Reconnect TickTick")
            .setContentText("Reminder alarms stopped updating. Tap to sign in again.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(RECONNECT_NOTIFICATION_ID, n)
    }

    companion object {
        private const val PERIODIC = "tickalarm-sync"
        private const val NOW = "tickalarm-sync-now"
        private const val STATUS_CHANNEL = "tickalarm_status"
        private const val RECONNECT_NOTIFICATION_ID = 4201

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        }

        fun syncNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build(),
            )
        }
    }
}
