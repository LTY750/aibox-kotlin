package com.aibox.kotlin.core.storage

/** 通用 KV 存储（对应旧版 chatbox.db 的 key_value 表）。 */
interface KeyValueStore {
    suspend fun get(key: String): String?

    suspend fun put(key: String, value: String)

    suspend fun delete(key: String)

    suspend fun all(): Map<String, String>
}

/** 与旧版一致的存储键。 */
object StorageKeys {
    const val SETTINGS = "settings"
    const val CONFIG_VERSION = "configVersion"
    const val CONFIGS = "configs"
    const val CHAT_SESSION_SETTINGS = "chat-session-settings"
    const val PICTURE_SESSION_SETTINGS = "picture-session-settings"

    /** 敏感值 keyring（SecureStore 中的键）。 */
    const val SETTINGS_SECRETS = "chatbox.settings.secrets"

    fun session(id: String): String = "session:$id"
}
