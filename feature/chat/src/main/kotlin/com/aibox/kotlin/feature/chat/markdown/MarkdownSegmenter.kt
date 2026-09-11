package com.aibox.kotlin.feature.chat.markdown

/**
 * Markdown 文本切分后的最小渲染单元。
 *
 * 渲染层据此把「散文」交给 GFM 渲染器，把代码/公式/图表交给各自的专用组件，
 * 从而在不牺牲正文渲染的前提下获得代码高亮、LaTeX 与 Mermaid 能力。
 */
sealed interface MarkdownSegment {
    /** 普通 Markdown 片段（标题/列表/表格/引用/行内码/链接等）。 */
    data class Prose(val text: String) : MarkdownSegment

    /** 普通围栏代码块（已归一化语言标签）。 */
    data class Code(val language: String, val code: String) : MarkdownSegment

    /** Mermaid 图表（```mermaid 围栏）。 */
    data class Mermaid(val code: String) : MarkdownSegment

    /** 块级 LaTeX 公式（```latex 围栏或 `$$…$$`）。 */
    data class Latex(val latex: String) : MarkdownSegment
}

/** 围栏代码块的语言归类。 */
enum class FenceKind { CODE, MERMAID, LATEX }

/**
 * 纯逻辑 Markdown 切分器（无 Android / Compose 依赖，可在 JVM 单测中直接覆盖）。
 *
 * 支持：
 * - 反引号 / 波浪线围栏（``` / ~~~），最多 3 个前导空格；
 * - 围栏 info string 取第一个 token 作为语言（`kotlin title=x` → `kotlin`）；
 * - `$…$` 行内公式与 `$$…$$` 块级公式（可跨行）；
 * - 未闭合围栏容错（自 opening fence 之后全部视为代码）。
 */
object MarkdownSegmenter {

    private val FENCE_OPEN = Regex("""^\s{0,3}(`{3,}|~{3,})\s*(\S*)""")
    private val MATH_FENCE = "$$"

    /** 把 [text] 切分为有序的渲染片段；空串返回空列表。 */
    fun segment(text: String): List<MarkdownSegment> {
        if (text.isEmpty()) return emptyList()
        val segments = mutableListOf<MarkdownSegment>()
        val prose = StringBuilder()
        val lines = text.lines()

        fun flushProse() {
            val trimmed = prose.toString().trim('\n')
            prose.setLength(0)
            if (trimmed.isNotBlank()) segments.add(MarkdownSegment.Prose(trimmed))
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            val fence = FENCE_OPEN.find(line)
            if (fence != null) {
                flushProse()
                val fenceToken = fence.groupValues[1]
                val info = fence.groupValues[2]
                val body = StringBuilder()
                i++
                while (i < lines.size && !isClosingFence(lines[i], fenceToken)) {
                    body.append(lines[i]).append('\n')
                    i++
                }
                if (i < lines.size) i++ // 跳过收尾围栏
                val code = body.toString().trimEnd('\n')
                when (classifyLanguage(info)) {
                    FenceKind.MERMAID -> segments.add(MarkdownSegment.Mermaid(code))
                    FenceKind.LATEX -> segments.add(MarkdownSegment.Latex(code))
                    FenceKind.CODE -> segments.add(MarkdownSegment.Code(normalizeLanguage(info), code))
                }
                continue
            }

            val trimmedLine = line.trim()
            if (trimmedLine.startsWith(MATH_FENCE)) {
                flushProse()
                val inline = singleLineMath(trimmedLine)
                if (inline != null) {
                    segments.add(MarkdownSegment.Latex(inline))
                    i++
                    continue
                }
                val math = StringBuilder(trimmedLine.removePrefix(MATH_FENCE).trimStart())
                i++
                while (i < lines.size && !lines[i].contains(MATH_FENCE)) {
                    math.append(lines[i]).append('\n')
                    i++
                }
                if (i < lines.size) math.append(lines[i].substringBefore(MATH_FENCE))
                i++
                val latex = math.toString().trim('\n').trim()
                if (latex.isNotEmpty()) segments.add(MarkdownSegment.Latex(latex))
                continue
            }

            prose.append(line).append('\n')
            i++
        }

        flushProse()
        return segments
    }

    /** 归一化语言标签：取 info string 首个 token，转小写。 */
    fun normalizeLanguage(raw: String?): String =
        raw?.trim()?.substringBefore(' ')?.substringBefore(',')?.lowercase().orEmpty()

    /** 语言标签归类。 */
    fun classifyLanguage(raw: String?): FenceKind = when (normalizeLanguage(raw)) {
        "mermaid", "mmd" -> FenceKind.MERMAID
        "latex", "tex", "math", "katex", "stex" -> FenceKind.LATEX
        else -> FenceKind.CODE
    }

    /** 单行 `$$…$$`（长度需 > 4 且内部非空）。 */
    private fun singleLineMath(trimmed: String): String? {
        if (!trimmed.startsWith(MATH_FENCE) || !trimmed.endsWith(MATH_FENCE)) return null
        if (trimmed.length <= 2 * MATH_FENCE.length) return null
        val inner = trimmed.substring(MATH_FENCE.length, trimmed.length - MATH_FENCE.length).trim()
        return inner.ifBlank { null }
    }

    /** 收尾围栏必须与 opening 同字符且长度不短于它，且除该字符外无其它内容。 */
    private fun isClosingFence(line: String, token: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < token.length) return false
        val ch = token[0]
        if (trimmed.first() != ch) return false
        return trimmed.all { it == ch }
    }
}
