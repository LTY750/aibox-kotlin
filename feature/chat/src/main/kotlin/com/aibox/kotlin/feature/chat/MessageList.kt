package com.aibox.kotlin.feature.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aibox.kotlin.core.common.appString
import com.aibox.kotlin.core.model.Message
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 消息列表（T04 / D2 修复）。
 *
 * **原缺陷（D2）**：以 `lastOrNull()?.contentParts?.size` 作为 [LaunchedEffect] 的键，
 * 流式增量导致该键每 token 变化 → 滚动副作用反复重启 → 整列表重组与滚动抖动。
 *
 * **修复**：
 * 1. 新消息到达才做一次**动画**滚动，副作用键收窄为「消息条数」；
 * 2. 流式跟随改为**订阅布局快照**（[snapshotFlow]）后按「是否贴近底部」决定是否
 *    `scrollToItem`（**非动画**，避免动画不断重启），不再使用会随 token 变化的键；
 * 3. `items` 保持 `key = message.id`，`MessageBubble` 只接收最小必要状态
 *    （展示选项 + blob 解析器 + 回调），配合模型 `@Immutable` 使未变化的消息可被跳过。
 */
@Composable
fun MessageList(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
    onEditRequest: (Message) -> Unit = {},
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    val messageCount = state.messages.size
    val lastMessageId = state.messages.lastOrNull()?.id
    val display = state.display

    // ① 新消息到达：以「消息条数」为键做一次动画滚动到底（流式增量不改变条数）。
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    // ② 流式跟随：仅在用户贴近底部时吸附到底；用布局快照驱动，不产生整列表重组。
    LaunchedEffect(listState) {
        snapshotFlow { listState.bottomProbe() }
            .distinctUntilChanged()
            .collect { probe ->
                if (probe.totalItemsCount > 0 && probe.isNearBottom) {
                    listState.scrollToItem(probe.totalItemsCount - 1)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
    ) {
        items(state.messages, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                isLastMessage = message.id == lastMessageId,
                generating = state.generating,
                forkNav = state.forks[message.id],
                display = display,
                resolveBlob = viewModel::blobFile,
                onImageClick = onImageClick,
                onEditRequest = onEditRequest,
                onRegenerateAt = { viewModel.regenerateAt(message.id) },
                onContinue = viewModel::continueGeneration,
                onSwitchFork = { toPosition -> viewModel.switchFork(message.id, toPosition) },
            )
        }
        if (state.messages.isEmpty()) {
            item {
                Text(
                    text = appString("chat.empty_start"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

/** 布局快照探针：内容增长或滚动都会改变它，但不会引入随 token 变化的副作用键。 */
data class BottomProbe(
    val totalItemsCount: Int,
    val lastVisibleIndex: Int,
    val lastVisibleBottom: Int,
) {
    /** 末尾项可见即视为「贴近底部」（生成中保持跟随；用户上翻后自动停止）。 */
    val isNearBottom: Boolean
        get() = totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 1
}

private fun LazyListState.bottomProbe(): BottomProbe {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull()
    return BottomProbe(
        totalItemsCount = info.totalItemsCount,
        lastVisibleIndex = lastVisible?.index ?: -1,
        lastVisibleBottom = lastVisible?.let { it.offset + it.size } ?: 0,
    )
}
