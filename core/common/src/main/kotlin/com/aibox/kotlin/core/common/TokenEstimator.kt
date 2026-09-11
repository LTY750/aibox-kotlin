package com.aibox.kotlin.core.common

/**
 * 本地 token 估算（无词典依赖的启发式，对齐旧版 cl100k 量级）：
 * - CJK 字符 ≈ 1 token/字
 * - 其他文本 ≈ 4 字符/token
 * - 每条消息固定开销 4 token
 */
object TokenEstimator {

    fun estimateText(text: String): Int {
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (isCjk(ch)) cjk++ else other++
        }
        return cjk + (other + 3) / 4
    }

    private fun isCjk(ch: Char): Boolean {
        val block = Character.UnicodeBlock.of(ch) ?: return false
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
            block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }

    fun estimateMessage(parts: List<String>): Int =
        parts.sumOf { estimateText(it) } + 4
}
