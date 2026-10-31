package com.alzadash.car

import android.app.Activity
import android.graphics.RectF
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import java.lang.ref.WeakReference

/**
 * Waze (or Google Maps) beside a narrow dashboard. Opened on the car screen on top of
 * everything else; the map itself runs on a second virtual display shown in [map].
 */
class SplitActivity : Activity() {
    private lateinit var dash: DashboardView
    private lateinit var map: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        open = WeakReference(this)
        val car = CarSurface.active?.get()
        val root = FrameLayout(this)
        dash = DashboardView(this).apply {
            onDockTap = { app -> car?.onDock?.invoke(app) }
            onRadioStep = { d -> car?.onRadioStep?.invoke(d) }
            val tick = object : Runnable {
                override fun run() {
                    invalidate()
                    postDelayed(this, 500)
                }
            }
            post(tick)
        }
        map = SurfaceView(this).apply {
            setZOrderMediaOverlay(true)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {}
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    CarSurface.active?.get()?.onMapSurface(holder.surface, width, height)
                }
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    CarSurface.active?.get()?.onMapSurfaceGone()
                }
            })
        }
        root.addView(dash, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(map, FrameLayout.LayoutParams(0, 0))
        setContentView(root)
        layoutAreas()
    }

    /** Places the map and the dashboard column using the car's split, see [CarSurface.splitAreas]. */
    fun layoutAreas() {
        val (m, panel) = CarSurface.active?.get()?.splitAreas() ?: return
        map.layoutParams = FrameLayout.LayoutParams(m.width(), m.height()).apply {
            leftMargin = m.left
            topMargin = m.top
        }
        dash.panel = RectF(panel)
    }

    override fun onDestroy() {
        if (open?.get() === this) open = null
        super.onDestroy()
    }

    companion object {
        private var open: WeakReference<SplitActivity>? = null

        fun close() {
            open?.get()?.finish()
            open = null
        }

        fun relayout() {
            open?.get()?.layoutAreas()
        }
    }
}
