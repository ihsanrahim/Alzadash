package com.alzadash.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.alzadash.core.DashLayout
import com.alzadash.core.Features
import com.alzadash.core.Widget
import kotlin.math.abs
import kotlin.math.round

/**
 * Move and resize the dashboard's widgets: drag a box to move it, drag its corner to resize.
 * Changes show on the car screen straight away.
 */
class LayoutEditorActivity : AppCompatActivity() {
    private lateinit var editor: EditorView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor = EditorView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        col.addView(heading("DASHBOARD LAYOUT"))
        col.addView(note("Drag a box to move it. Drag the corner dot to resize. Turn widgets on or off in Settings."))
        col.addView(editor, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        col.addView(buttonRow(
            smallButton("Reset") {
                DashLayout.reset(this)
                editor.reload()
            },
            smallButton("Done", getColor(com.alzadash.R.color.accent)) { finish() },
        ).apply { gravity = Gravity.CENTER })
        setContentView(col)
    }

    private class EditorView(context: Context) : View(context) {
        private val layout = mutableMapOf<Widget, RectF>()
        private val screen = Paint().apply { color = Color.parseColor("#0E1116") }
        private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A4250"); style = Paint.Style.STROKE; strokeWidth = 3f }
        private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1F27") }
        private val active = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FD1C5"); style = Paint.Style.STROKE; strokeWidth = 5f }
        private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FD1C5") }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val area = RectF()
        private var picked: Widget? = null
        private var resizing = false
        private var startX = 0f
        private var startY = 0f
        private var startRect = RectF()

        init {
            reload()
        }

        fun reload() {
            layout.clear()
            layout.putAll(DashLayout.get(context).mapValues { RectF(it.value) })
            invalidate()
        }

        private fun shown(w: Widget) = w.feature == null || Features.on(context, w.feature)

        override fun onDraw(canvas: Canvas) {
            // The car screen's shape, as wide as fits.
            val aspect = DashLayout.carAspect(context)
            val w = minOf(width.toFloat(), height * aspect)
            val h = w / aspect
            area.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawRect(area, screen)
            canvas.drawRect(area, frame)
            label.textSize = w / 38
            for (wd in Widget.values()) {
                if (!shown(wd)) continue
                val r = DashLayout.place(layout.getValue(wd), area)
                canvas.drawRoundRect(r, 10f, 10f, box)
                if (wd == picked) canvas.drawRoundRect(r, 10f, 10f, active)
                canvas.save()
                canvas.clipRect(r)
                canvas.drawText(wd.label, r.left + 10, r.top + label.textSize + 6, label)
                canvas.restore()
                canvas.drawCircle(r.right, r.bottom, w / 70, handle)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val fx = (e.x - area.left) / area.width()
            val fy = (e.y - area.top) / area.height()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val grab = 0.05f
                    // Corner handles first, then the smallest box under the finger.
                    picked = Widget.values().filter { shown(it) }.firstOrNull {
                        val r = layout.getValue(it)
                        abs(fx - r.right) < grab && abs(fy - r.bottom) < grab * area.width() / area.height()
                    }
                    resizing = picked != null
                    if (picked == null) {
                        picked = Widget.values().filter { shown(it) && layout.getValue(it).contains(fx, fy) }
                            .minByOrNull { layout.getValue(it).width() * layout.getValue(it).height() }
                    }
                    startX = fx
                    startY = fy
                    picked?.let { startRect = RectF(layout.getValue(it)) }
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> picked?.let { w ->
                    val dx = fx - startX
                    val dy = fy - startY
                    val r = RectF(startRect)
                    if (resizing) {
                        r.right = snap(startRect.right + dx, 48).coerceIn(r.left + 0.06f, 1f)
                        r.bottom = snap(startRect.bottom + dy, 40).coerceIn(r.top + 0.05f, 1f)
                    } else {
                        val nl = snap(startRect.left + dx, 48).coerceIn(0f, 1f - r.width())
                        val nt = snap(startRect.top + dy, 40).coerceIn(0f, 1f - r.height())
                        r.offsetTo(nl, nt)
                    }
                    layout[w] = r
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
