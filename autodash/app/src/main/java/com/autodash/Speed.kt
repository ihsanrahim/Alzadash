package com.autodash

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * One GPS feed for the whole app: the speedometer reads it, and the browser uses it to cover
 * the page while the car is moving.
 */
object Speed {
    /** Covers the browser at or above this speed. */
    const val MOVING_KMH = 10f
    /** Uncovers after the car has been below [PARKED_KMH] for this long. */
    private const val PARKED_MS = 3_000L
    private const val PARKED_KMH = 3f
    /** Uncovers if GPS has gone quiet this long (indoors, tunnel left long ago). */
    private const val NO_FIX_MS = 60_000L

    var speedMps by mutableFloatStateOf(0f)
        private set
    var hasPermission by mutableStateOf(false)
        private set
    /** True while the browser must stay covered. */
    var driving by mutableStateOf(false)
        private set

    private var lastFixAt by mutableLongStateOf(0L)
    private var slowSince = 0L
    private var started = false

    val speedKmh: Float get() = speedMps * 3.6f

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            speedMps = if (loc.hasSpeed()) loc.speed else 0f
            lastFixAt = SystemClock.elapsedRealtime()
            update()
        }
        // Needed on Android 8 to 10, where these aren't default methods yet.
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun update() {
        val now = SystemClock.elapsedRealtime()
        val kmh = speedKmh
        if (kmh >= MOVING_KMH) {
            driving = true
            slowSince = 0L
        } else if (driving) {
            if (kmh < PARKED_KMH) {
                if (slowSince == 0L) slowSince = now
                if (now - slowSince >= PARKED_MS) driving = false
            } else {
                slowSince = 0L
            }
        }
    }

    /** Called every second by the UI so "parked for 3 s" and "no GPS for a minute" still fire with no new fixes. */
    fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (driving && lastFixAt != 0L && now - lastFixAt > NO_FIX_MS) {
            driving = false
            speedMps = 0f
        } else {
            update()
        }
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission || started) return
        val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener, Looper.getMainLooper())
            started = true
        } catch (_: Exception) {
        }
    }

    fun stop(context: Context) {
        if (!started) return
        val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.removeUpdates(listener)
        started = false
    }
}
