package com.aibox.kotlin.core.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Settings] 自定义序列化的往返测试：
 * - 未知键被收入 [Settings.rawExtras] 并在写回时原样保留（D4 修复）；
 * - 已知字段不因自定义序列化而丢失。
 */
class SettingsSerializationTest {

    private val json = AiboxJson.lenient

    @Test
    fun `unknown keys are captured into rawExtras and preserved on round trip`() {
        val raw = """{"theme":1,"futureFlag":true,"nested":{"a":1,"b":"x"}}"""

        val decoded = json.decodeFromString(Settings.serializer(), raw)

        assertEquals(ThemeMode.LIGHT, decoded.theme)
        assertEquals(setOf("futureFlag", "nested"), decoded.rawExtras.keys)

        val reencoded = json.parseToJsonElement(
            json.encodeToString(Settings.serializer(), decoded),
        ).jsonObject

        assertEquals(true, reencoded["futureFlag"]?.jsonPrimitive?.boolean)
        assertEquals(1, reencoded["nested"]?.jsonObject?.get("a")?.jsonPrimitive?.int)
        assertEquals(ThemeMode.LIGHT, reencoded["theme"]?.jsonPrimitive?.int)
    }

    @Test
    fun `known fields survive round trip with no extras`() {
        val original = Settings(theme = ThemeMode.SYSTEM, language = "ja", fontSize = 120)

        val decoded = json.decodeFromString(
            Settings.serializer(),
            json.encodeToString(Settings.serializer(), original),
        )

        assertEquals(ThemeMode.SYSTEM, decoded.theme)
        assertEquals("ja", decoded.language)
        assertEquals(120, decoded.fontSize)
        assertTrue(decoded.rawExtras.isEmpty())
    }

    @Test
    fun `serializing settings emits rawExtras keys verbatim`() {
        val extras = mapOf("legacy_key" to JsonPrimitive("v"))

        val encoded = json.encodeToString(Settings.serializer(), Settings(rawExtras = extras))

        assertTrue(encoded.contains("\"legacy_key\""))
        assertTrue(encoded.contains("\"v\""))
    }
}
