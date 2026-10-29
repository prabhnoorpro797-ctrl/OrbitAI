package com.orbitai.assistant.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun allMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :q || '%' OR `key` LIKE '%' || :q || '%' ORDER BY updatedAt DESC LIMIT 20")
    suspend fun search(q: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memories")
    suspend fun clearAll()

    @Insert
    suspend fun insertMessage(msg: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentMessages(limit: Int): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun messageCount(): Int

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun memoryCount(): Int
}
