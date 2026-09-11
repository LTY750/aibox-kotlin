package com.aibox.kotlin.feature.chat.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aibox.kotlin.core.common.appString
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes

/**
 * 规范语言键（[languageKeyFor]）→ `dev.snipme:highlights` 引擎枚举。
 *
 * 引擎字节码为 Java 21（见 [HighlightModel] 注释），故本函数与下面的
 * [highlightRanges] / [highlightCode] / [CodeBlock] 一起留在本文件，
 * JVM 单测只覆盖纯逻辑部分（[languageKeyFor] / [normalizeRanges]）。
 */
fun syntaxLanguageFor(language: String?): SyntaxLanguage = when (languageKeyFor(language)) {
    "kotlin" -> SyntaxLanguage.KOTLIN
    "java" -> SyntaxLanguage.JAVA
    "python" -> SyntaxLanguage.PYTHON
    "javascript" -> SyntaxLanguage.JAVASCRIPT
    "typescript" -> SyntaxLanguage.TYPESCRIPT
    "go" -> SyntaxLanguage.GO
    "rust" -> SyntaxLanguage.RUST
    "swift" -> SyntaxLanguage.SWIFT
    "shell" -> SyntaxLanguage.SHELL
    "ruby" -> SyntaxLanguage.RUBY
    "php" -> SyntaxLanguage.PHP
    "csharp" -> SyntaxLanguage.CSHARP
    "cpp" -> SyntaxLanguage.CPP
    "c" -> SyntaxLanguage.C
    "dart" -> SyntaxLanguage.DART
    "perl" -> SyntaxLanguage.PERL
    "coffeescript" -> SyntaxLanguage.COFFEESCRIPT
    else -> SyntaxLanguage.DEFAULT
}

/**
 * 计算 [code] 的高亮区间。
 *
 * 每个色块由 `ColorHighlight.location`（`PhraseLocation`，含精确 `start`/`end`）
 * 与 `ColorHighlight.rgb` 给出；引擎异常或产出空结果时安全降级为空列表
 * （调用方回落为等宽纯文本），绝不崩溃。
 */
fun highlightRanges(code: String, language: String?, darkTheme: Boolean): List<HighlightRange> {
    if (code.isEmpty()) return emptyList()
    val raw = runCatching {
        Highlights.Builder()
            .code(code)
            .language(syntaxLanguageFor(language))
            .theme(SyntaxThemes.atom(darkTheme))
            .build()
            .getHighlights()
            .filterIsInstance<ColorHighlight>()
            .map { RawHighlight(it.location.start, it.location.end, it.rgb) }
    }.getOrDefault(emptyList())
    return normalizeRanges(raw, code.length)
}

/** 把高亮结果叠加为 [AnnotatedString]。 */
fun highlightCode(code: String, language: String?, darkTheme: Boolean): AnnotatedString {
    val ranges = highlightRanges(code, language, darkTheme)
    return buildAnnotatedString {
        append(code)
        ranges.forEach { range ->
            addStyle(SpanStyle(color = Color(range.colorArgb)), range.start, range.end)
        }
    }
}

/**
 * 围栏代码块：语言标签 + 复制按钮 + 横向滚动 + 语法高亮。
 */
@Composable
fun CodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val styled = remember(code, language, darkTheme) { highlightCode(code, language, darkTheme) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp),
            ) {
                Text(
                    text = language.ifBlank { "text" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
                if (copied) {
                    Text(
                        text = appString("chat.copied"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(28.dp),
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = appString("chat.copy"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = styled,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
            )
        }
    }
}
