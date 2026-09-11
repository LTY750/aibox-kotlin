package com.aibox.kotlin.feature.settingsgeneral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aibox.kotlin.core.common.AppLanguage
import com.aibox.kotlin.core.common.appString
import com.aibox.kotlin.core.model.ThemeMode
import kotlin.math.roundToInt

private const val FONT_SIZE_MIN = 80f
private const val FONT_SIZE_MAX = 160f
private const val FONT_SIZE_STEP = 10
private const val FONT_SIZE_DEFAULT = 100

private const val THRESHOLD_DEFAULT = 0.8

/**
 * 通用设置页：主题 / 语言 / 字体大小 / 默认提示词 / 消息显示 / Markdown /
 * 长文本粘贴 / 自动压缩。全部为可写控件，变更即通过 ViewModel 落盘。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    viewModel: GeneralSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appString("settings.general")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = appString("common.back"),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // ── 外观 ─────────────────────────────────────────────
            item { SectionHeader(appString("settings.appearance")) }

            item {
                ThemeRow(
                    selected = settings.theme ?: ThemeMode.SYSTEM,
                    onSelect = viewModel::setTheme,
                )
            }

            item {
                LanguageRow(
                    current = settings.language,
                    onSelect = viewModel::setLanguage,
                )
            }

            item {
                FontSizeRow(
                    percent = settings.fontSize ?: FONT_SIZE_DEFAULT,
                    onChange = viewModel::setFontSize,
                )
            }

            item { HorizontalDivider() }

            // ── 行为 ─────────────────────────────────────────────
            item { SectionHeader(appString("settings.behavior")) }

            item {
                DefaultPromptField(
                    value = settings.defaultPrompt ?: "",
                    onChange = viewModel::setDefaultPrompt,
                )
            }

            item {
                SwitchRow(
                    title = appString("settings.auto_name"),
                    subtitle = appString("settings.auto_name_desc"),
                    checked = settings.autoGenerateTitle ?: true,
                    onCheckedChange = viewModel::setAutoGenerateTitle,
                )
            }

            item {
                SwitchRow(
                    title = appString("settings.paste_long_text"),
                    subtitle = appString("settings.paste_long_text_desc"),
                    checked = settings.pasteLongTextAsAFile ?: true,
                    onCheckedChange = viewModel::setPasteLongTextAsAFile,
                )
            }

            item {
                SwitchRow(
                    title = appString("settings.markdown"),
                    subtitle = appString("settings.markdown_desc"),
                    checked = settings.enableMarkdownRendering ?: true,
                    onCheckedChange = viewModel::setEnableMarkdownRendering,
                )
            }

            item { HorizontalDivider() }

            // ── 消息显示 ─────────────────────────────────────────
            item { SectionHeader(appString("settings.display")) }

            item {
                SwitchRow(
                    title = appString("settings.show_word_count"),
                    subtitle = null,
                    checked = settings.showWordCount ?: false,
                    onCheckedChange = viewModel::setShowWordCount,
                )
            }
            item {
                SwitchRow(
                    title = appString("settings.show_token_count"),
                    subtitle = null,
                    checked = settings.showTokenCount ?: false,
                    onCheckedChange = viewModel::setShowTokenCount,
                )
            }
            item {
                SwitchRow(
                    title = appString("settings.show_used_token"),
                    subtitle = null,
                    checked = settings.showUsedToken ?: false,
                    onCheckedChange = viewModel::setShowUsedToken,
                )
            }
            item {
                SwitchRow(
                    title = appString("settings.show_model_name"),
                    subtitle = null,
                    checked = settings.showModelName ?: true,
                    onCheckedChange = viewModel::setShowModelName,
                )
            }
            item {
                SwitchRow(
                    title = appString("settings.show_timestamp"),
                    subtitle = null,
                    checked = settings.showMessageTimestamp ?: false,
                    onCheckedChange = viewModel::setShowMessageTimestamp,
                )
            }
            item {
                SwitchRow(
                    title = appString("settings.show_first_token_latency"),
                    subtitle = null,
                    checked = settings.showFirstTokenLatency ?: false,
                    onCheckedChange = viewModel::setShowFirstTokenLatency,
                )
            }
            item {
                SwitchRow(
                    title = appString("settings.show_avatar"),
                    subtitle = null,
                    checked = settings.showAvatar ?: true,
                    onCheckedChange = viewModel::setShowAvatar,
                )
            }

            item { HorizontalDivider() }

            // ── 上下文压缩 ───────────────────────────────────────
            item { SectionHeader(appString("settings.context")) }

            item {
                SwitchRow(
                    title = appString("settings.auto_compaction"),
                    subtitle = appString("settings.auto_compaction_desc"),
                    checked = settings.autoCompaction ?: false,
                    onCheckedChange = viewModel::setAutoCompaction,
                )
            }

            item {
                ThresholdRow(
                    value = settings.compactionThreshold ?: THRESHOLD_DEFAULT,
                    enabled = settings.autoCompaction ?: false,
                    onChange = viewModel::setCompactionThreshold,
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ThemeRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(appString("settings.theme"), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == ThemeMode.DARK,
                onClick = { onSelect(ThemeMode.DARK) },
                label = { Text(appString("settings.theme.dark")) },
            )
            FilterChip(
                selected = selected == ThemeMode.LIGHT,
                onClick = { onSelect(ThemeMode.LIGHT) },
                label = { Text(appString("settings.theme.light")) },
            )
            FilterChip(
                selected = selected == ThemeMode.SYSTEM,
                onClick = { onSelect(ThemeMode.SYSTEM) },
                label = { Text(appString("settings.theme.system")) },
            )
        }
    }
}

@Composable
private fun LanguageRow(current: String?, onSelect: (String?) -> Unit) {
    val selected = AppLanguage.fromTag(current)
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(appString("settings.language"), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(languageLabel(selected))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AppLanguage.selectable.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(languageLabel(lang)) },
                        onClick = {
                            expanded = false
                            onSelect(lang.tag)
                        },
                        trailingIcon = {
                            if (lang == selected) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 「跟随系统」用本地化文案，其余语言展示自称。 */
@Composable
private fun languageLabel(lang: AppLanguage): String =
    if (lang == AppLanguage.SYSTEM) appString("settings.language.system") else lang.displayLabel

@Composable
private fun FontSizeRow(percent: Int, onChange: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(appString("settings.font_size"), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = appString("common.percent", percent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = FONT_SIZE_MIN..FONT_SIZE_MAX,
            steps = ((FONT_SIZE_MAX - FONT_SIZE_MIN).toInt() / FONT_SIZE_STEP) - 1,
        )
    }
}

@Composable
private fun DefaultPromptField(value: String, onChange: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            label = { Text(appString("settings.default_prompt")) },
            placeholder = { Text(appString("settings.default_prompt_hint")) },
            supportingText = { Text(appString("settings.default_prompt_desc")) },
        )
    }
}

@Composable
private fun ThresholdRow(value: Double, enabled: Boolean, onChange: (Double) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = appString("settings.compaction_threshold"),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = appString("common.percent", (value * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = GeneralSettingsViewModel.MIN_THRESHOLD.toFloat()..
                GeneralSettingsViewModel.MAX_THRESHOLD.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
