package com.aibox.kotlin.core.provider

import com.aibox.kotlin.core.network.AiHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置 Provider 注册表：id / 显示名 / 协议族 / 默认 host。
 * host 值已包含规范路径，HostUtils 归一化时识别版本段不做二次追加。
 */
object ProviderRegistry {

    val builtin: List<BuiltinProviderDefinition> = listOf(
        BuiltinProviderDefinition("openai", "OpenAI", "openai", "https://api.openai.com/v1"),
        BuiltinProviderDefinition("deepseek", "DeepSeek", "openai", "https://api.deepseek.com/v1"),
        BuiltinProviderDefinition("openrouter", "OpenRouter", "openai", "https://openrouter.ai/api/v1"),
        BuiltinProviderDefinition("groq", "Groq", "openai", "https://api.groq.com/openai/v1"),
        BuiltinProviderDefinition("moonshot", "Moonshot AI", "openai", "https://api.moonshot.ai/v1"),
        BuiltinProviderDefinition("moonshot-cn", "Moonshot AI (CN)", "openai", "https://api.moonshot.cn/v1"),
        BuiltinProviderDefinition("minimax", "MiniMax", "openai", "https://api.minimax.io/v1"),
        BuiltinProviderDefinition("minimax-cn", "MiniMax (CN)", "openai", "https://api.minimaxi.com/v1"),
        BuiltinProviderDefinition("siliconflow", "SiliconFlow", "openai", "https://api.siliconflow.cn/v1"),
        BuiltinProviderDefinition("mistral-ai", "Mistral AI", "openai", "https://api.mistral.ai/v1"),
        BuiltinProviderDefinition("perplexity", "Perplexity", "openai", "https://api.perplexity.ai/v1"),
        BuiltinProviderDefinition("qwen", "Qwen", "openai", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
        BuiltinProviderDefinition("chatglm-6b", "ChatGLM", "openai", "https://open.bigmodel.cn/api/paas/v4/"),
        BuiltinProviderDefinition("ollama", "Ollama", "openai", "http://127.0.0.1:11434/v1"),
        BuiltinProviderDefinition("lm-studio", "LM Studio", "openai", "http://127.0.0.1:1234/v1"),
        BuiltinProviderDefinition("claude", "Claude", "claude", "https://api.anthropic.com"),
        BuiltinProviderDefinition("gemini", "Gemini", "gemini", "https://generativelanguage.googleapis.com"),
        BuiltinProviderDefinition("xai", "xAI", "openai", "https://api.x.ai/v1"),
        BuiltinProviderDefinition("volcengine", "VolcEngine", "openai", "https://ark.cn-beijing.volces.com/api/v3"),
        BuiltinProviderDefinition("bedrock", "Amazon Bedrock", "bedrock", "https://bedrock-runtime.us-east-1.amazonaws.com"),
    )

    fun byId(id: String): BuiltinProviderDefinition? = builtin.firstOrNull { it.id == id }
}

/** 按设置解析并构造 Provider 实例。 */
@Singleton
class ProviderFactory @Inject constructor(
    private val httpClient: AiHttpClient,
) {

    fun create(
        providerId: String,
        settings: com.aibox.kotlin.core.model.ProviderSettings,
        custom: com.aibox.kotlin.core.model.CustomProvider?,
    ): AiProvider {
        val customSettings = custom?.defaultSettings
        val userHost = settings.apiHost ?: customSettings?.apiHost
        val apiKey = settings.apiKey ?: customSettings?.apiKey
        val models = settings.models.ifEmpty { customSettings?.models ?: emptyList() }

        if (custom != null) {
            return when (custom.type) {
                "claude" -> AnthropicProvider(
                    id = custom.id,
                    apiHost = HostUtils.normalizeAnthropic(userHost),
                    apiKey = apiKey,
                    httpClient = httpClient,
                    models = models,
                )
                "gemini" -> GeminiProvider(
                    id = custom.id,
                    apiHost = HostUtils.normalizeGemini(userHost),
                    apiKey = apiKey,
                    httpClient = httpClient,
                    models = models,
                )
                "openai-responses" -> {
                    val (base, path) = HostUtils.normalizeOpenAi(userHost ?: "https://api.openai.com/v1")
                    OpenAiResponsesProvider(
                        id = custom.id,
                        base = base,
                        path = path,
                        apiKey = apiKey,
                        httpClient = httpClient,
                        models = models,
                    )
                }
                else -> {
                    val (base, path) = HostUtils.normalizeOpenAi(userHost ?: "https://api.openai.com/v1")
                    OpenAiCompatibleProvider(
                        id = custom.id,
                        base = base,
                        path = path.ifEmpty { "/v1" } + "/chat/completions",
                        apiKey = apiKey,
                        httpClient = httpClient,
                        models = models,
                    )
                }
            }
        }

        val definition = ProviderRegistry.byId(providerId)
            ?: throw IllegalArgumentException("Unknown provider: $providerId")
        return when (definition.type) {
            "claude" -> AnthropicProvider(
                id = providerId,
                apiHost = HostUtils.normalizeAnthropic(userHost ?: definition.defaultApiHost),
                apiKey = apiKey,
                httpClient = httpClient,
                models = models,
            )
            "gemini" -> GeminiProvider(
                id = providerId,
                apiHost = HostUtils.normalizeGemini(userHost ?: definition.defaultApiHost),
                apiKey = apiKey,
                httpClient = httpClient,
                models = models,
            )
            "bedrock" -> BedrockProvider(
                id = providerId,
                region = settings.region?.takeIf { it.isNotBlank() } ?: "us-east-1",
                accessKeyId = apiKey,
                secretAccessKey = settings.secretAccessKey,
                sessionToken = settings.sessionToken,
                httpClient = httpClient,
                models = models.ifEmpty { BEDROCK_DEFAULT_MODELS },
            )
            else -> {
                val (base, path) = HostUtils.normalizeOpenAi(userHost ?: definition.defaultApiHost)
                OpenAiCompatibleProvider(
                    id = providerId,
                    base = base,
                    path = path + "/chat/completions",
                    apiKey = apiKey ?: defaultApiKey(providerId),
                    httpClient = httpClient,
                    models = models,
                )
            }
        }
    }

    /** Ollama/LM Studio 无鉴权，使用占位 key（部分实现要求非空 Bearer）。 */
    private fun defaultApiKey(providerId: String): String? = when (providerId) {
        "ollama", "lm-studio" -> "ollama"
        else -> null
    }
}
