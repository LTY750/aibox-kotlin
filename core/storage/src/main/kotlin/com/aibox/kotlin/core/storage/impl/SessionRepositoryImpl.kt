package com.aibox.kotlin.core.storage.impl

import com.aibox.kotlin.core.model.AiboxJson
import com.aibox.kotlin.core.model.Session
import com.aibox.kotlin.core.model.SessionMeta
import com.aibox.kotlin.core.storage.KeyValueStore
import com.aibox.kotlin.core.storage.SessionMetaRecord
import com.aibox.kotlin.core.storage.SessionMetaSource
import com.aibox.kotlin.core.storage.SessionRepository
import com.aibox.kotlin.core.storage.StorageKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话仓库实现：session:<id> 全量 JSON（KV）+ session_meta 列表表双写。
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val keyValueStore: KeyValueStore,
    private val sessionMetaSource: SessionMetaSource,
) : SessionRepository {

    override fun observeSessions(): Flow<List<SessionMeta>> =
        sessionMetaSource.observeAll().map { records ->
            records.filter { !it.hidden }.map { it.toMeta() }
        }

    override suspend fun getSession(id: String): Session? {
        val raw = keyValueStore.get(StorageKeys.session(id)) ?: return null
        return runCatching {
            AiboxJson.lenient.decodeFromString(Session.serializer(), raw)
        }.getOrNull()
    }

    override suspend fun upsertSession(session: Session) {
        keyValueStore.put(StorageKeys.session(session.id), AiboxJson.lenient.encodeToString(Session.serializer(), session))
        sessionMetaSource.upsert(session.toRecord())
    }

    override suspend fun deleteSession(id: String) {
        keyValueStore.delete(StorageKeys.session(id))
        sessionMetaSource.deleteById(id)
    }

    override suspend fun renameSession(id: String, name: String) {
        val session = getSession(id) ?: return
        upsertSession(session.copy(name = name))
    }

    override suspend fun setStarred(id: String, starred: Boolean) {
        val session = getSession(id) ?: return
        upsertSession(session.copy(starred = starred))
    }

    override suspend fun archiveSession(id: String, archivedAt: Long?) {
        val session = getSession(id) ?: return
        upsertSession(session.copy(archivedAt = archivedAt))
    }

    override suspend fun upsertSessions(sessions: List<Session>) {
        for (session in sessions) {
            keyValueStore.put(
                StorageKeys.session(session.id),
                AiboxJson.lenient.encodeToString(Session.serializer(), session),
            )
        }
        sessionMetaSource.upsertAll(sessions.map { it.toRecord() })
    }

    private fun Session.toRecord(): SessionMetaRecord = SessionMetaRecord(
        id = id,
        name = name,
        starred = starred ?: false,
        hidden = hidden ?: false,
        archivedAt = archivedAt,
        assistantAvatarKey = assistantAvatarKey,
        picUrl = picUrl,
        backgroundImageJson = backgroundImage?.toString(),
        type = type,
        // 排序值：保持既有值或以创建时间参与排序
        sortOrder = System.currentTimeMillis().toDouble(),
        createdAt = messages.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
    )

    private fun SessionMetaRecord.toMeta(): SessionMeta = SessionMeta(
        id = id,
        name = name,
        starred = starred,
        hidden = hidden,
        archivedAt = archivedAt,
        assistantAvatarKey = assistantAvatarKey,
        picUrl = picUrl,
        type = type,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )
}
