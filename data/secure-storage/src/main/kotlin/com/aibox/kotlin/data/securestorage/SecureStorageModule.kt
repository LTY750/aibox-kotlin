package com.aibox.kotlin.data.securestorage

import com.aibox.kotlin.core.security.SecureStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecureStorageModule {

    @Binds
    @Singleton
    abstract fun bindSecureStore(impl: KeystoreSecureStore): SecureStore
}
