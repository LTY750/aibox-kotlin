package com.aibox.kotlin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aibox.kotlin.MainActivity
import com.aibox.kotlin.R

/**
 * 生成流保活通知：渠道创建 + 「进行中 / 已完成」通知构建。
 *
 * 安全约定（架构 §9）：通知**只显示会话标题与状态**，绝不包含回复正文，
 * 因为锁屏/通知栏可见，正文属隐私泄露面。
 * 点击通知通过 `chatbox://session/<id>` 深链回到会话（复用 T01 的 DeepLinkBus 捕获链路，
 * 此处不实现任何路由映射——路由映射属 T06）。
 */
object NotificationHelper {

    const val CHANNEL_ID = "chat_stream"

    /** 进行中通知 id（固定，便于更新/取消）。 */
    const val ONGOING_NOTIFICATION_ID = 1001

    /** 已完成通知 id（与进行中区分，避免互相覆盖）。 */
    const val COMPLETED_NOTIFICATION_ID = 1002

    /** 创建低重要性、无声的通知渠道（幂等）。API < 26 无渠道概念，直接返回。 */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_stream),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_stream_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 「进行中」通知：常驻、无声、仅一次提示。
     * @param progress 可选进度文案（如"已生成 N 字"），为 null 时显示会话标题。
     */
    fun buildOngoing(
        context: Context,
        sessionId: String,
        title: String,
        progress: String? = null,
    ): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(context.getString(R.string.notification_streaming))
        .setContentText(progress ?: title.ifBlank { context.getString(R.string.app_name) })
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(sessionPendingIntent(context, sessionId))
        .build()

    /** 「已完成」通知：可清除、无声、点击回会话。 */
    fun buildCompleted(
        context: Context,
        sessionId: String,
        title: String,
    ): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(context.getString(R.string.notification_completed))
        .setContentText(title.ifBlank { context.getString(R.string.app_name) })
        .setAutoCancel(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(sessionPendingIntent(context, sessionId))
        .build()

    /** 点击回会话的 PendingIntent：VIEW + chatbox://session/<id>，复用 DeepLinkBus 捕获链路。 */
    private fun sessionPendingIntent(context: Context, sessionId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("chatbox://session/$sessionId")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
