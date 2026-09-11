package com.aibox.kotlin.feature.chat.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MarkdownSegmenter] 纯逻辑单测（JVM，无 Android 依赖）。
 *
 * 覆盖：普通散文、各类围栏（``` / ~~~）、mermaid/latex 归类、`$…$` 公式、
 * 未闭合围栏容错、语言归一化与边界输入。
 */
class MarkdownSegmenterTest {

    @Test
    fun `plain prose becomes a single segment`() {
        val segments = MarkdownSegmenter.segment("hello **world**")
        assertEquals(listOf(MarkdownSegment.Prose("hello **world**")), segments)
    }

    @Test
    fun `empty and blank inputs produce no segments`() {
        assertTrue(MarkdownSegmenter.segment("").isEmpty())
        assertTrue(MarkdownSegmenter.segment("\n\n   \n").isEmpty())
    }

    @Test
    fun `fenced code block is extracted with normalized language`() {
        val text = "before\n\n```Kotlin title=demo\nval x = 1\nval y = 2\n```\n\nafter"
        val segments = MarkdownSegmenter.segment(text)

        assertEquals(3, segments.size)
        assertEquals(MarkdownSegment.Prose("before"), segments[0])
        assertEquals(MarkdownSegment.Code("kotlin", "val x = 1\nval y = 2"), segments[1])
        assertEquals(MarkdownSegment.Prose("after"), segments[2])
    }

    @Test
    fun `mermaid fence is classified as mermaid`() {
        val segments = MarkdownSegmenter.segment("```mermaid\ngraph TD\nA-->B\n```")
        assertEquals(1, segments.size)
        assertEquals(MarkdownSegment.Mermaid("graph TD\nA-->B"), segments[0])
    }

    @Test
    fun `latex fence is classified as latex`() {
        val segments = MarkdownSegmenter.segment("```tex\nE = mc^2\n```")
        assertEquals(listOf(MarkdownSegment.Latex("E = mc^2")), segments)
    }

    @Test
    fun `multi line dollar math is captured and trimmed`() {
        val segments = MarkdownSegmenter.segment("intro\n\n\$\$\na^2 + b^2 = c^2\n\$\$\n\nend")
        assertEquals(3, segments.size)
        assertEquals(MarkdownSegment.Prose("intro"), segments[0])
        assertEquals(MarkdownSegment.Latex("a^2 + b^2 = c^2"), segments[1])
        assertEquals(MarkdownSegment.Prose("end"), segments[2])
    }

    @Test
    fun `single line dollar math is captured`() {
        val segments = MarkdownSegmenter.segment("\$\$x^2 + y^2\$\$")
        assertEquals(listOf(MarkdownSegment.Latex("x^2 + y^2")), segments)
    }

    @Test
    fun `unterminated fence swallows remaining lines`() {
        val segments = MarkdownSegmenter.segment("```kotlin\nval a = 1\nval b = 2")
        assertEquals(listOf(MarkdownSegment.Code("kotlin", "val a = 1\nval b = 2")), segments)
    }

    @Test
    fun `tilde fence is supported`() {
        val segments = MarkdownSegmenter.segment("~~~python\nprint(1)\n~~~")
        assertEquals(listOf(MarkdownSegment.Code("python", "print(1)")), segments)
    }

    @Test
    fun `normalizeLanguage takes first token and lowercases`() {
        assertEquals("kotlin", MarkdownSegmenter.normalizeLanguage("Kotlin title=x"))
        assertEquals("js", MarkdownSegmenter.normalizeLanguage("  JS  "))
        assertEquals("", MarkdownSegmenter.normalizeLanguage(null))
        assertEquals("", MarkdownSegmenter.normalizeLanguage("   "))
    }

    @Test
    fun `classifyLanguage routes known aliases`() {
        assertEquals(FenceKind.MERMAID, MarkdownSegmenter.classifyLanguage("mermaid"))
        assertEquals(FenceKind.MERMAID, MarkdownSegmenter.classifyLanguage("mmd"))
        assertEquals(FenceKind.LATEX, MarkdownSegmenter.classifyLanguage("latex"))
        assertEquals(FenceKind.LATEX, MarkdownSegmenter.classifyLanguage("math"))
        assertEquals(FenceKind.CODE, MarkdownSegmenter.classifyLanguage("kotlin"))
        assertEquals(FenceKind.CODE, MarkdownSegmenter.classifyLanguage(null))
    }

    @Test
    fun `prose across code blocks keeps blank lines internally`() {
        val segments = MarkdownSegmenter.segment("line1\nline2\n\nline3")
        assertEquals(listOf(MarkdownSegment.Prose("line1\nline2\n\nline3")), segments)
    }
}
