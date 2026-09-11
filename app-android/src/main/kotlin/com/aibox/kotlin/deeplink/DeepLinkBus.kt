package com.aibox.kotlin.deeplink

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程内深链持有者：仅负责「捕获与暴露」进入应用的深链 URI。
 *
 * 设计边界（架构文档 §8「路由注册」）：
 * - 本类**只做捕获与暴露**，不包含任何「URI → 路由」的映射逻辑；
 *   路由映射表属于 T06 的 `DeepLinkRouter`，此处刻意不实现，避免职责重叠。
 *
 * 安全边界（架构文档 §8「日志与遥测挂钩」/ §9）：
 * - 深链的 query / fragment 可能携带 OAuth 授权码等凭据，因此本类
 *   **不记录任何日志**，调用方也应遵循同一约定。
 */
object DeepLinkBus {

    private val _uri = MutableStateFlow<Uri?>(null)

    /** 当前待处理的深链 URI；为 null 表示暂无待处理深链。 */
    val uri: StateFlow<Uri?> = _uri.asStateFlow()

    /**
     * 发布一条深链 URI（冷/热启动均调用）。
     *
     * @param uri 从 Intent.data 读取的 URI；为 null 时忽略。
     */
    fun publish(uri: Uri?) {
        if (uri != null) {
            _uri.value = uri
        }
    }

    /** 消费后清空，避免重复处理同一条深链。 */
    fun consume() {
        _uri.value = null
    }
}
