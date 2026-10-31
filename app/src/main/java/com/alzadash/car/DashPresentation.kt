package com.alzadash.car

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.TextView
import com.alzadash.core.Browser
import com.alzadash.core.TripTracker

/**
 * Everything drawn on the car screen: the dashboard, or the video browser.
 * Video is only visible while [TripTracker.isParked]; while moving it is covered and only audio continues.
 */
class DashPresentation(context: Context, display: Display) : Presentation(context, display) {
    private lateinit var root: FrameLayout
    private lateinit var dash: DashboardView
    private lateinit var videoLayer: FrameLayout
    private lateinit var block: TextView
    private var mode = Mode.DASH
    private var visible = Rect()
    private var lastUrl = Browser.HOME
    private var forcedBlock = false
    var onDock: (DockApp) -> Unit = {}
    var onRadioStep: (Int) -> Unit = {}
    var onStation: (com.alzadash.core.Station) -> Unit = {}
    var onSettings: () -> Unit = {}
    var onOpenApp: (String) -> Unit = {}
    var onOpenLink: (String) -> Unit = {}
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            dash.invalidate()
            updateVideoGate()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = getContext()
        root = FrameLayout(ctx).apply { setBackgroundColor(DashboardView.BG) }
        dash = DashboardView(ctx).apply {
            onDockTap = { app -> onDock(app) }
            onRadioStep = { d -> this@DashPresentation.onRadioStep(d) }
            onStationTap = { st -> onStation(st) }
            onSettingsTap = { onSettings() }
            onOpenApp = { pkg -> this@DashPresentation.onOpenApp(pkg) }
            onOpenLink = { url -> this@DashPresentation.onOpenLink(url) }
        }
        videoLayer = FrameLayout(ctx).apply { visibility = View.GONE }
        block = TextView(ctx).apply {
            setBackgroundColor(DashboardView.BG)
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            isClickable = true // swallows taps so the video can't be used while moving
            visibility = View.GONE
        }
        root.addView(dash, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(videoLayer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(block, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        handler.post(tick)
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
            if (visible.isEmpty) {
                videoLayer.setPadding(0, 0, 0, 0)
            } else {
                videoLayer.setPadding(visible.left, visible.top, root.width - visible.right, root.height - visible.bottom)
            }
        }
    }

    fun setMode(m: Mode) {
        mode = m
        if (!::root.isInitialized) return
        if (m == Mode.WEB) {
            if (browser == null) browser = CarBrowser(getContext(), videoLayer, lastUrl)
            videoLayer.visibility = View.VISIBLE
            dash.visibility = View.GONE
        } else {
            videoLayer.visibility = View.GONE
            dash.visibility = View.VISIBLE
            // Leaving the browser stops it entirely, video sound included.
            browser?.destroy()
            browser = null
        }
        updateVideoGate()
    }

    /** The browser while it is open. */
    var browser: CarBrowser? = null
        private set

    fun openUrl(url: String) {
        lastUrl = url
        if (mode != Mode.WEB) setMode(Mode.WEB) else browser?.load(url)
    }

    fun goBack() = browser?.goBack()

    /** Covers everything, used while a real app is open on the car screen and the car is moving. */
    fun setBlocked(on: Boolean) {
        forcedBlock = on
        if (::root.isInitialized) updateVideoGate()
    }

    /** The safety gate: video shows only when the car is confirmed stopped. */
    private fun updateVideoGate() {
        if (mode != Mode.WEB && !forcedBlock) {
            block.visibility = View.GONE
            return
        }
        if (TripTracker.isParked() && !forcedBlock) {
            block.visibility = View.GONE
        } else {
            block.text = if (TripTracker.speedKmh() == null && !forcedBlock) {
                TripTracker.problem(context) + "\n\nThe page appears once the car is confirmed stopped."
            } else {
                "The browser is hidden while driving.\n\nSound keeps playing. The page comes back when you stop."
            }
            block.visibility = View.VISIBLE
        }
    }

    fun tap(x: Float, y: Float) {
        val target = window?.decorView ?: return
        val t = SystemClock.uptimeMillis()
        send(target, MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        send(target, MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** Turns a scroll on the car screen into a finger drag, so web pages scroll naturally. */
    fun drag(dx: Float, dy: Float) {
        if (mode != Mode.WEB) return
        val target = window?.decorView ?: return
        val cx = target.width / 2f
        val cy = target.height / 2f
        val t = SystemClock.uptimeMillis()
        send(target, MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0))
        send(target, MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, cx - dx / 2, cy - dy / 2, 0))
        send(target, MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_MOVE, cx - dx, cy - dy, 0))
        send(target, MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cx - dx, cy - dy, 0))
    }

    private fun send(view: View, e: MotionEvent) {
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    companion object {
        const val YOUTUBE = "https://m.youtube.com"
        const val NETFLIX = "https://www.netflix.com"

    }
}
