package com.aibox.kotlin.core.storage.impl

import com.aibox.kotlin.core.model.CustomProvider
import com.aibox.kotlin.core.model.ProviderSettings
import com.aibox.kotlin.core.model.Settings
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 敏感值抽取/还原的回归测试（对应旧版 MOBILE_SETTINGS_SECRET_PATHS 语义）。 */
class SettingsSecretsTest {

    @Test
    fun `extract removes provider api keys and records paths`() {
        val settings = Settings(
            providers = mapOf(
                "openai" to ProviderSettings(apiKey = "sk-test-1"),
                "gemini" to ProviderSettings(apiKey = null),
            ),
        )

        val (redacted, secrets) = SettingsSecrets.extract(settings)

        assertNull(redacted.providers["openai"]?.apiKey)
        assertEquals(mapOf("providers.openai.apiKey" to "sk-test-1"), secrets)
    }

    @Test
    fun `extract handles custom provider with blank id via index`() {
        val settings = Settings(
            customProviders = listOf(
                CustomProvider(id = "", name = "My Provider", type = "openai", defaultSettings = ProviderSettings(apiKey = "custom-key")),
            ),
        )

        val (redacted, secrets) = SettingsSecrets.extract(settings)

        assertNull(redacted.customProviders[0].defaultSettings?.apiKey)
        assertEquals(mapOf("customProviders.index-0.defaultSettings.apiKey" to "custom-key"), secrets)
    }

    @Test
    fun `restore round-trips keys back into settings`() {
        val original = Settings(
            providers = mapOf("openai" to ProviderSettings(apiKey = "sk-abc")),
            customProviders = listOf(
                CustomProvider(id = "my-custom", name = "My", type = "openai", defaultSettings = ProviderSettings(apiKey = "ck-1")),
            ),
        )

        val (redacted, secrets) = SettingsSecrets.extract(original)
        val restored = SettingsSecrets.restore(redacted, secrets)

        assertEquals("sk-abc", restored.providers["openai"]?.apiKey)
        assertEquals("ck-1", restored.customProviders[0].defaultSettings?.apiKey)
    }

    @Test
    fun `restore with empty secrets returns redacted unchanged`() {
        val redacted = Settings(providers = mapOf("openai" to ProviderSettings(apiKey = null)))
        assertEquals(redacted, SettingsSecrets.restore(redacted, emptyMap()))
    }

    @Test
    fun `redacted settings never contain api keys`() {
        val settings = Settings(
            providers = mapOf("claude" to ProviderSettings(apiKey = "sk-ant")),
        )
        val (redacted, _) = SettingsSecrets.extract(settings)
        redacted.providers.values.forEach { assertTrue(it.apiKey == null) }
    }

    @Test
    fun `scrubExtras blanks secret-like keys and keeps the rest`() {
        val extras = mapOf(
            "apiKey" to JsonPrimitive("sk-leak"),
            "futureFlag" to JsonPrimitive(true),
            "nested" to JsonObject(
                mapOf(
                    "token" to JsonPrimitive("t"),
                    "keep" to JsonPrimitive(1),
                ),
            ),
        )

        val scrubbed = SettingsSecrets.scrubExtras(extras)

        assertEquals(JsonNull, scrubbed["apiKey"])
        assertEquals(JsonPrimitive(true), scrubbed["futureFlag"])
        val nested = scrubbed["nested"] as JsonObject
        assertEquals(JsonNull, nested["token"])
        assertEquals(JsonPrimitive(1), nested["keep"])
    }

    @Test
    fun `extract scrubs secret-like keys from rawExtras`() {
        val settings = Settings(
            rawExtras = mapOf(
                "refresh_token" to JsonPrimitive("rt"),
                "uiDensity" to JsonPrimitive("compact"),
            ),
        )

        val (redacted, _) = SettingsSecrets.extract(settings)

        assertEquals(JsonNull, redacted.rawExtras["refresh_token"])
        assertEquals(JsonPrimitive("compact"), redacted.rawExtras["uiDensity"])
    }

    @Test
    fun `isSecretLikeKey matches legacy secret patterns case-insensitively`() {
        assertTrue(SettingsSecrets.isSecretLikeKey("apiKey"))
        assertTrue(SettingsSecrets.isSecretLikeKey("api_key"))
        assertTrue(SettingsSecrets.isSecretLikeKey("refreshToken"))
        assertTrue(SettingsSecrets.isSecretLikeKey("Password"))
        assertTrue(SettingsSecrets.isSecretLikeKey("credentialId"))
        assertTrue(!SettingsSecrets.isSecretLikeKey("theme"))
    }
}
