package com.aibox.kotlin.core.model

import kotlinx.serialization.json.Json

/**
 * 全局 JSON 配置。
 *
 * 字段名与旧版（AIbox mobile / Chatbox）持久化格式保持一致，
 * `ignoreUnknownKeys` 保证导入旧备份时未知字段被安全跳过，
 * `encodeDefaults = false` 使输出 JSON 与 TS 端 "undefined 不序列化" 的语义一致。
 */
object AiboxJson {
    val lenient: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
    }
}
