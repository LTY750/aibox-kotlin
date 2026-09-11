package com.aibox.kotlin.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.aibox.kotlin.feature.chat.GenerationForegroundController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 前台服务保活控制器（应用层实现，提供 [GenerationForegroundController] 端口）。
 *
 * 关键约束：
 * - `start` 在 try/catch 内包裹，兼容 Android 12+ 后台启动前台服务受限
 *   （`ForegroundServiceStartNotAllowedException`，其父类为 `IllegalStateException` → `Exception`）；
 *   失败时**不崩溃**，记录日志并降级为普通流式。
 * - `updateProgress` 自带节流（≥1000ms 一次），避免每个 token 都刷新通知。
 */
@Singleton
class ChatForegroundController @Inject constructor(
    @ApplicationContext private val context: Context,
) : GenerationForegroundController {

    private var lastProgressAtMs: Long = 0L

    override fun start(sessionId: String, title: String) {
        try {
            ContextCompat.startForegroundService(
                context,
                StreamForegroundService.startIntent(context, sessionId, title),
            )
        } catch (e: Exception) {
            // 含 Android 12+ 的 ForegroundServiceStartNotAllowedException；
            // 此处显式捕获 Exception 而非具体子类，以兼容 minSdk 23（避免类加载失败）。
            Log.w(TAG, "Stream foreground service start rejected; degrading to plain streaming", e)
        }
    }

    override fun updateProgress(text: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProgressAtMs < PROGRESS_MIN_INTERVAL_MS) return
        lastProgressAtMs = now
        try {
            context.startService(StreamForegroundService.updateIntent(context, text))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update stream notification", e)
        }
    }

    override fun stop() {
        try {
            context.startService(StreamForegroundService.stopIntent(context))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to stop stream foreground service", e)
        }
    }

    private companion object {
        const val TAG = "ChatForeground"
        const val PROGRESS_MIN_INTERVAL_MS = 1000L
    }
}

/** 将应用层实现绑定到 feature 层声明的端口。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ForegroundModule {

    @Binds
    @Singleton
    abstract fun bindGenerationForegroundController(
        impl: ChatForegroundController,
    ): GenerationForegroundController
}
