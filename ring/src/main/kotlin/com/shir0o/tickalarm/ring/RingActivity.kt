package com.shir0o.tickalarm.ring

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

/** Full-screen alarm with the reminder's title and Dismiss / Snooze buttons. */
class RingActivity : Activity() {
    private var alarmId: String = ""
    private var alarmTitle: String = ""
    private var vibrator: Vibrator? = null

    private val closer = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(AlarmScheduler.EXTRA_ID) == alarmId) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.tickalarm_activity_ring)
        findViewById<Button>(R.id.tickalarm_dismiss).setOnClickListener {
            Alarms.dismiss(this, alarmId, tellOtherDevice = true)
            finish()
        }
        findViewById<Button>(R.id.tickalarm_snooze).setOnClickListener {
            Alarms.snooze(this, alarmId, alarmTitle)
            finish()
        }
        ContextCompat.registerReceiver(
            this, closer, IntentFilter(Alarms.ACTION_CLOSE), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        bind(intent)
        // A watch has no speaker to rely on, so keep buzzing while this screen is up.
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) startVibration()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(intent: Intent) {
        alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ID).orEmpty()
        alarmTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Reminder"
        val fireAt = intent.getLongExtra(AlarmScheduler.EXTRA_FIRE_AT, System.currentTimeMillis())
        findViewById<TextView>(R.id.tickalarm_title).text = alarmTitle
        findViewById<TextView>(R.id.tickalarm_time).text =
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(fireAt))
    }

    private fun startVibration() {
        @Suppress("DEPRECATION")
        val v = getSystemService(Vibrator::class.java) ?: return
        vibrator = v
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
    }

    override fun onDestroy() {
        vibrator?.cancel()
        unregisterReceiver(closer)
        super.onDestroy()
    }

    companion object {
        fun intent(ctx: Context, id: String, title: String, fireAtMillis: Long): Intent =
            Intent(ctx, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(AlarmScheduler.EXTRA_ID, id)
                .putExtra(AlarmScheduler.EXTRA_TITLE, title)
                .putExtra(AlarmScheduler.EXTRA_FIRE_AT, fireAtMillis)
    }
}
