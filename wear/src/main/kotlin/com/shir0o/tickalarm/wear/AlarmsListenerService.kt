package com.shir0o.tickalarm.wear

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.shir0o.tickalarm.core.AlarmCodec
import com.shir0o.tickalarm.core.Paths
import com.shir0o.tickalarm.ring.AlarmScheduler
import com.shir0o.tickalarm.ring.AlarmStore

/** Receives the alarm list from the phone and arms each alarm on the watch itself. */
class AlarmsListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED) applyItem(this, event.dataItem)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != Paths.TEST) return
        val alarm = AlarmCodec.decode(String(event.data)) ?: return
        AlarmScheduler.schedule(this, alarm.id, alarm.title, alarm.fireAtMillis)
    }

    companion object {
        fun applyItem(ctx: Context, item: DataItem) {
            if (item.uri.path != Paths.ALARMS) return
            val json = DataMapItem.fromDataItem(item).dataMap.getString(Paths.KEY_JSON) ?: return
            AlarmScheduler.replaceAll(ctx, AlarmCodec.decodeList(json))
            AlarmStore.setSynced(ctx, "Updated from phone")
        }
    }
}
