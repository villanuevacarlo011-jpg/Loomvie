package com.loomview.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HtmlFileEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun htmlFileDao(): HtmlFileDao
}
