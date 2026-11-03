package com.autodash.car

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A full keyboard drawn on the car screen (Android Auto's own keyboard only works inside its
 * list screens, not over a web page). Letters, a symbols page, shift, space, ".com", delete and Go.
 * Only reachable while parked, since the whole browser is covered while driving.
 */
class CarKeyboard(context: Context) : LinearLayout(context) {
    var onKey: (String) -> Unit = {}
    var onDelete: () -> Unit = {}
    var onGo: () -> Unit = {}
    var onClose: () -> Unit = {}

    private var shift = false
    private var symbols = false

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#101524"))
        val pad = (6 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        build()
    }

    private fun build() {
        removeAllViews()
        val rows = if (symbols) SYMBOLS else LETTERS
        rows.forEachIndexed { i, keys ->
            val row = row()
            if (i == 2) row.addView(key(if (symbols) "abc" else "⇧", 1.4f, accent = shift && !symbols) {
                if (symbols) { symbols = false } else shift = !shift
                build()
            })
            keys.forEach { k ->
                val label = if (shift && !symbols) k.uppercase() else k
                row.addView(key(label) {
                    onKey(label)
                    if (shift) { shift = false; build() }
                })
            }
            if (i == 2) row.addView(key("⌫", 1.4f) { onDelete() })
            addView(row, LayoutParams(MATCH_PARENT, 0, 1f))
        }
        val last = row()
        last.addView(key(if (symbols) "abc" else "?123", 1.3f) { symbols = !symbols; shift = false; build() })
        last.addView(key("/", 0.8f) { onKey("/") })
        last.addView(key("space", 3.6f) { onKey(" ") })
        last.addView(key(".", 0.8f) { onKey(".") })
        last.addView(key(".com", 1.2f) { onKey(".com") })
        last.addView(key("✕", 1f) { onClose() })
        last.addView(key("Go", 1.5f, accent = true) { onGo() })
        addView(last, LayoutParams(MATCH_PARENT, 0, 1f))
    }

    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL }

    private fun key(label: String, weight: Float = 1f, accent: Boolean = false, onTap: () -> Unit) =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(if (accent) Color.parseColor("#0B0F1A") else Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = if (label.length > 1) 15f else 20f
            background = GradientDrawable().apply {
                cornerRadius = 10 * resources.displayMetrics.density
                setColor(Color.parseColor(if (accent) "#4FD1C5" else "#1F2840"))
            }
            setOnClickListener { onTap() }
            val m = (3 * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(0, MATCH_PARENT, weight).apply { setMargins(m, m, m, m) }
        }

    companion object {
        private val LETTERS = listOf(
            "qwertyuiop".map { it.toString() },
            "asdfghjkl".map { it.toString() },
            "zxcvbnm".map { it.toString() },
        )
        private val SYMBOLS = listOf(
            "1234567890".map { it.toString() },
            listOf("@", "#", "&", "_", "-", "+", "(", ")", ":", "="),
            listOf(",", "?", "!", "'", "\"", ";", "%"),
        )
    }
}
