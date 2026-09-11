package com.aibox.kotlin.data.securestorage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.aibox.kotlin.core.model.AiboxJson
import com.aibox.kotlin.core.security.SecureStore
import com.aibox.kotlin.core.security.SecureStoreException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore 加密的敏感值存储。
 * 契约对齐旧版 SecureStoragePlugin：AES/GCM/NoPadding，
 * 密钥别名 chatbox_secure_storage_key，密文与 IV（Base64）存于
 * SharedPreferences("chatbox_secure_storage") 的 <key>.value / <key>.iv。
 */
@Singleton
class KeystoreSecureStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecureStore {

    private val prefs = context.getSharedPreferences("chatbox_secure_storage", Context.MODE_PRIVATE)

    override fun get(key: String): String? {
        val value = prefs.getString("$key.value", null) ?: return null
        val iv = prefs.getString("$key.iv", null)
            ?: throw SecureStoreException("Missing IV for secure value $key")
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH, decode(iv)))
            String(cipher.doFinal(decode(value)), Charsets.UTF_8)
        } catch (e: SecureStoreException) {
            throw e
        } catch (e: Exception) {
            throw SecureStoreException("Unable to decrypt secure value for $key", e)
        }
    }

    override fun put(key: String, value: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // Keystore 密钥默认要求随机化加密：加密时不允许外部 IV，
            // 由 Keystore 生成（cipher.iv 读出持久化），解密时再传回。
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString("$key.value", encode(encrypted))
                .putString("$key.iv", encode(iv))
                .apply()
        } catch (e: Exception) {
            throw SecureStoreException("Unable to encrypt secure value for $key", e)
        }
    }

    override fun delete(key: String) {
        prefs.edit()
            .remove("$key.value")
            .remove("$key.iv")
            .apply()
    }

    override fun contains(key: String): Boolean = prefs.contains("$key.value")

    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    override fun getJsonMap(key: String): Map<String, String> {
        val raw = get(key) ?: return emptyMap()
        return runCatching {
            AiboxJson.lenient.decodeFromString(mapSerializer, raw)
        }.getOrElse { throw SecureStoreException("Corrupted secure map for $key", it) }
    }

    override fun putJsonMap(key: String, value: Map<String, String>) {
        val raw = AiboxJson.lenient.encodeToString(mapSerializer, value)
        put(key, raw)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        // 不存在则生成
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "chatbox_secure_storage_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH = 128
        private const val KEY_SIZE = 256
    }
}
