package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ShortcutEntity::class, HistoryEntity::class, TransferTask::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shortcutDao(): ShortcutDao
    abstract fun transferDao(): TransferDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "web_vnc_portal_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
