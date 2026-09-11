package com.aibox.kotlin.core.storage.impl

import com.aibox.kotlin.core.model.AiboxJson
import com.aibox.kotlin.core.model.Settings
import com.aibox.kotlin.core.security.SecureStore
import com.aibox.kotlin.core.storage.KeyValueStore
import com.aibox.kotlin.core.storage.SettingsRepository
import com.aibox.kotlin.core.storage.StorageKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设置仓库实现。
 *
 * 安全模型：持久层只存脱敏快照（apiKey 置空），
 * 敏感值整体以 JSON keyring 存于 SecureStore（Keystore 加密）；
 * 读取时内存中合并还原。备份/导出只取 redactedSnapshot()。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val keyValueStore: KeyValueStore,
    private val secureStore: SecureStore,
) : SettingsRepository {

    private val mutex = Mutex()
    private val state = MutableStateFlow(Settings())

    init {
        // 构造期同步加载（Application 启动时在 IO 线程调用 init()）
    }

    /** 应用启动时调用：加载持久化设置并合并敏感值。 */
    suspend fun init() {
        mutex.withLock {
            state.value = loadFromStorage()
        }
    }

    private suspend fun loadFromStorage(): Settings {
        val raw = keyValueStore.get(StorageKeys.SETTINGS) ?: return Settings()
        val redacted = runCatching {
            AiboxJson.lenient.decodeFromString(Settings.serializer(), raw)
        }.getOrDefault(Settings())
        val secrets = runCatching {
            secureStore.getJsonMap(StorageKeys.SETTINGS_SECRETS)
        }.getOrDefault(emptyMap())
        return SettingsSecrets.restore(redacted, secrets)
    }

    override val settings: Flow<Settings> = state

    override fun snapshot(): Settings = state.value

    override fun redactedSnapshot(): Settings = SettingsSecrets.extract(state.value).first

    override suspend fun update(transform: (Settings) -> Settings) {
        mutex.withLock {
            val next = transform(state.value)
            val (redacted, secrets) = SettingsSecrets.extract(next)
            // 先写敏感值（失败则不落明文），再写脱敏快照
            secureStore.putJsonMap(StorageKeys.SETTINGS_SECRETS, secrets)
            keyValueStore.put(StorageKeys.SETTINGS, AiboxJson.lenient.encodeToString(Settings.serializer(), redacted))
            state.value = next
        }
    }
}
