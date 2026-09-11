package com.aibox.kotlin.core.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * SSE 帧字段的安全访问扩展。
 *
 * 真实服务商的流式帧形状不可信：字段可能是 null（JsonNull）、缺失或类型不符
 * （DeepSeek 的 usage 帧就带 "choices": null）。任何意外形状都视为"字段缺失"，
 * 绝不抛异常——一次解析失败会毁掉整条已计费的流式回复。
 */
internal fun JsonObject.optString(key: String): String? {
    val el = this[key] ?: return null
    if (el !is JsonPrimitive || el is JsonNull) return null
    return el.content
}

internal fun JsonObject.optLong(key: String): Long? = optString(key)?.toLongOrNull()

internal fun JsonObject.optInt(key: String): Int? = optString(key)?.toIntOrNull()

internal fun JsonObject.optObject(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.optArray(key: String): JsonArray? = this[key] as? JsonArray
