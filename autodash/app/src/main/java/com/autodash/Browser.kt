package com.autodash

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/** Keeps playing sound when the dashboard is in the background. */
class UnrestrictedCarWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
    }
}

val AD_DOMAINS = listOf("doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.", "pagead2.googlesyndication.com", "adnxs.com", "taboola.com")
const val SHIELD_JS = "(function() { if (window.bInj) return; window.bInj = true; Object.defineProperty(document, 'hidden', { get: () => false }); Object.defineProperty(document, 'visibilityState', { get: () => 'visible' }); setInterval(() => { const s = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern'); if (s) s.click(); const v = document.querySelector('video'); if (document.querySelector('.ad-showing') && v && v.duration > 0) v.currentTime = v.duration; }, 250); })();"

private fun toUrl(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        !t.contains(' ') && t.contains('.') -> "https://$t"
        else -> "https://www.google.com/search?q=" + Uri.encode(t)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShieldBrowserWidget() {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val prefs = remember { context.getSharedPreferences(WidgetLayoutPrefs.PREFS_NAME, Context.MODE_PRIVATE) }
    val startUrl = remember { prefs.getString("last_browser_url", null) ?: "https://m.youtube.com" }
    var url by remember { mutableStateOf(startUrl) }
    var wv by remember { mutableStateOf<WebView?>(null) }
    var blocked by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2638)).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (wv?.canGoBack() == true) wv?.goBack() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { wv?.loadUrl(toUrl(url)); focus.clearFocus() }),
                    modifier = Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F1420)).padding(horizontal = 10.dp, vertical = 8.dp),
                )
                TextButton(onClick = { url = "https://m.youtube.com"; wv?.loadUrl(url) }) { Text("YT", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Text("Shield: $blocked", color = Color(0xFFFF6D00), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
            }
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                UnrestrictedCarWebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(v: WebView?, req: WebResourceRequest?): WebResourceResponse? {
                            val u = req?.url?.toString()?.lowercase() ?: return null
                            if (AD_DOMAINS.any { u.contains(it) }) {
                                v?.post { blocked++ }
                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }
                            return super.shouldInterceptRequest(v, req)
                        }

                        override fun onPageFinished(v: WebView?, u: String?) {
                            if (u != null) { url = u; prefs.edit().putString("last_browser_url", u).apply() }
                            v?.evaluateJavascript(SHIELD_JS, null)
                        }
                    }
                    loadUrl(startUrl)
                    wv = this
                }
            }, onRelease = { it.destroy() })
        }

        // Safety rule: the page is hidden while the car is moving. Sound keeps playing.
        if (!Speed.hasPermission) {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Speed.start(context) }
            DriveCover(
                icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(36.dp)) },
                text = "Allow location so Autodash can tell when you're parked. The browser only shows while parked.",
                button = { Button(onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Allow location", fontSize = 12.sp) } },
            )
        } else if (Speed.driving) {
            DriveCover(
                icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(36.dp)) },
                text = "Browser hidden while driving. Sound keeps playing.\nIt comes back when you stop.",
            )
        }
    }
}

@Composable
private fun DriveCover(icon: @Composable () -> Unit, text: String, button: (@Composable () -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            // Swallow every touch so nothing reaches the page underneath.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
            icon()
            Text(text, color = Color(0xFFB0BEC5), fontSize = 13.sp, textAlign = TextAlign.Center)
            if (button != null) {
                Spacer(modifier = Modifier.height(2.dp))
                button()
            }
        }
    }
}
