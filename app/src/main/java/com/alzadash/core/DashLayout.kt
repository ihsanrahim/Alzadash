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
    CALENDAR("Calendar", null),
    AIR("Air quality", null),
    RADAR("Rain radar", null),
    PLAYER("Music player", null),
    YOUTUBE("YouTube", null),
    WEATHER2("Weather (detail)", null),
}

/** How a widget's text is drawn: [scale] 0.5–2.0, [align] 0 left, 1 centre, 2 right. */
data class WidgetStyle(val scale: Float = 1f, val align: Int = 0)

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
        // Newer widgets start hidden; added from the phone editor, they appear here until moved.
        Widget.CALENDAR to g(7.3f, 2.8f, 11.2f, 4.55f),
        Widget.AIR to g(11.4f, 0.5f, 14.1f, 4.55f),
        Widget.RADAR to g(11.4f, 0.5f, 14.1f, 2.5f),
        Widget.PLAYER to g(0.5f, 6.45f, 9.0f, 9.5f),
        Widget.YOUTUBE to g(9.2f, 6.45f, 14.1f, 7.9f),
        Widget.WEATHER2 to g(11.4f, 2.8f, 14.1f, 4.55f),
    )

    /** Hidden until added in the editor. */
    private val NEW_WIDGETS = setOf(Widget.CALENDAR, Widget.AIR, Widget.RADAR, Widget.PLAYER, Widget.YOUTUBE, Widget.WEATHER2)

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

    fun reset(context: Context) = sp(context).edit().remove("dash_layout").remove("dash_hidden").remove("widget_style").apply()

    // ---- Which widgets are shown ----

    fun hidden(context: Context): Set<Widget> {
        val raw = sp(context).getString("dash_hidden", null) ?: return NEW_WIDGETS
        return raw.split(',').mapNotNull { n -> Widget.values().firstOrNull { it.name == n } }.toSet()
    }

    fun setHidden(context: Context, w: Widget, hide: Boolean) {
        val set = hidden(context).toMutableSet()
        if (hide) set += w else set -= w
        sp(context).edit().putString("dash_hidden", set.joinToString(",") { it.name }).apply()
        if (!hide && w.feature != null) Features.set(context, w.feature, true)
    }

    /** Shown on the car: not removed in the editor, and its feature (if any) switched on. */
    fun shown(context: Context, w: Widget) =
        w !in hidden(context) && (w.feature == null || Features.on(context, w.feature))

    // ---- Text size and alignment per widget ----

    private var styleRaw: String? = "unset"
    private var styleCache: Map<Widget, WidgetStyle> = emptyMap()

    fun style(context: Context, w: Widget): WidgetStyle {
        val raw = sp(context).getString("widget_style", null)
        if (raw != styleRaw) {
            styleRaw = raw
            styleCache = try {
                val o = JSONObject(raw ?: "{}")
                Widget.values().mapNotNull { wd ->
                    o.optJSONObject(wd.name)?.let { wd to WidgetStyle(it.optDouble("scale", 1.0).toFloat(), it.optInt("align", 0)) }
                }.toMap()
            } catch (_: Exception) { emptyMap() }
        }
        return styleCache[w] ?: WidgetStyle()
    }

    fun setStyle(context: Context, w: Widget, st: WidgetStyle) {
        val o = try { JSONObject(sp(context).getString("widget_style", null) ?: "{}") } catch (_: Exception) { JSONObject() }
        o.put(w.name, JSONObject().put("scale", st.scale.toDouble()).put("align", st.align))
        sp(context).edit().putString("widget_style", o.toString()).apply()
    }

    // ---- Saved layouts ----

    fun savedNames(context: Context): List<String> = try {
        JSONObject(sp(context).getString("saved_layouts", null) ?: "{}").keys().asSequence().toList().sorted()
    } catch (_: Exception) { emptyList() }

    fun saveAs(context: Context, name: String) {
        val p = sp(context)
        val all = try { JSONObject(p.getString("saved_layouts", null) ?: "{}") } catch (_: Exception) { JSONObject() }
        all.put(name, JSONObject()
            .put("layout", p.getString("dash_layout", null) ?: "")
            .put("hidden", p.getString("dash_hidden", null) ?: hidden(context).joinToString(",") { it.name })
            .put("style", p.getString("widget_style", null) ?: ""))
        p.edit().putString("saved_layouts", all.toString()).apply()
    }

    fun load(context: Context, name: String) {
        val p = sp(context)
        val one = try { JSONObject(p.getString("saved_layouts", null) ?: "{}").optJSONObject(name) } catch (_: Exception) { null } ?: return
        val e = p.edit()
        fun put(key: String, v: String) { if (v.isEmpty()) e.remove(key) else e.putString(key, v) }
        put("dash_layout", one.optString("layout"))
        e.putString("dash_hidden", one.optString("hidden"))
        put("widget_style", one.optString("style"))
        e.apply()
    }

    fun deleteSaved(context: Context, name: String) {
        val p = sp(context)
        val all = try { JSONObject(p.getString("saved_layouts", null) ?: "{}") } catch (_: Exception) { return }
        all.remove(name)
        p.edit().putString("saved_layouts", all.toString()).apply()
    }

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
