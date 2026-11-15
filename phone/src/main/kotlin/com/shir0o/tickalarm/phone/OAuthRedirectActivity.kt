package com.shir0o.tickalarm.phone

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Receives tickalarm://oauth?code=... after you approve access in the browser. */
class OAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply {
            setPadding(48, 48, 48, 48)
            textSize = 16f
            text = getString(R.string.connecting)
        }
        setContentView(text)
        handle(intent, text)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handle(intent: Intent, view: TextView) {
        val data = intent.data ?: return finish()
        lifecycleScope.launch {
            val message = OAuthFlow.complete(this@OAuthRedirectActivity, data)
            startActivity(
                Intent(this@OAuthRedirectActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_MESSAGE, message),
            )
            view.text = message
            finish()
        }
    }
}
