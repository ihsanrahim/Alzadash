package com.autodash.car

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.autodash.core.DashLayout
import com.autodash.core.Station
import com.autodash.core.TripTracker
import com.autodash.dash.DashboardView
import com.autodash.dash.DockApp
import java.lang.ref.WeakReference

/** DASH: the dashboard. WEB: the built-in browser. */
enum class Mode { DASH, WEB }

/**
 * Owns the car screen: Android Auto hands us a surface, we put a virtual display on it and show
 * [DashPresentation] there (the same way AutoDeck draws its car browser). Taps and drags from
 * the car screen are passed on to it. No Shizuku: only our own screens are shown.
 */
class CarSurface(private val carContext: CarContext) : SurfaceCallback {
    var mode = Mode.DASH
        private set
    var onDock: (DockApp) -> Unit = {}
    var onRadioStep: (Int) -> Unit = {}
    var onStation: (Station) -> Unit = {}
    var onSettings: () -> Unit = {}
    var onQuick: (String) -> Unit = {}
    var onYouTube: (String) -> Unit = {}
    var onOpenApp: (String) -> Unit = {}
    /** The browser's own buttons changed something the car's top buttons depend on. */
    var onChanged: () -> Unit = {}
    private var display: VirtualDisplay? = null
    private var presentation: DashPresentation? = null
    private var visible = Rect()
    private var width = 0
    private var height = 0
    private var pendingUrl: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val gate = object : Runnable {
        override fun run() {
            // Picks up location permission granted after the car connected.
            TripTracker.start(carContext)
            handler.postDelayed(this, 1000)
        }
    }

    init {
        active = WeakReference(this)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        handler.post(gate)
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        release(keepGate = true)
        width = container.width
        height = container.height
        if (height > 0) DashLayout.saveCarAspect(carContext, width.toFloat() / height)
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay("Autodash", width, height, container.dpi, surface, 0)
        display = vd
        presentation = DashPresentation(carContext, vd.display).also {
            it.show()
            it.setVisibleArea(visible)
            it.onDock = { app -> onDock(app) }
            it.onRadioStep = { d -> onRadioStep(d) }
            it.onStation = { st -> onStation(st) }
            it.onSettings = { onSettings() }
            it.onQuick = { k -> onQuick(k) }
            it.onYouTube = { k -> onYouTube(k) }
            it.onOpenApp = { pkg -> onOpenApp(pkg) }
            it.onOpenLink = { url -> openUrl(url) }
            it.onCloseBrowser = { setMode(Mode.DASH) }
            it.onSites = { onQuick("sites") }
            it.setMode(mode)
            pendingUrl?.let { url -> it.openUrl(url) }
            pendingUrl = null
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        presentation?.setVisibleArea(visible)
        if (height > 0) DashLayout.saveCarAspect(carContext, width.toFloat() / height)
    }

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onSurfaceDestroyed(container: SurfaceContainer) = release(keepGate = true)

    override fun onClick(x: Float, y: Float) {
        presentation?.tap(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (mode == Mode.WEB) {
            presentation?.scroll(distanceX, distanceY)
        } else {
            // On the dashboard a sideways drag scrolls the radio row; on the lyrics page it scrolls the lyrics.
            if (DashboardView.lyricsOpen) DashboardView.scrollLyrics(distanceY) else DashboardView.scrollRow(distanceX)
        }
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (mode == Mode.WEB) presentation?.zoomBy(scaleFactor)
    }

    fun setMode(m: Mode) {
        if (mode == m) return
        mode = m
        presentation?.setMode(m)
        onChanged()
    }

    fun openUrl(url: String) {
        mode = Mode.WEB
        val p = presentation
        if (p == null) pendingUrl = url else p.openUrl(url)
        onChanged()
    }

    /** The built-in browser while it is open. */
    val browser: CarBrowser? get() = presentation?.browser

    fun goBack() {
        val b = browser
        if (b != null && b.canGoBack()) b.goBack() else setMode(Mode.DASH)
    }

    /** Shows the car keyboard for the web address (parked only, like Android Auto's own keyboard). */
    fun showAddressKeyboard() = presentation?.showAddressKeyboard()

    fun release(keepGate: Boolean = false) {
        if (!keepGate) handler.removeCallbacks(gate)
        presentation?.dismiss()
        presentation = null
        display?.release()
        display = null
    }

    companion object {
        /** The live car screen, so the phone app can send pages to it. */
        var active: WeakReference<CarSurface>? = null
    }
}
