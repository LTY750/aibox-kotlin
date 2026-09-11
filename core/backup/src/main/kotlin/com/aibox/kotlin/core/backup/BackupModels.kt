package com.aibox.kotlin.core.backup

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 备份 ZIP v2 的 manifest.json 模型。
 * 字段名与旧版 BackupManifestSchema 完全一致（导入兼容的关键）。
 */
@Serializable
data class BackupManifest(
    // 备份文件必须自描述：即使 AiboxJson 全局 encodeDefaults=false，
    // format/formatVersion 也要始终写出（导入方与旧版工具依赖这两个字段）。
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val format: String = "chatbox-backup",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val formatVersion: Int = 2,
    val exportedAt: String? = null,
    val application: ApplicationInfo? = null,
    val exportItems: List<String> = emptyList(),
    val sourceConfigVersion: Int? = null,
    val data: BackupData = BackupData(),
    val sessions: List<SessionEntry> = emptyList(),
    val resources: List<ResourceEntry> = emptyList(),
    val warnings: List<BackupWarning> = emptyList(),
    val stats: BackupStats? = null,
)

@Serializable
data class ApplicationInfo(
    val name: String? = null,
    val version: String? = null,
    val platform: String? = null,
)

@Serializable
data class BackupData(
    val settings: EntryInfo? = null,
    val copilots: EntryInfo? = null,
    @SerialName("sessionSettings") val sessionSettings: EntryInfo? = null,
)

@Serializable
data class EntryInfo(
    val path: String,
    val size: Long? = null,
    val checksum: Checksum? = null,
)

@Serializable
data class Checksum(
    val algorithm: String = "sha256",
    val value: String,
)

@Serializable
data class SessionEntry(
    val id: String,
    val meta: com.aibox.kotlin.core.model.SessionMeta? = null,
    val resourceIds: List<String> = emptyList(),
    val path: String,
    val size: Long? = null,
    val checksum: Checksum? = null,
)

@Serializable
data class ResourceEntry(
    val id: String,
    val originalStorageKeys: List<String> = emptyList(),
    val sessionIds: List<String> = emptyList(),
    /** session | shared | global。 */
    val scope: String = "global",
    /** utf8 | data-url-base64。 */
    val encoding: String = "utf8",
    val mimeType: String = "application/octet-stream",
    /** image | parsed-attachment | raw-attachment | parsed-link | tool-result | avatar | background | copilot-image。 */
    val kind: String = "raw-attachment",
    val filename: String? = null,
    val path: String,
    val size: Long? = null,
    val checksum: Checksum? = null,
)

@Serializable
data class BackupWarning(
    val code: String? = null,
    val itemType: String? = null,
    val itemId: String? = null,
    val message: String? = null,
)

@Serializable
data class BackupStats(
    val sessionCount: Int = 0,
    val resourceCount: Int = 0,
    val deduplicatedResourceCount: Int = 0,
    val warningCount: Int = 0,
)

/** 备份大小限制（对齐旧版契约）。 */
object BackupLimits {
    const val MAX_JSON_ENTRY_BYTES: Long = 128L * 1024 * 1024
    const val MAX_RESOURCE_BYTES: Long = 512L * 1024 * 1024
    const val MAX_TOTAL_BYTES: Long = 4L * 1024 * 1024 * 1024
    const val MAX_SESSIONS = 50_000
    const val MAX_RESOURCES = 50_000
}

/**
 * ZIP 条目路径安全校验：拒绝绝对路径、目录穿越、反斜杠与盘符。
 */
fun validateEntryPath(name: String): Boolean {
    if (name.isBlank()) return false
    if (name.startsWith("/") || name.startsWith("\\")) return false
    if (name.contains(":")) return false // 盘符（C:\）或 URL scheme
    if (name.contains("\\")) return false
    val segments = name.split('/')
    if (segments.any { it == "." || it == ".." || it.isBlank() }) return false
    return true
}
