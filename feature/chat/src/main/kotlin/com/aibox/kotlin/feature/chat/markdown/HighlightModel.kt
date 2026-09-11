package com.aibox.kotlin.feature.chat.markdown

/**
 * 代码高亮的**纯逻辑模型**（不含任何高亮引擎 / Android 依赖）。
 *
 * 拆出本文件的动机有两点：
 * 1. 边界归一化与语言映射是纯函数，可在 JVM 单测中直接覆盖；
 * 2. `dev.snipme:highlights:1.1.0` 仅发布 JVM 变体且为 **Java 21 字节码**
 *    （class file 65），在 JDK 17 的单测 JVM 中无法加载。把纯逻辑与引擎调用分离后，
 *    单测只加载本文件，从而避免 `UnsupportedClassVersionError`。
 */

/** 高亮区间（半开区间 `[start, end)`，颜色为 ARGB Int）。 */
data class HighlightRange(val start: Int, val end: Int, val colorArgb: Int)

/** 与引擎解耦的「原始色块」表示。 */
data class RawHighlight(val start: Int, val end: Int, val rgb: Int)

/**
 * 围栏语言标签 → 规范语言键（纯逻辑，未知语言回落 `default`）。
 *
 * 与引擎枚举解耦：既便于单测，也让语言映射在引擎升级时保持不变。
 */
fun languageKeyFor(language: String?): String = when (language?.lowercase()?.trim()) {
    "kotlin", "kt", "kts" -> "kotlin"
    "java" -> "java"
    "python", "py" -> "python"
    "javascript", "js", "node", "jsx" -> "javascript"
    "typescript", "ts", "tsx" -> "typescript"
    "go", "golang" -> "go"
    "rust", "rs" -> "rust"
    "swift" -> "swift"
    "bash", "sh", "shell", "zsh", "console" -> "shell"
    "ruby", "rb" -> "ruby"
    "php" -> "php"
    "csharp", "cs", "c#" -> "csharp"
    "cpp", "c++", "cxx", "cc" -> "cpp"
    "c" -> "c"
    "dart" -> "dart"
    "perl", "pl" -> "perl"
    "coffeescript", "coffee" -> "coffeescript"
    else -> "default"
}

/**
 * 把引擎产出的原始色块归一化为合法、有序、不越界的高亮区间（纯逻辑）。
 *
 * - 裁剪到 `[0, codeLength]`；
 * - 丢弃 `start >= end` 的空/非法区间；
 * - 按起点排序，保证 `addStyle` 叠加顺序稳定。
 */
fun normalizeRanges(raw: List<RawHighlight>, codeLength: Int): List<HighlightRange> {
    if (codeLength <= 0) return emptyList()
    return raw
        .mapNotNull { highlight ->
            val start = highlight.start.coerceIn(0, codeLength)
            val end = highlight.end.coerceIn(start, codeLength)
            if (start < end) HighlightRange(start, end, highlight.rgb) else null
        }
        .sortedBy { it.start }
}
