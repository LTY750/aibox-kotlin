package com.aibox.kotlin.feature.providers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** Provider 配置屏：API Key / Host / 模型列表获取。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    providerId: String,
    onBack: () -> Unit,
    viewModel: ProviderConfigViewModel = hiltViewModel(),
) {
    LaunchedEffect(providerId) { viewModel.load(providerId) }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name) },
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
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = viewModel::updateApiKey,
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.apiHost,
                    onValueChange = viewModel::updateApiHost,
                    label = { Text("API Host") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.Row {
                    Button(onClick = viewModel::save) { Text("保存") }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = viewModel::fetchModels,
                        enabled = !state.fetchingModels,
                    ) {
                        if (state.fetchingModels) {
                            CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("获取模型列表")
                    }
                }
                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "模型（${state.models.size}）",
                    style = MaterialTheme.typography.titleSmall,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            items(state.models, key = { it.modelId }) { model ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = model.nickname ?: model.modelId,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (model.nickname != null) {
                        Text(
                            text = model.modelId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
