package com.aibox.kotlin.core.storage.impl

import com.aibox.kotlin.core.storage.KeyValueSource
import com.aibox.kotlin.core.storage.KeyValueStore
import javax.inject.Inject
import javax.inject.Singleton

/** KV 存储实现：委托给数据源（Room key_value 表）。 */
@Singleton
class RoomKeyValueStore @Inject constructor(
    private val source: KeyValueSource,
) : KeyValueStore {
    override suspend fun get(key: String): String? = source.get(key)

    override suspend fun put(key: String, value: String) = source.put(key, value)

    override suspend fun delete(key: String) = source.delete(key)

    override suspend fun all(): Map<String, String> = source.all()
}
