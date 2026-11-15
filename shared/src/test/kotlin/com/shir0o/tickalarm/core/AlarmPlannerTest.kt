package com.shir0o.tickalarm.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmPlannerTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private fun at(iso: String): Instant = Instant.parse(iso)

    @Test
    fun parsesTriggers() {
        assertEquals(Duration.ZERO, Triggers.parse("TRIGGER:PT0S"))
        assertEquals(Duration.ZERO, Triggers.parse("TRIGGER:-PT0S"))
        assertEquals(Duration.ofMinutes(-15), Triggers.parse("TRIGGER:-PT15M"))
        assertEquals(Duration.ofHours(9), Triggers.parse("TRIGGER:P0DT9H0M0S"))
        assertEquals(Duration.ofHours(-15), Triggers.parse("TRIGGER:-P0DT15H0M0S"))
        assertEquals(Duration.ofDays(-7), Triggers.parse("TRIGGER:-P1W"))
        assertNull(Triggers.parse("TRIGGER:P"))
        assertNull(Triggers.parse("nonsense"))
    }

    @Test
    fun parsesTickTickDates() {
        assertEquals(at("2026-11-19T18:00:00Z"), TickDates.parse("2026-11-19T10:00:00-0800")?.toInstant())
        assertEquals(at("2025-10-05T20:17:18.063Z"), TickDates.parse("2025-10-05T20:17:18.063+0000")?.toInstant())
        assertEquals(at("2026-11-14T22:00:00Z"), TickDates.parse("2026-11-14T22:00:00Z")?.toInstant())
        assertNull(TickDates.parse(""))
    }

    @Test
    fun timedTaskRingsOnTimeAndEarly() {
        val task = TickTask(
            id = "t1", projectId = "inbox1", title = "Ask Ramiro",
            startDate = "2026-11-15T11:00:00-0800", dueDate = "2026-11-15T11:00:00-0800",
            timeZone = "America/Los_Angeles", isAllDay = false,
            reminders = listOf("TRIGGER:PT0S", "TRIGGER:-PT15M"), status = 0,
        )
        val alarms = AlarmPlanner.plan(listOf(task), now = at("2026-11-15T12:00:00Z"), zone = la)
        assertEquals(listOf(at("2026-11-15T18:45:00Z"), at("2026-11-15T19:00:00Z")),
            alarms.map { Instant.ofEpochMilli(it.fireAtMillis) })
        assertEquals("Ask Ramiro", alarms.first().title)
        assertTrue(alarms.all { it.id.startsWith("t1@") })
    }

    @Test
    fun allDayTaskCountsFromLocalMidnight() {
        val task = TickTask(
            id = "t2", title = "Annual exam",
            startDate = "2026-12-14T00:00:00-0800", isAllDay = true, timeZone = "America/Los_Angeles",
            reminders = listOf("TRIGGER:P0DT9H0M0S", "TRIGGER:-P0DT15H0M0S"),
        )
        val fires = Reminders.fireTimes(task, la)
        assertEquals(listOf(at("2026-12-14T17:00:00Z"), at("2026-12-13T17:00:00Z")), fires)
    }

    @Test
    fun skipsCompletedNotesUndatedAndOutOfWindow() {
        val now = at("2026-11-14T21:00:00Z")
        val tasks = listOf(
            TickTask(id = "done", dueDate = "2026-11-14T15:00:00-0800", reminders = listOf("TRIGGER:PT0S"), status = 2),
            TickTask(id = "note", dueDate = "2026-11-14T15:00:00-0800", reminders = listOf("TRIGGER:PT0S"), kind = "NOTE"),
            TickTask(id = "undated", reminders = listOf("TRIGGER:PT0S")),
            TickTask(id = "noreminder", dueDate = "2026-11-14T15:00:00-0800"),
            TickTask(id = "far", dueDate = "2026-11-20T15:00:00-0800", reminders = listOf("TRIGGER:PT0S")),
            TickTask(id = "old", dueDate = "2026-11-14T12:00:00-0800", reminders = listOf("TRIGGER:PT0S")),
            TickTask(id = "late", dueDate = "2026-11-14T12:55:00-0800", reminders = listOf("TRIGGER:PT0S")),
            TickTask(id = "soon", dueDate = "2026-11-14T15:00:00-0800", reminders = listOf("TRIGGER:PT0S")),
        )
        val ids = AlarmPlanner.plan(tasks, now, la).map { it.taskId }
        assertEquals(listOf("late", "soon"), ids)
    }

    @Test
    fun decodesRealApiShape() {
        val json = """
            {"project":{"id":"inbox115"},"tasks":[{"id":"6af7","projectId":"inbox115","title":"2fa change",
            "startDate":"2026-11-14T22:00:00+0000","dueDate":"2026-11-14T22:00:00+0000","timeZone":"",
            "isAllDay":false,"priority":0,"reminders":["TRIGGER:PT0S"],"status":0,"kind":"TEXT","items":null,
            "repeatFlag":"","etag":"7nf1igp0"}],"columns":[]}
        """.trimIndent()
        val data = TickJson.decodeFromString(TickProjectData.serializer(), json)
        val alarms = AlarmPlanner.plan(data.tasks.orEmpty(), at("2026-11-14T21:00:00Z"), la)
        assertEquals(1, alarms.size)
        assertEquals(at("2026-11-14T22:00:00Z").toEpochMilli(), alarms.single().fireAtMillis)
    }

    @Test
    fun codecRoundTrips() {
        val list = listOf(PlannedAlarm("a@1", "a", "p", "Title", 1000L))
        assertEquals(list, AlarmCodec.decodeList(AlarmCodec.encodeList(list)))
        assertEquals(emptyList(), AlarmCodec.decodeList("not json"))
    }
}
