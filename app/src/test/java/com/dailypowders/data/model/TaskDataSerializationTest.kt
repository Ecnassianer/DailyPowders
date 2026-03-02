package com.dailypowders.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TaskDataSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun `Trigger when_ serializes as 'when' not 'when_'`() {
        val trigger = Trigger(
            id = "t1",
            title = "Test",
            when_ = TimeOfDay(8, 30)
        )
        val serialized = json.encodeToString(Trigger.serializer(), trigger)
        assertTrue("JSON should contain 'when' key", serialized.contains("\"when\""))
        assertFalse("JSON should NOT contain 'when_' key", serialized.contains("\"when_\""))
    }

    @Test
    fun `Trigger when_ deserializes from 'when' key`() {
        val jsonString = """{"id":"t1","title":"Test","happensEvery":"DAILY","when":{"hour":8,"minute":30},"tasks":[]}"""
        val trigger = json.decodeFromString<Trigger>(jsonString)
        assertNotNull(trigger.when_)
        assertEquals(8, trigger.when_!!.hour)
        assertEquals(30, trigger.when_!!.minute)
    }

    @Test
    fun `TaskDataFile round-trips correctly`() {
        val data = TaskDataFile(
            schemaVersion = 1,
            dayResetHour = 3,
            dayResetMinute = 33,
            triggers = listOf(
                Trigger(
                    id = "t1",
                    title = "Morning",
                    happensEvery = HappensEvery.DAILY,
                    when_ = TimeOfDay(6, 40),
                    tasks = listOf(
                        Task(id = "caffeine", title = "Caffeine", expiresHours = 9),
                        Task(id = "fiber", title = "Fiber", expiresHours = null)
                    )
                ),
                Trigger(
                    id = "t2",
                    title = "Night",
                    happensEvery = HappensEvery.MANUALLY,
                    tasks = listOf(
                        Task(id = "floss", title = "Floss")
                    )
                )
            ),
            dailyState = DailyState(
                date = "2026-03-01",
                activatedTriggers = listOf("t1"),
                completedTaskIds = listOf("caffeine"),
                expiredTaskIds = listOf(),
                manualTriggerCounts = mapOf("t2" to 2)
            )
        )

        val serialized = json.encodeToString(TaskDataFile.serializer(), data)
        val deserialized = json.decodeFromString<TaskDataFile>(serialized)

        assertEquals(data.schemaVersion, deserialized.schemaVersion)
        assertEquals(data.dayResetHour, deserialized.dayResetHour)
        assertEquals(data.dayResetMinute, deserialized.dayResetMinute)
        assertEquals(data.triggers.size, deserialized.triggers.size)
        assertEquals(data.triggers[0].when_?.hour, deserialized.triggers[0].when_?.hour)
        assertEquals(data.triggers[0].when_?.minute, deserialized.triggers[0].when_?.minute)
        assertEquals(data.dailyState.date, deserialized.dailyState.date)
        assertEquals(data.dailyState.completedTaskIds, deserialized.dailyState.completedTaskIds)
        assertEquals(data.dailyState.manualTriggerCounts, deserialized.dailyState.manualTriggerCounts)
    }

    @Test
    fun `HappensEvery values serialize correctly`() {
        for (value in HappensEvery.entries) {
            val trigger = Trigger(id = "t1", happensEvery = value)
            val serialized = json.encodeToString(Trigger.serializer(), trigger)
            val deserialized = json.decodeFromString<Trigger>(serialized)
            assertEquals(value, deserialized.happensEvery)
        }
    }

    @Test
    fun `null when_ serializes correctly`() {
        val trigger = Trigger(id = "t1", when_ = null)
        val serialized = json.encodeToString(Trigger.serializer(), trigger)
        val deserialized = json.decodeFromString<Trigger>(serialized)
        assertNull(deserialized.when_)
    }

    @Test
    fun `null expiresHours serializes correctly`() {
        val task = Task(id = "task1", title = "Test", expiresHours = null)
        val serialized = json.encodeToString(Task.serializer(), task)
        val deserialized = json.decodeFromString<Task>(serialized)
        assertNull(deserialized.expiresHours)
    }

    @Test
    fun `default TaskDataFile has correct defaults`() {
        val data = TaskDataFile()
        assertEquals(1, data.schemaVersion)
        assertEquals(3, data.dayResetHour)
        assertEquals(33, data.dayResetMinute)
        assertTrue(data.triggers.isEmpty())
        assertEquals("", data.dailyState.date)
    }
}
