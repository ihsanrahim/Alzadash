package com.alzadash.car

import android.graphics.Rect
import android.view.Surface
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
import com.alzadash.core.Input
import com.alzadash.mirror.Mirror
import com.alzadash.mirror.MirrorTouch
import com.alzadash.core.Pkg
import com.alzadash.core.Shell
import com.alzadash.core.Station
import com.alzadash.core.TripTracker
import java.lang.ref.WeakReference

/**
 * DASH: the dashboard. WEB: the built-in browser. APP: a real phone app opened on the car screen.
 * SPLIT: Waze or Google Maps beside a narrow dashboard.
 */
enum class Mode { DASH, WEB, APP, SPLIT, MIRROR }

/**
 * Owns the car screen. It is a virtual display that shows [DashPresentation], and with Shizuku
 * running it can also host real apps such as YouTube ReVanced. Video of any kind is only
 * visible while [TripTracker.isParked].
 */
class CarSurface(private val carContext: CarContext) : SurfaceCallback {
    var mode = Mode.DASH
        private set
    var onDock: (DockApp) -> Unit = {}
    var onRadioStep: (Int) -> Unit = {}
    var onStation: (Station) -> Unit = {}
    var onSettings: () -> Unit = {}
    var onQuick: (String) -> Unit = {}
    /** Short spoken/toast message for the driver. */
    var onMessage: (String) -> Unit = {}
    var onOpenApp: (String) -> Unit = {}
    private var display: VirtualDisplay? = null
    private var presentation: DashPresentation? = null
    private var visible = Rect()
    private var width = 0
    private var height = 0
    private var dpi = 160
    /** Second screen that the map app runs on in [Mode.SPLIT]. Kept while connected so Waze keeps its route. */
    private var mapDisplay: VirtualDisplay? = null
    private var mapLaunched = false
    /** True while one of our cover activities sits on top of an app on the car screen. */
    private var coverOnTop = false
    private var blocking = false
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Apps on the car screen pause when the phone goes to sleep, so the phone is kept awake
     * (screen dimmed) while one is open.
     */
    @Suppress("DEPRECATION")
    private val wake = carContext.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "AlzaDash:carApp")
        .apply { setReferenceCounted(false) }

    private val gate = object : Runnable {
        override fun run() {
            // Picks up location permission granted after the car connected.
            TripTracker.start(carContext)
            Input.connect(carContext)
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
        dpi = container.dpi
        if (height > 0) DashLayout.saveCarAspect(carContext, width.toFloat() / height)
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay("AlzaDash", width, height, container.dpi, surface, 0)
        display = vd
        presentation = DashPresentation(carContext, vd.display).also {
            it.show()
            it.setVisibleArea(visible)
            it.onDock = { app -> onDock(app) }
            it.onRadioStep = { d -> onRadioStep(d) }
            it.onStation = { st -> onStation(st) }
            it.onSettings = { onSettings() }
            it.onQuick = { k -> onQuick(k) }
            it.onOpenApp = { pkg -> onOpenApp(pkg) }
            it.onOpenLink = { url -> openLink(url) }
            it.setMode(if (mode == Mode.APP) Mode.DASH else mode)
        }
        if (mode == Mode.APP) mode = Mode.DASH
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        presentation?.setVisibleArea(visible)
        // The dashboard now fills the whole surface, so the editor matches the full screen shape.
        if (height > 0) DashLayout.saveCarAspect(carContext, width.toFloat() / height)
        SplitActivity.relayout()
    }

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onSurfaceDestroyed(container: SurfaceContainer) = release(keepGate = true)

    /** The map's area and the dashboard column's area on the car screen, in [Mode.SPLIT]. */
    fun splitAreas(): Pair<Rect, Rect> {
        val a = if (visible.isEmpty) Rect(0, 0, width, height) else visible
        val split = a.left + (a.width() * 0.6f).toInt()
        return Rect(a.left, a.top, split, a.bottom) to Rect(split, a.top, a.right, a.bottom)
    }

    override fun onClick(x: Float, y: Float) {
        if (mode == Mode.MIRROR) {
            mirrorTap(x, y)
            return
        }
        val id = displayId
        if (mode == Mode.SPLIT) {
            val m = splitAreas().first
            val mapId = mapDisplay?.display?.displayId
            if (m.contains(x.toInt(), y.toInt()) && mapId != null) Shell.tap(mapId, x - m.left, y - m.top)
            else if (id >= 0) Shell.tap(id, x, y)
            return
        }
        if ((mode == Mode.APP || coverOnTop) && id >= 0) Shell.tap(id, x, y) else presentation?.tap(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (mode == Mode.MIRROR) {
            mirrorScroll(distanceX, distanceY)
            return
        }
        val id = displayId
        if (mode == Mode.SPLIT) {
            val m = splitAreas().first
            val mapId = mapDisplay?.display?.displayId ?: return
            Input.drag(mapId, m.width(), m.height(), distanceX, distanceY)
            return
        }
        if (mode == Mode.APP && !coverOnTop && id >= 0 && Input.drag(id, width, height, distanceX, distanceY)) return
        if (mode == Mode.APP && !coverOnTop && id >= 0) {
            val cx = width / 2f
            val cy = height / 2f
            Shell.swipe(id, cx, cy, cx - distanceX * 3, cy - distanceY * 3)
        } else if (mode == Mode.WEB && coverOnTop && id >= 0) {
            if (!Input.drag(id, width, height, distanceX, distanceY)) {
                Shell.swipe(id, width / 2f, height / 2f, width / 2f - distanceX * 3, height / 2f - distanceY * 3)
            }
        } else if (mode == Mode.WEB) {
            presentation?.drag(distanceX, distanceY)
        } else {
            // On the dashboard a sideways drag scrolls the radio row; on the lyrics page it scrolls the lyrics.
            if (DashboardView.lyricsOpen) DashboardView.scrollLyrics(distanceY) else DashboardView.scrollRow(distanceX)
        }
    }

    fun setMode(m: Mode) {
        if (m == Mode.APP || m == Mode.SPLIT || m == Mode.MIRROR) return
        leaveMirror()
        if (mode == Mode.SPLIT) {
            SplitActivity.close()
            mode = m
            if (wake.isHeld) wake.release()
            presentation?.setMode(m)
            return
        }
        val fromApp = mode == Mode.APP || coverOnTop
        mode = m
        if (wake.isHeld) wake.release()
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
        leaveMirror()
        val id = displayId
        if (mode == Mode.WEB && CoverActivity.browser != null) {
            CoverActivity.browser?.load(url)
            return
        }
        if ((mode == Mode.APP || coverOnTop) && id >= 0) {
            // A phone app is open underneath and would sit above the dashboard's own browser,
            // so the browser opens in a cover screen on top of it instead.
            if (mode == Mode.SPLIT) SplitActivity.close()
            mode = Mode.WEB
            blocking = false
            CoverActivity.finishAll()
            CoverActivity.pendingUrl = url
            showCover(CoverActivity.WEB)
            return
        }
        setMode(Mode.WEB)
        presentation?.openUrl(url)
    }

    /** Opens Waze (or Google Maps) on the left with the dashboard beside it. Returns false if neither is installed. */
    fun openSplit(): Boolean {
        val id = displayId
        if (id < 0) return false
        val pm = carContext.packageManager
        val launch = listOf(Pkg.WAZE, Pkg.MAPS).firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it)?.component }
            ?: return false
        mapComponent = launch.flattenToString()
        mode = Mode.SPLIT
        wake.acquire(4 * 60 * 60_000L)
        Shell.startOnDisplay(id, "${carContext.packageName}/${SplitActivity::class.java.name}")
        return true
    }

    private var mapComponent = ""

    /** The map area on [SplitActivity] is ready: show the map's screen there, starting the map app the first time. */
    fun onMapSurface(surface: Surface, w: Int, h: Int) {
        val vd = mapDisplay
        if (vd == null) {
            val dm = carContext.getSystemService(DisplayManager::class.java)
            mapDisplay = dm.createVirtualDisplay("AlzaDash map", w, h, dpi, surface, 0)
        } else {
            vd.resize(w, h, dpi)
            vd.surface = surface
        }
        val id = mapDisplay?.display?.displayId ?: return
        if (!mapLaunched && mapComponent.isNotEmpty()) {
            mapLaunched = true
            Shell.startOnDisplay(id, mapComponent)
        }
    }

    fun onMapSurfaceGone() {
        mapDisplay?.surface = null
    }

    /** Opens a real app on the car screen through Shizuku. */
    fun openApp(component: String) {
        val id = displayId
        if (id < 0) return
        leaveMirror()
        if (mode == Mode.SPLIT) SplitActivity.close()
        mode = Mode.APP
        coverOnTop = false
        blocking = false
        CoverActivity.finishAll()
        presentation?.hide()
        wake.acquire(4 * 60 * 60_000L)
        // An app already open on the phone is moved over first; otherwise Android would bring
        // it up on the phone again. Then it is started (or brought to the front) on the car
        // screen, and checked once more in case Android still put it on the phone.
        val pkg = component.substringBefore('/')
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Input.moveTask(pkg, id) {
            Shell.startOnDisplay(id, component)
            main.postDelayed({
                Input.moveTask(pkg, id) {
                    // A window moved in may land on top of the "hidden while driving" cover; put the cover back on top.
                    main.post { if (blocking && displayId == id) showCover(CoverActivity.BLOCK) }
                }
            }, 1500)
        }
        checkGate()
    }

    /** A news story was tapped: open it in the browser and refresh the car's buttons. */
    var onLinkOpened: () -> Unit = {}

    fun openLink(url: String) {
        openUrl(url)
        onLinkOpened()
    }

    /** The built-in browser while it is open, wherever it is shown. */
    val browser: CarBrowser? get() = CoverActivity.browser ?: presentation?.browser

    fun goBack() {
        if (mode == Mode.MIRROR) {
            MirrorTouch.back()
            return
        }
        if (mode == Mode.APP) Shell.key(displayId, 4) else browser?.goBack()
    }

    fun playPause() = Shell.key(displayId, 85)

    /** Types into whatever field is selected in the app on the car screen. */
    fun typeText(words: String) {
        val target = if (mode == Mode.SPLIT) mapDisplay?.display?.displayId ?: -1 else displayId
        if (target >= 0) Shell.typeText(target, words)
    }

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

    // ---- Phone mirroring (no Shizuku needed) ----

    /** Shows the phone's own screen on the car screen, asking the phone for permission first if needed. */
    fun openMirror() {
        val id = displayId
        if (mode == Mode.SPLIT) SplitActivity.close()
        if (!Mirror.active) Mirror.requestStart(carContext)
        Mirror.onChange = {
            presentation?.mirrorView?.refresh()
            CoverActivity.mirror?.get()?.refresh()
        }
        if (wake.isHeld) wake.release()
        wake.acquire(4 * 60 * 60_000L)
        if ((mode == Mode.APP || coverOnTop) && id >= 0) {
            // A phone app is open on the car screen above our own drawing; mirror in a cover screen on top.
            mode = Mode.MIRROR
            blocking = false
            CoverActivity.finishAll()
            showCover(CoverActivity.MIRROR)
            return
        }
        mode = Mode.MIRROR
        presentation?.let {
            it.show()
            it.setBlocked(false)
            it.setMode(Mode.MIRROR)
        }
    }

    /** Stops mirroring when the car screen switches to something else. */
    private fun leaveMirror() {
        if (mode != Mode.MIRROR) return
        Mirror.stop(carContext)
        if (wake.isHeld) wake.release()
        if (coverOnTop) {
            CoverActivity.finishAll()
            coverOnTop = false
            mode = Mode.APP // an app is still open underneath
        } else {
            mode = Mode.DASH
        }
    }

    fun mirrorHome() = MirrorTouch.home()

    private fun mirrorArea() = if (visible.isEmpty) Rect(0, 0, width, height) else visible

    /** The part of the car screen Android Auto leaves free for us. */
    fun contentArea(): Rect = Rect(mirrorArea())

    private fun mirrorView(): MirrorView? = CoverActivity.mirror?.get() ?: presentation?.mirrorView

    private fun mirrorTap(x: Float, y: Float) {
        val v = mirrorView() ?: return
        if (!v.accepting) return
        val a = mirrorArea()
        if (!a.contains(x.toInt(), y.toInt())) return
        val p = Mirror.toPhone(carContext, x - a.left, y - a.top, a.width(), a.height()) ?: return
        if (!MirrorTouch.tap(p.x, p.y)) onMessage("Turn on AlzaDash in Accessibility on your phone to tap here")
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
        if (mirrorView()?.accepting != true) return
        scrollX += dx
        scrollY += dy
        handler.removeCallbacks(sendScroll)
        handler.postDelayed(sendScroll, 80)
    }

    fun release(keepGate: Boolean = false) {
        if (!keepGate) handler.removeCallbacks(gate)
        if (wake.isHeld) wake.release()
        CoverActivity.finishAll()
        SplitActivity.close()
        if (mode == Mode.SPLIT) mode = Mode.DASH
        coverOnTop = false
        blocking = false
        mapDisplay?.release()
        mapDisplay = null
        mapLaunched = false
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
