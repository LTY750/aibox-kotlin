package com.aibox.kotlin.core.storage.impl

import android.content.Context
import com.aibox.kotlin.core.storage.BlobStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** 文件 blob 存储：内容寻址（sha256）落盘于应用私有目录。 */
@Singleton
class FileBlobStore @Inject constructor(
    @ApplicationContext context: Context,
) : BlobStore {

    override val directory: File = File(context.filesDir, "blobs")

    init {
        directory.mkdirs()
    }

    override suspend fun save(bytes: ByteArray, ext: String?): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString("") { "%02x".format(it) }
        val key = "$hex." + (ext?.takeIf { it.isNotBlank() } ?: "bin")
        val target = File(directory, key)
        if (!target.exists()) {
            val tmp = File(directory, "$key.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                // 极端情况下重命名失败（如并发写同 key）：直接写入
                target.writeBytes(bytes)
                tmp.delete()
            }
        }
        return key
    }

    override suspend fun read(storageKey: String): ByteArray? {
        val file = safeFile(storageKey) ?: return null
        return if (file.exists()) file.readBytes() else null
    }

    override suspend fun readAsDataUrl(storageKey: String): String? {
        val bytes = read(storageKey) ?: return null
        val mime = mimeFor(storageKey.substringAfterLast('.', "bin"))
        val b64 = Base64.getEncoder().encodeToString(bytes)
        return "data:$mime;base64,$b64"
    }

    override suspend fun delete(storageKey: String) {
        safeFile(storageKey)?.delete()
    }

    override fun file(storageKey: String): File? {
        val target = safeFile(storageKey) ?: return null
        return if (target.exists()) target else null
    }

    /** 路径安全：拒绝目录穿越。 */
    private fun safeFile(storageKey: String): File? {
        val name = storageKey.substringAfterLast('/')
            .substringAfterLast('\\')
        if (name.isBlank() || name.contains("..")) return null
        return File(directory, name)
    }

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "json" -> "application/json"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }
}
