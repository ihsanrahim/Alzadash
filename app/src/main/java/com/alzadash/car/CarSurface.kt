package com.alzadash.car

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer

enum class Mode { DASH, VIDEO }

/** Owns the car screen surface and shows [DashPresentation] on it through a virtual display. */
class CarSurface(private val carContext: CarContext) : SurfaceCallback {
    var mode = Mode.DASH
        private set
    private var display: VirtualDisplay? = null
    private var presentation: DashPresentation? = null
    private var visible = Rect()

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        release()
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay("AlzaDash", container.width, container.height, container.dpi, surface, 0)
        display = vd
        presentation = DashPresentation(carContext, vd.display).also {
            it.show()
            it.setVisibleArea(visible)
            it.setMode(mode)
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        presentation?.setVisibleArea(visible)
    }

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onSurfaceDestroyed(container: SurfaceContainer) = release()

    override fun onClick(x: Float, y: Float) {
        presentation?.tap(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        presentation?.drag(distanceX, distanceY)
    }

    fun setMode(m: Mode) {
        mode = m
        presentation?.setMode(m)
    }

    fun openUrl(url: String) = presentation?.openUrl(url)

    fun goBack() = presentation?.goBack()

    fun release() {
        presentation?.dismiss()
        presentation = null
        display?.release()
        display = null
    }
}
