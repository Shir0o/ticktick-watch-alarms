package com.shir0o.tickalarm.phone

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.shir0o.tickalarm.core.PlannedAlarm
import com.shir0o.tickalarm.ring.AlarmNotifier
import com.shir0o.tickalarm.ring.AlarmScheduler
import com.shir0o.tickalarm.ring.AlarmStore
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private lateinit var creds: Credentials
    private lateinit var status: TextView
    private lateinit var upcoming: TextView
    private lateinit var permissions: TextView

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        creds = Credentials(this)
        AlarmNotifier.ensureChannel(this)

        status = findViewById(R.id.status)
        upcoming = findViewById(R.id.upcoming)
        permissions = findViewById(R.id.permissions)
        val clientId = findViewById<EditText>(R.id.client_id)
        val clientSecret = findViewById<EditText>(R.id.client_secret)
        val redirect = findViewById<EditText>(R.id.redirect_uri)
        val pasted = findViewById<EditText>(R.id.pasted_url)
        val ringHere = findViewById<Switch>(R.id.ring_here)

        clientId.setText(creds.clientId)
        clientSecret.setText(creds.clientSecret)
        redirect.setText(creds.redirectUri)
        ringHere.isChecked = AlarmStore.ringHere(this)

        findViewById<Button>(R.id.connect).setOnClickListener {
            creds.clientId = clientId.text.toString()
            creds.clientSecret = clientSecret.text.toString()
            creds.redirectUri = redirect.text.toString()
            if (creds.clientId.isEmpty() || creds.clientSecret.isEmpty()) {
                toast("Enter the Client ID and Client Secret from developer.ticktick.com first.")
                return@setOnClickListener
            }
            try {
                startActivity(Intent(Intent.ACTION_VIEW, OAuthFlow.begin(this)))
            } catch (e: ActivityNotFoundException) {
                toast("No browser found.")
            }
        }

        findViewById<Button>(R.id.finish_paste).setOnClickListener {
            val text = pasted.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            lifecycleScope.launch {
                toast(OAuthFlow.complete(this@MainActivity, Uri.parse(text)))
                pasted.setText("")
                refresh()
            }
        }

        ringHere.setOnCheckedChangeListener { _, checked ->
            AlarmStore.setRingHere(this, checked)
            AlarmScheduler.applyStored(this)
        }

        findViewById<Button>(R.id.sync_now).setOnClickListener {
            SyncWorker.syncNow(this)
            toast("Syncing…")
        }

        findViewById<Button>(R.id.test_alarm).setOnClickListener { testAlarm() }
        findViewById<Button>(R.id.fix_permissions).setOnClickListener { fixNextPermission() }

        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("tickalarm-sync-now")
            .observe(this) { infos ->
                if (infos.any { it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED }) refresh()
            }

        if (!creds.accessToken.isNullOrBlank()) SyncWorker.schedulePeriodic(this)
        intent.getStringExtra(EXTRA_MESSAGE)?.let { toast(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_MESSAGE)?.let { toast(it) }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun testAlarm() {
        val at = System.currentTimeMillis() + 60_000L
        val alarm = PlannedAlarm(
            id = "test@$at#test", taskId = "test", projectId = "",
            title = "Test alarm from TickAlarm", fireAtMillis = at,
        )
        if (AlarmStore.ringHere(this)) AlarmScheduler.schedule(this, alarm.id, alarm.title, at)
        lifecycleScope.launch {
            val watches = WatchLink.sendTest(this@MainActivity, alarm)
            val where = when {
                watches > 0 && AlarmStore.ringHere(this@MainActivity) -> "this phone and your watch"
                watches > 0 -> "your watch"
                AlarmStore.ringHere(this@MainActivity) -> "this phone (no watch connected)"
                else -> "nowhere: no watch connected and phone ringing is off"
            }
            toast("Test alarm in 1 minute on $where.")
        }
    }

    private fun missingPermissions(): List<Pair<String, () -> Unit>> {
        val missing = mutableListOf<Pair<String, () -> Unit>>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += "Notifications" to { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            missing += "Alarms & reminders" to {
                openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            missing += "Full-screen alarms" to {
                openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
        return missing
    }

    private fun fixNextPermission() {
        val missing = missingPermissions()
        if (missing.isEmpty()) toast("All set.") else missing.first().second()
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun refresh() {
        val connected = !creds.accessToken.isNullOrBlank()
        val last = AlarmStore.lastSyncMillis(this)
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val lines = mutableListOf(
            if (connected) "TickTick: connected" else "TickTick: not connected",
            if (last > 0) "Last sync: ${time.format(Date(last))}" else "Last sync: never",
        )
        if (connected && creds.expiresAtMillis > 0) {
            lines += "Sign-in good until ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(creds.expiresAtMillis))}"
        }
        AlarmStore.status(this)?.let { lines += it }
        status.text = lines.joinToString("\n")

        val missing = missingPermissions()
        permissions.text = if (missing.isEmpty()) "Permissions: all set" else
            "Still needed: " + missing.joinToString(", ") { it.first }

        val list = AlarmStore.load(this).filter { it.fireAtMillis >= System.currentTimeMillis() - 60_000 }
        upcoming.text = if (list.isEmpty()) "No alarms in the next 48 hours." else
            list.take(12).joinToString("\n") { "${time.format(Date(it.fireAtMillis))}  ${it.title}" }

        lifecycleScope.launch {
            val watches = WatchLink.connectedWatches(this@MainActivity)
            status.text = status.text.toString() + "\n" +
                if (watches.isEmpty()) "Watch: not connected" else "Watch: ${watches.joinToString()}"
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_MESSAGE = "message"
    }
}
