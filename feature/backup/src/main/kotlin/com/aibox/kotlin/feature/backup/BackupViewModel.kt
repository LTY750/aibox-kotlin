package com.aibox.kotlin.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibox.kotlin.core.backup.BackupExporter
import com.aibox.kotlin.core.backup.BackupImporter
import com.aibox.kotlin.core.backup.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BackupUiState(
    val busy: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val lastImport: ImportResult? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val importer: BackupImporter,
    private val exporter: BackupExporter,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    /** 从备份文件导入。 */
    fun importFrom(file: File) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = BackupUiState(busy = true)
            try {
                val result = importer.import(file) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                _state.update {
                    it.copy(
                        busy = false,
                        progress = 1f,
                        lastImport = result,
                        message = "导入完成：${result.sessionsImported} 个会话" +
                            if (result.warnings.isNotEmpty()) "，${result.warnings.size} 条警告" else "",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = "导入失败: ${e.message}") }
            }
        }
    }

    /**
     * 导出到指定文件（真正的 suspend：调用方必须等待完成后再移动/复制产物，
     * 否则会读到半截文件——SAF 导出路径依赖此语义）。
     */
    suspend fun exportTo(file: File) {
        if (_state.value.busy) return
        _state.value = BackupUiState(busy = true)
        try {
            exporter.export(file, includeSettings = true) { progress ->
                _state.update { it.copy(progress = progress) }
            }
            _state.update { it.copy(busy = false, progress = 1f, message = "已导出到 ${file.name}") }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, message = "导出失败: ${e.message}") }
        }
    }

    fun suggestedExportName(): String = exporter.defaultFileName()
}
