package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_tasks")
data class TransferTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val isDownload: Boolean, // true for download, false for upload
    val totalBytes: Long,
    val transferredBytes: Long, // downloaded or uploaded bytes
    val status: String, // "PENDING", "RUNNING", "PAUSED", "COMPLETED", "FAILED"
    val localPath: String, // path within internal/external files directory
    val isMultipart: Boolean = true,
    val numParts: Int = 4,
    val userAgent: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
