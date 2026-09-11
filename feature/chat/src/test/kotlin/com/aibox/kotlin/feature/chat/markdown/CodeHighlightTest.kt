package com.aibox.kotlin.feature.chat.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 代码高亮**纯逻辑**单测（JVM）。
 *
 * 只覆盖与引擎解耦的部分（[languageKeyFor] / [normalizeRanges]）：
 * `dev.snipme:highlights:1.1.0` 仅发布 JVM 变体且为 Java 21 字节码，
 * 在 JDK 17 单测 JVM 中无法加载，故不在此断言引擎输出（改由样例页人工核验）。
 */
class CodeHighlightTest {

    @Test
    fun `language tags map to canonical keys`() {
        assertEquals("kotlin", languageKeyFor("kotlin"))
        assertEquals("kotlin", languageKeyFor("KT"))
        assertEquals("kotlin", languageKeyFor("kts"))
        assertEquals("java", languageKeyFor("java"))
        assertEquals("python", languageKeyFor("py"))
        assertEquals("javascript", languageKeyFor("js"))
        assertEquals("typescript", languageKeyFor("ts"))
        assertEquals("go", languageKeyFor("go"))
        assertEquals("csharp", languageKeyFor("c#"))
        assertEquals("cpp", languageKeyFor("c++"))
        assertEquals("c", languageKeyFor("c"))
        assertEquals("shell", languageKeyFor("bash"))
    }

    @Test
    fun `unknown and blank languages fall back to default`() {
        assertEquals("default", languageKeyFor("brainfuck"))
        assertEquals("default", languageKeyFor(null))
        assertEquals("default", languageKeyFor("   "))
        assertEquals("default", languageKeyFor(""))
    }

    @Test
    fun `normalizeRanges returns empty for non positive length`() {
        assertTrue(normalizeRanges(listOf(RawHighlight(0, 5, 1)), 0).isEmpty())
        assertTrue(normalizeRanges(emptyList(), 10).isEmpty())
    }

    @Test
    fun `normalizeRanges clamps and drops invalid ranges`() {
        val raw = listOf(
            RawHighlight(-5, 3, 0x11),   // start 越界 → 裁剪到 0
            RawHighlight(8, 100, 0x22),  // end 越界 → 裁剪到 codeLength
            RawHighlight(5, 5, 0x33),    // 空区间 → 丢弃
            RawHighlight(9, 4, 0x44),    // start > end → 丢弃
        )
        val ranges = normalizeRanges(raw, codeLength = 10)

        assertEquals(
            listOf(HighlightRange(0, 3, 0x11), HighlightRange(8, 10, 0x22)),
            ranges,
        )
        ranges.forEach { range ->
            assertTrue(range.start >= 0)
            assertTrue(range.end <= 10)
            assertTrue(range.start < range.end)
        }
    }

    @Test
    fun `normalizeRanges sorts by start`() {
        val raw = listOf(
            RawHighlight(6, 9, 0x03),
            RawHighlight(0, 3, 0x01),
            RawHighlight(3, 6, 0x02),
        )
        assertEquals(
            listOf(0, 3, 6),
            normalizeRanges(raw, codeLength = 10).map { it.start },
        )
    }
}
