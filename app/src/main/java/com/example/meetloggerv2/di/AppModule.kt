package com.example.meetloggerv2.di

import android.content.Context
import com.example.meetloggerv2.data.local.db.AppDatabase
import com.example.meetloggerv2.data.local.db.UserDao
import com.example.meetloggerv2.data.local.db.LocalFileDao
import com.example.meetloggerv2.data.local.SettingsDataStore
import com.example.meetloggerv2.core.session.SessionManager
import com.example.meetloggerv2.data.repository.IAuthRepository
import com.example.meetloggerv2.data.repository.AuthRepository
import com.example.meetloggerv2.data.repository.IAudioRepository
import com.example.meetloggerv2.data.repository.AudioRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.example.meetloggerv2.data.repository.FileRepository
import com.example.meetloggerv2.data.remote.ApiService
import com.example.meetloggerv2.data.remote.RetrofitClient
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
    fun provideAuthRepository(apiService: ApiService): IAuthRepository {
        return AuthRepository(apiService)
    }

    @Provides
    @Singleton
    fun provideAudioRepository(apiService: ApiService): IAudioRepository {
        return AudioRepository(apiService)
    }

    @Provides
    @Singleton
    fun provideFileRepository(
        userDao: UserDao,
        localFileDao: LocalFileDao,
        apiService: ApiService
    ): IFileRepository {
        return FileRepository(userDao, localFileDao, apiService)
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager {
        return SessionManager(context)
    }

    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return RetrofitClient.apiService
    }
}
