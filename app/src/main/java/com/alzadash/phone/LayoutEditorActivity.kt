package com.alzadash.phone

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alzadash.car.DashboardView
import com.alzadash.core.DashLayout
import com.alzadash.core.Look
import com.alzadash.core.Widget
import com.alzadash.core.WidgetStyle
import kotlin.math.abs
import kotlin.math.round

/**
 * The phone's widget editor: a live preview of the car screen (real data, real background)
 * where widgets are dragged and resized, plus size presets, move arrows, text size and
 * alignment, clock options and saved layouts. Every change shows on the car straight away.
 */
class LayoutEditorActivity : AppCompatActivity() {
    private lateinit var preview: PreviewView
    private lateinit var chips: LinearLayout
    private lateinit var panel: LinearLayout
    private var selected: Widget = Widget.CLOCK
    private var tab = 0 // 0 size, 1 style, 2 content
    private val accentColor = Color.parseColor("#4FD1C5")
    private val cardColor = Color.parseColor("#1A1F27")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F14"))
            setPadding(dp(10), dp(10), dp(10), 0)
        }
        // Top bar: title, saved layouts, reset.
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text = "Edit widgets"; textSize = 20f; setTextColor(Color.WHITE) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(smallButton("Layouts") { savedLayouts() })
        top.addView(smallButton("Reset") {
            AlertDialog.Builder(this).setMessage("Put every widget back where it started?")
                .setPositiveButton("Reset") { _, _ -> DashLayout.reset(this); refreshAll() }
                .setNegativeButton("Cancel", null).show()
        })
        root.addView(top)

        preview = PreviewView(this) { w -> select(w) }
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(230)))
        root.addView(note("Drag a widget to move it · drag the dot to resize · tap to edit").apply { gravity = Gravity.CENTER })

        chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(24)) }
        root.addView(ScrollView(this).apply { addView(panel) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refreshAll()
    }

    private fun refreshAll() {
        if (!DashLayout.shown(this, selected)) selected = Widget.values().firstOrNull { DashLayout.shown(this, it) } ?: Widget.CLOCK
        preview.reload(selected)
        buildChips()
        buildPanel()
    }

    private fun select(w: Widget) {
        selected = w
        preview.reload(w)
        buildChips()
        buildPanel()
    }

    private fun buildChips() {
        chips.removeAllViews()
        for (w in Widget.values().filter { DashLayout.shown(this, it) }) {
            chips.addView(smallButton(w.label, if (w == selected) accentColor else cardColor) { select(w) }.apply {
                if (w == selected) setTextColor(Color.BLACK)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        chips.addView(smallButton("+ Add widget", Color.parseColor("#2D3748")) { addWidget() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
    }

    private fun addWidget() {
        val hidden = Widget.values().filter { !DashLayout.shown(this, it) }
        if (hidden.isEmpty()) { toast("Every widget is already on the dashboard"); return }
        AlertDialog.Builder(this).setTitle("Add widget")
            .setItems(hidden.map { it.label }.toTypedArray()) { _, i ->
                DashLayout.setHidden(this, hidden[i], false)
                if (hidden[i] == Widget.CALENDAR && !com.alzadash.core.CalendarEvents.allowed(this)) {
                    requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), 7)
                }
                selected = hidden[i]
                refreshAll()
            }.show()
    }

    private fun buildPanel() {
        panel.removeAllViews()
        val w = selected
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply { text = w.label; textSize = 22f; setTextColor(Color.WHITE); setPadding(dp(6), 0, 0, 0) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(smallButton("Remove") {
            DashLayout.setHidden(this, w, true)
            toast("${w.label} removed. Add it back with + Add widget.")
            refreshAll()
        })
        panel.addView(head)
        val tabs = listOf("Size", "Style", "Content")
        panel.addView(buttonRow(*tabs.mapIndexed { i, t ->
            smallButton(t, if (i == tab) accentColor else cardColor) { tab = i; buildPanel() }.apply { if (i == tab) setTextColor(Color.BLACK) }
        }.toTypedArray()))
        when (tab) {
            0 -> sizeTab(w)
            1 -> styleTab(w)
            else -> contentTab(w)
        }
    }

    /** Size presets on a 12 × 6 grid of the car screen, and move arrows. */
    private fun sizeTab(w: Widget) {
        val presets = listOf("Small" to (2 to 2), "Square" to (3 to 3), "Wide" to (6 to 2),
            "Tall" to (3 to 6), "Large" to (6 to 4), "Full width" to (12 to 2))
        presets.chunked(3).forEach { row ->
            panel.addView(buttonRow(*row.map { (name, size) ->
                smallButton("$name\n${size.first} × ${size.second}") { resize(w, size.first / 12f, size.second / 6f) }.apply { minHeight = dp(64) }
            }.toTypedArray()))
        }
        panel.addView(heading("MOVE"))
        panel.addView(buttonRow(
            smallButton("←") { move(w, -1f / 24, 0f) },
            smallButton("↑") { move(w, 0f, -1f / 12) },
            smallButton("↓") { move(w, 0f, 1f / 12) },
            smallButton("→") { move(w, 1f / 24, 0f) },
        ))
    }

    private fun current() = DashLayout.get(this).mapValues { RectF(it.value) }.toMutableMap()

    private fun resize(w: Widget, fw: Float, fh: Float) {
        val l = current()
        val r = l.getValue(w)
        val left = r.left.coerceAtMost(1f - fw).coerceAtLeast(0f)
        val top = r.top.coerceAtMost(1f - fh).coerceAtLeast(0f)
        l[w] = RectF(left, top, left + fw, top + fh)
        DashLayout.save(this, l)
        preview.reload(w)
    }

    private fun move(w: Widget, dx: Float, dy: Float) {
        val l = current()
        val r = l.getValue(w)
        val nl = (r.left + dx).coerceIn(0f, 1f - r.width())
        val nt = (r.top + dy).coerceIn(0f, 1f - r.height())
        r.offsetTo(nl, nt)
        DashLayout.save(this, l)
        preview.reload(w)
    }

    /** Text size and alignment. */
    private fun styleTab(w: Widget) {
        val st = DashLayout.style(this, w)
        val label = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE); setPadding(dp(6), dp(12), 0, 0) }
        fun show(v: Float) { label.text = "Text size  ${(v * 100).toInt()}%" }
        show(st.scale)
        panel.addView(label)
        panel.addView(SeekBar(this).apply {
            max = 15 // 50% to 200% in steps of 10
            progress = ((st.scale - 0.5f) * 10).toInt().coerceIn(0, 15)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val v = 0.5f + p / 10f
                    show(v)
                    if (fromUser) {
                        DashLayout.setStyle(this@LayoutEditorActivity, w, DashLayout.style(this@LayoutEditorActivity, w).copy(scale = v))
                        preview.reload(w)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })
        if (w == Widget.CLOCK || w == Widget.SPEED) {
            panel.addView(heading("TEXT ALIGNMENT"))
            val names = listOf("Left", "Centre", "Right")
            panel.addView(buttonRow(*names.mapIndexed { i, n ->
                smallButton(n, if (st.align == i) accentColor else cardColor) {
                    DashLayout.setStyle(this, w, DashLayout.style(this, w).copy(align = i))
                    preview.reload(w)
                    buildPanel()
                }.apply { if (st.align == i) setTextColor(Color.BLACK) }
            }.toTypedArray()))
        }
    }

    /** What the widget shows. */
    private fun contentTab(w: Widget) {
        when (w) {
            Widget.CLOCK -> {
                val sw = Switch(this).apply {
                    text = "24-hour clock"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    isChecked = Look.clock24(this@LayoutEditorActivity) == 1
                    setPadding(dp(6), dp(12), dp(6), dp(12))
                    setOnCheckedChangeListener { _, on -> Look.setClock24(this@LayoutEditorActivity, if (on) 1 else 0); preview.reload(w) }
                }
                panel.addView(sw)
                panel.addView(heading("CLOCK FONT"))
                val cur = Look.clockFont(this)
                Look.CLOCK_FONTS.chunked(3).forEach { row ->
                    panel.addView(buttonRow(*row.map { f ->
                        smallButton("12:30\n$f", if (f == cur) accentColor else cardColor) {
                            Look.setClockFont(this, f)
                            preview.reload(w)
                            buildPanel()
                        }.apply {
                            typeface = Look.typeface(f)
                            letterSpacing = Look.letterSpacing(f)
                            minHeight = dp(64)
                            if (f == cur) setTextColor(Color.BLACK)
                        }
                    }.toTypedArray()))
                }
            }
            Widget.CALENDAR -> {
                panel.addView(note("Shows your next events from the phone's calendars."))
                if (!com.alzadash.core.CalendarEvents.allowed(this)) panel.addView(buttonRow(smallButton("Allow calendar", accentColor) {
                    requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), 7)
                }))
            }
            Widget.YOUTUBE -> panel.addView(note("Search by voice, or open Home, Subscriptions or History. Opens in the YouTube app with Shizuku, otherwise in the car browser. Video shows only when parked."))
            Widget.RADAR -> panel.addView(note("Rain radar from RainViewer on an OpenStreetMap map, around where the car is. Updates every 10 minutes."))
            Widget.AIR -> panel.addView(note("US air-quality index and PM2.5 from Open-Meteo, plus UV, humidity and wind."))
            Widget.PLAYER -> panel.addView(note("Album art and controls for Spotify, YouTube Music or any music app. Needs Song access (phone app > Setup)."))
            else -> panel.addView(note("Nothing else to set for this widget. Use Size and Style."))
        }
    }

    private fun savedLayouts() {
        val names = DashLayout.savedNames(this)
        val items = (listOf("Save current layout…") + names.map { "Use \"$it\"" }).toTypedArray()
        AlertDialog.Builder(this).setTitle("Layouts").setItems(items) { _, i ->
            if (i == 0) {
                val box = EditText(this).apply { hint = "Name, e.g. Day or Night" }
                AlertDialog.Builder(this).setTitle("Save layout").setView(box)
                    .setPositiveButton("Save") { _, _ ->
                        val n = box.text.toString().trim()
                        if (n.isNotEmpty()) { DashLayout.saveAs(this, n); toast("Saved \"$n\"") }
                    }.setNegativeButton("Cancel", null).show()
            } else {
                val n = names[i - 1]
                AlertDialog.Builder(this).setTitle(n)
                    .setPositiveButton("Use") { _, _ -> DashLayout.load(this, n); refreshAll() }
                    .setNeutralButton("Delete") { _, _ -> DashLayout.deleteSaved(this, n) }
                    .setNegativeButton("Cancel", null).show()
            }
        }.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        buildPanel()
    }

    /**
     * The car screen drawn by the real dashboard code, scaled down, with the picked widget
     * outlined. Drag to move, drag the corner dot to resize.
     */
    private class PreviewView(context: Context, val onPick: (Widget) -> Unit) : View(context) {
        private val dash = DashboardView(context).apply { preview = true }
        private val area = RectF()
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FD1C5"); style = Paint.Style.STROKE; strokeWidth = 5f }
        private val faint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2f }
        private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FD1C5") }
        private var layout = mutableMapOf<Widget, RectF>()
        private var picked: Widget = Widget.CLOCK
        private var resizing = false
        private var moved = false
        private var startX = 0f
        private var startY = 0f
        private var startRect = RectF()
        private var dashW = 0
        private var dashH = 0
        private val tick = object : Runnable {
            override fun run() { invalidate(); postDelayed(this, 1000) }
        }

        fun reload(w: Widget) {
            picked = w
            layout = DashLayout.get(context).mapValues { RectF(it.value) }.toMutableMap()
            invalidate()
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); post(tick) }
        override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

        private fun shown(w: Widget) = DashLayout.shown(context, w)

        override fun onDraw(canvas: Canvas) {
            val aspect = DashLayout.carAspect(context)
            val w = minOf(width.toFloat(), height * aspect)
            val h = w / aspect
            area.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            // Lay the dashboard out at a car-like size, then draw it scaled into the preview.
            val cw = 1280
            val ch = (cw / aspect).toInt()
            if (cw != dashW || ch != dashH) {
                dashW = cw; dashH = ch
                dash.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY))
                dash.layout(0, 0, cw, ch)
            }
            canvas.save()
            canvas.clipRect(area)
            canvas.translate(area.left, area.top)
            canvas.scale(area.width() / cw, area.height() / ch)
            dash.draw(canvas)
            canvas.restore()
            for (wd in Widget.values()) {
                if (!shown(wd)) continue
                val r = DashLayout.place(layout.getValue(wd), area)
                canvas.drawRoundRect(r, 8f, 8f, if (wd == picked) outline else faint)
            }
            if (shown(picked)) {
                val r = DashLayout.place(layout.getValue(picked), area)
                canvas.drawCircle(r.right, r.bottom, area.width() / 40, handle)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val fx = (e.x - area.left) / area.width()
            val fy = (e.y - area.top) / area.height()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val r = layout.getValue(picked)
                    resizing = shown(picked) && abs(fx - r.right) < 0.06f && abs(fy - r.bottom) < 0.06f * area.width() / area.height()
                    if (!resizing) {
                        Widget.values().filter { shown(it) && layout.getValue(it).contains(fx, fy) }
                            .minByOrNull { layout.getValue(it).width() * layout.getValue(it).height() }
                            ?.let { if (it != picked) onPick(it) }
                    }
                    moved = false
                    startX = fx
                    startY = fy
                    startRect = RectF(layout.getValue(picked))
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = fx - startX
                    val dy = fy - startY
                    if (abs(dx) + abs(dy) < 0.01f && !moved) return true
                    if (!startRect.contains(startX, startY) && !resizing) return true
                    moved = true
                    val r = RectF(startRect)
                    if (resizing) {
                        r.right = snap(startRect.right + dx, 48).coerceIn(r.left + 0.06f, 1f)
                        r.bottom = snap(startRect.bottom + dy, 40).coerceIn(r.top + 0.05f, 1f)
                    } else {
                        r.offsetTo(snap(startRect.left + dx, 48).coerceIn(0f, 1f - r.width()),
                            snap(startRect.top + dy, 40).coerceIn(0f, 1f - r.height()))
                    }
                    layout[picked] = r
                    DashLayout.save(context, layout)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> invalidate()
            }
            return true
        }

        private fun snap(v: Float, steps: Int) = round(v * steps) / steps
    }
}
