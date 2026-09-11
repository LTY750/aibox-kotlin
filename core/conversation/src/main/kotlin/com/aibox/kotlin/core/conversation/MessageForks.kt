package com.aibox.kotlin.core.conversation

import com.aibox.kotlin.core.common.newId
import com.aibox.kotlin.core.model.ForkList
import com.aibox.kotlin.core.model.Message
import com.aibox.kotlin.core.model.MessageFork
import com.aibox.kotlin.core.model.MessageRole
import com.aibox.kotlin.core.model.Session

/**
 * 消息分叉（对齐旧版 message-forks.ts 契约）。
 *
 * 分支槽位模型：以分叉点消息 id（通常是用户消息）为键，
 * 活跃分支的尾部保留在 session.messages 中，其在 lists 的槽位为空；
 * 切换分支时把当前尾部写回槽位、目标分支尾部拼回 messages。
 *
 * 纯函数变换，可单测。
 */
object MessageForks {

    /** 分叉点消息之后、跳过锚定的摘要消息（摘要属于共享前缀）的尾部落点。 */
    fun tailStartIndex(messages: List<Message>, forkMessageId: String): Int {
        val forkIndex = messages.indexOfFirst { it.id == forkMessageId }
        if (forkIndex < 0) return -1
        var start = forkIndex + 1
        while (start < messages.size && messages[start].isSummary == true) start++
        return start
    }

    /**
     * 在分叉点创建新分支：当前尾部写入当前槽位，追加新的空分支并切换过去。
     * 调用方随后在新的空分支上生成内容。
     */
    fun createFork(session: Session, forkMessageId: String): Session {
        val messages = session.messages
        val tailStart = tailStartIndex(messages, forkMessageId)
        require(tailStart >= 0) { "fork message not found: $forkMessageId" }
        val tail = messages.subList(tailStart, messages.size).toList()

        val existing = session.messageForksHash[forkMessageId]
        val fork = if (existing == null) {
            MessageFork(position = 0, lists = listOf(ForkList(id = "fork_list_${newId()}", messages = emptyList())))
        } else {
            existing
        }

        val lists = fork.lists.toMutableList()
        // 当前尾部写回当前槽位
        lists[fork.position.coerceIn(0, lists.size - 1)] =
            lists[fork.position.coerceIn(0, lists.size - 1)].copy(messages = tail)
        // 追加新空分支并切换
        lists.add(ForkList(id = "fork_list_${newId()}", messages = emptyList()))
        val newFork = fork.copy(
            position = lists.size - 1,
            lists = lists,
            createdAt = fork.createdAt ?: System.currentTimeMillis(),
        )

        return session.copy(
            messages = messages.subList(0, tailStart).toList(),
            messageForksHash = session.messageForksHash + (forkMessageId to newFork),
        )
    }

    /** 切换到指定分支：当前尾部入槽，目标分支尾部拼回 messages。 */
    fun switchFork(session: Session, forkMessageId: String, toPosition: Int): Session {
        val messages = session.messages
        val tailStart = tailStartIndex(messages, forkMessageId)
        val fork = session.messageForksHash[forkMessageId] ?: return session
        if (tailStart < 0) return session
        if (toPosition !in fork.lists.indices || toPosition == fork.position) return session

        val tail = messages.subList(tailStart, messages.size).toList()
        val lists = fork.lists.toMutableList()
        lists[fork.position] = lists[fork.position].copy(messages = tail)
        val targetTail = lists[toPosition].messages
        lists[toPosition] = lists[toPosition].copy(messages = emptyList())

        return session.copy(
            messages = messages.subList(0, tailStart) + targetTail,
            messageForksHash = session.messageForksHash +
                (forkMessageId to fork.copy(position = toPosition, lists = lists)),
        )
    }

    /** UI 导航信息：分叉点处的分支数与当前位置。 */
    fun forkNavInfo(session: Session, messageId: String): ForkNavInfo? {
        val fork = session.messageForksHash[messageId] ?: return null
        if (fork.lists.size < 2) return null
        return ForkNavInfo(position = fork.position, count = fork.lists.size)
    }

    /** 从目标消息向前找最近一条可作为分叉点的用户消息。 */
    fun findForkPointBefore(messages: List<Message>, messageId: String): String? {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return null
        for (i in index downTo 0) {
            val m = messages[i]
            if (m.role == MessageRole.USER && m.isSummary != true) return m.id
        }
        return null
    }
}

data class ForkNavInfo(
    /** 当前活跃分支下标（从 0 开始）。 */
    val position: Int,
    /** 分支总数。 */
    val count: Int,
) {
    val hasPrev: Boolean get() = position > 0
    val hasNext: Boolean get() = position < count - 1
}
