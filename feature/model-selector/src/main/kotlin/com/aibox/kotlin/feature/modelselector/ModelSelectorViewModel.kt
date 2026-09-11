package com.aibox.kotlin.feature.modelselector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibox.kotlin.core.model.ProviderModelInfo
import com.aibox.kotlin.core.model.Settings
import com.aibox.kotlin.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 可选模型条目：归属 Provider + 模型信息。 */
data class ModelOption(
    val providerId: String,
    val providerName: String,
    val isCustom: Boolean,
    val model: ProviderModelInfo,
)

data class ModelSelectorState(
    val options: List<ModelOption> = emptyList(),
)

@HiltViewModel
class ModelSelectorViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<ModelSelectorState> = settingsRepository.settings
        .map { settings -> ModelSelectorState(options = buildOptions(settings)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelSelectorState())

    private fun buildOptions(settings: Settings): List<ModelOption> {
        val result = mutableListOf<ModelOption>()
        // 内置 provider：已配置（有模型列表）才显示
        for ((id, providerSettings) in settings.providers) {
            if (providerSettings.models.isEmpty()) continue
            providerSettings.models.mapTo(result) { model ->
                ModelOption(id, id, false, model.copy(providerId = id))
            }
        }
        // 自定义 provider
        for (custom in settings.customProviders) {
            val models = custom.defaultSettings?.models ?: emptyList()
            models.mapTo(result) { model ->
                ModelOption(custom.id, custom.name, true, model.copy(providerId = custom.id))
            }
        }
        return result
    }
}