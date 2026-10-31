package com.alzadash.car

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * Our own screen, opened on the car display on top of a real app: either the dashboard (HOME)
 * or the "video hidden while driving" cover (BLOCK). Sound from the app underneath keeps playing.
 */
class CoverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        open += WeakReference(this)
        if (intent.getStringExtra("kind") == HOME) {
            setContentView(DashboardView(this).apply {
                onDockTap = { app -> CarSurface.active?.get()?.onDock?.invoke(app) }
                val tick = object : Runnable {
                    override fun run() {
                        invalidate()
                        postDelayed(this, 500)
                    }
                }
                post(tick)
            })
        } else {
            setContentView(TextView(this).apply {
                setBackgroundColor(DashboardView.BG)
                setTextColor(Color.WHITE)
                textSize = 26f
                gravity = Gravity.CENTER
                text = "Video is hidden while driving.\n\nSound keeps playing. The picture comes back when you stop."
            })
        }
    }

    override fun onDestroy() {
        open.removeAll { it.get() == null || it.get() === this }
        super.onDestroy()
    }

    companion object {
        const val HOME = "home"
        const val BLOCK = "block"
        private val open = mutableListOf<WeakReference<CoverActivity>>()

        fun finishAll() {
            open.mapNotNull { it.get() }.forEach { it.finish() }
            open.clear()
        }
    }
}
