package com.aibox.kotlin.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aibox.kotlin.feature.chat.RenderSampleScreen
import com.aibox.kotlin.ui.AiboxTheme

/**
 * T04 渲染样例入口（**仅存在于 debug 源集**，release 变体不包含该 Activity）。
 *
 * 启动：`adb shell am start -n com.aibox.kotlin.debug/.RenderSampleActivity`
 *
 * 页面本体 [RenderSampleScreen] 内部还有 `BuildConfig.DEBUG` 硬守卫，双重保障不会误发。
 */
class RenderSampleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiboxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RenderSampleScreen()
                }
            }
        }
    }
}
