package com.alzadash.car

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.alzadash.core.TripTracker
import com.alzadash.mirror.Mirror
import com.alzadash.mirror.MirrorTouch

/**
 * The phone's screen on the car display, with its own "hidden while driving" cover:
 * the picture shows only once the car is confirmed stopped, like video and web pages.
 */
class MirrorView(context: Context) : FrameLayout(context) {
    private val surfaceView = SurfaceView(context)
    private val note = TextView(context).apply {
        setBackgroundColor(DashboardView.BG)
        setTextColor(Color.WHITE)
        textSize = 24f
        gravity = Gravity.CENTER
        setPadding(48, 48, 48, 48)
        isClickable = true
    }

    private val tick = object : Runnable {
        override fun run() {
            update()
            postDelayed(this, 500)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(surfaceView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(note, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Mirror.attach(holder.surface, width, height, resources.displayMetrics.densityDpi)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) = Mirror.detach(holder.surface)
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    /** Re-attaches the picture once casting has started. */
    fun refresh() {
        val h = surfaceView.holder
        val f = h.surfaceFrame
        if (h.surface?.isValid == true && f.width() > 0) {
            Mirror.attach(h.surface, f.width(), f.height(), resources.displayMetrics.densityDpi)
        }
        update()
    }

    private fun update() {
        val text = when {
            !Mirror.active -> "Mirror your phone\n\nOn your phone, tap Start (choose \"Entire screen\").\nIf nothing pops up, tap the AlzaDash notification."
            !TripTracker.isParked() -> if (TripTracker.speedKmh() == null) {
                TripTracker.problem(context) + "\n\nThe phone's screen shows once the car is confirmed stopped."
            } else {
                "Mirroring is hidden while driving.\n\nSound keeps playing. The picture comes back when you stop."
            }
            !MirrorTouch.enabled(context) -> null.also { hint = true }
            else -> null.also { hint = false }
        }
        if (text == null) {
            note.visibility = View.GONE
        } else {
            note.text = text
            note.visibility = View.VISIBLE
        }
    }

    /** True when taps won't reach the phone because the accessibility switch is off. */
    var hint = false
        private set

    /** Only pass taps on while the picture is showing. */
    val accepting: Boolean get() = Mirror.active && TripTracker.isParked()
}
