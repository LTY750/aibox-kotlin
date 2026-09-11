package com.aibox.kotlin.core.network

/**
 * 错误分类，与旧版 errors.ts 对齐：
 * - ApiError：服务端返回的非 2xx 响应（可能携带 responseBody）
 * - NetworkError：连接失败、DNS、超时等本地网络错误
 * - MidStreamApiError：流式响应已输出内容后才发生的错误——
 *   绝不能自动重试（会产生重复计费），上层必须原样展示。
 */
class ApiError(
    val statusCode: Int? = null,
    message: String,
    val responseBody: String? = null,
) : Exception(message)

class NetworkError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class MidStreamApiError(
    val statusCode: Int? = null,
    message: String,
) : Exception(message)

/** 是否为可重试状态码：429 或 5xx。 */
fun isRetryableStatus(statusCode: Int?): Boolean =
    statusCode != null && (statusCode == 429 || statusCode >= 500)

/** 将 HTML 错误页（网关 502/503/504 页面）替换为简短状态文本，避免超大无意义错误串。 */
fun sanitizeErrorBody(statusCode: Int?, body: String): String {
    val trimmed = body.trimStart()
    if (trimmed.startsWith("<!doctype", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true)
    ) {
        return "HTTP ${statusCode ?: "error"}"
    }
    return body.take(2000)
}
