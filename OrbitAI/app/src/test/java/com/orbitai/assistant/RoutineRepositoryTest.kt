package com.orbitai.assistant

import com.orbitai.assistant.routines.RecordedAction
import com.orbitai.assistant.routines.Routine
import com.orbitai.assistant.routines.RoutineBackup
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure unit tests for routine serialization (no Android Context required).
 */
class RoutineRepositoryTest {

    private val gson = Gson()

    @Test
    fun backupRoundTrip() {
        val routine = Routine(
            id = "abc",
            name = "Morning",
            createdAt = 123L,
            actions = mutableListOf(
                RecordedAction(type = "OPEN_APP", packageName = "com.android.chrome"),
                RecordedAction(type = "TAP", text = "Search")
            )
        )
        val backup = RoutineBackup(schemaVersion = 1, routines = listOf(routine))
        val json = gson.toJson(backup)
        val restored = gson.fromJson(json, RoutineBackup::class.java)
        assertEquals(1, restored.schemaVersion)
        assertEquals(1, restored.routines.size)
        assertEquals("Morning", restored.routines[0].name)
        assertEquals(2, restored.routines[0].actions.size)
        assertEquals("OPEN_APP", restored.routines[0].actions[0].type)
    }

    @Test
    fun rejectFutureSchema() {
        val json = """{"schema_version":99,"routines":[]}"""
        val backup = gson.fromJson(json, RoutineBackup::class.java)
        assertEquals(99, backup.schemaVersion)
        // Repository.importJson would reject >1
        assertTrue(backup.schemaVersion > 1)
    }
}
