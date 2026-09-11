package com.aibox.kotlin.core.storage

import com.aibox.kotlin.core.model.Settings
import kotlinx.coroutines.flow.Flow

/**
 * 设置仓库。
 *
 * 安全模型与旧版一致：写入时抽取敏感路径（apiKey、oauth token 等）到
 * SecureStore keyring（StorageKeys.SETTINGS_SECRETS），明文快照（已脱敏）
 * 进 KV 存储；读取时在内存中合并还原。备份/导出永远只看到脱敏快照。
 */
interface SettingsRepository {
    /** 合并敏感值后的完整设置流。 */
    val settings: Flow<Settings>

    /** 当前完整设置（含敏感值）。 */
    fun snapshot(): Settings

    /** 原子更新；transform 收到完整设置并返回新设置。 */
    suspend fun update(transform: (Settings) -> Settings)

    /** 已脱敏的设置快照（用于备份导出）。 */
    fun redactedSnapshot(): Settings
}
