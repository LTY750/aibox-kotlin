package com.aibox.kotlin.core.model

import kotlinx.serialization.Serializable

/** 会话列表行（对应 session_meta 表与备份 manifest 的 meta 字段）。 */
@Serializable
data class SessionMeta(
    val id: String,
    val name: String = "",
    val starred: Boolean = false,
    val hidden: Boolean = false,
    val archivedAt: Long? = null,
    val assistantAvatarKey: String? = null,
    val picUrl: String? = null,
    val backgroundImage: kotlinx.serialization.json.JsonElement? = null,
    val type: String? = null,
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0,
)
