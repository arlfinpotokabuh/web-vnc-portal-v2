package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutDao {
    // Shortcuts
    @Query("SELECT * FROM shortcuts ORDER BY createdAt DESC")
    fun getAllShortcuts(): Flow<List<ShortcutEntity>>

    @Query("SELECT * FROM shortcuts WHERE id = :id")
    suspend fun getShortcutById(id: Int): ShortcutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: ShortcutEntity): Long

    @Update
    suspend fun updateShortcut(shortcut: ShortcutEntity)

    @Delete
    suspend fun deleteShortcut(shortcut: ShortcutEntity)

    // History
    @Query("SELECT * FROM access_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity): Long

    @Query("DELETE FROM access_history")
    suspend fun clearHistory()

    @Query("DELETE FROM access_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)
}
