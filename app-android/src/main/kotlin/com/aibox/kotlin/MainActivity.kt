package com.aibox.kotlin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aibox.kotlin.deeplink.DeepLinkBus
import com.aibox.kotlin.permission.NotificationPermission
import com.aibox.kotlin.ui.AiboxApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用唯一 Activity（`launchMode=singleTask`）。
 *
 * 职责：
 * - 装配 Compose 内容；
 * - edge-to-edge 显示，安全区由 `AiboxApp()` 统一处理；
 * - 冷/热启动深链入口：**仅捕获并暴露** URI（路由映射由 T06 的 `DeepLinkRouter` 负责）；
 * - Android 13+ 首次请求通知权限（用于生成保活通知，T02）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 必须在 Activity STARTED 之前注册；字段初始化早于 onCreate，满足该约束。
    // 用户拒绝也仅意味着不显示通知，生成流程不受影响。
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前调用，用于消除冷启动白屏（架构 T01 / §3.1）。
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 冷启动深链入口。
        publishDeepLink(intent)
        setContent {
            AiboxApp()
        }
        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask 下热启动走 onNewIntent；更新 Intent 并捕获深链。
        setIntent(intent)
        publishDeepLink(intent)
    }

    /** Android 13+ 首次进入时申请通知权限；API < 33 或已授权时为 no-op。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (NotificationPermission.shouldRequest(this)) {
            notificationPermissionLauncher.launch(NotificationPermission.PERMISSION)
        }
    }

    /**
     * 捕获深链并将 URI 暴露到 [DeepLinkBus]。
     *
     * 仅处理 `ACTION_VIEW`；出于安全考虑（架构 §8/§9），此处及下游
     * **不得记录 query / fragment**，以免泄露 OAuth 授权码等凭据。
     */
    private fun publishDeepLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            DeepLinkBus.publish(intent.data)
        }
    }
}
