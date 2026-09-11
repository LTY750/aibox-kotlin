package com.aibox.kotlin.feature.chat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aibox.kotlin.core.model.MessageContentPart
import com.aibox.kotlin.core.model.MessageFile
import com.aibox.kotlin.core.model.MessageLink
import com.aibox.kotlin.feature.chat.attachment.AttachmentBundle
import com.aibox.kotlin.feature.chat.attachment.MessageAttachmentGrid
import com.aibox.kotlin.feature.chat.markdown.CodeBlock
import com.aibox.kotlin.feature.chat.markdown.LatexBlock
import com.aibox.kotlin.feature.chat.markdown.MermaidBlock

/**
 * T04 渲染样例页（**仅 debug 构建**）。
 *
 * 由 `BuildConfig.DEBUG` 硬守卫：release 构建下直接返回、不渲染任何内容，
 * 因此不会被普通用户触达（入口本身也只存在于 app-android 的 debug 源集）。
 *
 * 用于人工核验 GFM / 代码高亮 / LaTeX / Mermaid / 附件网格的渲染效果，
 * 并作为 QA 截图证据的来源页面。
 */
@Composable
fun RenderSampleScreen(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return

    val dark = isSystemInDarkTheme()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "T04 Render Sample (debug only)",
            style = MaterialTheme.typography.titleMedium,
        )

        SectionTitle("1. GFM Markdown")
        MarkdownText(text = SAMPLE_GFM)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("2. Code highlight (kotlin)")
        CodeBlock(language = "kotlin", code = SAMPLE_KOTLIN, darkTheme = dark)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("3. LaTeX (jlatexmath)")
        LatexBlock(latex = "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}")

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("4. Mermaid (sandboxed WebView)")
        MermaidBlock(code = SAMPLE_MERMAID, darkTheme = dark)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("5. Attachment grid (missing blob → placeholder)")
        MessageAttachmentGrid(
            bundle = AttachmentBundle(
                images = listOf(
                    MessageContentPart.Image(storageKey = "missing-image-1.png"),
                    MessageContentPart.Image(storageKey = "missing-image-2.png"),
                ),
                files = listOf(
                    MessageFile(id = "f1", name = "report.pdf", fileType = "application/pdf"),
                ),
                links = listOf(
                    MessageLink(id = "l1", url = "https://example.com/doc", title = "Example doc"),
                ),
            ),
            resolveBlob = { null },
            onImageClick = { _, _ -> },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

private val SAMPLE_GFM = """
# Heading 1

A paragraph with **bold**, *italic*, `inline code`, and a [link](https://example.com).

- First bullet
- Second bullet
  - Nested bullet

1. Ordered one
2. Ordered two

> A block quote line.

| Feature | Status |
|---------|--------|
| Tables  | OK     |
| Lists   | OK     |
""".trimIndent()

private val SAMPLE_KOTLIN = """
data class User(val id: String, val name: String) {
    fun greet(): String {
        // syntax highlight check
        return "Hello, ${'$'}name"
    }
}
""".trimIndent()

private val SAMPLE_MERMAID = """
graph TD
    A[Start] --> B{Decision}
    B -->|Yes| C[Do work]
    B -->|No| D[Stop]
    C --> E[Done]
""".trimIndent()
