package com.aibox.kotlin.core.backup

import com.aibox.kotlin.core.model.AiboxJson
import com.aibox.kotlin.core.model.Session
import com.aibox.kotlin.core.model.Settings
import com.aibox.kotlin.core.storage.SessionRepository
import com.aibox.kotlin.core.storage.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份导出：ZIP v2 格式（manifest.json 最后写入）。
 * 设置永远导出脱敏快照（apiKey 不进备份文件）。
 */
@Singleton
class BackupExporter @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
) {

    suspend fun export(output: File, includeSettings: Boolean = true, onProgress: (Float) -> Unit = {}) {
        val metas = sessionRepository.observeSessions().first()
        val sessions = metas.mapNotNull { sessionRepository.getSession(it.id) }
        onProgress(0.2f)

        val sessionEntries = mutableListOf<SessionEntry>()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            if (includeSettings) {
                val settingsJson = AiboxJson.lenient.encodeToString(
                    Settings.serializer(),
                    settingsRepository.redactedSnapshot(),
                )
                writeEntry(zip, "settings.json", settingsJson.toByteArray())
            }
            onProgress(0.4f)

            sessions.forEachIndexed { index, session ->
                val dirName = encodeSessionDir(session.id)
                val path = "sessions/$dirName/session.json"
                val bytes = AiboxJson.lenient.encodeToString(Session.serializer(), session).toByteArray()
                writeEntry(zip, path, bytes)
                sessionEntries.add(
                    SessionEntry(
                        id = session.id,
                        path = path,
                        size = bytes.size.toLong(),
                        checksum = Checksum(value = sha256Hex(bytes)),
                    ),
                )
                if (index % 20 == 0) onProgress(0.4f + 0.5f * (index + 1) / sessions.size)
            }

            // manifest 最后写入
            val manifest = BackupManifest(
                format = "chatbox-backup",
                formatVersion = 2,
                exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
                application = ApplicationInfo(name = "AIbox", version = "0.1.0", platform = "android"),
                exportItems = buildList {
                    if (includeSettings) add("setting")
                    add("conversations")
                },
                data = BackupData(
                    settings = if (includeSettings) {
                        EntryInfo(path = "settings.json")
                    } else {
                        null
                    },
                ),
                sessions = sessionEntries,
                stats = BackupStats(sessionCount = sessionEntries.size),
            )
            writeEntry(
                zip,
                "manifest.json",
                AiboxJson.lenient.encodeToString(BackupManifest.serializer(), manifest).toByteArray(),
            )
        }
        onProgress(1f)
    }

    /** 默认备份文件名：chatbox-backup-YYYY-M-D.zip。 */
    fun defaultFileName(): String {
        val now = Date()
        val y = SimpleDateFormat("yyyy", Locale.US).format(now)
        val m = SimpleDateFormat("M", Locale.US).format(now)
        val d = SimpleDateFormat("d", Locale.US).format(now)
        return "chatbox-backup-$y-$m-$d.zip"
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    /** encodeURIComponent 语义："." → "%2E"，"+" → "%20"。 */
    private fun encodeSessionDir(sessionId: String): String {
        val encoded = java.net.URLEncoder.encode(sessionId, "UTF-8")
            .replace("+", "%20")
            .replace(".", "%2E")
        return if (encoded.toByteArray().size > 200) {
            "id-" + sha256Hex(sessionId.toByteArray()).take(16)
        } else {
            encoded
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
