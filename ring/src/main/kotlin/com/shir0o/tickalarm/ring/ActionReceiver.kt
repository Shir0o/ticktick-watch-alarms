package com.shir0o.tickalarm.ring

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Reminder"
        when (intent.action) {
            ACTION_DISMISS -> Alarms.dismiss(context, id, tellOtherDevice = true)
            ACTION_SNOOZE -> Alarms.snooze(context, id, title)
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.shir0o.tickalarm.DISMISS"
        const val ACTION_SNOOZE = "com.shir0o.tickalarm.SNOOZE"

        fun pending(ctx: Context, action: String, id: String, title: String): PendingIntent =
            PendingIntent.getBroadcast(
                ctx,
                (action + id).hashCode(),
                Intent(ctx, ActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(AlarmScheduler.EXTRA_ID, id)
                    .putExtra(AlarmScheduler.EXTRA_TITLE, title),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
