package com.aibox.kotlin.data.database

import android.content.Context
import androidx.room.Room
import com.aibox.kotlin.core.storage.KeyValueSource
import com.aibox.kotlin.core.storage.SessionMetaRecord
import com.aibox.kotlin.core.storage.SessionMetaSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AiboxDatabase =
        Room.databaseBuilder(context, AiboxDatabase::class.java, "chatbox.db").build()

    @Provides
    @Singleton
    fun provideKeyValueDao(database: AiboxDatabase): KeyValueDao = database.keyValueDao()

    @Provides
    @Singleton
    fun provideSessionMetaDao(database: AiboxDatabase): SessionMetaDao = database.sessionMetaDao()

    @Provides
    @Singleton
    fun provideKeyValueSource(dao: KeyValueDao): KeyValueSource = RoomKeyValueSource(dao)

    @Provides
    @Singleton
    fun provideSessionMetaSource(dao: SessionMetaDao): SessionMetaSource = RoomSessionMetaSource(dao)
}

/** KV 数据源的 Room 实现。 */
@Singleton
class RoomKeyValueSource(
    private val dao: KeyValueDao,
) : KeyValueSource {
    override suspend fun get(key: String): String? = dao.getByKey(key)

    override suspend fun put(key: String, value: String?) = dao.upsert(KeyValueEntity(key, value))

    override suspend fun delete(key: String) = dao.deleteByKey(key)

    override suspend fun all(): Map<String, String> =
        dao.getAll().associate { it.key to (it.value ?: "") }
}

/** 会话列表数据源的 Room 实现。 */
@Singleton
class RoomSessionMetaSource(
    private val dao: SessionMetaDao,
) : SessionMetaSource {
    override fun observeAll(): Flow<List<SessionMetaRecord>> =
        dao.observeAll().map { list -> list.map { it.toRecord() } }

    override suspend fun upsert(record: SessionMetaRecord) = dao.upsert(record.toEntity())

    override suspend fun upsertAll(records: List<SessionMetaRecord>) =
        dao.upsertAll(records.map { it.toEntity() })

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    private fun SessionMetaEntity.toRecord() = SessionMetaRecord(
        id = id,
        name = name,
        starred = starred,
        hidden = hidden,
        archivedAt = archivedAt,
        assistantAvatarKey = assistantAvatarKey,
        picUrl = picUrl,
        backgroundImageJson = backgroundImage,
        type = type,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )

    private fun SessionMetaRecord.toEntity() = SessionMetaEntity(
        id = id,
        name = name,
        starred = starred,
        hidden = hidden,
        archivedAt = archivedAt,
        assistantAvatarKey = assistantAvatarKey,
        picUrl = picUrl,
        backgroundImage = backgroundImageJson,
        type = type,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )
}
