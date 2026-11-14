package com.shir0o.tickalarm.ring

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.shir0o.tickalarm.core.Paths

class DismissListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == Paths.DISMISS) {
            Alarms.dismiss(this, String(event.data), tellOtherDevice = false)
        }
    }
}
