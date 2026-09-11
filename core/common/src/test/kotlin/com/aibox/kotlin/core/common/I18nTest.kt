package com.aibox.kotlin.core.common

import com.aibox.kotlin.core.common.i18n.StringsAr
import com.aibox.kotlin.core.common.i18n.StringsDe
import com.aibox.kotlin.core.common.i18n.StringsEn
import com.aibox.kotlin.core.common.i18n.StringsEs
import com.aibox.kotlin.core.common.i18n.StringsFr
import com.aibox.kotlin.core.common.i18n.StringsIt
import com.aibox.kotlin.core.common.i18n.StringsJa
import com.aibox.kotlin.core.common.i18n.StringsKo
import com.aibox.kotlin.core.common.i18n.StringsNb
import com.aibox.kotlin.core.common.i18n.StringsPt
import com.aibox.kotlin.core.common.i18n.StringsRu
import com.aibox.kotlin.core.common.i18n.StringsSv
import com.aibox.kotlin.core.common.i18n.StringsZhCn
import com.aibox.kotlin.core.common.i18n.StringsZhHant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * i18n 回归测试：
 * 1. 14 种语言与英文的 key 集合完全一致（防漏译/多译，真正的翻译完整性防线）；
 * 2. 占位符 `%n$s` / `%n$d` 的序号与顺序在各语言间完全一致（防 String.format 崩溃）；
 * 3. [AppLanguage.fromTag] 对旧版历史 tag 映射正确，未知 tag 安全回退；
 * 4. 枚举 tag 自往返一致；并抽查非英文表确已翻译（技术术语允许保留英文）。
 */
class I18nTest {

    /** tag → 词条表（全部 14 种语言）。 */
    private val tables: Map<String, Map<String, String>> = mapOf(
        "en" to StringsEn,
        "zh-Hans" to StringsZhCn,
        "zh-Hant" to StringsZhHant,
        "ar" to StringsAr,
        "de" to StringsDe,
        "es" to StringsEs,
        "fr" to StringsFr,
        "it-IT" to StringsIt,
        "ja" to StringsJa,
        "ko" to StringsKo,
        "nb-NO" to StringsNb,
        "pt-PT" to StringsPt,
        "ru" to StringsRu,
        "sv" to StringsSv,
    )

    /** 提取 `%1$s` / `%2$d` 形式的占位符。 */
    private val placeholderRegex = Regex("""%(\d+)\$[sd]""")

    @Test
    fun `every language exposes exactly the same key set as english`() {
        val expected = StringsEn.keys
        assertEquals("English baseline key count changed unexpectedly", 124, expected.size)

        tables.forEach { (tag, table) ->
            val missing = expected - table.keys
            val extra = table.keys - expected
            assertTrue("[$tag] missing keys: $missing", missing.isEmpty())
            assertTrue("[$tag] unexpected keys: $extra", extra.isEmpty())
        }
    }

    @Test
    fun `I18n routes every selectable language to its own table`() {
        AppLanguage.entries
            .filter { it != AppLanguage.SYSTEM }
            .forEach { language ->
                val table = I18n.tableFor(language)
                assertEquals("[$language] key set mismatch", StringsEn.keys, table.keys)
                val fileTable = tables.getValue(language.tag!!)
                assertEquals(
                    "[$language] tableFor mismatches corpus file for tag ${language.tag}",
                    fileTable,
                    table,
                )
            }
        // SYSTEM 回退到英文表
        assertEquals(StringsEn, I18n.tableFor(AppLanguage.SYSTEM))
    }

    @Test
    fun `placeholder tokens are identical across all languages`() {
        StringsEn.forEach { (key, english) ->
            val expected = placeholderRegex.findAll(english).map { it.value }.toList()
            tables.forEach { (tag, table) ->
                val actual = placeholderRegex.findAll(table.getValue(key)).map { it.value }.toList()
                assertEquals("[$tag] placeholder mismatch in '$key'", expected, actual)
            }
        }
    }

    @Test
    fun `I18n t returns non-blank text for every key in every language`() {
        AppLanguage.entries.forEach { language ->
            I18n.language = language
            I18n.tableFor(language).keys.forEach { key ->
                assertTrue("[$language] blank text for '$key'", I18n.t(key).isNotBlank())
            }
        }
        I18n.language = AppLanguage.SYSTEM
    }

    @Test
    fun `fromTag maps legacy AIbox locale tags`() {
        // 简体：zh-Hans / zh-CN / zh
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromTag("zh-Hans"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromTag("zh-CN"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromTag("zh"))
        // 繁体：zh-Hant / zh-TW / zh-HK
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromTag("zh-Hant"))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromTag("zh-TW"))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromTag("zh-HK"))
        // 意大利语
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromTag("it-IT"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromTag("it"))
        // 书面挪威语
        assertEquals(AppLanguage.NORWEGIAN_BOKMAL, AppLanguage.fromTag("nb-NO"))
        assertEquals(AppLanguage.NORWEGIAN_BOKMAL, AppLanguage.fromTag("nb"))
        assertEquals(AppLanguage.NORWEGIAN_BOKMAL, AppLanguage.fromTag("no"))
        // 葡萄牙语
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTag("pt-PT"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTag("pt"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTag("pt-BR"))
        // 其它
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTag("de-AT"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromTag("ja"))
    }

    @Test
    fun `enum tags round trip through fromTag`() {
        AppLanguage.entries
            .filter { it != AppLanguage.SYSTEM }
            .forEach { language ->
                val tag = language.tag!!
                assertEquals("exact tag '$tag'", language, AppLanguage.fromTag(tag))
                assertEquals("lowercase tag '$tag'", language, AppLanguage.fromTag(tag.lowercase()))
            }
    }

    @Test
    fun `fromTag falls back to SYSTEM for unknown or blank tags`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("   "))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("xx-YY"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("!!!"))
    }

    @Test
    fun `non-english tables are overwhelmingly translated`() {
        tables.filterKeys { it != "en" }.forEach { (tag, table) ->
            val identical = table.count { (key, value) -> StringsEn.getValue(key) == value }
            val ratio = identical.toDouble() / table.size
            // 技术术语（API Key / API Host / Token / Markdown 等）有意保留英文，
            // 个别短词也可能与英文相同（如挪威语 "Send"），但不应超过 25%。
            assertTrue(
                "[$tag] $identical/${table.size} entries identical to English (ratio=$ratio) — likely untranslated",
                ratio < 0.25,
            )
        }
    }
}
