package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_tasks ORDER BY createdAt DESC")
    fun getAllTransferTasks(): Flow<List<TransferTask>>

    @Query("SELECT * FROM transfer_tasks WHERE id = :id")
    suspend fun getTransferTaskById(id: Long): TransferTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransferTask(task: TransferTask): Long

    @Update
    suspend fun updateTransferTask(task: TransferTask)

    @Delete
    suspend fun deleteTransferTask(task: TransferTask)

    @Query("DELETE FROM transfer_tasks")
    suspend fun clearAllTransferTasks()
}
