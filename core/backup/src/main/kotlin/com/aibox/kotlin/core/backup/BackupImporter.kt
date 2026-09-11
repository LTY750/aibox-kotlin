package com.aibox.kotlin.core.backup

import com.aibox.kotlin.core.model.AiboxJson
import com.aibox.kotlin.core.model.Session
import com.aibox.kotlin.core.model.Settings
import com.aibox.kotlin.core.storage.BlobStore
import com.aibox.kotlin.core.storage.SessionRepository
import com.aibox.kotlin.core.storage.SettingsRepository
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val sessionsImported: Int,
    val settingsImported: Boolean,
    val resourcesImported: Int,
    val warnings: List<String>,
)

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 备份导入：支持 ZIP v2（manifest.json）与旧版单 JSON 两种格式。
 * 三阶段：读取校验 → 全量解析 → 批量提交；致命错误直接抛出，不写半截数据。
 */
@Singleton
class BackupImporter @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val blobStore: BlobStore,
) {

    suspend fun import(file: File, onProgress: (Float) -> Unit = {}): ImportResult {
        require(file.exists()) { "备份文件不存在" }
        val entries = readZipEntries(file)
        onProgress(0.15f)

        val manifestEntry = entries["manifest.json"]
        return if (manifestEntry != null) {
            importV2(entries, onProgress)
        } else {
            importLegacy(entries, onProgress)
        }
    }

    private fun readZipEntries(file: File): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                try {
                    if (entry.isDirectory) continue
                    if (!validateEntryPath(entry.name)) {
                        throw ImportException("非法的备份条目路径: ${entry.name}")
                    }
                    val bytes = zip.readBytes()
                    if (bytes.size > BackupLimits.MAX_RESOURCE_BYTES) {
                        throw ImportException("条目过大: ${entry.name}")
                    }
                    totalBytes += bytes.size
                    if (totalBytes > BackupLimits.MAX_TOTAL_BYTES) {
                        throw ImportException("备份总大小超过 4 GiB 限制")
                    }
                    if (entries.containsKey(entry.name)) {
                        throw ImportException("重复的备份条目: ${entry.name}")
                    }
                    entries[entry.name] = bytes
                } finally {
                    zip.closeEntry()
                }
            }
        }
        return entries
    }

    private suspend fun importV2(entries: Map<String, ByteArray>, onProgress: (Float) -> Unit): ImportResult {
        val manifest = decodeManifest(entries)
            ?: throw ImportException("manifest.json 解析失败")
        if (manifest.format != "chatbox-backup" || manifest.formatVersion != 2) {
            throw ImportException("不支持的备份格式: ${manifest.format} v${manifest.formatVersion}")
        }
        if (manifest.sessions.size > BackupLimits.MAX_SESSIONS) {
            throw ImportException("会话数超过 50,000 限制")
        }

        val warnings = mutableListOf<String>()
        onProgress(0.3f)

        // 1. 设置
        var settingsImported = false
        manifest.data.settings?.path?.let { path ->
            val bytes = entries[path] ?: throw ImportException("备份缺少 $path")
            if (bytes.size > BackupLimits.MAX_JSON_ENTRY_BYTES) throw ImportException("settings.json 超过 128 MiB")
            val settings = runCatching {
                AiboxJson.lenient.decodeFromString(Settings.serializer(), bytes.decodeToString())
            }.getOrElse { throw ImportException("settings.json 解析失败", it) }
            // apiKey 等敏感值由 SettingsRepository 自动路由进 SecureStore
            settingsRepository.update { settings }
            settingsImported = true
        }
        onProgress(0.45f)

        // 2. 会话
        val sessions = mutableListOf<Session>()
        for (entry in manifest.sessions) {
            val bytes = entries[entry.path]
            if (bytes == null) {
                warnings.add("会话 ${entry.id} 内容缺失，已跳过")
                continue
            }
            val session = runCatching {
                AiboxJson.lenient.decodeFromString(Session.serializer(), bytes.decodeToString())
            }.getOrNull()
            if (session == null) {
                warnings.add("会话 ${entry.id} 解析失败，已跳过")
            } else {
                sessions.add(session)
            }
        }
        if (sessions.isNotEmpty()) sessionRepository.upsertSessions(sessions)
        onProgress(0.7f)

        // 3. 资源 → BlobStore（按 resource id 存储）
        var resourcesImported = 0
        for (resource in manifest.resources) {
            val bytes = entries[resource.path] ?: continue
            val ext = resource.mimeType.substringAfterLast('/', "bin").takeIf { it != "octet-stream" } ?: "bin"
            runCatching { blobStore.save(bytes, ext = resource.filename?.substringAfterLast('.') ?: ext) }
                .onSuccess { resourcesImported++ }
                .onFailure { warnings.add("资源 ${resource.id} 保存失败") }
        }
        onProgress(1f)

        return ImportResult(
            sessionsImported = sessions.size,
            settingsImported = settingsImported,
            resourcesImported = resourcesImported,
            warnings = warnings,
        )
    }

    /** 旧版单 JSON 备份：根对象含 "settings" / "chat-sessions"。 */
    private suspend fun importLegacy(entries: Map<String, ByteArray>, onProgress: (Float) -> Unit): ImportResult {
        val mainEntry = entries.entries.firstOrNull { it.key.endsWith(".json") }
            ?: throw ImportException("无法识别的备份格式（缺少 manifest.json）")
        val bytes = mainEntry.value
        if (bytes.size > BackupLimits.MAX_JSON_ENTRY_BYTES) {
            throw ImportException("备份 JSON 超过 128 MiB 限制")
        }
        val root = runCatching {
            AiboxJson.lenient.parseToJsonElement(bytes.decodeToString())
        }.getOrElse { throw ImportException("备份 JSON 解析失败", it) }
        val obj = root as? kotlinx.serialization.json.JsonObject
            ?: throw ImportException("备份 JSON 结构不正确")

        var settingsImported = false
        obj["settings"]?.let { settingsEl ->
            val settings = runCatching {
                AiboxJson.lenient.decodeFromJsonElement(Settings.serializer(), settingsEl)
            }.getOrElse { throw ImportException("备份 settings 解析失败") }
            settingsRepository.update { settings }
            settingsImported = true
        }
        onProgress(0.5f)

        val sessions = mutableListOf<Session>()
        obj["chat-sessions"]?.let { sessionsEl ->
            val arr = sessionsEl as? kotlinx.serialization.json.JsonArray ?: return@let
            for (el in arr) {
                runCatching {
                    AiboxJson.lenient.decodeFromJsonElement(Session.serializer(), el)
                }.getOrNull()?.let { sessions.add(it) }
            }
        }
        if (sessions.isNotEmpty()) sessionRepository.upsertSessions(sessions)
        onProgress(1f)

        return ImportResult(
            sessionsImported = sessions.size,
            settingsImported = settingsImported,
            resourcesImported = 0,
            warnings = if (sessions.isEmpty() && !settingsImported) listOf("备份中未找到可导入数据") else emptyList(),
        )
    }

    private fun decodeManifest(entries: Map<String, ByteArray>): BackupManifest? {
        val bytes = entries["manifest.json"] ?: return null
        return runCatching {
            AiboxJson.lenient.decodeFromString(BackupManifest.serializer(), bytes.decodeToString())
        }.getOrNull()
    }

    private fun InputStream.readBytes(): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val n = read(chunk)
            if (n < 0) break
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }
}

/** 避免 java.io.ByteArrayOutputStream 与 kotlin.io 的歧义。 */
private typealias ByteArrayOutputStream = java.io.ByteArrayOutputStream
