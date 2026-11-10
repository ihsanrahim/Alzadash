package com.alzadash.car

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.alzadash.R
import com.alzadash.core.Bookmark
import com.alzadash.core.Browser
import java.io.ByteArrayInputStream
import org.json.JSONObject

/**
 * The built-in browser on the car screen: tabs of web pages under a floating bar that can be
 * moved, recoloured, resized and given only the buttons you want, a Sites page of shortcuts,
 * an on-screen keyboard that appears when a text box is tapped, page zoom and a dark theme.
 */
@SuppressLint("SetJavaScriptEnabled")
class CarBrowser(private val context: Context, private val container: FrameLayout, startUrl: String) {
    /** Called when the bar's Close button is tapped. */
    var onClose: () -> Unit = {}
    /** Short message for the driver. */
    var onMessage: (String) -> Unit = {}

    private val handler = Handler(Looper.getMainLooper())
    private val pages = FrameLayout(context)
    private val tabs = ArrayList<WebView>()
    private var current = 0
    private val web: WebView get() = tabs[current]
    private var fullscreenView: View? = null

    private val bar = LinearLayout(context)
    private val restore = ImageView(context)
    private val keyboard = CarKeyboard(context) { key -> onKey(key) }
    private val tabPanel = LinearLayout(context)
    private var fullscreen = false

    /** While true, car drags move the bar instead of scrolling the page; a tap ends it. */
    var moving = false
        private set

