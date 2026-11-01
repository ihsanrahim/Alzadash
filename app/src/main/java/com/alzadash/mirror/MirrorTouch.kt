package com.alzadash.mirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Lets taps on the car's mirror reach the phone. An accessibility service is the one way a
 * normal app may touch other apps' screens; it reads nothing and only sends the taps given to it.
 */
class MirrorTouch : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun stroke(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val p = Path().apply {
            moveTo(x1.coerceAtLeast(0f), y1.coerceAtLeast(0f))
            lineTo(x2.coerceAtLeast(0f), y2.coerceAtLeast(0f))
        }
        try {
            dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile var instance: MirrorTouch? = null
            private set

        fun enabled(context: Context): Boolean {
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.contains("${context.packageName}/") && list.contains("MirrorTouch")
        }

        fun tap(x: Float, y: Float) = instance?.stroke(x, y, x, y, 60) != null
        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) = instance?.stroke(x1, y1, x2, y2, 250) != null
        fun back() = instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        fun home() = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
    }
}
