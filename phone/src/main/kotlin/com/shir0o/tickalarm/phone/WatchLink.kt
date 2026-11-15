package com.shir0o.tickalarm.phone

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.shir0o.tickalarm.core.AlarmCodec
import com.shir0o.tickalarm.core.Paths
import com.shir0o.tickalarm.core.PlannedAlarm
import kotlinx.coroutines.tasks.await

/** Sends the alarm list to the watch. The watch keeps it and rings even if the phone is away. */
object WatchLink {
    suspend fun pushAlarms(ctx: Context, alarms: List<PlannedAlarm>): Boolean = runCatching {
        val request = PutDataMapRequest.create(Paths.ALARMS).apply {
            dataMap.putString(Paths.KEY_JSON, AlarmCodec.encodeList(alarms))
            dataMap.putLong(Paths.KEY_SENT_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(ctx.applicationContext).putDataItem(request).await()
        true
    }.getOrDefault(false)

    suspend fun sendTest(ctx: Context, alarm: PlannedAlarm): Int = runCatching {
        val app = ctx.applicationContext
        val nodes = Wearable.getNodeClient(app).connectedNodes.await()
        nodes.forEach { node ->
            Wearable.getMessageClient(app)
                .sendMessage(node.id, Paths.TEST, AlarmCodec.encode(alarm).toByteArray())
                .await()
        }
        nodes.size
    }.getOrDefault(0)

    suspend fun connectedWatches(ctx: Context): List<String> = runCatching {
        Wearable.getNodeClient(ctx.applicationContext).connectedNodes.await().map { it.displayName }
    }.getOrDefault(emptyList())
}
