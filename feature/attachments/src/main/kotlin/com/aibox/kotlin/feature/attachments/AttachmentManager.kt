package com.aibox.kotlin.feature.attachments

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.aibox.kotlin.core.common.newId
import com.aibox.kotlin.core.model.MessageFile
import com.aibox.kotlin.core.storage.BlobStore
import javax.inject.Inject
import javax.inject.Singleton

/** 附件上限（单文件），超过则拒绝导入，防止内存暴涨。 */
const val MAX_ATTACHMENT_BYTES: Long = 32L * 1024 * 1024

/**
 * 附件导入：Uri → BlobStore → MessageFile。
 * 数据一律拷贝进应用私有目录，不持有外部 Uri（旧版 localPath 语义在移动端本就受限）。
 */
@Singleton
class AttachmentManager @Inject constructor(
    private val blobStore: BlobStore,
) {

    suspend fun importUris(uris: List<Uri>, resolver: ContentResolver): List<MessageFile> {
        return uris.mapNotNull { uri -> importUri(uri, resolver) }
    }

    suspend fun importUri(uri: Uri, resolver: ContentResolver): MessageFile? {
        return try {
            val name = queryDisplayName(uri, resolver) ?: "file"
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.size > MAX_ATTACHMENT_BYTES) return null
            val ext = name.substringAfterLast('.', "").ifBlank { mime.substringAfter('/', "bin") }
            val key = blobStore.save(bytes, ext = ext)
            MessageFile(
                id = newId(),
                name = name,
                fileType = mime,
                rawStorageKey = key,
                byteLength = bytes.size.toLong(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDisplayName(uri: Uri, resolver: ContentResolver): String? {
        return try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment
    }
}
