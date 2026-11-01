package com.alzadash.mirror

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface

/**
 * Phone mirroring without Shizuku: the phone's screen is captured with Android's own screen
 * casting (MediaProjection) and drawn on the car screen; taps go back through [MirrorTouch].
 */
object Mirror {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var target: Surface? = null
    private var tw = 0
    private var th = 0
    private var tdpi = 160
    private val main = Handler(Looper.getMainLooper())

    /** Called on the main thread whenever mirroring starts or stops. */
    var onChange: () -> Unit = {}

    val active: Boolean get() = projection != null

    /** Opens the phone's "Start recording or casting?" question. */
    fun requestStart(context: Context) {
        val i = Intent(context, MirrorStartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(i)
        } catch (_: Exception) {
        }
        // Android may not let the question pop up by itself; a notification opens it too.
        MirrorService.notifyTapToStart(context)
    }

    /** The car screen's mirror area is ready (or changed size). */
    fun attach(surface: Surface, w: Int, h: Int, dpi: Int) {
        target = surface
        tw = w
        th = h
        tdpi = dpi
        val p = projection ?: return
        val d = display
        if (d == null) {
            display = try {
                p.createVirtualDisplay("AlzaDash mirror", w, h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null)
            } catch (_: Exception) {
                null
            }
        } else {
            d.resize(w, h, dpi)
            d.surface = surface
        }
    }

    fun detach(surface: Surface) {
        if (target !== surface) return
        target = null
        display?.surface = null
    }

    /** MirrorService got permission to capture the screen. */
    internal fun started(p: MediaProjection) {
        projection?.stop()
        display?.release()
        display = null
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { if (projection === p) cleanUp() }
            }
        }, main)
        target?.let { attach(it, tw, th, tdpi) }
        onChange()
    }

    /** Stops mirroring and gives the phone its normal rotation back. */
    fun stop(context: Context) {
        val p = projection
        cleanUp()
        try { p?.stop() } catch (_: Exception) {}
        context.stopService(Intent(context, MirrorService::class.java))
    }

    private fun cleanUp() {
        display?.release()
        display = null
        projection = null
        Rotation.release()
        onChange()
    }

    /**
     * Turns a point on the car's mirror area (size [vw]×[vh]) into a point on the phone's
     * screen. The phone's picture is scaled to fit and centred, so the bars are left out.
     */
    fun toPhone(context: Context, x: Float, y: Float, vw: Int, vh: Int): PointF? {
        val real = phoneSize(context) ?: return null
        val scale = minOf(vw.toFloat() / real.x, vh.toFloat() / real.y)
        val ox = (vw - real.x * scale) / 2
        val oy = (vh - real.y * scale) / 2
        val px = (x - ox) / scale
        val py = (y - oy) / scale
        if (px < 0 || py < 0 || px > real.x || py > real.y) return null
        return PointF(px, py)
    }

    /** How many phone pixels one car pixel is worth, for turning drags into swipes. */
    fun scale(context: Context, vw: Int, vh: Int): Float {
        val real = phoneSize(context) ?: return 1f
        return 1f / minOf(vw.toFloat() / real.x, vh.toFloat() / real.y)
    }

    @Suppress("DEPRECATION")
    private fun phoneSize(context: Context): Point? {
        val d = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val p = Point()
        d.getRealSize(p)
        return if (p.x > 0 && p.y > 0) p else null
    }
}
