package com.aibox.kotlin.core.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.util.Locale

/**
 * 应用语言（设置项 `language` 的取值）。
 *
 * `tag` 与旧版 AIbox mobile 持久化的 locale 对齐（`zh-Hans` / `zh-Hant` /
 * `it-IT` / `nb-NO` / `pt-PT`），保证新旧版本来回切换时语言不丢失；
 * 旧版本的 `zh-CN` / `it` / `nb` / `pt` 等历史 tag 由 [fromTag] 兼容映射。
 */
enum class AppLanguage(val tag: String?, val displayLabel: String) {
    SYSTEM(null, "System"),
    ENGLISH("en", "English"),
    SIMPLIFIED_CHINESE("zh-Hans", "简体中文"),
    TRADITIONAL_CHINESE("zh-Hant", "繁體中文"),
    ARABIC("ar", "العربية"),
    GERMAN("de", "Deutsch"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    ITALIAN("it-IT", "Italiano"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    NORWEGIAN_BOKMAL("nb-NO", "Norsk bokmål"),
    PORTUGUESE("pt-PT", "Português"),
    RUSSIAN("ru", "Русский"),
    SWEDISH("sv", "Svenska"),
    ;

    companion object {
        /** 设置页可选语言（含「跟随系统」）。 */
        val selectable: List<AppLanguage> get() = entries

        /**
         * 历史/区域 tag 别名 → 语言。
         * 覆盖旧版 locale（[zh-Hans]/[zh-Hant]/[it-IT]/[nb-NO]/[pt-PT]）
         * 及常见的语言主标签。
         */
        private val ALIASES: Map<String, AppLanguage> = buildMap {
            put("en", ENGLISH)
            put("zh", SIMPLIFIED_CHINESE)
            put("zh-hans", SIMPLIFIED_CHINESE)
            put("zh-cn", SIMPLIFIED_CHINESE)
            put("zh-sg", SIMPLIFIED_CHINESE)
            put("zh-hant", TRADITIONAL_CHINESE)
            put("zh-tw", TRADITIONAL_CHINESE)
            put("zh-hk", TRADITIONAL_CHINESE)
            put("zh-mo", TRADITIONAL_CHINESE)
            put("ar", ARABIC)
            put("de", GERMAN)
            put("es", SPANISH)
            put("fr", FRENCH)
            put("it", ITALIAN)
            put("it-it", ITALIAN)
            put("ja", JAPANESE)
            put("ko", KOREAN)
            put("nb", NORWEGIAN_BOKMAL)
            put("nb-no", NORWEGIAN_BOKMAL)
            put("no", NORWEGIAN_BOKMAL)
            put("nn", NORWEGIAN_BOKMAL)
            put("pt", PORTUGUESE)
            put("pt-pt", PORTUGUESE)
            put("pt-br", PORTUGUESE)
            put("ru", RUSSIAN)
            put("sv", SWEDISH)
        }

        /**
         * 由持久化的 tag 还原语言枚举。
         *
         * 匹配顺序：枚举 tag 精确匹配 → 历史/区域别名（含主标签）→ 语言主标签匹配。
         * 无法识别时回退 [SYSTEM]，绝不抛异常。
         */
        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return SYSTEM
            val normalized = tag.trim()

            // 1) 枚举 tag 精确匹配（忽略大小写）
            entries.firstOrNull { it.tag != null && it.tag.equals(normalized, ignoreCase = true) }
                ?.let { return it }

            // 2) 历史/区域别名
            val lowered = normalized.lowercase()
            ALIASES[lowered]?.let { return it }
            ALIASES[lowered.substringBefore('-')]?.let { return it }

            // 3) 语言主标签匹配枚举 tag
            val primary = lowered.substringBefore('-')
            return entries.firstOrNull {
                it.tag != null && it.tag.substringBefore('-').lowercase() == primary
            } ?: SYSTEM
        }
    }
}

/**
 * 轻量 i18n：key → 语言词条表查找，缺失回退英文，再回退 key 本身。
 *
 * 词条本体按语言拆分到 `com.aibox.kotlin.core.common.i18n` 包下，本文件只负责
 * [AppLanguage]、[t] / [format] / [fromTag] 与「语言 → 词条表」路由。
 *
 * - Composable 内用 [appString]（语言变化触发重组）
 * - 非 Composable（ViewModel / 引擎）用 [I18n.t]
 */
object I18n {

    /** 当前语言；mutableStateOf 保证 Composable 订阅重组。 */
    var language: AppLanguage by mutableStateOf(AppLanguage.SYSTEM)

    fun resolvedTag(): String = language.tag ?: Locale.getDefault().toLanguageTag()

    /** 生效语言：SYSTEM 时解析系统区域。 */
    private fun effectiveLanguage(): AppLanguage {
        val current = language
        return if (current == AppLanguage.SYSTEM) {
            AppLanguage.fromTag(Locale.getDefault().toLanguageTag())
        } else {
            current
        }
    }

    /** 语言 → 词条表（internal，供一致性单测覆盖全部语言）。 */
    internal fun tableFor(language: AppLanguage): Map<String, String> = when (language) {
        AppLanguage.SYSTEM -> StringsEn
        AppLanguage.ENGLISH -> StringsEn
        AppLanguage.SIMPLIFIED_CHINESE -> StringsZhCn
        AppLanguage.TRADITIONAL_CHINESE -> StringsZhHant
        AppLanguage.ARABIC -> StringsAr
        AppLanguage.GERMAN -> StringsDe
        AppLanguage.SPANISH -> StringsEs
        AppLanguage.FRENCH -> StringsFr
        AppLanguage.ITALIAN -> StringsIt
        AppLanguage.JAPANESE -> StringsJa
        AppLanguage.KOREAN -> StringsKo
        AppLanguage.NORWEGIAN_BOKMAL -> StringsNb
        AppLanguage.PORTUGUESE -> StringsPt
        AppLanguage.RUSSIAN -> StringsRu
        AppLanguage.SWEDISH -> StringsSv
    }

    fun t(key: String, vararg args: Any?): String {
        val table = tableFor(effectiveLanguage())
        val raw = table[key] ?: StringsEn[key] ?: key
        return if (args.isEmpty()) raw else format(raw, args)
    }

    private fun format(template: String, args: Array<out Any?>): String {
        // 支持 %1\$s / %1\$d 位置占位；非法格式安全回退
        return try {
            String.format(template, *args)
        } catch (_: Exception) {
            template
        }
    }
}

/** Composable 内取词；语言切换时自动重组。 */
@Composable
fun appString(key: String, vararg args: Any?): String {
    I18n.language // 读状态建立订阅
    return I18n.t(key, *args)
}
