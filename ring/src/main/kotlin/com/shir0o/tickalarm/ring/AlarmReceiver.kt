package com.shir0o.tickalarm.ring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return
        if (AlarmStore.isFired(context, id)) return
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Reminder"
        val fireAt = intent.getLongExtra(AlarmScheduler.EXTRA_FIRE_AT, System.currentTimeMillis())
        AlarmNotifier.ring(context, id, title, fireAt)
    }
}
