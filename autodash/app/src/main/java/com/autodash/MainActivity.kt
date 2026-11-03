package com.autodash

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CarDashboardScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Speed.start(this)
    }

    override fun onStop() {
        super.onStop()
        Speed.stop(this)
    }
}

/** Size of the layout the widget positions below were designed for, in dp. The whole dashboard is scaled to fit the screen. */
private const val FULL_W = 992f
private const val FULL_H = 688f
private const val SPLIT_W = 496f
private const val SPLIT_H = 752f

@Composable
fun GridBackgroundOverlay(gridStepDp: Float = 16f) {
    val density = LocalDensity.current.density
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepPx = gridStepDp * density
        var x = stepPx
        while (x < size.width) {
            drawLine(Color(0x28FF5722), Offset(x, 0f), Offset(x, size.height), 1f)
            x += stepPx
        }
        var y = stepPx
        while (y < size.height) {
            drawLine(Color(0x28FF5722), Offset(0f, y), Offset(size.width, y), 1f)
            y += stepPx
        }
    }
}

@Composable
fun CarDashboardScreen() {
    val context = LocalContext.current
    var editMode by remember { mutableStateOf(false) }
    var isSplitMode by remember { mutableStateOf(WidgetLayoutPrefs.isSplitModeActive(context)) }
    var showBrowserInSplit by remember { mutableStateOf(false) }
    var resetTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            Speed.tick()
            delay(1000)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0E14))) {
        val base = LocalDensity.current
        val designW = if (isSplitMode) SPLIT_W else FULL_W
        val designH = if (isSplitMode) SPLIT_H else FULL_H
        val scale = minOf(maxWidth.value / designW, maxHeight.value / designH)
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (editMode) { GridBackgroundOverlay(16f) }
                if (isSplitMode) {
                    Box(modifier = Modifier.offset(x = 492.dp).fillMaxHeight().width(2.dp).background(Color(0xFF1E293B)))
                }

                AdjustableWidgetContainer("widget_clock", "Clock", 16f, 16f, 176f, 160f, 16f, 16f, 224f, 128f, isSplitMode, editMode, resetTrigger) { TimeDisplayWidget() }
                AdjustableWidgetContainer("widget_speedo", "Speedometer", 208f, 16f, 160f, 160f, 256f, 16f, 224f, 128f, isSplitMode, editMode, resetTrigger) { GpsSpeedometerWidget() }
                AdjustableWidgetContainer("widget_media", "Now Playing", 16f, 192f, 352f, 160f, 16f, 160f, 464f, 144f, isSplitMode, editMode, resetTrigger) { NowPlayingWidget() }
                AdjustableWidgetContainer("widget_prayer", "Prayer Times", 16f, 368f, 352f, 144f, 16f, 320f, 464f, 128f, isSplitMode, editMode, resetTrigger) { PrayerTimesWidget() }
                AdjustableWidgetContainer("widget_calendar", "Calendar", 384f, 368f, 288f, 144f, 16f, 464f, 224f, 112f, isSplitMode, editMode, resetTrigger) {
                    GoogleCalendarWidget { if (!isSplitMode) { isSplitMode = true; WidgetLayoutPrefs.setSplitModeActive(context, true) } }
                }
                AdjustableWidgetContainer("widget_app_dock", "Split-Screen App Dock", 688f, 368f, 288f, 144f, 256f, 464f, 224f, 112f, isSplitMode, editMode, resetTrigger) {
                    SideBySideAppDockWidget { if (!isSplitMode) { isSplitMode = true; WidgetLayoutPrefs.setSplitModeActive(context, true) } }
                }
                AdjustableWidgetContainer("widget_radio_row", "Top 20 Radio", 16f, 528f, 960f, 96f, 16f, 592f, 464f, 96f, isSplitMode, editMode, resetTrigger) { Top20RadioRowWidget() }

                if (!isSplitMode || showBrowserInSplit) {
                    AdjustableWidgetContainer("widget_browser", "Shield Browser", 384f, 16f, 592f, 336f, 16f, 16f, 464f, 560f, isSplitMode, editMode, resetTrigger) { ShieldBrowserWidget() }
                }

                Row(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FloatingActionButton(onClick = { editMode = !editMode }, containerColor = if (editMode) Color(0xFFFF5722) else Color(0xFF1E2638), modifier = Modifier.size(44.dp)) {
                        Icon(if (editMode) Icons.Default.Check else Icons.Default.GridOn, contentDescription = "Edit", tint = Color.White)
                    }
                    ExtendedFloatingActionButton(onClick = { isSplitMode = !isSplitMode; WidgetLayoutPrefs.setSplitModeActive(context, isSplitMode) }, containerColor = if (isSplitMode) Color(0xFF00C853) else Color(0xFF1E2638), contentColor = Color.White, modifier = Modifier.height(44.dp)) {
                        Icon(Icons.Default.VerticalSplit, contentDescription = "Split")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSplitMode) "Left-Dock Active" else "1-Tap Split", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    if (isSplitMode) {
                        ExtendedFloatingActionButton(onClick = { showBrowserInSplit = !showBrowserInSplit }, containerColor = if (showBrowserInSplit) Color(0xFFFF6D00) else Color(0xFF1E2638), contentColor = Color.White, modifier = Modifier.height(44.dp)) {
                            Icon(Icons.Default.Public, contentDescription = "Web")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showBrowserInSplit) "Hide Web" else "Web", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    if (editMode) {
                        ExtendedFloatingActionButton(onClick = { WidgetLayoutPrefs.clearCurrentProfile(context, isSplitMode); resetTrigger++ }, containerColor = Color(0xFFB71C1C), contentColor = Color.White, modifier = Modifier.height(44.dp)) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Grid", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
