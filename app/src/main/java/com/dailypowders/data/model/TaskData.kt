package com.dailypowders.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskDataFile(
    val schemaVersion: Int = 1,
    val dayResetHour: Int = 3,
    val dayResetMinute: Int = 33,
    val debugFeaturesEnabled: Boolean = false,
    val triggers: List<Trigger> = emptyList(),
    val dailyState: DailyState = DailyState()
)

@Serializable
data class Trigger(
    val id: String,
    val title: String = "",
    val happensEvery: HappensEvery = HappensEvery.DAILY,
    @SerialName("when") val when_: TimeOfDay? = null,
    val weekDay: Int? = null,
    val monthDay: Int? = null,
    val tasks: List<Task> = emptyList()
)

@Serializable
enum class HappensEvery {
    DAILY, WEEKLY, MONTHLY, MANUALLY
}

@Serializable
data class TimeOfDay(
    val hour: Int,
    val minute: Int
)

@Serializable
data class Task(
    val id: String,
    val title: String,
    val expiresHours: Int? = null
)

@Serializable
data class DailyState(
    val date: String = "",
    val activatedTriggers: List<String> = emptyList(),
    val completedTaskIds: List<String> = emptyList(),
    val expiredTaskIds: List<String> = emptyList(),
    val manualTriggerCounts: Map<String, Int> = emptyMap()
)
