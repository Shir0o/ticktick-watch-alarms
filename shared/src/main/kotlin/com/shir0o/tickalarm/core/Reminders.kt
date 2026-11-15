package com.shir0o.tickalarm.core

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/** Parses TickTick's date strings, e.g. `2026-11-19T10:00:00-0800` or `2025-10-05T20:17:18.063+0000`. */
object TickDates {
    private val tickFormat: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
        .optionalEnd()
        .appendOffset("+HHMM", "+0000")
        .toFormatter()

    fun parse(value: String?): OffsetDateTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value, tickFormat) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value) }.getOrNull()
    }
}

/**
 * Parses TickTick reminder triggers, which are iCalendar durations:
 * `TRIGGER:PT0S` (on time), `TRIGGER:-PT15M` (15 minutes early),
 * `TRIGGER:P0DT9H0M0S` (all-day task, 9 AM that day), `TRIGGER:-P0DT15H0M0S` (9 AM the day before).
 */
object Triggers {
    private val pattern = Regex(
        """^TRIGGER:([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): Duration? {
        val match = pattern.matchEntire(raw.trim()) ?: return null
        val (sign, weeks, days, hours, minutes, seconds) = match.destructured
        if (listOf(weeks, days, hours, minutes, seconds).all { it.isEmpty() }) return null
        val total = Duration.ofDays(7 * weeks.num() + days.num())
            .plusHours(hours.num())
            .plusMinutes(minutes.num())
            .plusSeconds(seconds.num())
        return if (sign == "-") total.negated() else total
    }

    private fun String.num(): Long = toLongOrNull() ?: 0L
}

object Reminders {
    /** Every moment a task's reminders go off. Empty when the task has no date or no reminders. */
    fun fireTimes(task: TickTask, defaultZone: ZoneId): List<Instant> {
        val triggers = task.reminders.orEmpty().mapNotNull { Triggers.parse(it) }
        if (triggers.isEmpty()) return emptyList()
        val anchor = TickDates.parse(task.startDate) ?: TickDates.parse(task.dueDate) ?: return emptyList()
        val base: Instant = if (task.isAllDay == true) {
            // An all-day task's date is local midnight written with its own offset;
            // its reminders count from the start of that calendar day.
            val zone = task.timeZone
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: defaultZone
            anchor.toLocalDate().atStartOfDay(zone).toInstant()
        } else {
            anchor.toInstant()
        }
        return triggers.map { base.plus(it) }.distinct()
    }
}

object AlarmPlanner {
    /**
     * Turns open tasks into alarms that fire between [now] minus [lookBack] and [now] plus [horizon].
     * The look-back lets an alarm still ring when its task was added a few minutes late.
     */
    fun plan(
        tasks: List<TickTask>,
        now: Instant,
        zone: ZoneId,
        horizon: Duration = Duration.ofHours(48),
        lookBack: Duration = Duration.ofMinutes(10),
    ): List<PlannedAlarm> {
        val earliest = now.minus(lookBack)
        val latest = now.plus(horizon)
        return tasks
            .asSequence()
            .filter { (it.status ?: 0) == 0 }
            .filterNot { it.kind.equals("NOTE", ignoreCase = true) }
            .flatMap { task ->
                Reminders.fireTimes(task, zone)
                    .filter { !it.isBefore(earliest) && !it.isAfter(latest) }
                    .map { at ->
                        PlannedAlarm(
                            id = "${task.id}@${at.epochSecond}",
                            taskId = task.id,
                            projectId = task.projectId.orEmpty(),
                            title = task.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Reminder",
                            fireAtMillis = at.toEpochMilli(),
                        )
                    }
            }
            .distinctBy { it.id }
            .sortedBy { it.fireAtMillis }
            .toList()
    }
}
