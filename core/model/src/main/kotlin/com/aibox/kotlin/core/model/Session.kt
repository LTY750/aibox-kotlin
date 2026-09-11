package com.aibox.kotlin.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Session(
    /** uuid v4；未持久化的新会话使用保留 id "new"。 */
    val id: String,
    /** "chat" | "picture" | "guide"。 */
    val type: String? = null,
    val name: String = "",
    val picUrl: String? = null,
    /** 当前活跃线程的消息。 */
    val messages: List<Message> = emptyList(),
    val starred: Boolean? = null,
    val hidden: Boolean? = null,
    val archivedAt: Long? = null,
    val assistantAvatarKey: String? = null,
    /** 旧版为多态类型（url / storage-key），此处原样 JSON 保留。 */
    val backgroundImage: JsonElement? = null,
    val settings: SessionSettings? = null,
    /** 归档的话题分支。 */
    val threads: List<SessionThread> = emptyList(),
    val threadName: String? = null,
    val messageForksHash: Map<String, MessageFork> = emptyMap(),
    val compactionPoints: List<CompactionPoint> = emptyList(),
)

@Serializable
data class SessionThread(
    val id: String,
    val name: String = "",
    val messages: List<Message> = emptyList(),
    val createdAt: Long? = null,
    val compactionPoints: List<CompactionPoint> = emptyList(),
)

/** 上下文压缩点：boundary 之前（含）的消息由 summary 消息替代。 */
@Serializable
data class CompactionPoint(
    val summaryMessageId: String,
    val boundaryMessageId: String,
    val createdAt: Long,
)

/** 消息级分叉，以分叉支点消息 id 为键。 */
@Serializable
data class MessageFork(
    val position: Int = 0,
    val lists: List<ForkList> = emptyList(),
    val createdAt: Long? = null,
)

@Serializable
data class ForkList(
    val id: String,
    val messages: List<Message> = emptyList(),
)
