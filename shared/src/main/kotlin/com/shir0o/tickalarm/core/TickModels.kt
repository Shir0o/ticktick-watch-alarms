package com.shir0o.tickalarm.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A TickTick project as returned by `GET /open/v1/project`. */
@Serializable
data class TickProject(
    val id: String,
    val name: String? = null,
    val closed: Boolean? = null,
    val kind: String? = null,
)

/** The subset of a TickTick task that matters for alarms. Unknown fields are ignored. */
@Serializable
data class TickTask(
    val id: String,
    val projectId: String? = null,
    val title: String? = null,
    val startDate: String? = null,
    val dueDate: String? = null,
    val isAllDay: Boolean? = null,
    val timeZone: String? = null,
    val reminders: List<String>? = null,
    /** 0 = open, 2 = completed. */
    val status: Int? = null,
    /** "TEXT", "CHECKLIST" or "NOTE". */
    val kind: String? = null,
)

/** Response of `GET /open/v1/project/{id}/data`. */
@Serializable
data class TickProjectData(
    val tasks: List<TickTask>? = null,
)

/** One alarm to ring, on the phone and on the watch. */
@Serializable
data class PlannedAlarm(
    /** Stable id: task id plus fire time, so a moved reminder becomes a new alarm. */
    val id: String,
    val taskId: String,
    val projectId: String,
    val title: String,
    val fireAtMillis: Long,
)

/** Data Layer paths shared by the phone and the watch app. */
object Paths {
    const val ALARMS = "/tickalarm/alarms"
    const val DISMISS = "/tickalarm/dismiss"
    const val TEST = "/tickalarm/test"
    const val KEY_JSON = "json"
    const val KEY_SENT_AT = "sentAt"
}

val TickJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

object AlarmCodec {
    fun encodeList(alarms: List<PlannedAlarm>): String = TickJson.encodeToString(alarms)
    fun decodeList(json: String): List<PlannedAlarm> =
        runCatching { TickJson.decodeFromString<List<PlannedAlarm>>(json) }.getOrDefault(emptyList())
    fun encode(alarm: PlannedAlarm): String = TickJson.encodeToString(alarm)
    fun decode(json: String): PlannedAlarm? =
        runCatching { TickJson.decodeFromString<PlannedAlarm>(json) }.getOrNull()
}
