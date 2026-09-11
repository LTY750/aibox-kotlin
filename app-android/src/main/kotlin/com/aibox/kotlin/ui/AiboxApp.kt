package com.aibox.kotlin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aibox.kotlin.core.common.AppLanguage
import com.aibox.kotlin.core.common.I18n
import com.aibox.kotlin.core.model.Settings
import com.aibox.kotlin.core.storage.SettingsRepository
import com.aibox.kotlin.feature.backup.BackupScreen
import com.aibox.kotlin.feature.backup.BackupViewModel
import com.aibox.kotlin.feature.chat.ChatScreen
import com.aibox.kotlin.feature.providers.ProviderConfigScreen
import com.aibox.kotlin.feature.sessions.SessionsScreen
import com.aibox.kotlin.feature.settings.ProvidersScreen
import com.aibox.kotlin.feature.settings.SettingsHomeScreen
import com.aibox.kotlin.feature.settingsgeneral.GeneralSettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 导航路由。 */
object Routes {
    const val CHAT = "chat?sessionId={sessionId}"
    const val SESSIONS = "sessions"
    const val SETTINGS = "settings"
    const val SETTINGS_GENERAL = "settings/general"
    const val PROVIDERS = "settings/providers"
    const val PROVIDER_CONFIG = "settings/providers/{providerId}"
    const val BACKUP = "settings/backup"

    fun chat(sessionId: String? = null) = "chat" + (sessionId?.let { "?sessionId=$it" } ?: "")
    fun providerConfig(providerId: String) = "settings/providers/$providerId"
}

/**
 * 根 ViewModel：暴露全局设置，供主题（暗/亮/跟随系统、字号）
 * 与语言在整棵 UI 树上即时生效。
 */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.snapshot())
}

@Composable
fun AiboxApp(viewModel: AppRootViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()

    // 语言：启动加载完成与变更时同步到内存 i18n 表（I18n.language 为 mutableStateOf，
    // 赋值后所有 appString 读取点立即重组）。
    LaunchedEffect(settings.language) {
        I18n.language = AppLanguage.fromTag(settings.language)
    }

    // 主题/字号由设置驱动；主题即可切换暗/亮，字号即时缩放排版。
    AiboxTheme(themeMode = settings.theme, fontSizePercent = settings.fontSize) {
        Surface(Modifier.fillMaxSize()) {
            AppNavHost()
        }
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 聊天附件选择：ChatScreen 内部通过回调挂载 ActivityResultLauncher
    NavHost(navController = navController, startDestination = Routes.chat()) {
        composable(Routes.CHAT) { entry ->
            val sessionId = entry.arguments?.getString("sessionId")?.takeIf { it.isNotBlank() && it != "new" }
            ChatScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onOpenSessions = { navController.navigate(Routes.SESSIONS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SESSIONS) {
            SessionsScreen(
                onOpenSession = { id -> navController.navigate(Routes.chat(id)) },
                onNewSession = {
                    navController.popBackStack()
                    navController.navigate(Routes.chat()) {
                        popUpTo(Routes.chat()) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsHomeScreen(
                onOpenGeneral = { navController.navigate(Routes.SETTINGS_GENERAL) },
                onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS_GENERAL) {
            GeneralSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PROVIDERS) {
            ProvidersScreen(
                onOpenProvider = { id -> navController.navigate(Routes.providerConfig(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PROVIDER_CONFIG) { entry ->
            val providerId = entry.arguments?.getString("providerId") ?: return@composable
            ProviderConfigScreen(
                providerId = providerId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.BACKUP) {
            BackupRoute(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun BackupRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: BackupViewModel = androidx.hilt.navigation.compose.hiltViewModel()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val cached = File(context.cacheDir, "import-backup.zip")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cached.outputStream().use { input.copyTo(it) }
                }
            }.onSuccess { viewModel.importFrom(cached) }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val target = File(context.cacheDir, "export-backup.zip")
                runCatching {
                    // exportTo 为 suspend：等待缓存文件写完后再复制到 SAF 目标
                    viewModel.exportTo(target)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        target.inputStream().use { it.copyTo(output) }
                    }
                }
            }
        }
    }

    BackupScreen(
        onPickImportFile = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
        onPickExportFile = { suggestedName -> exportLauncher.launch(suggestedName) },
        onBack = onBack,
        viewModel = viewModel,
    )
}
