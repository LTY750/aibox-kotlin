package com.aibox.kotlin.feature.chat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aibox.kotlin.feature.chat.markdown.CodeBlock
import com.aibox.kotlin.feature.chat.markdown.LatexBlock
import com.aibox.kotlin.feature.chat.markdown.MarkdownSegment
import com.aibox.kotlin.feature.chat.markdown.MarkdownSegmenter
import com.aibox.kotlin.feature.chat.markdown.MermaidBlock
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * 消息正文渲染（T04）。
 *
 * - 散文交给 GFM 渲染器（`com.mikepenz:multiplatform-markdown-renderer-m3`）：
 *   标题 / 列表 / 表格 / 引用 / 行内码 / 链接；
 * - 围栏代码块交给 [CodeBlock]（语法高亮 + 复制）；
 * - `mermaid` 围栏交给 [MermaidBlock]（本地资产 WebView 沙箱）；
 * - `latex`/`tex`/`math` 围栏与 `$$…$$` 交给 [LatexBlock]（jlatexmath 原生渲染）；
 * - [markdownEnabled] = false 时整体回落为纯文本（对齐设置项 `enableMarkdownRendering`）。
 *
 * 签名保持 `MarkdownText(text, modifier)` 兼容（新增参数均有默认值）。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    markdownEnabled: Boolean = true,
) {
    if (!markdownEnabled) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
        return
    }

    val segments = remember(text) { MarkdownSegmenter.segment(text) }
    val darkTheme = isSystemInDarkTheme()

    Column(modifier = modifier) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Prose -> Markdown(
                    content = segment.text,
                    colors = markdownColor(),
                    typography = markdownTypography(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                )

                is MarkdownSegment.Code -> CodeBlock(
                    language = segment.language,
                    code = segment.code,
                    darkTheme = darkTheme,
                )

                is MarkdownSegment.Mermaid -> MermaidBlock(
                    code = segment.code,
                    darkTheme = darkTheme,
                )

                is MarkdownSegment.Latex -> LatexBlock(latex = segment.latex)
            }
        }
    }
}
