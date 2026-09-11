package com.aibox.kotlin.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat

/**
 * 生成流保活前台服务（`foregroundServiceType="dataSync"`）。
 *
 * 职责：在流式生成期间持有一个 `dataSync` 类型前台服务 + 常驻通知 + 限时
 * `PARTIAL_WAKE_LOCK`，避免 App 切后台/锁屏后进程被冻结或杀死，导致长回复中断。
 *
 * 指令协议（通过 Intent action 驱动，见 companion）：
 * - [ACTION_START]：startForeground + 取唤醒锁
 * - [ACTION_UPDATE]：刷新通知进度
 * - [ACTION_STOP]：发完成通知 + 停止前台 + stopSelf
 *
 * 返回 `START_NOT_STICKY`：流式无法断点续传，进程被杀后不自动重建，
 * 残留的 `isStreamingMode` 占位消息由 `ChatEngine.recoverInterrupted` 在下次打开会话时清理。
 */
class StreamForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var sessionId: String = ""
    private var title: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                startForegroundCompat()
                acquireWakeLock()
            }
            ACTION_UPDATE -> {
                val progress = intent.getStringExtra(EXTRA_PROGRESS)
                updateNotification(progress)
            }
            ACTION_STOP -> {
                postCompletedNotification()
                stopForegroundCompat()
                stopSelf()
            }
            else -> Unit
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = NotificationHelper.buildOngoing(this, sessionId, title)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ONGOING_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun updateNotification(progress: String?) {
        val notification = NotificationHelper.buildOngoing(this, sessionId, title, progress)
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NotificationHelper.ONGOING_NOTIFICATION_ID, notification)
        }
    }

    /** 完成通知：可点回会话。通知权限缺失/被拒时静默失败，不影响主流程。 */
    private fun postCompletedNotification() {
        val notification = NotificationHelper.buildCompleted(this, sessionId, title)
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NotificationHelper.COMPLETED_NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG,
        ).apply {
            setReferenceCounted(false)
            // 限时持有，防止异常路径下唤醒锁泄漏（最长 10 分钟）
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val WAKELOCK_TAG = "aibox:stream"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L

        private const val ACTION_START = "com.aibox.kotlin.action.STREAM_START"
        private const val ACTION_UPDATE = "com.aibox.kotlin.action.STREAM_UPDATE"
        private const val ACTION_STOP = "com.aibox.kotlin.action.STREAM_STOP"
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_PROGRESS = "extra_progress"

        fun startIntent(context: Context, sessionId: String, title: String): Intent =
            Intent(context, StreamForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_TITLE, title)
            }

        fun updateIntent(context: Context, progress: String): Intent =
            Intent(context, StreamForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_PROGRESS, progress)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, StreamForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
