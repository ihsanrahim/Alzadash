package com.alzadash.car

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A keyboard drawn on the car screen for the browser: letters, numbers and symbols, shift,
 * backspace, space, Go and hide. Each key press is passed to [onKey] as the text to type or one
 * of the special keys below.
 */
class CarKeyboard(context: Context, private val onKey: (String) -> Unit) : LinearLayout(context) {
    private var shift = false
    private var symbols = false

    init {
        orientation = VERTICAL
        setBackgroundColor(0xF2121820.toInt())
        val p = dp(6)
        setPadding(p, p, p, p)
        isClickable = true
        build()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun build() {
        removeAllViews()
        val rows = if (symbols) SYMBOLS else LETTERS
        rows.forEachIndexed { i, keys ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            if (i == 2) row.addView(key(if (symbols) "=\\<" else "⇧", 1.5f, shift) {
                if (symbols) return@key
                shift = !shift
                build()
            })
            for (k in keys) {
                val text = if (shift && !symbols) k.uppercase() else k
                row.addView(key(text, 1f) {
                    onKey(text)
                    if (shift) {
                        shift = false
                        build()
                    }
                })
            }
            if (i == 2) row.addView(key("⌫", 1.5f) { onKey(BACKSPACE) })
            addView(row, LayoutParams(MATCH_PARENT, dp(46)))
        }
        val last = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        last.addView(key(if (symbols) "ABC" else "?123", 1.5f) { symbols = !symbols; shift = false; build() })
        last.addView(key(",", 1f) { onKey(",") })
        last.addView(key("space", 5f) { onKey(" ") })
        last.addView(key(".", 1f) { onKey(".") })
        last.addView(key("Go", 1.5f, true) { onKey(ENTER) })
        last.addView(key("⌄", 1.2f) { onKey(HIDE) })
        addView(last, LayoutParams(MATCH_PARENT, dp(46)))
    }

    private fun key(text: String, weight: Float, lit: Boolean = false, click: () -> Unit) = TextView(context).apply {
        this.text = text
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(if (lit) Color.parseColor("#0E1116") else Color.WHITE)
        background = GradientDrawable().apply {
            setColor(if (lit) Color.parseColor("#4FD1C5") else Color.parseColor("#2A313C"))
            cornerRadius = dp(8).toFloat()
        }
        setOnClickListener { click() }
        layoutParams = LayoutParams(0, MATCH_PARENT, weight).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    }

    companion object {
        const val BACKSPACE = "\u0008"
        const val ENTER = "\n"
        const val HIDE = "\u001B"
        private val LETTERS = listOf(
            "qwertyuiop".map { it.toString() },
            "asdfghjkl".map { it.toString() },
            "zxcvbnm".map { it.toString() },
        )
        private val SYMBOLS = listOf(
            "1234567890".map { it.toString() },
            listOf("@", "#", "&", "-", "_", "/", ":", "?", "!"),
            listOf("'", "\"", "(", ")", "+", "%", "*"),
        )
    }
}
