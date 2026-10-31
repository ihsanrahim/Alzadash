package com.alzadash.car

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.alzadash.core.Shell
import com.alzadash.core.TripTracker
import java.lang.ref.WeakReference

/** DASH: the dashboard. WEB: the built-in browser. APP: a real phone app opened on the car screen. */
enum class Mode { DASH, WEB, APP }

/**
 * Owns the car screen. It is a virtual display that shows [DashPresentation], and with Shizuku
 * running it can also host real apps such as YouTube ReVanced. Video of any kind is only
 * visible while [TripTracker.isParked].
 */
class CarSurface(private val carContext: CarContext) : SurfaceCallback {
    var mode = Mode.DASH
        private set
    var onDock: (DockApp) -> Unit = {}
    private var display: VirtualDisplay? = null
    private var presentation: DashPresentation? = null
    private var visible = Rect()
    private var width = 0
    private var height = 0
    /** True while one of our cover activities sits on top of an app on the car screen. */
    private var coverOnTop = false
    private var blocking = false
    private val handler = Handler(Looper.getMainLooper())

    private val gate = object : Runnable {
        override fun run() {
            // Picks up location permission granted after the car connected.
            TripTracker.start(carContext)
            checkGate()
            handler.postDelayed(this, 500)
        }
    }

    init {
        active = WeakReference(this)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        handler.post(gate)
    }

    val displayId: Int get() = display?.display?.displayId ?: -1

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        release(keepGate = true)
        width = container.width
        height = container.height
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay("AlzaDash", width, height, container.dpi, surface, 0)
        display = vd
        presentation = DashPresentation(carContext, vd.display).also {
            it.show()
            it.setVisibleArea(visible)
            it.onDock = { app -> onDock(app) }
            it.setMode(if (mode == Mode.APP) Mode.DASH else mode)
        }
        if (mode == Mode.APP) mode = Mode.DASH
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        presentation?.setVisibleArea(visible)
    }

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onSurfaceDestroyed(container: SurfaceContainer) = release(keepGate = true)

    override fun onClick(x: Float, y: Float) {
        val id = displayId
        if ((mode == Mode.APP || coverOnTop) && id >= 0) Shell.tap(id, x, y) else presentation?.tap(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        val id = displayId
        if (mode == Mode.APP && !coverOnTop && id >= 0) {
            val cx = width / 2f
            val cy = height / 2f
            Shell.swipe(id, cx, cy, cx - distanceX * 3, cy - distanceY * 3)
        } else {
            presentation?.drag(distanceX, distanceY)
        }
    }

    fun setMode(m: Mode) {
        if (m == Mode.APP) return
        val fromApp = mode == Mode.APP || coverOnTop
        mode = m
        blocking = false
        if (fromApp) {
            // An app is still open underneath; put our dashboard on top of it.
            CoverActivity.finishAll()
            coverOnTop = false
            if (m == Mode.DASH) showCover(CoverActivity.HOME)
        }
        presentation?.let {
            it.show()
            it.setBlocked(false)
            it.setMode(m)
        }
    }

    fun openUrl(url: String) {
        setMode(Mode.WEB)
        presentation?.openUrl(url)
    }

    /** Opens a real app on the car screen through Shizuku. */
    fun openApp(component: String) {
        val id = displayId
        if (id < 0) return
        mode = Mode.APP
        coverOnTop = false
        blocking = false
        CoverActivity.finishAll()
        presentation?.hide()
        Shell.startOnDisplay(id, component)
        checkGate()
    }

    fun goBack() {
        if (mode == Mode.APP) Shell.key(displayId, 4) else presentation?.goBack()
    }

    fun playPause() = Shell.key(displayId, 85)

    /** Covers an app with our own screen while moving, and uncovers it once parked again. */
    private fun checkGate() {
        if (mode != Mode.APP) return
        val parked = TripTracker.isParked()
        if (!parked && !blocking) {
            blocking = true
            showCover(CoverActivity.BLOCK)
            presentation?.let {
                it.show()
                it.setBlocked(true)
            }
        } else if (parked && blocking) {
            blocking = false
            coverOnTop = false
            CoverActivity.finishAll()
            presentation?.let {
                it.setBlocked(false)
                it.hide()
            }
        }
    }

    private fun showCover(kind: String) {
        val id = displayId
        if (id < 0) return
        coverOnTop = true
        Shell.startOnDisplay(id, "${carContext.packageName}/${CoverActivity::class.java.name}", "kind", kind)
    }

    fun release(keepGate: Boolean = false) {
        if (!keepGate) handler.removeCallbacks(gate)
        CoverActivity.finishAll()
        coverOnTop = false
        blocking = false
        presentation?.dismiss()
        presentation = null
        display?.release()
        display = null
    }

    companion object {
        /** The live car screen, so the cover activity can forward shortcut taps to it. */
        var active: WeakReference<CarSurface>? = null
    }
}
