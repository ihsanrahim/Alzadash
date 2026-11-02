package com.autodash.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.autodash.R
import com.autodash.core.Bookmark
import com.autodash.core.Browser
import com.autodash.core.TripTracker
import java.io.ByteArrayInputStream

/**
 * A full web browser: address bar that also searches, back and forward, reload, bookmarks on
 * a start page, ad blocking, desktop-site mode, downloads, file uploads and full-screen video.
 *
 * Driving safety: when the phone's GPS shows the car moving, the page is covered (sound keeps
 * playing) until the car has stopped again.
 */
@SuppressLint("SetJavaScriptEnabled")
class BrowserActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var star: ImageButton
    private lateinit var reload: ImageButton
    private lateinit var content: FrameLayout
    private lateinit var cover: TextView
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var loading = false
    private var drivingCover = false
    private var noReadingSince = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val pickFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    private val gate = object : Runnable {
        override fun run() {
            checkDriving()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = getColor(R.color.bg)
        val accent = getColor(R.color.accent)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(getColor(R.color.card))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        bar.addView(iconButton(R.drawable.ic_back, "Back") { if (web.canGoBack()) web.goBack() })
        bar.addView(iconButton(R.drawable.ic_forward, "Forward") { if (web.canGoForward()) web.goForward() })
        address = EditText(this).apply {
            hint = "Search or type a web address"
            isSingleLine = true
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8A94A6"))
            setSelectAllOnFocus(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    go(text.toString())
                    true
                } else false
            }
        }
        bar.addView(address, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        reload = iconButton(R.drawable.ic_reload, "Reload") { if (loading) web.stopLoading() else web.reload() }
        bar.addView(reload)
        star = iconButton(R.drawable.ic_star, "Bookmark") { toggleBookmark() }
        bar.addView(star)
        bar.addView(iconButton(R.drawable.ic_home, "Start page") { load(Browser.HOME) })
        val more = iconButton(R.drawable.ic_menu, "More") {}
        more.setOnClickListener { showMenu(it) }
        bar.addView(more)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            visibility = View.GONE
        }

        web = WebView(this)
        setUpWebView()

        cover = TextView(this).apply {
            setBackgroundColor(bg)
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            text = "The page is hidden while driving.\n\nSound keeps playing. It comes back when you stop."
            isClickable = true // swallows taps while moving
            visibility = View.GONE
        }

        content = FrameLayout(this)
        content.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(cover, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, dp(3)))
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        TripTracker.start(this)
        val restored = savedInstanceState?.let { web.restoreState(it) } != null
        if (!restored) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Opens what was asked for: a link from another app, a page from the dashboard, or the start page. */
    private fun handleIntent(intent: Intent?) {
        val url = intent?.getStringExtra(EXTRA_URL)
            ?: intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
            ?: intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)?.let { Browser.toUrl(it) }
            ?: Browser.HOME
        load(url)
    }

    private fun setUpWebView() {
        web.setBackgroundColor(getColor(R.color.bg))
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            setSupportMultipleWindows(false)
            setGeolocationEnabled(true)
            javaScriptCanOpenWindowsAutomatically = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        applyDesktop()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (Browser.adBlock(this@BrowserActivity) && Browser.isAd(request.url.host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.toString().startsWith(Browser.HOME)) {
                    load(Browser.HOME)
                    return true
                }
                val scheme = url.scheme ?: return false
                if (scheme == "http" || scheme == "https") return false
                openOutside(url.toString())
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                setLoading(true)
                showAddress(url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                setLoading(false)
                showAddress(url)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView?.let { onHideCustomView() }
                fullscreenView = view
                fullscreenCallback = callback
                content.addView(view, 1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }

            override fun onHideCustomView() {
                fullscreenView?.let { content.removeView(it) }
                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
            }

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    pickFiles.launch(params.createIntent())
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                val allowed = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                callback.invoke(origin, allowed, false)
            }
        }

        web.setDownloadListener { url, userAgent, disposition, mime, _ ->
            try {
                val name = URLUtil.guessFileName(url, disposition, mime)
                val req = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mime)
                    .addRequestHeader("User-Agent", userAgent)
                    .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                getSystemService(DownloadManager::class.java).enqueue(req)
                toast("Downloading $name")
            } catch (e: Exception) {
                openOutside(url)
            }
        }
    }

    private fun load(url: String) {
        if (url.startsWith(Browser.HOME)) {
            web.loadDataWithBaseURL(Browser.HOME, Browser.homeHtml(this), "text/html", "utf-8", null)
            showAddress(Browser.HOME)
        } else {
            web.loadUrl(url)
        }
    }

    private fun go(typed: String) {
        load(Browser.toUrl(typed))
        address.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(address.windowToken, 0)
        web.requestFocus()
    }

    private fun showAddress(url: String?) {
        val onHome = url == null || url.startsWith(Browser.HOME) || url == "about:blank"
        if (!address.hasFocus()) address.setText(if (onHome) "" else url)
        val saved = !onHome && Browser.bookmarks(this).any { it.url == url }
        star.setImageResource(if (saved) R.drawable.ic_star_filled else R.drawable.ic_star)
    }

    private fun setLoading(on: Boolean) {
        loading = on
        reload.setImageResource(if (on) R.drawable.ic_close else R.drawable.ic_reload)
    }

    private fun currentPage(): Bookmark? {
        val url = web.url ?: return null
        if (url.startsWith(Browser.HOME) || url == "about:blank") return null
        return Bookmark(web.title?.takeIf { it.isNotBlank() }?.take(40) ?: url, url)
    }

    private fun toggleBookmark() {
        val page = currentPage() ?: return toast("Open a page first")
        if (Browser.bookmarks(this).any { it.url == page.url }) {
            Browser.removeBookmark(this, page.url)
            toast("Removed from the start page")
        } else {
            Browser.addBookmark(this, page)
            toast("Added to the start page")
        }
        showAddress(page.url)
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "Bookmarks")
        menu.menu.add(0, 2, 1, "Share page")
        menu.menu.add(0, 3, 2, "Open in another app")
        menu.menu.add(0, 4, 3, "Desktop site").apply { isCheckable = true; isChecked = Browser.desktop(this@BrowserActivity) }
        menu.menu.add(0, 5, 4, "Block ads").apply { isCheckable = true; isChecked = Browser.adBlock(this@BrowserActivity) }
        menu.menu.add(0, 6, 5, "Clear history and cookies")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showBookmarks()
                2 -> currentPage()?.let {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, it.url), "Share"))
                } ?: toast("Open a page first")
                3 -> currentPage()?.let { openOutside(it.url) } ?: toast("Open a page first")
                4 -> {
                    Browser.setDesktop(this, !Browser.desktop(this))
                    applyDesktop()
                    web.reload()
                }
                5 -> {
                    Browser.setAdBlock(this, !Browser.adBlock(this))
                    web.reload()
                }
                6 -> {
                    web.clearHistory()
                    web.clearCache(true)
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                    toast("Cleared")
                    load(Browser.HOME)
                }
            }
            true
        }
        menu.show()
    }

    /** Bookmarks list: tap to open, long-press to remove. */
    private fun showBookmarks() {
        val list = Browser.bookmarks(this)
        if (list.isEmpty()) return toast("No bookmarks yet. Tap the star on a page to add one.")
        val dialog = AlertDialog.Builder(this).setTitle("Bookmarks")
            .setItems(list.map { it.title }.toTypedArray()) { _, i -> load(list[i].url) }
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnShowListener {
            dialog.listView.setOnItemLongClickListener { _, _, i, _ ->
                Browser.removeBookmark(this, list[i].url)
                toast("Removed ${list[i].title}")
                dialog.dismiss()
                true
            }
        }
        dialog.show()
    }

    private fun applyDesktop() {
        val desktop = Browser.desktop(this)
        web.settings.userAgentString = if (desktop) DESKTOP_UA else null
        web.settings.loadWithOverviewMode = desktop
    }

    /** Links meant for apps (intent:, market:, tel:, whatsapp: ...) open in those apps. */
    private fun openOutside(url: String) {
        try {
            val intent = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null
            startActivity(intent)
        } catch (_: Exception) {
            // An intent: link may carry a web fallback.
            val fallback = Regex("S\\.browser_fallback_url=([^;]+)").find(url)?.groupValues?.get(1)
            if (fallback != null) load(Uri.decode(fallback)) else toast("No app can open this link")
        }
    }

    /**
     * Covers the page once GPS shows the car moving, and uncovers it after the car has been
     * stopped for a few seconds (or there's been no speed reading for a minute, e.g. indoors).
     */
    private fun checkDriving() {
        val speed = TripTracker.speedKmh()
        val now = android.os.SystemClock.elapsedRealtime()
        if (speed == null) {
            if (noReadingSince == 0L) noReadingSince = now
        } else noReadingSince = 0L
        if (!drivingCover && speed != null && speed >= MOVING_KMH) {
            drivingCover = true
        } else if (drivingCover && (TripTracker.isParked() || (noReadingSince != 0L && now - noReadingSince > 60_000))) {
            drivingCover = false
        }
        val v = if (drivingCover) View.VISIBLE else View.GONE
        if (cover.visibility != v) {
            cover.visibility = v
            if (drivingCover) {
                cover.bringToFront()
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(address.windowToken, 0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        handler.post(gate)
    }

    override fun onPause() {
        handler.removeCallbacks(gate)
        web.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        web.loadUrl("about:blank")
        web.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            fullscreenView != null -> web.webChromeClient?.onHideCustomView()
            web.canGoBack() -> web.goBack()
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    private fun iconButton(res: Int, label: String, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(res)
        setColorFilter(Color.WHITE)
        contentDescription = label
        background = null
        setPadding(dp(8), dp(8), dp(8), dp(8))
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_URL = "url"
        private const val MOVING_KMH = 10f
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }
}
