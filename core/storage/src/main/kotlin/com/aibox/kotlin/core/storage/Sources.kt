package com.aibox.kotlin.core.storage

import kotlinx.coroutines.flow.Flow

/**
 * KV 数据源契约：core:storage 定义，data:database 用 Room 实现并通过 Hilt 绑定。
 * （避免 core:storage → data:database 的反向依赖）
 */
interface KeyValueSource {
    suspend fun get(key: String): String?

    suspend fun put(key: String, value: String?)

    suspend fun delete(key: String)

    suspend fun all(): Map<String, String>
}

/** 会话列表行的数据源契约（session_meta 表）。 */
data class SessionMetaRecord(
    val id: String,
    val name: String,
    val starred: Boolean,
    val hidden: Boolean,
    val archivedAt: Long?,
    val assistantAvatarKey: String?,
    val picUrl: String?,
    val backgroundImageJson: String?,
    val type: String?,
    val sortOrder: Double,
    val createdAt: Long,
)

interface SessionMetaSource {
    fun observeAll(): Flow<List<SessionMetaRecord>>

    suspend fun upsert(record: SessionMetaRecord)

    suspend fun upsertAll(records: List<SessionMetaRecord>)

    suspend fun deleteById(id: String)
}
