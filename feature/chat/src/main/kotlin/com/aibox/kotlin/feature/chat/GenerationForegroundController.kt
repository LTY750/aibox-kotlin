package com.aibox.kotlin.feature.chat

/**
 * 生成期前台服务保活的**端口**（依赖倒置）。
 *
 * 为什么需要这个接口：`ChatViewModel` 位于 `feature:chat`，而前台服务的具体实现
 * 位于 `app-android`（应用层）。模块依赖方向是 `app-android → feature:chat`，
 * 反向依赖会形成环。因此消费者（feature:chat）声明它需要的抽象，
 * 由 `app-android` 提供实现并经 Hilt 绑定（见 `ChatForegroundController`）。
 *
 * 语义：仅用于「后台流保活」这一横切关注点，不承载任何业务逻辑；
 * 实现失败（如 Android 12+ 后台受限）必须静默降级，不得影响生成主流程。
 */
interface GenerationForegroundController {

    /** 开始生成：启动前台服务并展示「进行中」通知。可重复调用（幂等刷新 id/标题）。 */
    fun start(sessionId: String, title: String)

    /** 更新进度文案（实现方需自行节流，≥1000ms 一次），不得包含回复正文。 */
    fun updateProgress(text: String)

    /** 结束生成：停止前台服务并（视实现）给出完成通知。 */
    fun stop()
}
