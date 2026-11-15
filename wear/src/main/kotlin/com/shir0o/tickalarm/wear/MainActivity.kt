package com.shir0o.tickalarm.wear

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.wearable.Wearable
import com.shir0o.tickalarm.core.Paths
import com.shir0o.tickalarm.ring.AlarmNotifier
import com.shir0o.tickalarm.ring.AlarmScheduler
import com.shir0o.tickalarm.ring.AlarmStore
import java.text.DateFormat
import java.util.Date

/** Shows the next alarms on the watch and offers a one-minute test. */
class MainActivity : Activity() {
    private lateinit var list: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        list = findViewById(R.id.list)
        AlarmNotifier.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        findViewById<Button>(R.id.test).setOnClickListener {
            val at = System.currentTimeMillis() + 60_000L
            AlarmScheduler.schedule(this, "watchtest@$at#test", "Test alarm on your watch", at)
            list.text = getString(R.string.test_set)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        // Pick up the latest list in case it arrived before this app was installed.
        Wearable.getDataClient(this)
            .getDataItems(Uri.parse("wear://*" + Paths.ALARMS))
            .addOnSuccessListener { items ->
                items.forEach { AlarmsListenerService.applyItem(this, it) }
                items.release()
                render()
            }
    }

    private fun render() {
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val alarms = AlarmStore.load(this).filter { it.fireAtMillis >= System.currentTimeMillis() - 60_000 }
        val last = AlarmStore.lastSyncMillis(this)
        val header = if (last > 0) "Updated ${fmt.format(Date(last))}" else "Waiting for the phone app"
        list.text = if (alarms.isEmpty()) "$header\nNo alarms in the next 48 hours." else
            header + "\n" + alarms.take(8).joinToString("\n") { "${fmt.format(Date(it.fireAtMillis))} ${it.title}" }
    }
}
