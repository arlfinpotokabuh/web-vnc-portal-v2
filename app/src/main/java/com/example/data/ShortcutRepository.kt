package com.example.data

import kotlinx.coroutines.flow.Flow

class ShortcutRepository(private val shortcutDao: ShortcutDao) {

    val allShortcuts: Flow<List<ShortcutEntity>> = shortcutDao.getAllShortcuts()
    val recentHistory: Flow<List<HistoryEntity>> = shortcutDao.getRecentHistory()

    suspend fun getShortcutById(id: Int): ShortcutEntity? {
        return shortcutDao.getShortcutById(id)
    }

    suspend fun insertShortcut(shortcut: ShortcutEntity): Long {
        return shortcutDao.insertShortcut(shortcut)
    }

    suspend fun updateShortcut(shortcut: ShortcutEntity) {
        shortcutDao.updateShortcut(shortcut)
    }

    suspend fun deleteShortcut(shortcut: ShortcutEntity) {
        shortcutDao.deleteShortcut(shortcut)
    }

    suspend fun insertHistory(history: HistoryEntity): Long {
        return shortcutDao.insertHistory(history)
    }

    suspend fun clearHistory() {
        shortcutDao.clearHistory()
    }

    suspend fun deleteHistoryById(id: Int) {
        shortcutDao.deleteHistoryById(id)
    }
}
