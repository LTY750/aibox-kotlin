package com.aibox.kotlin.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 消息附件（`@Immutable` 便于 Compose 跳过未变化的消息项）。 */
@Immutable
@Serializable
data class MessageFile(
    val id: String = "",
    val name: String = "",
    val fileType: String = "",
    /** 解析后内容的 blob 键。 */
    val storageKey: String? = null,
    /** 原始文件的 blob 键。 */
    val rawStorageKey: String? = null,
    val url: String? = null,
    val ragMode: String? = null,
    val tokenCountMap: Map<String, Long>? = null,
    val lineCount: Long? = null,
    val byteLength: Long? = null,
)

/** 消息中的链接附件（`@Immutable` 便于 Compose 跳过未变化的消息项）。 */
@Immutable
@Serializable
data class MessageLink(
    val id: String = "",
    val url: String = "",
    val title: String = "",
    val storageKey: String? = null,
    val tokenCountMap: Map<String, Long>? = null,
    val lineCount: Long? = null,
    val byteLength: Long? = null,
)
