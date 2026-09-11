package com.aibox.kotlin.core.storage.impl

import com.aibox.kotlin.core.model.CustomProvider
import com.aibox.kotlin.core.model.ProviderSettings
import com.aibox.kotlin.core.model.Settings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * 设置敏感路径抽取/还原（纯函数，可单测）。
 * 与旧版 MOBILE_SETTINGS_SECRET_PATHS 对齐，但只覆盖本模型存在的字段：
 * - providers.<id>.apiKey
 * - providers.<id>.secretAccessKey / sessionToken（Bedrock）
 * - customProviders.<id>.defaultSettings.apiKey
 *
 * 另有 [scrubExtras] 处理旧备份未知字段（[Settings.rawExtras]）：未知键本身不落明文，
 * 但键名形似密钥的值在脱敏时置空，避免把未来的密钥字段明文写盘/导出。
 */
internal object SettingsSecrets {

    /** 形似密钥的键名（大小写不敏感，与旧版脱敏正则语义一致）。 */
    private val SECRET_KEY_REGEX =
        Regex("(?i)(apikey|api_key|secret|token|password|credential)")

    private fun extractProvider(id: String, s: ProviderSettings): Pair<ProviderSettings, Map<String, String>> {
        val secrets = mutableMapOf<String, String>()
        var redacted = s
        // ProviderSettings 属性来自 core:model（跨模块），无法对公共属性做 smart cast，
        // 因此先取到本地 val 再判空（行为不变）。
        val apiKey = s.apiKey
        if (!apiKey.isNullOrBlank()) {
            secrets["providers.$id.apiKey"] = apiKey
            redacted = redacted.copy(apiKey = null)
        }
        val secretAccessKey = s.secretAccessKey
        if (!secretAccessKey.isNullOrBlank()) {
            secrets["providers.$id.secretAccessKey"] = secretAccessKey
            redacted = redacted.copy(secretAccessKey = null)
        }
        val sessionToken = s.sessionToken
        if (!sessionToken.isNullOrBlank()) {
            secrets["providers.$id.sessionToken"] = sessionToken
            redacted = redacted.copy(sessionToken = null)
        }
        return redacted to secrets
    }

    private fun restoreProvider(id: String, s: ProviderSettings, secrets: Map<String, String>): ProviderSettings {
        var restored = s
        secrets["providers.$id.apiKey"]?.let { restored = restored.copy(apiKey = it) }
        secrets["providers.$id.secretAccessKey"]?.let { restored = restored.copy(secretAccessKey = it) }
        secrets["providers.$id.sessionToken"]?.let { restored = restored.copy(sessionToken = it) }
        return restored
    }

    fun extract(settings: Settings): Pair<Settings, Map<String, String>> {
        val secrets = mutableMapOf<String, String>()

        val redactedProviders = settings.providers.mapValues { (id, providerSettings) ->
            val (redacted, providerSecrets) = extractProvider(id, providerSettings)
            secrets.putAll(providerSecrets)
            redacted
        }

        val redactedCustom = settings.customProviders.mapIndexed { index, custom ->
            val apiKey = custom.defaultSettings?.apiKey
            if (!apiKey.isNullOrBlank()) {
                val pathId = custom.id.ifBlank { "index-$index" }
                secrets["customProviders.$pathId.defaultSettings.apiKey"] = apiKey
                custom.copy(
                    defaultSettings = custom.defaultSettings?.copy(apiKey = null),
                )
            } else {
                custom
            }
        }

        return settings.copy(
            providers = redactedProviders,
            customProviders = redactedCustom,
            // 未知键里的密钥一律置空；其余未知键保留，保证往返不丢（D4）。
            rawExtras = scrubExtras(settings.rawExtras),
        ) to secrets
    }

    fun restore(redacted: Settings, secrets: Map<String, String>): Settings {
        if (secrets.isEmpty()) return redacted

        val providers = redacted.providers.mapValues { (id, providerSettings) ->
            restoreProvider(id, providerSettings, secrets)
        }

        val customProviders = redacted.customProviders.mapIndexed { index, custom ->
            val pathId = custom.id.ifBlank { "index-$index" }
            val key = secrets["customProviders.$pathId.defaultSettings.apiKey"]
            if (key != null) {
                custom.copy(
                    defaultSettings = (custom.defaultSettings
                        ?: ProviderSettings()).copy(apiKey = key),
                )
            } else {
                custom
            }
        }

        return redacted.copy(providers = providers, customProviders = customProviders)
    }

    /** 判断键名是否形似密钥（供脱敏策略复用/单测）。 */
    fun isSecretLikeKey(key: String): Boolean = SECRET_KEY_REGEX.containsMatchIn(key)

    /**
     * 递归脱敏 [Settings.rawExtras]：键名命中 [SECRET_KEY_REGEX] 的条目整体置空（[JsonNull]），
     * 其余键/值原样保留，以保证「读取 → 写回」往返不丢字段。
     *
     * 纯函数（无副作用、可重复调用），便于单测。
     */
    fun scrubExtras(extras: Map<String, JsonElement>): Map<String, JsonElement> =
        extras.mapValues { (key, value) -> scrubEntry(key, value) }

    private fun scrubEntry(key: String, value: JsonElement): JsonElement {
        // 命中敏感键：整棵子树置空（对象/数组一并遮蔽），避免漏出嵌套密钥。
        if (isSecretLikeKey(key)) return JsonNull
        return when (value) {
            is JsonObject -> JsonObject(value.mapValues { (k, v) -> scrubEntry(k, v) })
            is JsonArray -> JsonArray(value.map { scrubArrayElement(it) })
            else -> value
        }
    }

    private fun scrubArrayElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (k, v) -> scrubEntry(k, v) })
        is JsonArray -> JsonArray(element.map { scrubArrayElement(it) })
        else -> element
    }
}
