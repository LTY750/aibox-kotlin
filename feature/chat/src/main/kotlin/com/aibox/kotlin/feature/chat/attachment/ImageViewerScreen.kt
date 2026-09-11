package com.aibox.kotlin.feature.chat.attachment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aibox.kotlin.core.common.appString
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import java.io.File

/** 全屏查看器请求：图片 key 列表 + 起始下标。 */
data class ImageViewerRequest(
    val keys: List<String>,
    val startIndex: Int = 0,
)

/**
 * 全屏图片查看器：左右翻页 + 双指缩放 / 双击放大（telephoto）。
 *
 * 解析失败时显示占位图标与图片标识，不会因缺图而崩溃。
 */
@Composable
fun ImageViewerScreen(
    request: ImageViewerRequest,
    resolveBlob: suspend (String) -> File?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (request.keys.isEmpty()) return
    val safeStart = request.startIndex.coerceIn(0, request.keys.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeStart) { request.keys.size }

    Surface(color = Color.Black, modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ViewerPage(
                    storageKey = request.keys[page],
                    resolveBlob = resolveBlob,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${request.keys.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = appString("common.back"),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerPage(
    storageKey: String,
    resolveBlob: suspend (String) -> File?,
) {
    var file by remember(storageKey) { mutableStateOf<File?>(null) }
    var resolved by remember(storageKey) { mutableStateOf(false) }

    LaunchedEffect(storageKey) {
        file = runCatching { resolveBlob(storageKey) }.getOrNull()
        resolved = true
    }

    val resolvedFile = file
    when {
        resolvedFile != null -> ZoomableAsyncImage(
            model = resolvedFile,
            contentDescription = appString("attachment.image_title"),
            modifier = Modifier.fillMaxSize(),
        )

        resolved -> Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = appString("attachment.load_failed", storageKey.substringAfterLast('/')),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
