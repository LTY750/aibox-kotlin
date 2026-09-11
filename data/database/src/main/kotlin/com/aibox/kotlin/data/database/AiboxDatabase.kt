package com.aibox.kotlin.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [KeyValueEntity::class, SessionMetaEntity::class, ImageGenerationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AiboxDatabase : RoomDatabase() {
    abstract fun keyValueDao(): KeyValueDao

    abstract fun sessionMetaDao(): SessionMetaDao

    abstract fun imageGenerationDao(): ImageGenerationDao
}
