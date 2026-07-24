package com.loomview.app.di

import android.content.Context
import androidx.room.Room
import com.loomview.app.data.datastore.SettingsDataStore
import com.loomview.app.data.local.AppDatabase
import com.loomview.app.data.local.HtmlFileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "loomview.db")
            // 早期開發階段，schema 仍在變動；縮圖快取本身也是可重建的衍生資料，
            // 破壞性遷移比手寫 Migration 更務實。上架前應改為正式 Migration 策略。
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideHtmlFileDao(db: AppDatabase): HtmlFileDao = db.htmlFileDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)
}
