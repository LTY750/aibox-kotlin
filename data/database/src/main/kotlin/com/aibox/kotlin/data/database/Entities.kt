package com.aibox.kotlin.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** KV 表：与旧版 chatbox.db key_value 完全一致。 */
@Entity(tableName = "key_value")
data class KeyValueEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String?,
)

/** 会话列表表：与旧版 chatbox-session-meta session_meta v2 一致。 */
@Entity(
    tableName = "session_meta",
    indices = [Index("sort_order")],
)
data class SessionMetaEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "name", defaultValue = "")
    val name: String = "",

    @ColumnInfo(name = "starred", defaultValue = "0")
    val starred: Boolean = false,

    @ColumnInfo(name = "hidden", defaultValue = "0")
    val hidden: Boolean = false,

    @ColumnInfo(name = "archived_at")
    val archivedAt: Long? = null,

    @ColumnInfo(name = "assistant_avatar_key")
    val assistantAvatarKey: String? = null,

    @ColumnInfo(name = "pic_url")
    val picUrl: String? = null,

    /** JSON ImageSource。 */
    @ColumnInfo(name = "background_image")
    val backgroundImage: String? = null,

    @ColumnInfo(name = "type")
    val type: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Double,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/** 图片生成历史表：与旧版 chatbox-image-generation v2 一致。 */
@Entity(
    tableName = "image_generation",
    indices = [Index("created_at")],
)
data class ImageGenerationEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "prompt")
    val prompt: String,

    /** JSON 数组：storage keys。 */
    @ColumnInfo(name = "reference_images", defaultValue = "[]")
    val referenceImages: String = "[]",

    /** JSON 数组：storage keys。 */
    @ColumnInfo(name = "generated_images", defaultValue = "[]")
    val generatedImages: String = "[]",

    @ColumnInfo(name = "generated_image_thumbnails")
    val generatedImageThumbnails: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "model_provider")
    val modelProvider: String,

    @ColumnInfo(name = "model_id")
    val modelId: String,

    @ColumnInfo(name = "dalle_style")
    val dalleStyle: String? = null,

    @ColumnInfo(name = "image_generate_num")
    val imageGenerateNum: Int? = null,

    /** pending | generating | done | error。 */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "parent_id")
    val parentId: String? = null,

    @ColumnInfo(name = "error")
    val error: String? = null,

    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,

    @ColumnInfo(name = "error_item_uuid")
    val errorItemUuid: String? = null,

    @ColumnInfo(name = "task_id")
    val taskId: String? = null,

    @ColumnInfo(name = "aspect_ratio")
    val aspectRatio: String? = null,

    @ColumnInfo(name = "source")
    val source: String? = null,
)
