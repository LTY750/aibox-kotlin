package com.aibox.kotlin.core.security

/**
 * 敏感值安全存储接口（API Key、OAuth Token 等）。
 *
 * 实现必须基于 Android Keystore 加密，敏感值不得降级为明文存储，
 * 也不得进入 Room 数据库、备份文件或日志。
 */
interface SecureStore {
    fun get(key: String): String?

    /** @throws SecureStoreException 加密/解密失败时抛出，绝不回退到明文。 */
    fun put(key: String, value: String)

    fun delete(key: String)

    fun contains(key: String): Boolean

    /** 以 JSON map 形式整体读写（对应旧版 chatbox.settings.secrets keyring）。 */
    fun getJsonMap(key: String): Map<String, String>

    fun putJsonMap(key: String, value: Map<String, String>)
}

class SecureStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
