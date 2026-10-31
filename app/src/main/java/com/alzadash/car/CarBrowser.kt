package com.alzadash.car

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.alzadash.core.Browser
import java.io.ByteArrayInputStream

/**
 * The built-in web browser: a start page of bookmarks, ad blocking and a desktop-site mode.
 * It lives in [container], either on the dashboard's screen or in [CoverActivity] when a
 * phone app is open underneath.
 */
@SuppressLint("SetJavaScriptEnabled")
class CarBrowser(private val context: Context, private val container: FrameLayout, startUrl: String) {
    private val web = WebView(context)
    private var fullscreenView: View? = null

    init {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.settings.builtInZoomControls = true
        web.settings.displayZoomControls = false
        web.settings.useWideViewPort = true
        applyDesktop()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (Browser.adBlock(context) && Browser.isAd(request.url.host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.toString() == Browser.HOME) {
                    load(Browser.HOME)
                    return true
                }
                // Links to apps (intent:, market:, tel: ...) can't open on the car screen.
                val scheme = request.url.scheme ?: return false
                return scheme != "http" && scheme != "https"
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView = view
                container.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }

            override fun onHideCustomView() {
                fullscreenView?.let { container.removeView(it) }
                fullscreenView = null
            }
        }
        container.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        load(startUrl)
    }

    fun load(url: String) {
        if (url == Browser.HOME) {
            web.loadDataWithBaseURL(Browser.HOME, Browser.homeHtml(context), "text/html", "utf-8", null)
        } else {
            web.loadUrl(url)
        }
    }

    fun goBack() {
        if (web.canGoBack()) web.goBack()
    }

    fun goForward() {
        if (web.canGoForward()) web.goForward()
    }

    fun reload() = web.reload()

    /** Title and address of the page showing now, or null on the start page. */
    val page: Pair<String, String>?
        get() {
            val url = web.url ?: return null
            if (url.startsWith(Browser.HOME)) return null
            return (web.title?.takeIf { it.isNotBlank() } ?: url) to url
        }

    private fun applyDesktop() {
        val desktop = Browser.desktop(context)
        web.settings.userAgentString = if (desktop) DESKTOP_UA else null
        web.settings.loadWithOverviewMode = desktop
    }

    /** Applies the desktop-site setting and reloads. */
    fun applySettings() {
        applyDesktop()
        web.reload()
    }

    /** Forgets cookies, history and cached pages. */
    fun clearData() {
        web.clearHistory()
        web.clearCache(true)
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }

    /** Stops everything, including any video sound. */
    fun destroy() {
        web.loadUrl("about:blank")
        container.removeAllViews()
        web.destroy()
    }

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }
}
