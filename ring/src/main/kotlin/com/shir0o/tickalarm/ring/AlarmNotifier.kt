package com.shir0o.tickalarm.ring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import java.text.DateFormat
import java.util.Date

/**
 * Rings an alarm as an insistent, full-screen notification: alarm sound and vibration
 * repeat until it is dismissed or snoozed, and the lock screen shows [RingActivity].
 */
object AlarmNotifier {
    const val CHANNEL_ID = "tickalarm_alarms_v1"
    private const val TIMEOUT_MILLIS = 15 * 60 * 1000L

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        val channel = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.tickalarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = ctx.getString(R.string.tickalarm_channel_description)
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    fun ring(ctx: Context, id: String, title: String, fireAtMillis: Long) {
        ensureChannel(ctx)
        AlarmStore.markFired(ctx, id)
        val requestCode = id.hashCode()
        val ringPi = PendingIntent.getActivity(
            ctx, requestCode, RingActivity.intent(ctx, id, title, fireAtMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(ctx, R.drawable.tickalarm_ic_alarm)
        val dismiss = Notification.Action.Builder(
            icon, ctx.getString(R.string.tickalarm_dismiss),
            ActionReceiver.pending(ctx, ActionReceiver.ACTION_DISMISS, id, title),
        ).build()
        val snooze = Notification.Action.Builder(
            icon, ctx.getString(R.string.tickalarm_snooze),
            ActionReceiver.pending(ctx, ActionReceiver.ACTION_SNOOZE, id, title),
        ).build()
        val notification = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.tickalarm_ic_alarm)
            .setContentTitle(title)
            .setContentText(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(fireAtMillis)))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(ringPi, true)
            .setContentIntent(ringPi)
            .setOngoing(true)
            .setAutoCancel(false)
            // The watch rings on its own, so don't also mirror the phone's alarm to it.
            .setLocalOnly(true)
            .setTimeoutAfter(TIMEOUT_MILLIS)
            .addAction(dismiss)
            .addAction(snooze)
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        ctx.getSystemService(NotificationManager::class.java).notify(requestCode, notification)
    }

    fun cancel(ctx: Context, id: String) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
    }
}
