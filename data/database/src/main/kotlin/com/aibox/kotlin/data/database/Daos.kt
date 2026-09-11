package com.aibox.kotlin.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyValueDao {
    @Query("SELECT `value` FROM key_value WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KeyValueEntity)

    @Query("DELETE FROM key_value WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT `key`, `value` FROM key_value")
    suspend fun getAll(): List<KeyValueEntity>

    @Query("DELETE FROM key_value")
    suspend fun deleteAll()
}

@Dao
interface SessionMetaDao {
    @Query("SELECT * FROM session_meta ORDER BY starred DESC, sort_order DESC")
    fun observeAll(): Flow<List<SessionMetaEntity>>

    @Query("SELECT * FROM session_meta WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SessionMetaEntity>)

    @Query("DELETE FROM session_meta WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ImageGenerationDao {
    @Query("SELECT * FROM image_generation ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ImageGenerationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImageGenerationEntity)

    @Query("DELETE FROM image_generation WHERE id = :id")
    suspend fun deleteById(id: String)
}