    init {
        container.addView(pages, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        tabPanel.orientation = LinearLayout.VERTICAL
        tabPanel.setBackgroundColor(0xF20E1116.toInt())
        tabPanel.setPadding(dp(16), dp(16), dp(16), dp(16))
        tabPanel.visibility = View.GONE
        tabPanel.isClickable = true
        container.addView(tabPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        keyboard.visibility = View.GONE
        container.addView(keyboard, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))

        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        container.addView(bar, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        restore.setImageResource(R.drawable.ic_fullscreen_exit)
        restore.setOnClickListener { setFullscreen(false) }
        restore.visibility = View.GONE
        container.addView(restore, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END))

        newTab(startUrl)
        applyBar()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    // ---- Tabs ----

    private fun makeWeb(): WebView {
        val w = WebView(context)
        w.setBackgroundColor(Color.parseColor("#0E1116"))
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        w.settings.builtInZoomControls = true
        w.settings.displayZoomControls = false
        w.settings.useWideViewPort = true
        w.settings.setSupportMultipleWindows(false)
        w.addJavascriptInterface(Bridge(), "AutodashKb")
        applyDesktop(w)
        w.webViewClient = object : WebViewClient() {
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

            override fun onPageFinished(view: WebView, url: String?) {
                pageSetup(view)
                if (view == web) applyBar()
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView = view
                container.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                bar.bringToFront()
            }

            override fun onHideCustomView() {
                fullscreenView?.let { container.removeView(it) }
                fullscreenView = null
            }
        }
        return w
    }

    fun newTab(url: String = Browser.HOME) {
        if (tabs.size >= MAX_TABS) {
            onMessage("Up to $MAX_TABS tabs; close one first")
            return
        }
        val w = makeWeb()
        tabs += w
        pages.addView(w, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        showTab(tabs.size - 1)
        loadInto(w, url)
    }

    private fun showTab(i: Int) {
        current = i.coerceIn(0, tabs.size - 1)
        tabs.forEachIndexed { n, w -> w.visibility = if (n == current) View.VISIBLE else View.GONE }
        applyBar()
    }

    private fun closeTab(i: Int) {
        if (tabs.size <= 1) {
            load(Browser.HOME)
            return
        }
        val w = tabs.removeAt(i)
        pages.removeView(w)
        w.destroy()
        showTab(if (current >= i) current - 1 else current)
    }

    private fun toggleTabs() {
        if (tabPanel.visibility == View.VISIBLE) {
            tabPanel.visibility = View.GONE
            return
        }
        tabPanel.removeAllViews()
        tabPanel.addView(label("Tabs", 22f, Color.WHITE).apply { setPadding(0, 0, 0, dp(10)) })
        tabs.forEachIndexed { i, w ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = round(if (i == current) 0xFF2C7A7B.toInt() else 0xFF1A1F27.toInt(), dp(12).toFloat())
                setPadding(dp(16), dp(6), dp(6), dp(6))
            }
            val title = w.title?.takeIf { it.isNotBlank() && !(w.url ?: "").startsWith(Browser.HOME) } ?: "Sites"
            row.addView(label(title, 18f, Color.WHITE).apply {
                maxLines = 1
                setOnClickListener { showTab(i); tabPanel.visibility = View.GONE }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            row.addView(icon(R.drawable.ic_close) { closeTab(i); tabPanel.visibility = View.GONE; toggleTabs() }, LinearLayout.LayoutParams(dp(48), dp(48)))
            tabPanel.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(pill("+ New tab") { tabPanel.visibility = View.GONE; newTab() })
        actions.addView(pill("Done") { tabPanel.visibility = View.GONE })
        tabPanel.addView(actions)
        tabPanel.visibility = View.VISIBLE
        bar.bringToFront()
    }

    // ---- Pages ----

    fun load(url: String) = loadInto(web, url)

    private fun loadInto(w: WebView, url: String) {
        tabPanel.visibility = View.GONE
        if (url == Browser.HOME) {
            w.loadDataWithBaseURL(Browser.HOME, Browser.homeHtml(context), "text/html", "utf-8", null)
        } else {
            w.loadUrl(url)
        }
    }

    fun goBack() {
        when {
            tabPanel.visibility == View.VISIBLE -> tabPanel.visibility = View.GONE
            keyboard.visibility == View.VISIBLE -> showKeyboard(false)
            web.canGoBack() -> web.goBack()
            !(web.url ?: Browser.HOME).startsWith(Browser.HOME) -> load(Browser.HOME)
        }
    }

    fun goForward() {
        if (web.canGoForward()) web.goForward()
    }

    fun reload() {
        if ((web.url ?: "").startsWith(Browser.HOME)) load(Browser.HOME) else web.reload()
    }

    /** Title and address of the page showing now, or null on the Sites page. */
    val page: Pair<String, String>?
        get() {
            val url = web.url ?: return null
            if (url.startsWith(Browser.HOME)) return null
            return (web.title?.takeIf { it.isNotBlank() } ?: url) to url
        }

    private fun toggleBookmark() {
        val p = page ?: return onMessage("Open a website first")
        if (Browser.bookmarks(context).any { it.url == p.second }) {
            Browser.removeBookmark(context, p.second)
            onMessage("Bookmark removed")
        } else {
            Browser.addBookmark(context, Bookmark(p.first.take(40), p.second))
            onMessage("Bookmarked")
        }
        applyBar()
    }

    /** Zoom, theme and the keyboard hook, run on every page once it has loaded. */
    private fun pageSetup(w: WebView) {
        val zoom = Browser.zoom(context) / 100f
        val theme = Browser.theme(context)
        w.evaluateJavascript(
            """
(function(){
 var d=document.documentElement; if(!d) return;
 d.style.zoom='$zoom';
 var old=document.getElementById('autodash-theme'); if(old) old.remove();
 var mode='$theme';
 if(mode!=='Light'){
  var bg=getComputedStyle(document.body||d).backgroundColor, m=bg.match(/\d+/g), light=true;
  if(m&&m.length>=3){ var a=m.length>3?parseFloat(m[3]):1; light = a===0 || (0.299*m[0]+0.587*m[1]+0.114*m[2])>140; }
  if(light && mode==='Dark'){
   var s=document.createElement('style'); s.id='autodash-theme';
   s.textContent='html{filter:invert(1) hue-rotate(180deg);background:#fff}img,video,picture,canvas,iframe,svg image,[style*="background-image"]{filter:invert(1) hue-rotate(180deg)}';
   (document.head||d).appendChild(s);
  }
 }
 if(!window.__autodashKb){
  window.__autodashKb=1;
  var isText=function(e){ if(!e) return false; if(e.isContentEditable) return true; var t=(e.type||'').toLowerCase();
   return e.tagName==='TEXTAREA' || (e.tagName==='INPUT' && ['text','search','email','url','tel','password','number',''].indexOf(t)>=0); };
  document.addEventListener('focusin',function(ev){ if(isText(ev.target)) AutodashKb.show(); },true);
  document.addEventListener('focusout',function(ev){ if(isText(ev.target)) AutodashKb.hide(); },true);
 }
})();
""".trimIndent(), null,
        )
    }

    /** Pinch on the car screen zooms the page. */
    fun pinch(factor: Float) {
        if (factor <= 0f) return
        Browser.setZoom(context, (Browser.zoom(context) * factor).toInt())
        tabs.forEach { pageSetup(it) }
    }

    private fun zoomBy(step: Int) {
        Browser.setZoom(context, Browser.zoom(context) + step)
        tabs.forEach { pageSetup(it) }
        onMessage("Zoom ${Browser.zoom(context)}%")
    }

    private fun applyDesktop(w: WebView) {
        val desktop = Browser.desktop(context)
        w.settings.userAgentString = if (desktop) DESKTOP_UA else null
        w.settings.loadWithOverviewMode = desktop
    }

    /** Applies changed settings (desktop site, theme, zoom, bar) and reloads. */
    fun applySettings() {
        tabs.forEach {
            applyDesktop(it)
            it.reload()
        }
        applyBar()
    }

    // ---- Keyboard ----

    private inner class Bridge {
        @JavascriptInterface
        fun show() {
            handler.removeCallbacks(hideKb)
            handler.post { showKeyboard(true) }
        }

        @JavascriptInterface
        fun hide() {
            // Focus moves briefly while a key is pressed; only hide if it doesn't come back.
            handler.removeCallbacks(hideKb)
            handler.postDelayed(hideKb, 400)
        }
    }

    private val hideKb = Runnable { showKeyboard(false) }

    private fun showKeyboard(on: Boolean) {
        keyboard.visibility = if (on) View.VISIBLE else View.GONE
        if (on) {
            keyboard.bringToFront()
            bar.bringToFront()
        }
        positionBar()
    }

    private fun onKey(key: String) {
        val js = when (key) {
            CarKeyboard.HIDE -> return showKeyboard(false)
            CarKeyboard.BACKSPACE -> """
(function(){var e=document.activeElement;if(!e)return;
 if(e.isContentEditable){document.execCommand('delete');return;}
 try{var s=e.selectionStart,n=e.selectionEnd;if(s===n&&s>0)s--;e.setRangeText('',s,n,'end');}catch(x){e.value=(e.value||'').slice(0,-1);}
 e.dispatchEvent(new Event('input',{bubbles:true}));})();"""
            CarKeyboard.ENTER -> """
(function(){var e=document.activeElement;if(!e)return;
 var o={key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true};
 var go=e.dispatchEvent(new KeyboardEvent('keydown',o));e.dispatchEvent(new KeyboardEvent('keyup',o));
 if(go&&e.form&&e.tagName!=='TEXTAREA'){if(e.form.requestSubmit)e.form.requestSubmit();else e.form.submit();}
 else if(go&&e.form&&e.tagName==='TEXTAREA'&&e.form.querySelector('[type=submit]')==null&&e.form.requestSubmit){e.form.requestSubmit();}
 if(e.blur)e.blur();})();"""
            else -> {
                val t = JSONObject.quote(key)
                """
(function(){var e=document.activeElement;if(!e)return;var t=$t;
 if(e.isContentEditable){document.execCommand('insertText',false,t);return;}
 try{e.setRangeText(t,e.selectionStart,e.selectionEnd,'end');}catch(x){e.value=(e.value||'')+t;}
 e.dispatchEvent(new Event('input',{bubbles:true}));})();"""
            }
        }
        web.evaluateJavascript(js, null)
        if (key == CarKeyboard.ENTER) showKeyboard(false)
    }

    // ---- The floating bar ----

    fun applyBar() {
        bar.removeAllViews()
        val scale = Browser.barSize(context) / 100f
        val size = (dp(52) * scale).toInt()
        bar.background = round(Browser.barColourInt(context), size / 2f)
        bar.setPadding(size / 6, size / 10, size / 6, size / 10)
        val show = Browser.barButtons(context)
        val starred = page?.let { p -> Browser.bookmarks(context).any { it.url == p.second } } == true
        for ((key, _) in Browser.BAR_BUTTONS) {
            if (key !in show) continue
            val res = when (key) {
                "back" -> R.drawable.ic_back
                "forward" -> R.drawable.ic_forward
                "home" -> R.drawable.ic_home
                "reload" -> R.drawable.ic_reload
                "star" -> if (starred) R.drawable.ic_star_filled else R.drawable.ic_star
                "tabs" -> R.drawable.ic_tabs
                "zoomout" -> R.drawable.ic_zoom_out
                "zoomin" -> R.drawable.ic_zoom_in
                "keyboard" -> R.drawable.ic_keyboard
                "fullscreen" -> R.drawable.ic_fullscreen
                "move" -> R.drawable.ic_move
                else -> R.drawable.ic_close
            }
            val v = icon(res) { press(key) }
            if (key == "tabs" && tabs.size > 1) {
                val box = FrameLayout(context)
                box.addView(v, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                box.addView(label("${tabs.size}", 11f * scale, Color.WHITE).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                bar.addView(box, LinearLayout.LayoutParams(size, size))
            } else {
                bar.addView(v, LinearLayout.LayoutParams(size, size))
            }
        }
        if (moving) bar.addView(label("  Drag to move, tap to finish  ", 14f * scale, Color.WHITE))
        bar.visibility = if (fullscreen) View.GONE else View.VISIBLE
        restore.visibility = if (fullscreen) View.VISIBLE else View.GONE
        restore.background = round(Browser.barColourInt(context), dp(22).toFloat())
        restore.setPadding(dp(8), dp(8), dp(8), dp(8))
        bar.bringToFront()
        restore.bringToFront()
        container.post { positionBar() }
    }

    private fun press(key: String) {
        when (key) {
            "back" -> goBack()
            "forward" -> goForward()
            "home" -> load(Browser.HOME)
            "reload" -> reload()
            "star" -> toggleBookmark()
            "tabs" -> toggleTabs()
            "zoomout" -> zoomBy(-10)
            "zoomin" -> zoomBy(10)
            "keyboard" -> showKeyboard(keyboard.visibility != View.VISIBLE)
            "fullscreen" -> setFullscreen(true)
            "move" -> { moving = true; applyBar() }
            "close" -> onClose()
        }
    }

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        applyBar()
    }

    /** Places the bar at its saved spot, kept on screen and above the keyboard. */
    private fun positionBar() {
        val cw = container.width - container.paddingLeft - container.paddingRight
        val ch = container.height - container.paddingTop - container.paddingBottom
        if (cw <= 0 || bar.width <= 0) {
            if (cw > 0) bar.post { positionBar() }
            return
        }
        val kb = if (keyboard.visibility == View.VISIBLE) keyboard.height else 0
        val freeX = (cw - bar.width).coerceAtLeast(0)
        val freeY = (ch - kb - bar.height - dp(8)).coerceAtLeast(0)
        bar.translationX = Browser.barX(context) * freeX
        bar.translationY = dp(4) + Browser.barY(context) * freeY
    }

    /** In move mode a car drag moves the bar. */
    fun moveBar(dx: Float, dy: Float) {
        val cw = (container.width - bar.width).coerceAtLeast(1)
        val ch = (container.height - bar.height).coerceAtLeast(1)
        Browser.setBarPos(context, Browser.barX(context) - dx / cw, Browser.barY(context) - dy / ch)
        positionBar()
    }

    fun endMove() {
        moving = false
        applyBar()
    }

    // ---- Small views ----

    private fun round(colour: Int, radius: Float) = GradientDrawable().apply {
        setColor(colour)
        cornerRadius = radius
    }

    private fun icon(res: Int, click: () -> Unit) = ImageView(context).apply {
        setImageResource(res)
        scaleType = ImageView.ScaleType.FIT_CENTER
        val p = dp(11)
        setPadding(p, p, p, p)
        setOnClickListener { click() }
    }

    private fun label(text: String, size: Float, colour: Int) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(colour)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun pill(text: String, click: () -> Unit) = label(text, 18f, Color.parseColor("#0E1116")).apply {
        gravity = Gravity.CENTER
        background = round(Color.parseColor("#4FD1C5"), dp(24).toFloat())
        setPadding(dp(24), dp(10), dp(24), dp(10))
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(12); topMargin = dp(8) }
    }

    /** Forgets cookies, history and cached pages. */
    fun clearData() {
        tabs.forEach {
            it.clearHistory()
            it.clearCache(true)
        }
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }

    /** Stops everything, including any video sound. */
    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        tabs.forEach {
            it.loadUrl("about:blank")
            it.destroy()
        }
        tabs.clear()
        container.removeAllViews()
    }

    companion object {
        const val MAX_TABS = 6
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
