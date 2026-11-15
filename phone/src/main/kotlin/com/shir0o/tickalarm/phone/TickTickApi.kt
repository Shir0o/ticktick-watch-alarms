package com.shir0o.tickalarm.phone

import android.net.Uri
import android.util.Base64
import com.shir0o.tickalarm.core.TickJson
import com.shir0o.tickalarm.core.TickProject
import com.shir0o.tickalarm.core.TickProjectData
import com.shir0o.tickalarm.core.TickTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AuthExpiredException : IOException("TickTick sign-in expired")

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/** TickTick Open API OAuth: https://developer.ticktick.com */
object TickTickAuth {
    private const val AUTHORIZE_URL = "https://ticktick.com/oauth/authorize"
    private const val TOKEN_URL = "https://ticktick.com/oauth/token"
    private const val SCOPE = "tasks:read"

    fun authorizeUri(clientId: String, redirectUri: String, state: String): Uri =
        Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("state", state)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .build()

    suspend fun exchangeCode(creds: Credentials, code: String): TokenResponse = token(
        creds,
        mapOf(
            "code" to code,
            "grant_type" to "authorization_code",
            "scope" to SCOPE,
            "redirect_uri" to creds.redirectUri,
        ),
    )

    suspend fun refresh(creds: Credentials, refreshToken: String): TokenResponse = token(
        creds,
        mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken, "scope" to SCOPE),
    )

    private suspend fun token(creds: Credentials, form: Map<String, String>): TokenResponse =
        withContext(Dispatchers.IO) {
            val body = form.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
            val basic = Base64.encodeToString(
                "${creds.clientId}:${creds.clientSecret}".toByteArray(), Base64.NO_WRAP,
            )
            val conn = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Basic $basic")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200).orEmpty()
                    throw IOException("TickTick sign-in failed (HTTP $code). $detail")
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                TickJson.decodeFromString(TokenResponse.serializer(), text)
            } finally {
                conn.disconnect()
            }
        }
}

/** Reads projects and tasks with a bearer token. Read-only. */
class TickTickClient(private val token: String) {
    suspend fun projects(): List<TickProject> =
        TickJson.decodeFromString<List<TickProject>>(get("/open/v1/project"))

    suspend fun projectTasks(projectId: String): List<TickTask> {
        val path = "/open/v1/project/${URLEncoder.encode(projectId, "UTF-8")}/data"
        return TickJson.decodeFromString(TickProjectData.serializer(), get(path)).tasks.orEmpty()
    }

    /** Every open task in the inbox and in each open project. A project that fails is skipped. */
    suspend fun allTasks(): FetchResult {
        val ids = listOf("inbox") + projects().filter { it.closed != true }.map { it.id }
        val tasks = mutableListOf<TickTask>()
        var failed = 0
        for (id in ids.distinct()) {
            try {
                tasks += projectTasks(id)
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: IOException) {
                failed++
            }
        }
        return FetchResult(tasks.distinctBy { it.id }, failed)
    }

    data class FetchResult(val tasks: List<TickTask>, val failedProjects: Int)

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            when (val code = conn.responseCode) {
                401 -> throw AuthExpiredException()
                in 200..299 -> conn.inputStream.bufferedReader().use { it.readText() }
                else -> throw IOException("TickTick returned HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val BASE_URL = "https://api.ticktick.com"
    }
}
