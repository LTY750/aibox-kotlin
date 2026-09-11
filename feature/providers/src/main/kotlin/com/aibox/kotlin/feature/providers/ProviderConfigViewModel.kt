package com.aibox.kotlin.feature.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibox.kotlin.core.model.ProviderModelInfo
import com.aibox.kotlin.core.model.ProviderSettings
import com.aibox.kotlin.core.provider.ProviderFactory
import com.aibox.kotlin.core.provider.ProviderRegistry
import com.aibox.kotlin.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderConfigState(
    val providerId: String,
    val name: String,
    val apiKey: String = "",
    val apiHost: String = "",
    val models: List<ProviderModelInfo> = emptyList(),
    val fetchingModels: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ProviderConfigViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val providerFactory: ProviderFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(ProviderConfigState("", ""))
    val state: StateFlow<ProviderConfigState> = _state

    fun load(providerId: String) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val existing = settings.providers[providerId]
            val definition = ProviderRegistry.byId(providerId)
            _state.value = ProviderConfigState(
                providerId = providerId,
                name = definition?.name ?: providerId,
                apiKey = existing?.apiKey ?: "",
                apiHost = existing?.apiHost ?: definition?.defaultApiHost ?: "",
                models = existing?.models ?: emptyList(),
            )
        }
    }

    fun updateApiKey(key: String) = _state.update { it.copy(apiKey = key) }

    fun updateApiHost(host: String) = _state.update { it.copy(apiHost = host) }

    fun save() {
        val current = _state.value
        viewModelScope.launch {
            try {
                settingsRepository.update { settings ->
                    val providers = settings.providers.toMutableMap()
                    val old = providers[current.providerId] ?: ProviderSettings()
                    providers[current.providerId] = old.copy(
                        apiKey = current.apiKey.takeIf { it.isNotBlank() },
                        apiHost = current.apiHost.takeIf { it.isNotBlank() },
                        models = current.models,
                    )
                    settings.copy(providers = providers)
                }
                _state.update { it.copy(message = "已保存") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "保存失败: ${e.message}") }
            }
        }
    }

    /** 从远端拉取模型列表（先保存当前配置）。 */
    fun fetchModels() {
        val current = _state.value
        if (current.fetchingModels) return
        viewModelScope.launch {
            _state.update { it.copy(fetchingModels = true, message = null) }
            save()
            try {
                val settings = settingsRepository.settings.first()
                val providerSettings = settings.providers[current.providerId] ?: ProviderSettings()
                val provider = providerFactory.create(current.providerId, providerSettings, null)
                val models = provider.listModels()
                if (models.isEmpty()) {
                    _state.update { it.copy(fetchingModels = false, message = "未获取到模型（检查 API Key 与网络）") }
                } else {
                    settingsRepository.update { settings ->
                        val providers = settings.providers.toMutableMap()
                        val old = providers[current.providerId] ?: ProviderSettings()
                        providers[current.providerId] = old.copy(models = models)
                        settings.copy(providers = providers)
                    }
                    _state.update { it.copy(models = models, fetchingModels = false, message = "已获取 ${models.size} 个模型") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(fetchingModels = false, message = "获取失败: ${e.message}") }
            }
        }
    }
}
