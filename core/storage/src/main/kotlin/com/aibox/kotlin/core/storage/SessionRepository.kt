package com.aibox.kotlin.core.storage

import com.aibox.kotlin.core.model.Session
import com.aibox.kotlin.core.model.SessionMeta
import kotlinx.coroutines.flow.Flow

/** 会话仓库：session:<id> 全量 JSON + session_meta 列表表双写。 */
interface SessionRepository {
    /** 按排序规则观察会话列表（置顶优先，然后按时间倒序）。 */
    fun observeSessions(): Flow<List<SessionMeta>>

    suspend fun getSession(id: String): Session?

    /** 保存会话并同步 session_meta。 */
    suspend fun upsertSession(session: Session)

    suspend fun deleteSession(id: String)

    suspend fun renameSession(id: String, name: String)

    suspend fun setStarred(id: String, starred: Boolean)

    suspend fun archiveSession(id: String, archivedAt: Long?)

    /** 备份导入用：批量写入。 */
    suspend fun upsertSessions(sessions: List<Session>)
}
