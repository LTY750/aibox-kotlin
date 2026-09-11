package com.aibox.kotlin.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 全局设置。字段名与旧版（AIbox mobile / Chatbox）持久化格式保持一致。
 *
 * **D4 修复**：旧备份可能含本模型尚未建模的字段。为在「读取 → 写回」往返中不丢字段，
 * 未知键被收集进 [rawExtras] 并在序列化时原样合并写回（见 [SettingsSerializer]），
 * 不再依赖 `ignoreUnknownKeys` 静默丢弃。
 *
 * 安全策略与旧版一致：apiKey 等敏感值不落明文存储，
 * 由 SettingsRepository 写入时抽取到 SecureStore；[rawExtras] 中形似密钥的未知键
 * 在脱敏时会被置空（见 SettingsSecrets）。
 */
@Serializable(with = SettingsSerializer::class)
data class Settings(
    val providers: Map<String, ProviderSettings> = emptyMap(),
    val customProviders: List<CustomProvider> = emptyList(),
    val favoritedModels: List<ModelRef> = emptyList(),
    val defaultChatModel: ModelRef? = null,
    val threadNamingModel: ModelRef? = null,
    val ocrModel: ModelRef? = null,

    // 显示
    val showWordCount: Boolean? = null,
    val showTokenCount: Boolean? = null,
    val showUsedToken: Boolean? = null,
    val showModelName: Boolean? = null,
    val showMessageTimestamp: Boolean? = null,
    val showFirstTokenLatency: Boolean? = null,
    val showAvatar: Boolean? = null,
    val messageLayout: String? = null,

    // 主题/外观；旧版 theme 为数值枚举：0=Dark 1=Light 2=System
    val theme: Int? = null,
    val language: String? = null,
    val fontSize: Int? = null,

    // 行为
    val startupPage: String? = null,
    val defaultPrompt: String? = null,
    val proxy: String? = null,
    val enableMarkdownRendering: Boolean? = null,
    val autoGenerateTitle: Boolean? = null,
    val pasteLongTextAsAFile: Boolean? = null,

    // 上下文压缩
    val autoCompaction: Boolean? = null,
    val compactionThreshold: Double? = null,

    /** 旧备份中的未知字段（原样保留，导出往返不丢）。 */
    val rawExtras: Map<String, JsonElement> = emptyMap(),
)

/** 主题数值枚举，与旧版 Theme 对齐。 */
object ThemeMode {
    const val DARK = 0
    const val LIGHT = 1
    const val SYSTEM = 2
}

/**
 * [Settings] 的「已知字段」代理（不含 [Settings.rawExtras]）。
 * 仅用于默认序列化路径，未知键由 [SettingsSerializer] 单独收集/合并。
 */
@Serializable
private data class SettingsKnown(
    val providers: Map<String, ProviderSettings> = emptyMap(),
    val customProviders: List<CustomProvider> = emptyList(),
    val favoritedModels: List<ModelRef> = emptyList(),
    val defaultChatModel: ModelRef? = null,
    val threadNamingModel: ModelRef? = null,
    val ocrModel: ModelRef? = null,
    val showWordCount: Boolean? = null,
    val showTokenCount: Boolean? = null,
    val showUsedToken: Boolean? = null,
    val showModelName: Boolean? = null,
    val showMessageTimestamp: Boolean? = null,
    val showFirstTokenLatency: Boolean? = null,
    val showAvatar: Boolean? = null,
    val messageLayout: String? = null,
    val theme: Int? = null,
    val language: String? = null,
    val fontSize: Int? = null,
    val startupPage: String? = null,
    val defaultPrompt: String? = null,
    val proxy: String? = null,
    val enableMarkdownRendering: Boolean? = null,
    val autoGenerateTitle: Boolean? = null,
    val pasteLongTextAsAFile: Boolean? = null,
    val autoCompaction: Boolean? = null,
    val compactionThreshold: Double? = null,
)

/**
 * [Settings] 自定义序列化器：把已知字段交给 [SettingsKnown] 处理，
 * 未知键收进/写回 [Settings.rawExtras]。
 */
object SettingsSerializer : KSerializer<Settings> {

    private val delegate = SettingsKnown.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    /** 已知字段的 JSON 键集合（即代理描述符中的元素名）。 */
    private val knownKeys: Set<String> =
        (0 until delegate.descriptor.elementsCount)
            .map { delegate.descriptor.getElementName(it) }
            .toSet()

    override fun deserialize(decoder: Decoder): Settings {
        require(decoder is JsonDecoder) { "Settings 仅支持 JSON 格式" }
        val json = decoder.json
        val obj = decoder.decodeJsonElement().jsonObject
        val known = json.decodeFromJsonElement(delegate, obj)
        val extras = obj.filterKeys { it !in knownKeys }
        return known.toSettings(extras)
    }

    override fun serialize(encoder: Encoder, value: Settings) {
        require(encoder is JsonEncoder) { "Settings 仅支持 JSON 格式" }
        val json = encoder.json
        val base = json.encodeToJsonElement(delegate, value.toKnown()).jsonObject
        // 已知字段优先；rawExtras 仅填补未知键
        val merged = JsonObject(value.rawExtras + base)
        encoder.encodeJsonElement(merged)
    }

    private fun SettingsKnown.toSettings(extras: Map<String, JsonElement>): Settings = Settings(
        providers = providers,
        customProviders = customProviders,
        favoritedModels = favoritedModels,
        defaultChatModel = defaultChatModel,
        threadNamingModel = threadNamingModel,
        ocrModel = ocrModel,
        showWordCount = showWordCount,
        showTokenCount = showTokenCount,
        showUsedToken = showUsedToken,
        showModelName = showModelName,
        showMessageTimestamp = showMessageTimestamp,
        showFirstTokenLatency = showFirstTokenLatency,
        showAvatar = showAvatar,
        messageLayout = messageLayout,
        theme = theme,
        language = language,
        fontSize = fontSize,
        startupPage = startupPage,
        defaultPrompt = defaultPrompt,
        proxy = proxy,
        enableMarkdownRendering = enableMarkdownRendering,
        autoGenerateTitle = autoGenerateTitle,
        pasteLongTextAsAFile = pasteLongTextAsAFile,
        autoCompaction = autoCompaction,
        compactionThreshold = compactionThreshold,
        rawExtras = extras,
    )

    private fun Settings.toKnown(): SettingsKnown = SettingsKnown(
        providers = providers,
        customProviders = customProviders,
        favoritedModels = favoritedModels,
        defaultChatModel = defaultChatModel,
        threadNamingModel = threadNamingModel,
        ocrModel = ocrModel,
        showWordCount = showWordCount,
        showTokenCount = showTokenCount,
        showUsedToken = showUsedToken,
        showModelName = showModelName,
        showMessageTimestamp = showMessageTimestamp,
        showFirstTokenLatency = showFirstTokenLatency,
        showAvatar = showAvatar,
        messageLayout = messageLayout,
        theme = theme,
        language = language,
        fontSize = fontSize,
        startupPage = startupPage,
        defaultPrompt = defaultPrompt,
        proxy = proxy,
        enableMarkdownRendering = enableMarkdownRendering,
        autoGenerateTitle = autoGenerateTitle,
        pasteLongTextAsAFile = pasteLongTextAsAFile,
        autoCompaction = autoCompaction,
        compactionThreshold = compactionThreshold,
    )
}
