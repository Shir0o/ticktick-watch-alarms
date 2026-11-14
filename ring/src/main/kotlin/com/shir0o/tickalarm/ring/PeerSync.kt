package com.shir0o.tickalarm.ring

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.shir0o.tickalarm.core.Paths

/** Tells the paired phone or watch to stop ringing the same alarm. */
object PeerSync {
    fun sendDismiss(ctx: Context, id: String) {
        val app = ctx.applicationContext
        Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { nodes ->
            val messages = Wearable.getMessageClient(app)
            nodes.forEach { node -> messages.sendMessage(node.id, Paths.DISMISS, id.toByteArray()) }
        }
    }
}
