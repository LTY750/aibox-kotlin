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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aibox.kotlin.core.common.appString

/** 设置首页：入口列表（通用 / 模型 Provider / 备份）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onOpenGeneral: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit,
) {
    val entries = listOf(
        SettingsEntry(appString("settings.general"), appString("settings.general_desc")) { onOpenGeneral() },
        SettingsEntry(appString("settings.providers"), appString("settings.providers_desc")) { onOpenProviders() },
        SettingsEntry(appString("settings.backup"), appString("settings.backup_desc")) { onOpenBackup() },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appString("settings.title")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = appString("common.back"))
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
            items(entries) { entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = entry.onClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

private data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)
