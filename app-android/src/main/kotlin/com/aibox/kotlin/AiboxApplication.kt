package com.aibox.kotlin

import android.app.Application
import com.aibox.kotlin.core.storage.impl.SettingsRepositoryImpl
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AiboxApplication : Application() {

    @Inject
    lateinit var settingsRepositoryImpl: SettingsRepositoryImpl

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 启动时加载设置（合并 Keystore 敏感值）
        appScope.launch { settingsRepositoryImpl.init() }
    }
}
