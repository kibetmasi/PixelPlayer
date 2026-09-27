package com.theveloper.pixelplay.presentation.youtube.auth

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import com.theveloper.pixelplay.youtube.YoutubeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class YoutubeLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                YoutubeWebLoginScreen(onClose = { finish() })
            }
        }
    }

    companion object {
        const val TARGET_URL = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2F&hl=en"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        const val DESKTOP_VIEWPORT =
            "(function(){var meta=document.querySelector('meta[name=viewport]');if(!meta){meta=document.createElement('meta');meta.name='viewport';(document.head||document.documentElement).appendChild(meta);}meta.setAttribute('content','width=1280');})();"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YoutubeWebLoginScreen(
    onClose: () -> Unit,
    viewModel: YoutubeViewModel = hiltViewModel(),
) {
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler { onClose() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.youtube_sign_in),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.youtube_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val cookies = extractYoutubeSession()
                            if (cookies != null && viewModel.importSession(cookies)) {
                                onClose()
                            } else {
                                Toast.makeText(
                                    webView?.context,
                                    R.string.youtube_sign_in_incomplete,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.youtube_sign_in_done), fontFamily = GoogleSansRounded)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.fillMaxSize()) {
                YoutubeWebView(
                    onProgress = { progress = it },
                    onWebViewCreated = { webView = it },
                    onCookiesMaybeReady = {
                        val cookies = extractYoutubeSession() ?: return@YoutubeWebView
                        if (viewModel.importSession(cookies)) onClose()
                    },
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YoutubeWebView(
    onProgress: (Int) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onCookiesMaybeReady: () -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString = YoutubeLoginActivity.DESKTOP_UA
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                settings.setSupportZoom(true)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        view?.settings?.userAgentString = YoutubeLoginActivity.DESKTOP_UA
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(YoutubeLoginActivity.DESKTOP_VIEWPORT, null)
                        onCookiesMaybeReady()
                    }
                }
                loadUrl(YoutubeLoginActivity.TARGET_URL)
                onWebViewCreated(this)
            }
        },
    )
}

internal fun extractYoutubeSession(): String? {
    val manager = CookieManager.getInstance()
    val map = linkedMapOf<String, String>()
    listOf(
        "https://www.youtube.com",
        "https://youtube.com",
        "https://music.youtube.com",
        "https://accounts.google.com",
        "https://google.com",
    ).map { manager.getCookie(it).orEmpty() }
        .joinToString("; ")
        .split(';')
        .map { it.trim() }
        .filter { it.contains('=') }
        .forEach { part ->
            val index = part.indexOf('=')
            val key = part.substring(0, index).trim()
            val value = part.substring(index + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) map[key] = value
        }
    val signedIn = map.keys.any { key ->
        key.equals("LOGIN_INFO", ignoreCase = true) ||
            key.equals("SAPISID", ignoreCase = true) ||
            key.equals("__Secure-1PSID", ignoreCase = true) ||
            key.equals("__Secure-3PSID", ignoreCase = true)
    }
    if (!signedIn) return null
    return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
}
