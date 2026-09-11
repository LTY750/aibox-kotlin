package com.aibox.kotlin.feature.chat.markdown

import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aibox.kotlin.core.common.appString
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** 本地 Mermaid 查看页（随 APK 打包，不联网）。 */
private const val VIEWER_URL = "file:///android_asset/mermaid/viewer.html"

/** 唯一的白名单目录：只允许加载打包进 `assets/mermaid/` 的本地资源。 */
private const val ASSET_PREFIX = "file:///android_asset/mermaid/"

private const val BRIDGE_NAME = "AiboxMermaid"
private const val MIN_HEIGHT_PX = 120
private const val MAX_HEIGHT_PX = 2400

/**
 * Mermaid 图表渲染（§9 安全设计：WebView 沙箱）。
 *
 * 沙箱约束：
 * - `allowFileAccess=false` / `allowContentAccess=false`：禁止任意文件与 ContentProvider 访问
 *   （`file:///android_asset/` 仍可读，它是渲染引擎的唯一来源）；
 * - `allowFileAccessFromFileURLs=false` / `allowUniversalAccessFromFileURLs=false`：禁止 file 源跨域；
 * - `blockNetworkLoads=true`：禁止一切网络加载；
 * - `securityLevel='strict'`（viewer 内）：禁用图表内脚本与点击回调；
 * - 只放行 `file:///android_asset/mermaid/`，其余请求一律拦截。
 */
@Composable
fun MermaidBlock(
    code: String,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(MIN_HEIGHT_PX * 2) }
    var failed by remember { mutableStateOf(false) }
    var pageReady by remember { mutableStateOf(false) }
    val payload = remember(code) { JSONObject.quote(code) }
    val themeName = if (darkTheme) "dark" else "default"

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false
            settings.blockNetworkLoads = true
            settings.domStorageEnabled = false
            settings.databaseEnabled = false
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            setBackgroundColor(AndroidColor.TRANSPARENT)
        }
    }

    DisposableEffect(webView) {
        val bridge = MermaidBridge(
            onHeight = { px -> heightPx = px.coerceIn(MIN_HEIGHT_PX, MAX_HEIGHT_PX) },
            onError = { failed = true },
        )
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        webView.webViewClient = MermaidWebViewClient(onReady = { pageReady = true })
        webView.loadUrl(VIEWER_URL)
        onDispose {
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.destroy()
        }
    }

    LaunchedEffect(pageReady, payload, themeName) {
        if (!pageReady) return@LaunchedEffect
        failed = false
        webView.evaluateJavascript("window.renderMermaid($payload, '$themeName');", null)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        if (failed) {
            Text(
                text = appString("markdown.mermaid_error"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { heightPx.toDp() }),
            )
        }
    }
}

/** 仅用于回传渲染高度；不接收任意指令。 */
private class MermaidBridge(
    private val onHeight: (Int) -> Unit,
    private val onError: (String) -> Unit,
) {
    @JavascriptInterface
    fun postHeight(px: Int) {
        onHeight(px)
    }

    @JavascriptInterface
    fun postError(message: String) {
        onError(message)
    }
}

/** 把 WebView 限制在本地资产白名单内。 */
private class MermaidWebViewClient(private val onReady: () -> Unit) : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String) {
        onReady()
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !isAllowed(request.url.toString())

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean = !isAllowed(url)

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url.toString()
        return if (isAllowed(url) || url.startsWith("data:") || url.startsWith("about:")) {
            null
        } else {
            // 静默拒绝：不发起网络/文件访问
            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
    }

    private fun isAllowed(url: String?): Boolean = url != null && url.startsWith(ASSET_PREFIX)
}
