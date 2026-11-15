package com.shir0o.tickalarm.ring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.shir0o.tickalarm.core.PlannedAlarm

/** Schedules exact, labeled alarms with AlarmManager so they fire even in Doze. */
object AlarmScheduler {
    const val ACTION_FIRE = "com.shir0o.tickalarm.FIRE"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_FIRE_AT = "fireAt"

    /** Makes the scheduled set match [alarms]: cancels dropped ones, schedules new ones, rings ones just missed. */
    fun replaceAll(ctx: Context, alarms: List<PlannedAlarm>) {
        val newIds = alarms.map { it.id }.toSet()
        AlarmStore.load(ctx).filter { it.id !in newIds }.forEach { cancel(ctx, it.id) }
        AlarmStore.save(ctx, alarms)
        applyStored(ctx)
    }

    /** Re-arms the stored list, e.g. after a reboot or when "ring here" is switched. */
    fun applyStored(ctx: Context) {
        val alarms = AlarmStore.load(ctx)
        if (!AlarmStore.ringHere(ctx)) {
            alarms.forEach { cancel(ctx, it.id) }
            return
        }
        val now = System.currentTimeMillis()
        for (alarm in alarms) {
            if (AlarmStore.isFired(ctx, alarm.id)) continue
            if (alarm.fireAtMillis <= now) {
                AlarmNotifier.ring(ctx, alarm.id, alarm.title, alarm.fireAtMillis)
            } else {
                schedule(ctx, alarm.id, alarm.title, alarm.fireAtMillis)
            }
        }
    }

    fun schedule(ctx: Context, id: String, title: String, fireAtMillis: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val fire = firePendingIntent(ctx, id, title, fireAtMillis, PendingIntent.FLAG_UPDATE_CURRENT)!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, fire)
            return
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?: Intent(ctx, RingActivity::class.java)
        val show = PendingIntent.getActivity(
            ctx, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAtMillis, show), fire)
    }

    fun cancel(ctx: Context, id: String) {
        val pi = firePendingIntent(ctx, id, null, 0L, PendingIntent.FLAG_NO_CREATE) ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    private fun firePendingIntent(
        ctx: Context,
        id: String,
        title: String?,
        fireAtMillis: Long,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("tickalarm://alarm/" + Uri.encode(id)))
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_FIRE_AT, fireAtMillis)
        return PendingIntent.getBroadcast(ctx, id.hashCode(), intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}
