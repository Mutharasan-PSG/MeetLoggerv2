package com.example.meetloggerv2.di

import android.content.Context
import com.example.meetloggerv2.data.local.db.AppDatabase
import com.example.meetloggerv2.data.local.db.UserDao
import com.example.meetloggerv2.data.local.db.LocalFileDao
import com.example.meetloggerv2.data.local.SettingsDataStore
import com.example.meetloggerv2.data.repository.IAuthRepository
import com.example.meetloggerv2.data.repository.AuthRepository
import com.example.meetloggerv2.data.repository.IAudioRepository
import com.example.meetloggerv2.data.repository.AudioRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.example.meetloggerv2.data.repository.FileRepository
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideLocalFileDao(database: AppDatabase): LocalFileDao {
        return database.localFileDao()
    }

    @Provides
    @Singleton
    fun provideAuthRepository(): IAuthRepository {
        return AuthRepository()
    }

    @Provides
    @Singleton
    fun provideAudioRepository(): IAudioRepository {
        return AudioRepository()
    }

    @Provides
    @Singleton
    fun provideFileRepository(
        userDao: UserDao,
        localFileDao: LocalFileDao
    ): IFileRepository {
        return FileRepository(userDao, localFileDao)
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }
}
