package com.alzadash.car

import android.content.Intent
import androidx.car.app.CarAppApiLevels
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.alzadash.core.Chime
import com.alzadash.core.Prefs
import com.alzadash.core.TripTracker

class DashSession : Session() {
    private var surface: CarSurface? = null
    private var speedListener: OnCarDataAvailableListener<Speed>? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val s = CarSurface(carContext)
        surface = s
        TripTracker.start(carContext)
        attachCarSpeed()
        if (Prefs(carContext).welcomeSound) Chime.play()

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                detachCarSpeed()
                surface?.release()
                surface = null
                TripTracker.stop()
            }
        })
        return DashScreen(carContext, s)
    }

    /** Uses the car's own speed sensor when Android Auto shares it; GPS is the fallback. */
    private fun attachCarSpeed() {
        try {
            if (carContext.carAppApiLevel < CarAppApiLevels.LEVEL_3) return
            val info = carContext.getCarService(CarHardwareManager::class.java).carInfo
            val listener = OnCarDataAvailableListener<Speed> { speed ->
                val v = speed.displaySpeedMetersPerSecond
                if (v.status == CarValue.STATUS_SUCCESS) v.value?.let { TripTracker.onCarSpeed(it) }
            }
            info.addSpeedListener(ContextCompat.getMainExecutor(carContext), listener)
            speedListener = listener
        } catch (_: Exception) {
        }
    }

    private fun detachCarSpeed() {
        val listener = speedListener ?: return
        try {
            carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(listener)
        } catch (_: Exception) {
        }
        speedListener = null
    }
}
