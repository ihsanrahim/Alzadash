package com.autodash.car

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.autodash.R
import com.autodash.core.Bookmark
import com.autodash.core.Browser
import com.autodash.core.TripTracker
import com.autodash.dash.DashboardView
import com.autodash.dash.DockApp

/**
 * Everything drawn on the car screen: the dashboard, or the browser with its own address bar,
 * buttons and keyboard (laid out like AutoDeck's car browser).
 * The browser is only visible while [TripTracker.isParked]; while moving it is covered and only audio continues.
 */
class DashPresentation(context: Context, display: Display) : Presentation(context, display) {
    private lateinit var root: FrameLayout
    private lateinit var dash: DashboardView
    private lateinit var webLayer: LinearLayout
    private lateinit var webBox: FrameLayout
    private lateinit var address: TextView
    private lateinit var reloadIcon: ImageView
    private lateinit var starIcon: ImageView
    private lateinit var keyboard: CarKeyboard
    private lateinit var block: TextView
    private var mode = Mode.DASH
    private var visible = Rect()
    private var lastUrl = Browser.HOME
    private var loading = false
    /** What the car keyboard is typing into: the address bar, or a box on the page. */
    private var typingAddress = true
    private var typed = ""
    var onDock: (DockApp) -> Unit = {}
    var onRadioStep: (Int) -> Unit = {}
    var onStation: (com.autodash.core.Station) -> Unit = {}
    var onSettings: () -> Unit = {}
    var onQuick: (String) -> Unit = {}
    var onYouTube: (String) -> Unit = {}
    var onOpenApp: (String) -> Unit = {}
    var onOpenLink: (String) -> Unit = {}
    var onCloseBrowser: () -> Unit = {}
    var onSites: () -> Unit = {}
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (mode == Mode.DASH) dash.invalidate()
            updateGate()
            handler.postDelayed(this, 500)
        }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = getContext()
        root = FrameLayout(ctx).apply { setBackgroundColor(DashboardView.BG) }
        dash = DashboardView(ctx).apply {
            onDockTap = { app -> onDock(app) }
            onRadioStep = { d -> this@DashPresentation.onRadioStep(d) }
            onStationTap = { st -> onStation(st) }
            onSettingsTap = { onSettings() }
            onQuick = { k -> this@DashPresentation.onQuick(k) }
            onYouTube = { k -> this@DashPresentation.onYouTube(k) }
            onOpenApp = { pkg -> this@DashPresentation.onOpenApp(pkg) }
            onOpenLink = { url -> this@DashPresentation.onOpenLink(url) }
        }

        // ---- Browser: bar on top, page, keyboard at the bottom when typing ----
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#14192A"))
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        bar.addView(icon(R.drawable.ic_back) { browser?.let { if (it.canGoBack()) it.goBack() else onCloseBrowser() } })
        bar.addView(icon(R.drawable.ic_forward) { browser?.goForward() })
        reloadIcon = icon(R.drawable.ic_reload) { browser?.let { if (loading) it.stop() else it.reload() } }
        bar.addView(reloadIcon)
        address = TextView(ctx).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8B95AD"))
            hint = "Search or type a web address"
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#1F2840"))
            }
            setOnClickListener { showAddressKeyboard() }
        }
        bar.addView(address, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(6), 0, dp(6), 0) })
        bar.addView(icon(R.drawable.ic_keyboard) { showKeyboard(address = false) })
        starIcon = icon(R.drawable.ic_star) { toggleBookmark() }
        bar.addView(starIcon)
        bar.addView(icon(R.drawable.ic_menu) { onSites() })
        bar.addView(icon(R.drawable.ic_dash) { onCloseBrowser() })

        webBox = FrameLayout(ctx)
        keyboard = CarKeyboard(ctx).apply {
            visibility = View.GONE
            onKey = { k -> key(k) }
            onDelete = { deleteKey() }
            onGo = { goKey() }
            onClose = { hideKeyboard() }
        }
        webLayer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(webBox, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(keyboard, LinearLayout.LayoutParams(MATCH_PARENT, 0, 0.75f))
        }

        block = TextView(ctx).apply {
            setBackgroundColor(DashboardView.BG)
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            isClickable = true // swallows taps so the page can't be used while moving
            visibility = View.GONE
        }
        root.addView(dash, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(webLayer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(block, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        setVisibleArea(visible)
        setMode(mode)
        handler.post(tick)
    }

    private fun icon(res: Int, onTap: () -> Unit) = ImageView(context).apply {
        setImageResource(res)
        setColorFilter(Color.WHITE)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(9), dp(9), dp(9), dp(9))
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        setOnClickListener { onTap() }
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        browser?.destroy()
        browser = null
        super.onStop()
    }

    fun setVisibleArea(r: Rect) {
        visible = Rect(r)
        if (!::root.isInitialized) return
        dash.visibleArea = visible
        root.post {
            // The browser keeps clear of the area Android Auto uses for its own buttons.
            if (visible.isEmpty) webLayer.setPadding(0, 0, 0, 0)
            else webLayer.setPadding(visible.left, visible.top, root.width - visible.right, root.height - visible.bottom)
        }
    }

    fun setMode(m: Mode) {
        mode = m
        if (!::root.isInitialized) return
        if (m == Mode.WEB) {
            if (browser == null) browser = newBrowser(lastUrl)
            webLayer.visibility = View.VISIBLE
            dash.visibility = View.GONE
        } else {
            hideKeyboard()
            webLayer.visibility = View.GONE
            dash.visibility = View.VISIBLE
            // Leaving the browser stops it entirely, video sound included.
            browser?.destroy()
            browser = null
        }
        updateGate()
    }

    private fun newBrowser(url: String) = CarBrowser(context, webBox, url).also { b ->
        b.onPage = { u, _ -> showAddress(u) }
        b.onLoading = { on ->
            loading = on
            reloadIcon.setImageResource(if (on) R.drawable.ic_close else R.drawable.ic_reload)
        }
        // Selecting a text box on the page brings up the car keyboard for it.
        b.onField = { on -> if (on) showKeyboard(address = false) else if (!typingAddress) hideKeyboard() }
    }

    /** The browser while it is open. */
    var browser: CarBrowser? = null
        private set

    fun openUrl(url: String) {
        lastUrl = url
        if (mode != Mode.WEB) setMode(Mode.WEB) else browser?.load(url)
    }

    private fun showAddress(url: String) {
        lastUrl = url.ifEmpty { lastUrl }
        if (keyboard.visibility == View.VISIBLE && typingAddress) return
        val home = url.isEmpty() || url.startsWith(Browser.HOME) || url == "about:blank"
        address.text = if (home) "" else url.removePrefix("https://").removePrefix("www.")
        val saved = !home && Browser.bookmarks(context).any { it.url == url }
        starIcon.setImageResource(if (saved) R.drawable.ic_star_filled else R.drawable.ic_star)
    }

    private fun toggleBookmark() {
        val page = browser?.page ?: return
        if (Browser.bookmarks(context).any { it.url == page.second }) Browser.removeBookmark(context, page.second)
        else Browser.addBookmark(context, Bookmark(page.first.take(40), page.second))
        showAddress(page.second)
    }

    // ---- Car keyboard ----

    fun showAddressKeyboard() = showKeyboard(address = true)

    private fun showKeyboard(address: Boolean) {
        if (!::root.isInitialized || mode != Mode.WEB) return
        typingAddress = address
        if (address) {
            typed = ""
            this.address.text = "▏"
        }
        keyboard.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        if (!::root.isInitialized) return
        keyboard.visibility = View.GONE
        if (typingAddress) showAddress(lastUrl)
        typingAddress = true
    }

    private fun key(k: String) {
        if (typingAddress) {
            typed += k
            address.text = typed + "▏"
        } else browser?.type(k)
    }

    private fun deleteKey() {
        if (typingAddress) {
            typed = typed.dropLast(1)
            address.text = typed + "▏"
        } else browser?.backspace()
    }

    private fun goKey() {
        if (typingAddress) {
            val url = Browser.toUrl(typed)
            hideKeyboard()
            openUrl(url)
        } else {
            browser?.enter()
            hideKeyboard()
        }
    }

    /** The safety gate: the browser shows only when the car is confirmed stopped. */
    private fun updateGate() {
        if (mode != Mode.WEB || TripTracker.isParked()) {
            block.visibility = View.GONE
            return
        }
        block.text = if (TripTracker.speedKmh() == null) {
            TripTracker.problem(context) + "\n\nThe page appears once the car is confirmed stopped."
        } else {
            "The browser is hidden while driving.\n\nSound keeps playing. The page comes back when you stop."
        }
        if (block.visibility != View.VISIBLE) {
            block.visibility = View.VISIBLE
            keyboard.visibility = View.GONE
        }
    }

    // ---- Input from the car screen ----

    fun tap(x: Float, y: Float) {
        val target = window?.decorView ?: return
        val t = SystemClock.uptimeMillis()
        send(target, MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        send(target, MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
    }

    fun scroll(dx: Float, dy: Float) {
        if (block.visibility == View.VISIBLE) return
        browser?.scroll(dx, dy)
    }

    fun zoomBy(factor: Float) {
        if (block.visibility == View.VISIBLE) return
        browser?.zoomBy(factor)
    }

    private fun send(view: View, e: MotionEvent) {
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    companion object {
        const val YOUTUBE = "https://m.youtube.com"
    }
}
