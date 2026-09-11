package com.aibox.kotlin.feature.settingsgeneral

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibox.kotlin.core.model.Settings
import com.aibox.kotlin.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通用设置 ViewModel。
 *
 * 订阅 [SettingsRepository.settings] 作为唯一数据源，所有写操作都通过
 * `update { it.copy(...) }` 原子落盘（脱敏由仓库内部负责）。
 */
@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = settingsRepository.snapshot(),
        )

    fun setTheme(mode: Int) = update { it.copy(theme = mode) }

    fun setLanguage(tag: String?) = update { it.copy(language = tag) }

    fun setFontSize(percent: Int) = update { it.copy(fontSize = percent) }

    fun setDefaultPrompt(prompt: String) = update { it.copy(defaultPrompt = prompt) }

    fun setAutoGenerateTitle(enabled: Boolean) = update { it.copy(autoGenerateTitle = enabled) }

    fun setShowWordCount(enabled: Boolean) = update { it.copy(showWordCount = enabled) }

    fun setShowTokenCount(enabled: Boolean) = update { it.copy(showTokenCount = enabled) }

    fun setShowUsedToken(enabled: Boolean) = update { it.copy(showUsedToken = enabled) }

    fun setShowModelName(enabled: Boolean) = update { it.copy(showModelName = enabled) }

    fun setShowMessageTimestamp(enabled: Boolean) = update { it.copy(showMessageTimestamp = enabled) }

    fun setShowFirstTokenLatency(enabled: Boolean) = update { it.copy(showFirstTokenLatency = enabled) }

    fun setShowAvatar(enabled: Boolean) = update { it.copy(showAvatar = enabled) }

    fun setEnableMarkdownRendering(enabled: Boolean) = update { it.copy(enableMarkdownRendering = enabled) }

    fun setPasteLongTextAsAFile(enabled: Boolean) = update { it.copy(pasteLongTextAsAFile = enabled) }

    fun setAutoCompaction(enabled: Boolean) = update { it.copy(autoCompaction = enabled) }

    fun setCompactionThreshold(value: Double) =
        update { it.copy(compactionThreshold = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)) }

    private fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    companion object {
        const val MIN_THRESHOLD = 0.1
        const val MAX_THRESHOLD = 0.95
    }
}
