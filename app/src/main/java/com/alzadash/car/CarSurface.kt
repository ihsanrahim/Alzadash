package com.alzadash.car

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.alzadash.core.DashLayout
import com.alzadash.core.Station
import com.alzadash.core.TripTracker
import com.alzadash.mirror.Mirror
import com.alzadash.mirror.MirrorTouch
import java.lang.ref.WeakReference

/** DASH: the dashboard. WEB: the built-in browser. MIRROR: the phone's own screen. */
enum class Mode { DASH, WEB, MIRROR }

/**
 * Owns the car screen: a virtual display that shows [DashPresentation]. Web pages and the
 * mirrored phone screen are only visible while [TripTracker.isParked].
 */
class CarSurface(private val carContext: CarContext) : SurfaceCallback {
    var mode = Mode.DASH
        private set
    var onRadioStep: (Int) -> Unit = {}
    var onStation: (Station) -> Unit = {}
    var onSettings: () -> Unit = {}
    var onQuick: (String) -> Unit = {}
    /** Short spoken/toast message for the driver. */
    var onMessage: (String) -> Unit = {}
    var onOpenApp: (String) -> Unit = {}
    /** The browser's own buttons changed something the car's button row shows. */
    var onBrowserChanged: () -> Unit = {}
    private var display: VirtualDisplay? = null
    private var presentation: DashPresentation? = null
    private var visible = Rect()
    private var width = 0
    private var height = 0
    private val handler = Handler(Looper.getMainLooper())

    /** The mirrored phone screen pauses when the phone sleeps, so the phone is kept awake (dimmed) meanwhile. */
    @Suppress("DEPRECATION")
    private val wake = carContext.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Autodash:carApp")
        .apply { setReferenceCounted(false) }

    private val gate = object : Runnable {
        override fun run() {
            // Picks up location permission granted after the car connected.
            TripTracker.start(carContext)
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
        if (height > 0) DashLayout.saveCarAspect(carContext, width.toFloat() / height)
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay("Autodash", width, height, container.dpi, surface, 0)
        display = vd
        presentation = DashPresentation(carContext, vd.display).also {
            it.show()
            it.setVisibleArea(visible)
            it.onRadioStep = { d -> onRadioStep(d) }
            it.onStation = { st -> onStation(st) }
            it.onSettings = { onSettings() }
            it.onQuick = { k -> onQuick(k) }
            it.onOpenApp = { pkg -> onOpenApp(pkg) }
            it.onOpenLink = { url -> openLink(url) }
            it.onCloseBrowser = { setMode(Mode.DASH); onBrowserChanged() }
            it.onMessage = { m -> onMessage(m) }
            it.setMode(mode)
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
        if (mode == Mode.MIRROR) mirrorTap(x, y) else presentation?.tap(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        when {
            mode == Mode.MIRROR -> mirrorScroll(distanceX, distanceY)
            mode == Mode.WEB -> presentation?.drag(distanceX, distanceY)
            // On the dashboard a sideways drag scrolls the radio row; on the lyrics page it scrolls the lyrics.
            DashboardView.lyricsOpen -> DashboardView.scrollLyrics(distanceY)
            else -> DashboardView.scrollRow(distanceX)
        }
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (mode == Mode.WEB) browser?.pinch(scaleFactor)
    }

    fun setMode(m: Mode) {
        if (m == Mode.MIRROR) return openMirror()
        leaveMirror()
        mode = m
        presentation?.setMode(m)
    }

    fun openUrl(url: String) {
        leaveMirror()
        if (mode != Mode.WEB) setMode(Mode.WEB)
        presentation?.openUrl(url)
    }

    /** A news story was tapped: open it in the browser and refresh the car's buttons. */
    var onLinkOpened: () -> Unit = {}

    fun openLink(url: String) {
        openUrl(url)
        onLinkOpened()
    }

    /** The built-in browser while it is open. */
    val browser: CarBrowser? get() = presentation?.browser

    fun goBack() {
        if (mode == Mode.MIRROR) MirrorTouch.back() else browser?.goBack()
    }

    // ---- Phone mirroring ----

    /** Shows the phone's own screen on the car screen, asking the phone for permission first if needed. */
    fun openMirror() {
        if (!Mirror.active) Mirror.requestStart(carContext)
        Mirror.onChange = { presentation?.mirrorView?.refresh() }
        wake.acquire(4 * 60 * 60_000L)
        mode = Mode.MIRROR
        presentation?.setMode(Mode.MIRROR)
    }

    /** Stops mirroring when the car screen switches to something else. */
    private fun leaveMirror() {
        if (mode != Mode.MIRROR) return
        Mirror.stop(carContext)
        if (wake.isHeld) wake.release()
        mode = Mode.DASH
    }

    fun mirrorHome() = MirrorTouch.home()

    private fun mirrorArea() = if (visible.isEmpty) Rect(0, 0, width, height) else visible

    /** The part of the car screen Android Auto leaves free for us. */
    fun contentArea(): Rect = Rect(mirrorArea())

    private fun mirrorTap(x: Float, y: Float) {
        val v = presentation?.mirrorView ?: return
        if (!v.accepting) return
        val a = mirrorArea()
        if (!a.contains(x.toInt(), y.toInt())) return
        val p = Mirror.toPhone(carContext, x - a.left, y - a.top, a.width(), a.height()) ?: return
        if (!MirrorTouch.tap(p.x, p.y)) onMessage("Turn on Autodash in Accessibility on your phone to tap here")
    }

    private var scrollX = 0f
    private var scrollY = 0f
    private val sendScroll = Runnable {
        val a = mirrorArea()
        val k = Mirror.scale(carContext, a.width(), a.height())
        val c = Mirror.toPhone(carContext, a.width() / 2f, a.height() / 2f, a.width(), a.height())
        if (c != null) MirrorTouch.swipe(c.x, c.y, c.x - scrollX * k, c.y - scrollY * k)
        scrollX = 0f
        scrollY = 0f
    }

    /** Car drags arrive as many small scrolls; they are gathered into one swipe on the phone. */
    private fun mirrorScroll(dx: Float, dy: Float) {
        if (presentation?.mirrorView?.accepting != true) return
        scrollX += dx
        scrollY += dy
        handler.removeCallbacks(sendScroll)
        handler.postDelayed(sendScroll, 80)
    }

    fun release(keepGate: Boolean = false) {
        if (!keepGate) handler.removeCallbacks(gate)
        if (wake.isHeld) wake.release()
        presentation?.dismiss()
        presentation = null
        display?.release()
        display = null
    }

    companion object {
        /** The live car screen, so the phone app can send links to it. */
        var active: WeakReference<CarSurface>? = null
    }
}
