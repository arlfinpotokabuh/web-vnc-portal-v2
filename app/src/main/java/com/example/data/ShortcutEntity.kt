package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String, // Can be IP:port or a web URL
    val iconName: String, // e.g., "Globe", "Desktop", "Router", "Cloud", "Terminal", "Tv", "Cast", "Camera", "Settings"
    val favIconUrl: String? = null,
    val isVnc: Boolean,
    val vncQuality: String = "Medium", // Low, Medium, High
    val vncColorDepth: String = "24-bit", // 8-bit, 16-bit, 24-bit
    val vncScale: String = "Fit to screen", // Fit to screen, Original
    val isFullscreen: Boolean = false,
    val openInChrome: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "access_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val shortcutId: Int? = null,
    val name: String,
    val url: String,
    val isVnc: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
