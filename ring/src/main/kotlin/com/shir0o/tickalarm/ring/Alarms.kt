package com.shir0o.tickalarm.ring

import android.content.Context
import android.content.Intent

/** Dismiss and snooze, shared by the notification buttons, the ring screen and the other device. */
object Alarms {
    const val ACTION_CLOSE = "com.shir0o.tickalarm.CLOSE_RING"
    const val SNOOZE_MINUTES = 10

    fun dismiss(ctx: Context, id: String, tellOtherDevice: Boolean) {
        AlarmStore.markFired(ctx, id)
        AlarmScheduler.cancel(ctx, id)
        AlarmNotifier.cancel(ctx, id)
        ctx.sendBroadcast(
            Intent(ACTION_CLOSE).setPackage(ctx.packageName).putExtra(AlarmScheduler.EXTRA_ID, id),
        )
        if (tellOtherDevice) PeerSync.sendDismiss(ctx, id)
    }

    fun snooze(ctx: Context, id: String, title: String) {
        dismiss(ctx, id, tellOtherDevice = true)
        val baseId = id.substringBefore('#')
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        AlarmScheduler.schedule(ctx, "$baseId#snooze$at", title, at)
    }
}
