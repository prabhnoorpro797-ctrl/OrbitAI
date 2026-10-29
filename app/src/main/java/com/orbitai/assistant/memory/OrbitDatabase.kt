package com.orbitai.assistant.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MemoryEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile private var instance: OrbitDatabase? = null

        fun get(context: Context): OrbitDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OrbitDatabase::class.java,
                    "orbit_memory.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
