package com.aibox.kotlin.feature.chat.attachment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aibox.kotlin.core.common.appString
import com.aibox.kotlin.core.model.MessageContentPart
import com.aibox.kotlin.core.model.MessageFile
import com.aibox.kotlin.core.model.MessageLink
import java.io.File

/** 单条消息可展示的附件集合。 */
data class AttachmentBundle(
    val images: List<MessageContentPart.Image> = emptyList(),
    val files: List<MessageFile> = emptyList(),
    val links: List<MessageLink> = emptyList(),
) {
    val isEmpty: Boolean get() = images.isEmpty() && files.isEmpty() && links.isEmpty()
}

/**
 * 附件网格：图片缩略图（多列、点击全屏）+ 文件/链接芯片。
 *
 * [resolveBlob] 由上层注入（`BlobStore.file`），避免 UI 直接依赖存储实现。
 * 图片加载失败时显示占位图标 + 文件标识。
 */
@Composable
fun MessageAttachmentGrid(
    bundle: AttachmentBundle,
    resolveBlob: suspend (String) -> File?,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    maxColumns: Int = 3,
    maxRows: Int = 3,
) {
    if (bundle.isEmpty) return

    val imageKeys = remember(bundle.images) {
        bundle.images.map { it.storageKey }.filter { it.isNotBlank() }
    }
    val maxCells = (maxColumns * maxRows).coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth()) {
        if (imageKeys.isNotEmpty()) {
            val visible = imageKeys.take(maxCells)
            val hiddenCount = imageKeys.size - visible.size
            visible.chunked(maxColumns).forEach { rowKeys ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    rowKeys.forEach { key ->
                        val globalIndex = imageKeys.indexOf(key)
                        val isOverflowCell = hiddenCount > 0 && globalIndex == maxCells - 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(4f / 3f),
                        ) {
                            AttachmentThumbnail(
                                storageKey = key,
                                resolveBlob = resolveBlob,
                                modifier = Modifier.fillMaxSize(),
                                onClick = { onImageClick(imageKeys, globalIndex) },
                            )
                            if (isOverflowCell) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onImageClick(imageKeys, globalIndex) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(
                                            text = appString("attachment.more_count", hiddenCount),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 补齐空位，保持网格对齐
                    repeat(maxColumns - rowKeys.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        bundle.files.forEach { file ->
            AttachmentChip(
                icon = Icons.Default.Description,
                title = file.name.ifBlank { file.fileType.ifBlank { "file" } },
                subtitle = file.fileType.takeIf { it.isNotBlank() },
            )
        }

        bundle.links.forEach { link ->
            AttachmentChip(
                icon = Icons.Default.Link,
                title = link.title.ifBlank { link.url },
                subtitle = link.url,
            )
        }
    }
}

/** 图片缩略图：异步解析 blob → Coil 加载；失败显示占位 + 标识。 */
@Composable
private fun AttachmentThumbnail(
    storageKey: String,
    resolveBlob: suspend (String) -> File?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    var file by remember(storageKey) { mutableStateOf<File?>(null) }
    var resolved by remember(storageKey) { mutableStateOf(false) }

    LaunchedEffect(storageKey) {
        file = runCatching { resolveBlob(storageKey) }.getOrNull()
        resolved = true
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val resolvedFile = file
            when {
                resolvedFile != null -> AsyncImage(
                    model = resolvedFile,
                    contentDescription = appString("attachment.open_image"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                resolved -> Text(
                    text = appString("attachment.load_failed", storageKey.substringAfterLast('/')),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(6.dp),
                )

                else -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 文件 / 链接芯片。 */
@Composable
private fun AttachmentChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
