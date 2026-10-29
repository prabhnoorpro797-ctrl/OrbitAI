package com.orbitai.assistant.routines

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

class RoutineRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file: File
        get() = File(context.filesDir, "routines.json")

    @Synchronized
    fun loadAll(): MutableList<Routine> {
        if (!file.exists()) return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Routine>>() {}.type
            gson.fromJson(file.readText(), type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    fun saveAll(list: List<Routine>) {
        file.writeText(gson.toJson(list))
    }

    fun create(name: String, actions: List<RecordedAction> = emptyList()): Routine {
        val r = Routine(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = System.currentTimeMillis(),
            actions = actions.toMutableList()
        )
        val all = loadAll()
        all.add(r)
        saveAll(all)
        return r
    }

    fun update(routine: Routine) {
        val all = loadAll()
        val idx = all.indexOfFirst { it.id == routine.id }
        if (idx >= 0) {
            all[idx] = routine
            saveAll(all)
        }
    }

    fun delete(id: String) {
        val all = loadAll().filter { it.id != id }
        saveAll(all)
    }

    fun findByName(name: String): Routine? {
        val all = loadAll()
        if (name.equals("last", ignoreCase = true)) {
            return all.maxByOrNull { it.lastRunAt ?: it.createdAt }
        }
        return all.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun exportJson(): String {
        val backup = RoutineBackup(routines = loadAll())
        return gson.toJson(backup)
    }

    fun importJson(json: String): Result<Int> {
        return try {
            val backup = gson.fromJson(json, RoutineBackup::class.java)
            if (backup.schemaVersion > 1) {
                return Result.failure(IllegalArgumentException("Unsupported schema version ${backup.schemaVersion}"))
            }
            val existing = loadAll().toMutableList()
            var added = 0
            backup.routines.forEach { incoming ->
                if (existing.none { it.id == incoming.id }) {
                    existing.add(incoming)
                    added++
                }
            }
            saveAll(existing)
            Result.success(added)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
