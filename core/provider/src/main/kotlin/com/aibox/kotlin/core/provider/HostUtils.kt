package com.aibox.kotlin.core.provider

/**
 * 主机/路径归一化，对齐旧版 llm_utils.ts 契约。
 * 仅对用户输入的 host 生效；注册表默认值已含正确路径，直接使用。
 */
object HostUtils {

    private val VERSION_SEGMENT = Regex("/v\\d+([a-z].*)?/?$")

    /**
     * OpenAI Compatible 归一化。
     * @return (base, path)：base 不含路径，path 为请求路径（/chat/completions 等）
     */
    fun normalizeOpenAi(host: String?): Pair<String, String> {
        var h = host?.trim().takeUnless { it.isNullOrBlank() } ?: "https://api.openai.com/v1"
        if (!h.startsWith("http://") && !h.startsWith("https://")) {
            h = "https://$h"
        }
        // 拆出 base 与已有路径
        val schemeEnd = h.indexOf("://") + 3
        val pathStart = h.indexOf('/', schemeEnd)
        var base: String
        var path: String
        if (pathStart < 0) {
            base = h
            path = ""
        } else {
            base = h.substring(0, pathStart)
            path = h.substring(pathStart)
        }
        // 已带完整版本路径（/v1、/v3、/v4、/compatible-mode/v1、/api/v3 等）则不追加
        if (path.isNotEmpty()) {
            return base to (if (path.endsWith("/")) path.dropLast(1) else path)
        }
        // 特例：官方 OpenAI 与 OpenRouter 的裸域名的规范路径
        val lower = base.lowercase()
        return when {
            lower.endsWith("api.openai.com") -> base to "/v1"
            lower.endsWith("openrouter.ai") -> base to "/api/v1"
            lower.endsWith("api.x.com") -> base to "/v1"
            else -> base to "/v1"
        }
    }

    /** Anthropic 归一化：默认追加 /v1（已含 /v1 或其他版本段则不追加）。 */
    fun normalizeAnthropic(host: String?): String {
        val h = host?.trim().takeUnless { it.isNullOrBlank() } ?: "https://api.anthropic.com"
        val withScheme = if (h.startsWith("http")) h else "https://$h"
        return if (VERSION_SEGMENT.containsMatchIn(withScheme)) {
            withScheme.removeSuffix("/")
        } else {
            withScheme.removeSuffix("/") + "/v1"
        }
    }

    /** Gemini 归一化：默认追加 /v1beta。 */
    fun normalizeGemini(host: String?): String {
        val h = host?.trim().takeUnless { it.isNullOrBlank() } ?: "https://generativelanguage.googleapis.com"
        val withScheme = if (h.startsWith("http")) h else "https://$h"
        return if (withScheme.contains("/v1beta") || withScheme.contains("/v1")) {
            withScheme.removeSuffix("/")
        } else {
            withScheme.removeSuffix("/") + "/v1beta"
        }
    }

    /** 从 base+path 中取 /models 列表端点（剥离 /chat/completions 等请求路径后拼接）。 */
    fun modelsListBase(base: String, path: String): String {
        val stripped = path.removeSuffix("/chat/completions")
        return base.removeSuffix("/") + stripped + "/models"
    }
}
