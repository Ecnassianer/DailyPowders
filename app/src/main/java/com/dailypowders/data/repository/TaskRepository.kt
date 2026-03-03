package com.dailypowders.data.repository

import android.content.Context
import com.dailypowders.data.model.TaskDataFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TaskRepository(private val context: Context) {

    companion object {
        private const val FILE_NAME = "tasks.json"
        private const val CURRENT_SCHEMA_VERSION = 1

        // Shared lock for all repository instances within this process
        private val LOCK = Any()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val dataDir: File get() = context.filesDir

    private val dataFile: File get() = File(dataDir, FILE_NAME)
    private val tmpFile: File get() = File(dataDir, "$FILE_NAME.tmp")
    private val bakFile: File get() = File(dataDir, "$FILE_NAME.bak")

    fun load(): TaskDataFile {
        synchronized(LOCK) {
            return loadInternal()
        }
    }

    fun save(data: TaskDataFile) {
        synchronized(LOCK) {
            saveInternal(data)
        }
    }

    /**
     * Atomically load, transform, and save data.
     * Prevents race conditions between concurrent callers.
     */
    fun update(transform: (TaskDataFile) -> TaskDataFile) {
        synchronized(LOCK) {
            val data = loadInternal()
            val updated = transform(data)
            saveInternal(updated)
        }
    }

    private fun loadInternal(): TaskDataFile {
        val file = dataFile
        if (!file.exists()) {
            // Try backup file
            if (bakFile.exists()) {
                return tryParseFile(bakFile) ?: TaskDataFile()
            }
            return TaskDataFile()
        }

        val result = tryParseFile(file)
        if (result != null) return result

        // Primary file is corrupt - try backup
        if (bakFile.exists()) {
            val backup = tryParseFile(bakFile)
            if (backup != null) return backup
        }

        return TaskDataFile()
    }

    private fun tryParseFile(file: File): TaskDataFile? {
        return try {
            val content = file.readText()
            if (content.isBlank()) return null

            val rawJson = json.parseToJsonElement(content) as? JsonObject ?: return null

            val fileVersion = rawJson["schemaVersion"]?.jsonPrimitive?.int ?: 1

            if (fileVersion > CURRENT_SCHEMA_VERSION) {
                throw SchemaVersionTooNewException(fileVersion, CURRENT_SCHEMA_VERSION)
            }

            var migrated = rawJson
            if (fileVersion < CURRENT_SCHEMA_VERSION) {
                migrated = migrateToCurrentVersion(migrated, fileVersion)
            }

            json.decodeFromString<TaskDataFile>(migrated.toString())
        } catch (e: SchemaVersionTooNewException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun saveInternal(data: TaskDataFile) {
        dataDir.mkdirs()

        val dataWithVersion = data.copy(schemaVersion = CURRENT_SCHEMA_VERSION)
        val jsonString = json.encodeToString(TaskDataFile.serializer(), dataWithVersion)

        // Step 1-3: Write to tmp file
        tmpFile.writeText(jsonString)

        // Step 4: Verify tmp file is valid JSON that round-trips
        try {
            val verification = json.decodeFromString<TaskDataFile>(tmpFile.readText())
            if (verification.schemaVersion != CURRENT_SCHEMA_VERSION) {
                tmpFile.delete()
                return
            }
        } catch (e: Exception) {
            tmpFile.delete()
            return
        }

        // Step 5: Backup current file
        if (dataFile.exists()) {
            dataFile.copyTo(bakFile, overwrite = true)
        }

        // Step 6: Atomic rename
        try {
            Files.move(
                tmpFile.toPath(),
                dataFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(
                tmpFile.toPath(),
                dataFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun migrateToCurrentVersion(data: JsonObject, fromVersion: Int): JsonObject {
        var current = data
        var version = fromVersion
        while (version < CURRENT_SCHEMA_VERSION) {
            current = when (version) {
                // Add migration functions here as schema evolves:
                // 0 -> migrateV0toV1(current)
                else -> current
            }
            version++
        }
        return current
    }
}

class SchemaVersionTooNewException(
    val fileVersion: Int,
    val appVersion: Int
) : Exception(
    "Your task data was created by a newer version of Daily Powders (schema v$fileVersion). " +
    "This version only supports up to schema v$appVersion. Please update the app."
)
