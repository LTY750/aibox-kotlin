package com.aibox.kotlin.core.storage.di

import com.aibox.kotlin.core.storage.BlobStore
import com.aibox.kotlin.core.storage.KeyValueStore
import com.aibox.kotlin.core.storage.SessionRepository
import com.aibox.kotlin.core.storage.SettingsRepository
import com.aibox.kotlin.core.storage.impl.FileBlobStore
import com.aibox.kotlin.core.storage.impl.RoomKeyValueStore
import com.aibox.kotlin.core.storage.impl.SessionRepositoryImpl
import com.aibox.kotlin.core.storage.impl.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindKeyValueStore(impl: RoomKeyValueStore): KeyValueStore

    @Binds
    @Singleton
    abstract fun bindBlobStore(impl: FileBlobStore): BlobStore

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
}
