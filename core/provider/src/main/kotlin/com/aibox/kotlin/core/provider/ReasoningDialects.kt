package com.aibox.kotlin.core.provider

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAI Compatible 协议族的推理方言参数（对齐旧版 reasoning-control 契约）。
 *
 * 统一 4 档：off / low / medium / high；null（default）= 什么都不发，跟随模型默认。
 * 各服务商方言：
 * - DeepSeek：body.thinking = {type: enabled|disabled}（V3.1+ 混合推理模型）
 * - Qwen（DashScope 兼容模式）：body 顶层 enable_thinking + thinking_budget（仅 qwen3*）
 * - OpenRouter：body.reasoning = {effort} / off = {enabled:false, exclude:true}
 *
 * 纯函数，便于单元测试。
 */
object ReasoningDialects {

    const val LEVEL_OFF = "off"
    const val LEVEL_LOW = "low"
    const val LEVEL_MEDIUM = "medium"
    const val LEVEL_HIGH = "high"

    val LEVELS = listOf(LEVEL_OFF, LEVEL_LOW, LEVEL_MEDIUM, LEVEL_HIGH)

    /** 会话当前模型是否支持某方言（用于 UI 决定是否显示档位选择）。 */
    fun supportsLevel(providerId: String, modelId: String): Boolean = when (providerId) {
        "deepseek" -> isDeepSeekReasoningModel(modelId)
        "qwen" -> isQwenThinkingModel(modelId)
        "openrouter" -> true
        else -> false
    }

    fun isDeepSeekReasoningModel(modelId: String): Boolean {
        val lower = modelId.lowercase()
        // reasoner / r1 系列 + V3.1 及以后的混合推理 + V4
        return lower.contains("reasoner") ||
            Regex("(?:^|/)deepseek-r1").containsMatchIn(lower) ||
            Regex("(?:^|/)deepseek-v3\\.[1-9]").containsMatchIn(lower) ||
            Regex("(?:^|/)deepseek-v[4-9]").containsMatchIn(lower)
    }

    fun isQwenThinkingModel(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return Regex("(?:^|/)qwen3").containsMatchIn(lower)
    }

    /**
     * 把方言参数写入 OpenAI Compatible 请求体。
     * level 为 null 时不写入任何参数。
     */
    fun apply(
        body: JsonObjectBuilder,
        providerId: String,
        modelId: String,
        level: String?,
    ) {
        if (level == null || level !in LEVELS) return
        when (providerId) {
            "deepseek" -> applyDeepSeek(body, modelId, level)
            "qwen" -> applyQwen(body, modelId, level)
            "openrouter" -> applyOpenRouter(body, level)
        }
    }

    private fun applyDeepSeek(body: JsonObjectBuilder, modelId: String, level: String) {
        if (!isDeepSeekReasoningModel(modelId)) return
        when (level) {
            LEVEL_OFF -> body.putJsonObject("thinking") { put("type", "disabled") }
            LEVEL_LOW -> body.putJsonObject("thinking") { put("type", "enabled") }
            LEVEL_MEDIUM, LEVEL_HIGH -> {
                body.putJsonObject("thinking") { put("type", "enabled") }
                // V4+ 支持 effort 档位（medium→high, high→max，与旧版映射一致）
                if (Regex("(?:^|/)deepseek-v[4-9]").containsMatchIn(modelId.lowercase())) {
                    body.put("reasoning_effort", if (level == LEVEL_HIGH) "max" else "high")
                }
            }
        }
    }

    private fun applyQwen(body: JsonObjectBuilder, modelId: String, level: String) {
        if (!isQwenThinkingModel(modelId)) return
        when (level) {
            LEVEL_OFF -> {
                body.put("enable_thinking", false)
            }
            LEVEL_LOW, LEVEL_MEDIUM, LEVEL_HIGH -> {
                body.put("enable_thinking", true)
                body.put("thinking_budget", qwenBudget(level))
            }
        }
    }

    private fun qwenBudget(level: String): Int = when (level) {
        LEVEL_LOW -> 1024
        LEVEL_MEDIUM -> 4096
        else -> 8192
    }

    private fun applyOpenRouter(body: JsonObjectBuilder, level: String) {
        when (level) {
            LEVEL_OFF -> body.putJsonObject("reasoning") {
                put("enabled", false)
                put("exclude", true)
            }
            else -> body.putJsonObject("reasoning") { put("effort", level) }
        }
    }
}

/** OpenRouter 建议的应用标识头。 */
fun openRouterHeaders(): Map<String, String> = mapOf(
    "HTTP-Referer" to "https://chatboxai.app",
    "X-Title" to "AIbox",
)

/** 供测试与调试：从已构建请求体中读取方言字段是否存在。 */
fun JsonObject.hasDialectField(key: String): Boolean = contains(key)
