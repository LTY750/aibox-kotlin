package com.aibox.kotlin.feature.chat.markdown

import android.graphics.Color as AndroidColor
import android.widget.ImageView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aibox.kotlin.core.common.appString
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * 块级 LaTeX 公式渲染（`ru.noties:jlatexmath-android`，纯原生、无网络）。
 *
 * 目标色跟随主题 [foreground]，公式按 Drawable 渲染进 [ImageView]；
 * 解析失败时降级为可读错误文案，绝不崩溃。
 */
@Composable
fun LatexBlock(
    latex: String,
    modifier: Modifier = Modifier,
    foreground: Color = MaterialTheme.colorScheme.onSurface,
) {
    val argb = foreground.toArgb()
    val drawable = remember(latex, argb) {
        runCatching {
            JLatexMathDrawable.builder(latex)
                .textSize(TEXT_SIZE_PX)
                .padding(PADDING_PX)
                .background(AndroidColor.TRANSPARENT)
                // jlatexmath 的公式着色入口是 color(int)（并无 foreground）
                .color(argb)
                .align(JLatexMathDrawable.ALIGN_CENTER)
                .build()
        }.getOrNull()
    }

    if (drawable == null) {
        Text(
            text = appString("markdown.latex_error"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(vertical = 4.dp),
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        setImageDrawable(drawable)
                        adjustViewBounds = true
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                    }
                },
            )
        }
    }
}

private const val TEXT_SIZE_PX = 52f
private const val PADDING_PX = 10
