package com.aibox.kotlin.feature.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 备份与迁移屏。
 * 选择导入文件 / 选择导出位置由宿主 Activity 通过系统文件选择器完成，
 * 本屏只负责触发与进度展示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onPickImportFile: () -> Unit,
    onPickExportFile: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与迁移") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = "导入旧版备份",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "支持旧版 AIbox/Chatbox 导出的 ZIP 备份（含会话与设置）。导入设置时 API Key 会自动转入安全存储。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPickImportFile,
                enabled = !state.busy,
            ) { Text("选择备份文件导入") }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "导出备份",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "导出会话与设置（API Key 不包含在备份中）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onPickExportFile(viewModel.suggestedExportName()) },
                enabled = !state.busy,
            ) { Text("导出到文件") }

            if (state.busy) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.message?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            state.lastImport?.takeIf { it.warnings.isNotEmpty() }?.let { result ->
                Spacer(Modifier.height(8.dp))
                result.warnings.take(5).forEach { warning ->
                    Text(
                        text = "⚠ $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
