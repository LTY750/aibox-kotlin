package com.aibox.kotlin.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibox.kotlin.core.provider.ProviderRegistry
import com.aibox.kotlin.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProvidersListState(
    /** 已配置（有 key 或模型）的 provider id。 */
    val configured: Set<String> = emptySet(),
)

@HiltViewModel
class ProvidersListViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<ProvidersListState> = settingsRepository.settings
        .map { settings ->
            ProvidersListState(
                configured = settings.providers
                    .filterValues { it.apiKey != null || it.models.isNotEmpty() }
                    .keys,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProvidersListState())
}

/** Provider 列表屏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    onOpenProvider: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ProvidersListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型服务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(ProviderRegistry.builtin, key = { it.id }) { provider ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProvider(provider.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = provider.name + if (provider.id in state.configured) " · 已配置" else "",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = provider.defaultApiHost,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
