package com.alzadash.car

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.alzadash.core.TripTracker
import java.lang.ref.WeakReference

/**
 * Our own screen, opened on the car display on top of a real app: either the dashboard (HOME)
 * or the "video hidden while driving" cover (BLOCK). Sound from the app underneath keeps playing.
 */
class CoverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        open += WeakReference(this)
        if (intent.getStringExtra("kind") == MIRROR) {
            // The phone's mirrored screen, kept inside the area Android Auto leaves free.
            val root = FrameLayout(this).apply { setBackgroundColor(DashboardView.BG) }
            val v = MirrorView(this)
            val a = CarSurface.active?.get()?.contentArea()
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            if (a != null && !a.isEmpty) root.post {
                lp.setMargins(a.left, a.top, (root.width - a.right).coerceAtLeast(0), (root.height - a.bottom).coerceAtLeast(0))
                v.layoutParams = lp
            }
            root.addView(v, lp)
            setContentView(root)
            mirror = WeakReference(v)
            return
        }
        if (intent.getStringExtra("kind") == WEB) {
            val root = FrameLayout(this)
            val page = FrameLayout(this)
            root.addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            val block = TextView(this).apply {
                setBackgroundColor(DashboardView.BG)
                setTextColor(Color.WHITE)
                textSize = 26f
                gravity = Gravity.CENTER
                isClickable = true
                text = "The browser is hidden while driving.\n\nSound keeps playing. The page comes back when you stop."
                visibility = android.view.View.GONE
            }
            root.addView(block, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            setContentView(root)
            browser = CarBrowser(this, page, pendingUrl ?: com.alzadash.core.Browser.HOME)
            ownsBrowser = true
            // Same safety gate as the dashboard's browser: the page shows only while parked.
            val gate = object : Runnable {
                override fun run() {
                    block.visibility = if (TripTracker.isParked()) android.view.View.GONE else android.view.View.VISIBLE
                    root.postDelayed(this, 500)
                }
            }
            root.post(gate)
            return
        }
        if (intent.getStringExtra("kind") == HOME) {
            setContentView(DashboardView(this).apply {
                onDockTap = { app -> CarSurface.active?.get()?.onDock?.invoke(app) }
                onRadioStep = { d -> CarSurface.active?.get()?.onRadioStep?.invoke(d) }
                onStationTap = { st -> CarSurface.active?.get()?.onStation?.invoke(st) }
                onSettingsTap = { CarSurface.active?.get()?.onSettings?.invoke() }
                onQuick = { k -> CarSurface.active?.get()?.onQuick?.invoke(k) }
                onOpenApp = { pkg -> CarSurface.active?.get()?.onOpenApp?.invoke(pkg) }
                onOpenLink = { url -> CarSurface.active?.get()?.openLink(url) }
                val tick = object : Runnable {
                    override fun run() {
                        invalidate()
                        postDelayed(this, 500)
                    }
                }
                post(tick)
            })
        } else {
            val label = TextView(this).apply {
                setBackgroundColor(DashboardView.BG)
                setTextColor(Color.WHITE)
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(48, 0, 48, 0)
            }
            setContentView(label)
            // Say why the picture is hidden: driving, or no speed reading yet (and what to fix).
            val update = object : Runnable {
                override fun run() {
                    label.text = if (TripTracker.speedKmh() == null) {
                        TripTracker.problem(this@CoverActivity) + "\n\nVideo shows once the car is confirmed stopped. Sound keeps playing."
                    } else {
                        "Video is hidden while driving.\n\nSound keeps playing. The picture comes back when you stop."
                    }
                    label.postDelayed(this, 1000)
                }
            }
            label.post(update)
        }
    }

    private var ownsBrowser = false

    override fun onDestroy() {
        if (ownsBrowser) {
            browser?.destroy()
            browser = null
        }
        open.removeAll { it.get() == null || it.get() === this }
        if (mirror?.get()?.context === this) mirror = null
        super.onDestroy()
    }

    companion object {
        const val HOME = "home"
        const val BLOCK = "block"
        const val WEB = "web"
        const val MIRROR = "mirror"

        /** The mirrored phone screen shown in a cover screen, while open. */
        var mirror: WeakReference<MirrorView>? = null

        /** The browser shown in a cover screen, while open. */
        var browser: CarBrowser? = null
            private set

        /** Address the next web cover screen opens. */
        var pendingUrl: String? = null
        private val open = mutableListOf<WeakReference<CoverActivity>>()

        fun finishAll() {
            open.mapNotNull { it.get() }.forEach { it.finish() }
            open.clear()
        }
    }
}
