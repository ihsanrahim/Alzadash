package com.alzadash.mirror

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * Holds the phone in landscape while mirroring, even standing upright in its holder, so the
 * car gets a wide picture. Uses a 1-pixel invisible overlay ("Display over other apps").
 */
object Rotation {
    private var view: View? = null
    private var wm: WindowManager? = null

    fun allowed(context: Context) = Settings.canDrawOverlays(context)

    fun forceLandscape(context: Context) {
        if (view != null || !allowed(context)) return
        val m = context.applicationContext.getSystemService(WindowManager::class.java) ?: return
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(1, 1, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply {
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        val v = View(context.applicationContext)
        try {
            m.addView(v, lp)
            view = v
            wm = m
        } catch (_: Exception) {
        }
    }

    fun release() {
        val v = view ?: return
        try { wm?.removeView(v) } catch (_: Exception) {}
        view = null
    }
}
