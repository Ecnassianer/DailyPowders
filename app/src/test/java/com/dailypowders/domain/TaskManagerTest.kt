package com.dailypowders.domain

import com.dailypowders.data.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class TaskManagerTest {

    private lateinit var taskManager: TaskManager

    @Before
    fun setup() {
        taskManager = TaskManager()
    }

    // =========================================================================
    // Example Usage #1: York Morning Trigger
    // =========================================================================

    @Test
    fun `example1 - installing at 7pm activates trigger and expires caffeine`() {
        val trigger = Trigger(
            id = "t1",
            title = "York Morning",
            happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(6, 40),
            tasks = listOf(
                Task(id = "caffeine", title = "Caffeine", expiresHours = 9),
                Task(id = "fiber", title = "Fiber", expiresHours = null)
            )
        )
        var data = TaskDataFile(triggers = listOf(trigger))

        val now = LocalDateTime.of(2026, 3, 1, 19, 0)
        data = taskManager.ensureFreshState(data, now)
        data = taskManager.activateTrigger(data, "t1", now)

        assertTrue("t1" in data.dailyState.activatedTriggers)
        assertTrue("caffeine" in data.dailyState.expiredTaskIds)
        assertFalse("fiber" in data.dailyState.expiredTaskIds)
        assertFalse("fiber" in data.dailyState.completedTaskIds)

        val active = taskManager.getActiveTasks(data)
        assertEquals(1, active.size)
        assertEquals("Fiber", active[0].first.title)

        val expired = taskManager.getExpiredTasks(data)
        assertEquals(1, expired.size)
        assertEquals("Caffeine", expired[0].first.title)
    }

    // =========================================================================
    // Example Usage #2: Nightly Triggers
    // =========================================================================

    @Test
    fun `example2 - nightly trigger not activated before its time`() {
        val trigger = Trigger(
            id = "night1", title = "York Night 1",
            happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(21, 45),
            tasks = listOf(
                Task(id = "pockets", title = "Empty Pockets"),
                Task(id = "pajamas", title = "Pajamas"),
                Task(id = "blanket", title = "Fix Blanket")
            )
        )
        var data = TaskDataFile(triggers = listOf(trigger))
        val now = LocalDateTime.of(2026, 3, 1, 13, 0)
        data = taskManager.ensureFreshState(data, now)

        val active = taskManager.getActiveTasks(data)
        assertEquals(0, active.size)
    }

    @Test
    fun `example2 - manual trigger activation posts all tasks`() {
        val trigger = Trigger(
            id = "night2", title = "York Night 2",
            happensEvery = HappensEvery.MANUALLY,
            tasks = listOf(
                Task(id = "teapot", title = "Teapot"),
                Task(id = "agz", title = "AGZ"),
                Task(id = "floss", title = "Floss"),
                Task(id = "toothbrush", title = "Toothbrush"),
                Task(id = "nose", title = "Nose thing"),
                Task(id = "skin", title = "Skin Treatment"),
                Task(id = "retina", title = "Retina"),
                Task(id = "eyedrops", title = "Eye drops"),
                Task(id = "lotion", title = "Triple hand lotion")
            )
        )
        var data = TaskDataFile(triggers = listOf(trigger))
        val now = LocalDateTime.of(2026, 3, 1, 22, 45)
        data = taskManager.ensureFreshState(data, now)
        data = taskManager.activateTrigger(data, "night2", now)

        val active = taskManager.getActiveTasks(data)
        assertEquals(9, active.size)
        assertEquals(1, data.dailyState.manualTriggerCounts["night2"])
    }

    @Test
    fun `example2 - completing tasks moves them to completed`() {
        val trigger = Trigger(
            id = "night2", title = "York Night 2",
            happensEvery = HappensEvery.MANUALLY,
            tasks = listOf(
                Task(id = "lotion", title = "Triple hand lotion"),
                Task(id = "retina", title = "Retina"),
                Task(id = "floss", title = "Floss"),
                Task(id = "toothbrush", title = "Toothbrush")
            )
        )
        var data = TaskDataFile(triggers = listOf(trigger))
        val now = LocalDateTime.of(2026, 3, 1, 22, 45)
        data = taskManager.ensureFreshState(data, now)
        data = taskManager.activateTrigger(data, "night2", now)

        data = taskManager.completeTask(data, "lotion")
        data = taskManager.completeTask(data, "retina")
        assertEquals(2, taskManager.getActiveTasks(data).size)
        assertEquals(2, taskManager.getCompletedTasks(data).size)

        data = taskManager.completeTask(data, "floss")
        assertEquals(3, taskManager.getCompletedTasks(data).size)
        assertEquals(1, taskManager.getActiveTasks(data).size)
    }

    // =========================================================================
    // Example Usage #3: Trigger editing
    // =========================================================================

    @Test
    fun `example3 - deleting a task from trigger removes it`() {
        val trigger = Trigger(
            id = "night2", title = "York Night 2",
            happensEvery = HappensEvery.MANUALLY,
            tasks = listOf(
                Task(id = "skin", title = "Skin Treatment"),
                Task(id = "rinse", title = "Mouth Rinse", expiresHours = 1)
            )
        )
        var data = TaskDataFile(triggers = listOf(trigger))
        val updatedTrigger = trigger.copy(tasks = trigger.tasks.filter { it.id != "skin" })
        data = taskManager.updateTrigger(data, updatedTrigger)

        val updatedFromData = data.triggers.find { it.id == "night2" }!!
        assertEquals(1, updatedFromData.tasks.size)
        assertEquals("Mouth Rinse", updatedFromData.tasks[0].title)
    }

    // =========================================================================
    // Day Reset & Effective Date
    // =========================================================================

    @Test
    fun `day reset clears daily state`() {
        var data = TaskDataFile(
            dailyState = DailyState(
                date = "2026-02-28",
                activatedTriggers = listOf("t1"),
                completedTaskIds = listOf("task1", "task2"),
                expiredTaskIds = listOf("task3")
            )
        )
        val now = LocalDateTime.of(2026, 3, 1, 4, 0)
        data = taskManager.ensureFreshState(data, now)

        assertEquals("2026-03-01", data.dailyState.date)
        assertTrue(data.dailyState.activatedTriggers.isEmpty())
        assertTrue(data.dailyState.completedTaskIds.isEmpty())
        assertTrue(data.dailyState.expiredTaskIds.isEmpty())
    }

    @Test
    fun `before day reset time uses previous day`() {
        var data = TaskDataFile(dailyState = DailyState(date = "2026-03-01"))
        val now = LocalDateTime.of(2026, 3, 2, 2, 0) // 2 AM, before 3:33 AM reset
        data = taskManager.ensureFreshState(data, now)
        assertEquals("2026-03-01", data.dailyState.date)
    }

    @Test
    fun `effective date at exactly dayResetTime is new day`() {
        val dayResetTime = LocalTime.of(3, 33)
        val now = LocalDateTime.of(2026, 3, 2, 3, 33)
        val result = taskManager.effectiveDate(now, dayResetTime)
        assertEquals(now.toLocalDate(), result) // 3:33 AM is new day
    }

    @Test
    fun `effective date one minute before dayResetTime is previous day`() {
        val dayResetTime = LocalTime.of(3, 33)
        val now = LocalDateTime.of(2026, 3, 2, 3, 32)
        val result = taskManager.effectiveDate(now, dayResetTime)
        assertEquals(now.toLocalDate().minusDays(1), result) // 3:32 AM is still yesterday
    }

    // =========================================================================
    // shouldTriggerFire with dayResetTime (bug fix tests)
    // =========================================================================

    @Test
    fun `shouldTriggerFire WEEKLY uses effective date, not calendar date`() {
        // It's 2:00 AM on Tuesday March 3, but dayReset is at 3:33 AM
        // So effective date is Monday March 2. Monday = DayOfWeek.MONDAY = 1
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.WEEKLY,
            when_ = TimeOfDay(8, 0), weekDay = 1 // Monday
        )
        val dayResetTime = LocalTime.of(3, 33)
        val now = LocalDateTime.of(2026, 3, 3, 2, 0) // Tuesday 2 AM

        assertTrue(taskManager.shouldTriggerFire(trigger, now, dayResetTime))
    }

    @Test
    fun `shouldTriggerFire WEEKLY after dayReset uses actual date`() {
        // 4:00 AM on Tuesday March 3, after dayReset. Effective date = Tuesday
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.WEEKLY,
            when_ = TimeOfDay(8, 0), weekDay = 1 // Monday
        )
        val dayResetTime = LocalTime.of(3, 33)
        val now = LocalDateTime.of(2026, 3, 3, 4, 0) // Tuesday 4 AM

        assertFalse(taskManager.shouldTriggerFire(trigger, now, dayResetTime))
    }

    @Test
    fun `shouldTriggerFire MONTHLY uses effective date`() {
        // 2:00 AM on March 2, dayReset at 3:33 AM. Effective date = March 1
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.MONTHLY,
            when_ = TimeOfDay(8, 0), monthDay = 1
        )
        val dayResetTime = LocalTime.of(3, 33)
        val now = LocalDateTime.of(2026, 3, 2, 2, 0)

        assertTrue(taskManager.shouldTriggerFire(trigger, now, dayResetTime))
    }

    @Test
    fun `shouldTriggerFire returns false for MANUALLY`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.MANUALLY,
            when_ = TimeOfDay(8, 0)
        )
        val dayResetTime = LocalTime.of(3, 33)
        val now = LocalDateTime.of(2026, 3, 1, 9, 0)

        assertFalse(taskManager.shouldTriggerFire(trigger, now, dayResetTime))
    }

    @Test
    fun `shouldTriggerFire returns false for invalid weekDay`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.WEEKLY,
            when_ = TimeOfDay(8, 0), weekDay = 9 // invalid
        )
        val dayResetTime = LocalTime.of(3, 33)
        val now = LocalDateTime.of(2026, 3, 1, 9, 0)

        assertFalse(taskManager.shouldTriggerFire(trigger, now, dayResetTime))
    }

    @Test
    fun `shouldTriggerFire returns false for null when_`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = null
        )
        val dayResetTime = LocalTime.of(3, 33)
        val now = LocalDateTime.of(2026, 3, 1, 9, 0)

        assertFalse(taskManager.shouldTriggerFire(trigger, now, dayResetTime))
    }

    // =========================================================================
    // hasTriggerTimePassed (via getTriggersToFireNow)
    // =========================================================================

    @Test
    fun `getTriggersToFireNow returns trigger when time has passed`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(8, 0),
            tasks = listOf(Task(id = "task1", title = "Task"))
        )
        val data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(date = "2026-03-01")
        )
        val now = LocalDateTime.of(2026, 3, 1, 9, 0) // 9 AM, after 8 AM trigger

        val result = taskManager.getTriggersToFireNow(data, now)
        assertEquals(1, result.size)
    }

    @Test
    fun `getTriggersToFireNow skips already-activated triggers`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(8, 0),
            tasks = listOf(Task(id = "task1", title = "Task"))
        )
        val data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(
                date = "2026-03-01",
                activatedTriggers = listOf("t1")
            )
        )
        val now = LocalDateTime.of(2026, 3, 1, 9, 0)

        val result = taskManager.getTriggersToFireNow(data, now)
        assertEquals(0, result.size)
    }

    @Test
    fun `getTriggersToFireNow skips triggers whose time has not passed`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(14, 0),
            tasks = listOf(Task(id = "task1", title = "Task"))
        )
        val data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(date = "2026-03-01")
        )
        val now = LocalDateTime.of(2026, 3, 1, 9, 0) // 9 AM, before 2 PM trigger

        val result = taskManager.getTriggersToFireNow(data, now)
        assertEquals(0, result.size)
    }

    @Test
    fun `hasTriggerTimePassed handles pre-dayReset trigger correctly`() {
        // Trigger at 2:00 AM, dayReset at 3:33 AM
        // If it's 2:30 AM (before dayReset), trigger time has passed -> fires
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(2, 0),
            tasks = listOf(Task(id = "task1", title = "Task"))
        )
        val data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(date = "2026-02-28") // yesterday's effective date
        )
        val now = LocalDateTime.of(2026, 3, 1, 2, 30) // 2:30 AM

        val result = taskManager.getTriggersToFireNow(data, now)
        assertEquals(1, result.size)
    }

    @Test
    fun `hasTriggerTimePassed - pre-dayReset trigger not fired after dayReset`() {
        // Trigger at 2:00 AM, dayReset at 3:33 AM
        // If it's 4:00 AM (after dayReset), this trigger belongs to next day's pre-reset window
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(2, 0),
            tasks = listOf(Task(id = "task1", title = "Task"))
        )
        val data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(date = "2026-03-01") // today's effective date
        )
        val now = LocalDateTime.of(2026, 3, 1, 4, 0)

        val result = taskManager.getTriggersToFireNow(data, now)
        assertEquals(0, result.size)
    }

    // =========================================================================
    // Task Expiration
    // =========================================================================

    @Test
    fun `getExpirationTime returns null for no expiresHours`() {
        val trigger = Trigger(id = "t1", when_ = TimeOfDay(8, 0))
        val task = Task(id = "task1", title = "Task", expiresHours = null)
        val data = TaskDataFile(triggers = listOf(trigger))
        val now = LocalDateTime.of(2026, 3, 1, 9, 0)

        assertNull(taskManager.getExpirationTime(task, trigger, data, now))
    }

    @Test
    fun `getExpirationTime returns null for zero expiresHours`() {
        val trigger = Trigger(id = "t1", when_ = TimeOfDay(8, 0))
        val task = Task(id = "task1", title = "Task", expiresHours = 0)
        val data = TaskDataFile(triggers = listOf(trigger))
        val now = LocalDateTime.of(2026, 3, 1, 9, 0)

        assertNull(taskManager.getExpirationTime(task, trigger, data, now))
    }

    @Test
    fun `getExpirationTime returns null for negative expiresHours`() {
        val trigger = Trigger(id = "t1", when_ = TimeOfDay(8, 0))
        val task = Task(id = "task1", title = "Task", expiresHours = -1)
        val data = TaskDataFile(triggers = listOf(trigger))
        val now = LocalDateTime.of(2026, 3, 1, 9, 0)

        assertNull(taskManager.getExpirationTime(task, trigger, data, now))
    }

    @Test
    fun `getExpirationTime returns correct time`() {
        val trigger = Trigger(id = "t1", when_ = TimeOfDay(6, 40))
        val task = Task(id = "caffeine", title = "Caffeine", expiresHours = 9)
        val data = TaskDataFile(triggers = listOf(trigger))
        val now = LocalDateTime.of(2026, 3, 1, 10, 0) // 10 AM

        val expiration = taskManager.getExpirationTime(task, trigger, data, now)
        assertNotNull(expiration)
        // 6:40 AM + 9 hours = 3:40 PM
        assertEquals(LocalDateTime.of(2026, 3, 1, 15, 40), expiration)
    }

    @Test
    fun `isTaskExpired returns true after expiration time`() {
        val trigger = Trigger(id = "t1", when_ = TimeOfDay(6, 40))
        val task = Task(id = "caffeine", title = "Caffeine", expiresHours = 9)
        val data = TaskDataFile(triggers = listOf(trigger))
        val now = LocalDateTime.of(2026, 3, 1, 16, 0) // 4 PM > 3:40 PM

        assertTrue(taskManager.isTaskExpired(task, trigger, data, now))
    }

    @Test
    fun `isTaskExpired returns false before expiration time`() {
        val trigger = Trigger(id = "t1", when_ = TimeOfDay(6, 40))
        val task = Task(id = "caffeine", title = "Caffeine", expiresHours = 9)
        val data = TaskDataFile(triggers = listOf(trigger))
        val now = LocalDateTime.of(2026, 3, 1, 10, 0) // 10 AM < 3:40 PM

        assertFalse(taskManager.isTaskExpired(task, trigger, data, now))
    }

    @Test
    fun `isTaskExpired returns false when no expiresHours set`() {
        val trigger = Trigger(id = "t1", when_ = TimeOfDay(6, 40))
        val task = Task(id = "fiber", title = "Fiber", expiresHours = null)
        val data = TaskDataFile(triggers = listOf(trigger))
        val now = LocalDateTime.of(2026, 3, 1, 23, 0)

        assertFalse(taskManager.isTaskExpired(task, trigger, data, now))
    }

    // =========================================================================
    // Uncomplete
    // =========================================================================

    @Test
    fun `uncompleting a task returns it to active or expired`() {
        val trigger = Trigger(
            id = "t1", title = "Test",
            happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(6, 40),
            tasks = listOf(Task(id = "caffeine", title = "Caffeine", expiresHours = 9))
        )
        var data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(
                date = "2026-03-01",
                activatedTriggers = listOf("t1"),
                completedTaskIds = listOf("caffeine")
            )
        )
        val now = LocalDateTime.of(2026, 3, 1, 16, 0) // 4 PM, caffeine expired at 3:40 PM
        data = taskManager.uncompleteTask(data, "caffeine", now)

        assertFalse("caffeine" in data.dailyState.completedTaskIds)
        assertTrue("caffeine" in data.dailyState.expiredTaskIds)
    }

    @Test
    fun `uncompleting a non-expired task returns it to active`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(6, 40),
            tasks = listOf(Task(id = "fiber", title = "Fiber", expiresHours = null))
        )
        var data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(
                date = "2026-03-01",
                activatedTriggers = listOf("t1"),
                completedTaskIds = listOf("fiber")
            )
        )
        val now = LocalDateTime.of(2026, 3, 1, 16, 0)
        data = taskManager.uncompleteTask(data, "fiber", now)

        assertFalse("fiber" in data.dailyState.completedTaskIds)
        assertFalse("fiber" in data.dailyState.expiredTaskIds)
        assertEquals(1, taskManager.getActiveTasks(data).size)
    }

    @Test
    fun `uncompleting a task not in completed list is no-op`() {
        val data = TaskDataFile(
            dailyState = DailyState(date = "2026-03-01")
        )
        val now = LocalDateTime.of(2026, 3, 1, 9, 0)
        val result = taskManager.uncompleteTask(data, "nonexistent", now)
        assertEquals(data, result)
    }

    // =========================================================================
    // Complete idempotency
    // =========================================================================

    @Test
    fun `completing already-completed task is no-op`() {
        val data = TaskDataFile(
            dailyState = DailyState(
                date = "2026-03-01",
                completedTaskIds = listOf("task1")
            )
        )
        val result = taskManager.completeTask(data, "task1")
        assertEquals(data, result)
    }

    @Test
    fun `completing expired task moves it from expired to completed`() {
        var data = TaskDataFile(
            dailyState = DailyState(
                date = "2026-03-01",
                expiredTaskIds = listOf("task1")
            )
        )
        data = taskManager.completeTask(data, "task1")
        assertTrue("task1" in data.dailyState.completedTaskIds)
        assertFalse("task1" in data.dailyState.expiredTaskIds)
    }

    // =========================================================================
    // Trigger Deletion
    // =========================================================================

    @Test
    fun `deleting a trigger removes its tasks from state`() {
        var data = TaskDataFile(
            triggers = listOf(
                Trigger(id = "t1", title = "Test",
                    tasks = listOf(Task(id = "task1", title = "Task 1")))
            ),
            dailyState = DailyState(
                date = "2026-03-01",
                activatedTriggers = listOf("t1"),
                completedTaskIds = listOf("task1")
            )
        )
        data = taskManager.deleteTrigger(data, "t1")

        assertTrue(data.triggers.isEmpty())
        assertFalse("t1" in data.dailyState.activatedTriggers)
        assertFalse("task1" in data.dailyState.completedTaskIds)
    }

    @Test
    fun `deleting nonexistent trigger is no-op`() {
        val data = TaskDataFile(
            triggers = listOf(
                Trigger(id = "t1", title = "Test", tasks = emptyList())
            )
        )
        val result = taskManager.deleteTrigger(data, "nonexistent")
        assertEquals(data, result)
    }

    @Test
    fun `deleting trigger cleans up manual trigger counts`() {
        var data = TaskDataFile(
            triggers = listOf(
                Trigger(id = "t1", happensEvery = HappensEvery.MANUALLY,
                    tasks = listOf(Task(id = "task1", title = "Task")))
            ),
            dailyState = DailyState(
                date = "2026-03-01",
                activatedTriggers = listOf("t1"),
                manualTriggerCounts = mapOf("t1" to 3)
            )
        )
        data = taskManager.deleteTrigger(data, "t1")
        assertFalse("t1" in data.dailyState.manualTriggerCounts)
    }

    // =========================================================================
    // Activation idempotency
    // =========================================================================

    @Test
    fun `activating already-activated trigger does not duplicate`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(8, 0),
            tasks = listOf(Task(id = "task1", title = "Task"))
        )
        var data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(
                date = "2026-03-01",
                activatedTriggers = listOf("t1")
            )
        )
        val now = LocalDateTime.of(2026, 3, 1, 9, 0)
        data = taskManager.activateTrigger(data, "t1", now)

        assertEquals(1, data.dailyState.activatedTriggers.count { it == "t1" })
    }

    @Test
    fun `manual trigger increments count on each activation`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.MANUALLY,
            tasks = listOf(Task(id = "task1", title = "Task"))
        )
        var data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(date = "2026-03-01")
        )
        val now = LocalDateTime.of(2026, 3, 1, 9, 0)

        data = taskManager.activateTrigger(data, "t1", now)
        assertEquals(1, data.dailyState.manualTriggerCounts["t1"])

        data = taskManager.activateTrigger(data, "t1", now)
        assertEquals(2, data.dailyState.manualTriggerCounts["t1"])
    }

    // =========================================================================
    // updateExpirations
    // =========================================================================

    @Test
    fun `updateExpirations expires newly-due tasks`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(8, 0),
            tasks = listOf(
                Task(id = "task1", title = "Task 1", expiresHours = 2),
                Task(id = "task2", title = "Task 2", expiresHours = null)
            )
        )
        var data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(
                date = "2026-03-01",
                activatedTriggers = listOf("t1")
            )
        )
        val now = LocalDateTime.of(2026, 3, 1, 11, 0) // 11 AM, 8+2=10 AM expired

        data = taskManager.updateExpirations(data, now)
        assertTrue("task1" in data.dailyState.expiredTaskIds)
        assertFalse("task2" in data.dailyState.expiredTaskIds)
    }

    @Test
    fun `updateExpirations skips completed tasks`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(8, 0),
            tasks = listOf(
                Task(id = "task1", title = "Task 1", expiresHours = 2)
            )
        )
        var data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(
                date = "2026-03-01",
                activatedTriggers = listOf("t1"),
                completedTaskIds = listOf("task1")
            )
        )
        val now = LocalDateTime.of(2026, 3, 1, 11, 0)
        data = taskManager.updateExpirations(data, now)

        assertFalse("task1" in data.dailyState.expiredTaskIds)
    }

    @Test
    fun `updateExpirations skips non-activated triggers`() {
        val trigger = Trigger(
            id = "t1", happensEvery = HappensEvery.DAILY,
            when_ = TimeOfDay(8, 0),
            tasks = listOf(
                Task(id = "task1", title = "Task 1", expiresHours = 2)
            )
        )
        var data = TaskDataFile(
            triggers = listOf(trigger),
            dailyState = DailyState(date = "2026-03-01")
        )
        val now = LocalDateTime.of(2026, 3, 1, 11, 0)
        data = taskManager.updateExpirations(data, now)

        assertFalse("task1" in data.dailyState.expiredTaskIds)
    }

    // =========================================================================
    // findTriggerForTask
    // =========================================================================

    @Test
    fun `findTriggerForTask returns correct trigger`() {
        val t1 = Trigger(id = "t1", tasks = listOf(Task(id = "a", title = "A")))
        val t2 = Trigger(id = "t2", tasks = listOf(Task(id = "b", title = "B")))
        val data = TaskDataFile(triggers = listOf(t1, t2))

        assertEquals("t1", taskManager.findTriggerForTask(data, "a")?.id)
        assertEquals("t2", taskManager.findTriggerForTask(data, "b")?.id)
        assertNull(taskManager.findTriggerForTask(data, "c"))
    }

    // =========================================================================
    // Add/Update trigger
    // =========================================================================

    @Test
    fun `addTrigger appends to list`() {
        val data = TaskDataFile()
        val trigger = Trigger(id = "new", title = "New Trigger")
        val result = taskManager.addTrigger(data, trigger)
        assertEquals(1, result.triggers.size)
        assertEquals("new", result.triggers[0].id)
    }

    @Test
    fun `updateTrigger replaces matching trigger`() {
        val data = TaskDataFile(
            triggers = listOf(
                Trigger(id = "t1", title = "Old Title"),
                Trigger(id = "t2", title = "Other")
            )
        )
        val result = taskManager.updateTrigger(data, Trigger(id = "t1", title = "New Title"))
        assertEquals("New Title", result.triggers.find { it.id == "t1" }?.title)
        assertEquals("Other", result.triggers.find { it.id == "t2" }?.title)
    }
}
