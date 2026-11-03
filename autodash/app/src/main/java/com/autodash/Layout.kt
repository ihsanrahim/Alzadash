package com.autodash

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

object WidgetLayoutPrefs {
    const val PREFS_NAME = "car_dashboard_layout_prefs"
    private const val KEY_SPLIT_MODE = "is_split_screen_preset_active"

    fun snapToGrid(valueDp: Float, gridStepDp: Float = 16f): Float {
        return (valueDp / gridStepDp).roundToInt() * gridStepDp
    }

    fun isSplitModeActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SPLIT_MODE, false)
    }

    fun setSplitModeActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SPLIT_MODE, active)
            .apply()
    }

    private fun keyPrefix(widgetId: String, isSplitMode: Boolean): String {
        val profile = if (isSplitMode) "split" else "full"
        return "${profile}_${widgetId}"
    }

    fun loadBounds(context: Context, widgetId: String, isSplitMode: Boolean, defaultX: Float, defaultY: Float, defaultW: Float, defaultH: Float): FloatArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefix = keyPrefix(widgetId, isSplitMode)
        return floatArrayOf(
            prefs.getFloat("${prefix}_x", defaultX),
            prefs.getFloat("${prefix}_y", defaultY),
            prefs.getFloat("${prefix}_w", defaultW),
            prefs.getFloat("${prefix}_h", defaultH)
        )
    }

    fun savePosition(context: Context, widgetId: String, isSplitMode: Boolean, xDp: Float, yDp: Float) {
        val prefix = keyPrefix(widgetId, isSplitMode)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("${prefix}_x", snapToGrid(xDp))
            .putFloat("${prefix}_y", snapToGrid(yDp))
            .apply()
    }

    fun saveSize(context: Context, widgetId: String, isSplitMode: Boolean, widthDp: Float, heightDp: Float) {
        val prefix = keyPrefix(widgetId, isSplitMode)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("${prefix}_w", snapToGrid(widthDp))
            .putFloat("${prefix}_h", snapToGrid(heightDp))
            .apply()
    }

    fun clearCurrentProfile(context: Context, isSplitMode: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefix = if (isSplitMode) "split_" else "full_"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }
}

@Composable
fun AdjustableWidgetContainer(
    widgetId: String, title: String,
    initialX: Float, initialY: Float, initialWidth: Float, initialHeight: Float,
    splitX: Float, splitY: Float, splitWidth: Float, splitHeight: Float,
    isSplitMode: Boolean, editMode: Boolean, resetTrigger: Int,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val defX = if (isSplitMode) splitX else initialX
    val defY = if (isSplitMode) splitY else initialY
    val defW = if (isSplitMode) splitWidth else initialWidth
    val defH = if (isSplitMode) splitHeight else initialHeight
    val bounds = remember(widgetId, isSplitMode, resetTrigger) { WidgetLayoutPrefs.loadBounds(context, widgetId, isSplitMode, defX, defY, defW, defH) }

    // Position is kept in dp so it stays put when the dashboard rescales (rotation, split screen).
    var xDp by remember(widgetId, isSplitMode, resetTrigger) { mutableFloatStateOf(bounds[0]) }
    var yDp by remember(widgetId, isSplitMode, resetTrigger) { mutableFloatStateOf(bounds[1]) }
    var widthDp by remember(widgetId, isSplitMode, resetTrigger) { mutableFloatStateOf(bounds[2]) }
    var heightDp by remember(widgetId, isSplitMode, resetTrigger) { mutableFloatStateOf(bounds[3]) }

    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .offset { IntOffset((xDp * density).roundToInt(), (yDp * density).roundToInt()) }
            .size(widthDp.dp, heightDp.dp)
            .clip(shape)
            .background(Color(0xFF161B26))
            .border(width = if (editMode) 2.dp else 1.dp, color = if (editMode) Color(0xFFFF5722) else Color(0xFF262F45), shape = shape)
    ) {
        content()
        if (editMode) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xAA000000)).pointerInput(widgetId, isSplitMode, density) {
                    detectDragGestures(onDragEnd = {
                        xDp = WidgetLayoutPrefs.snapToGrid(xDp).coerceAtLeast(0f)
                        yDp = WidgetLayoutPrefs.snapToGrid(yDp).coerceAtLeast(0f)
                        WidgetLayoutPrefs.savePosition(context, widgetId, isSplitMode, xDp, yDp)
                    }) { c, d -> c.consume(); xDp += d.x / density; yDp += d.y / density }
                },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.OpenWith, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).size(38.dp).clip(RoundedCornerShape(topStart = 12.dp)).background(Color(0xFFFF5722)).pointerInput(widgetId, isSplitMode, density) {
                        detectDragGestures(onDragEnd = {
                            widthDp = WidgetLayoutPrefs.snapToGrid(widthDp).coerceAtLeast(128f)
                            heightDp = WidgetLayoutPrefs.snapToGrid(heightDp).coerceAtLeast(80f)
                            WidgetLayoutPrefs.saveSize(context, widgetId, isSplitMode, widthDp, heightDp)
                        }) { c, d -> c.consume(); widthDp = (widthDp + d.x / density).coerceAtLeast(128f); heightDp = (heightDp + d.y / density).coerceAtLeast(80f) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.AspectRatio, contentDescription = "Resize", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
