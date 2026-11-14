package com.shir0o.tickalarm.phone

import android.content.Context
import android.net.Uri
import java.util.UUID

object OAuthFlow {
    /** Starts sign-in: returns the TickTick page to open in the browser. */
    fun begin(ctx: Context): Uri {
        val creds = Credentials(ctx)
        val state = UUID.randomUUID().toString()
        creds.pendingState = state
        return TickTickAuth.authorizeUri(creds.clientId, creds.redirectUri, state)
    }

    /** Finishes sign-in from the address TickTick redirected to. Returns a message for the screen. */
    suspend fun complete(ctx: Context, redirected: Uri): String {
        val creds = Credentials(ctx)
        redirected.getQueryParameter("error")?.let { return "TickTick said: $it" }
        val code = redirected.getQueryParameter("code")
            ?: return "That address has no sign-in code in it."
        val expected = creds.pendingState
        if (expected == null || redirected.getQueryParameter("state") != expected) {
            return "That sign-in link is stale. Tap Connect TickTick again."
        }
        return try {
            creds.saveToken(TickTickAuth.exchangeCode(creds, code))
            creds.pendingState = null
            SyncWorker.schedulePeriodic(ctx)
            SyncWorker.syncNow(ctx)
            "Connected to TickTick. Syncing your reminders now."
        } catch (e: Exception) {
            e.message ?: "Sign-in failed."
        }
    }
}
