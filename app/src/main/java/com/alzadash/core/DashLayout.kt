package com.alzadash.core

import android.content.Context
import android.graphics.RectF
import org.json.JSONObject

/** The parts of the dashboard that can be moved and resized from the phone. */
enum class Widget(val label: String, val feature: Feature?) {
    CLOCK("Clock & date", null),
    SPEED("Speed & trip", Feature.SPEED),
    PRAYER("Prayer times", Feature.PRAYER),
    WEATHER("Weather", Feature.WEATHER),
    NEWS("News", Feature.NEWS),
    RADIO_ROW("Radio row", Feature.RADIO_ROW),
    NOW_PLAYING("Now playing", Feature.NOW_PLAYING),
    DOCK("Shortcuts", Feature.DOCK),
}

/**
 * Where each widget sits on the car screen, as fractions (0 to 1) of the visible area, so the
 * same layout fits any screen. Saved on the phone, read by the car screen on every redraw.
 */
object DashLayout {
    // Measured on a 17 × 10 grid, then stored as fractions.
    private fun g(l: Float, t: Float, r: Float, b: Float) = RectF(l / 17f, t / 10f, r / 17f, b / 10f)

    val DEFAULTS: Map<Widget, RectF> = mapOf(
        Widget.CLOCK to g(0.5f, 0.5f, 6.9f, 4.4f),
        Widget.SPEED to g(0.5f, 4.4f, 6.9f, 6.2f),
        Widget.PRAYER to g(7.3f, 0.5f, 14.1f, 2.5f),
        Widget.WEATHER to g(7.3f, 2.8f, 14.1f, 4.55f),
        Widget.NEWS to g(7.3f, 4.85f, 14.1f, 6.15f),
        Widget.RADIO_ROW to g(0.5f, 6.45f, 14.1f, 7.25f),
        Widget.NOW_PLAYING to g(0.5f, 7.5f, 14.1f, 9.5f),
        Widget.DOCK to g(14.6f, 0.5f, 16.5f, 9.5f),
    )

    private var lastRaw: String? = "unset"
    private var cached: Map<Widget, RectF> = DEFAULTS

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences("alzadash", Context.MODE_PRIVATE)

    fun get(context: Context): Map<Widget, RectF> {
        val raw = sp(context).getString("dash_layout", null)
        if (raw == lastRaw) return cached
        lastRaw = raw
        cached = DEFAULTS.toMutableMap().apply {
            if (raw != null) try {
                val o = JSONObject(raw)
                Widget.values().forEach { w ->
                    o.optJSONArray(w.name)?.let { a ->
                        put(w, RectF(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat()))
                    }
                }
            } catch (_: Exception) {
            }
        }
        return cached
    }

    fun save(context: Context, layout: Map<Widget, RectF>) {
        val o = JSONObject()
        layout.forEach { (w, r) -> o.put(w.name, org.json.JSONArray(listOf(r.left, r.top, r.right, r.bottom).map { it.toDouble() })) }
        sp(context).edit().putString("dash_layout", o.toString()).apply()
    }

    fun reset(context: Context) = sp(context).edit().remove("dash_layout").apply()

    /** Widest-to-tallest shape of the car screen's usable area, remembered so the phone editor matches it. */
    fun carAspect(context: Context): Float = sp(context).getFloat("car_aspect", 1.75f)

    fun saveCarAspect(context: Context, aspect: Float) {
        if (aspect > 0.5f && aspect < 5f && kotlin.math.abs(aspect - carAspect(context)) > 0.01f) {
            sp(context).edit().putFloat("car_aspect", aspect).apply()
        }
    }

    /** [frac] placed inside [area]. */
    fun place(frac: RectF, area: RectF) = RectF(
        area.left + frac.left * area.width(), area.top + frac.top * area.height(),
        area.left + frac.right * area.width(), area.top + frac.bottom * area.height(),
    )
}
