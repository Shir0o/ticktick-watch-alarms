package com.shir0o.tickalarm.ring

import android.content.Context
import android.content.SharedPreferences
import com.shir0o.tickalarm.core.AlarmCodec
import com.shir0o.tickalarm.core.PlannedAlarm

/** What this device has scheduled and already rung. Lives in app-private storage. */
object AlarmStore {
    private const val PREFS = "tickalarm_alarms"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_FIRED = "fired"
    private const val KEY_RING_HERE = "ringHere"
    private const val KEY_LAST_SYNC = "lastSync"
    private const val KEY_STATUS = "status"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<PlannedAlarm> =
        prefs(ctx).getString(KEY_ALARMS, null)?.let { AlarmCodec.decodeList(it) } ?: emptyList()

    fun save(ctx: Context, alarms: List<PlannedAlarm>) {
        val keep = alarms.map { it.id }.toSet()
        // Forget "already rang" marks for alarms that left the list, so the set stays small.
        val fired = firedIds(ctx).filter { it in keep || it.contains("#") }.toSet()
        prefs(ctx).edit()
            .putString(KEY_ALARMS, AlarmCodec.encodeList(alarms))
            .putStringSet(KEY_FIRED, fired)
            .apply()
    }

    private fun firedIds(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_FIRED, emptySet())?.toSet() ?: emptySet()

    fun isFired(ctx: Context, id: String): Boolean = id in firedIds(ctx)

    fun markFired(ctx: Context, id: String) {
        prefs(ctx).edit().putStringSet(KEY_FIRED, firedIds(ctx) + id).apply()
    }

    /** Whether this device should ring itself. On the phone it is a setting; the watch always rings. */
    fun ringHere(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_RING_HERE, true)

    fun setRingHere(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_RING_HERE, value).apply()
    }

    fun lastSyncMillis(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_SYNC, 0L)

    fun status(ctx: Context): String? = prefs(ctx).getString(KEY_STATUS, null)

    fun setSynced(ctx: Context, status: String) {
        prefs(ctx).edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .putString(KEY_STATUS, status)
            .apply()
    }

    fun setStatus(ctx: Context, status: String) {
        prefs(ctx).edit().putString(KEY_STATUS, status).apply()
    }
}
