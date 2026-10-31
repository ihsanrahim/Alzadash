package com.alzadash.phone

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Tiny helpers for big, glove-friendly buttons. */
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.bigButton(label: String, color: Int = Color.parseColor("#1A1F27"), onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 18f
        setTextColor(Color.WHITE)
        minHeight = dp(72)
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(color)
        }
        setOnClickListener { onClick() }
    }

fun Context.heading(label: String): TextView = TextView(this).apply {
    text = label
    textSize = 14f
    setTextColor(Color.parseColor("#4FD1C5"))
    setPadding(dp(4), dp(20), dp(4), dp(8))
}

fun Context.note(label: String): TextView = TextView(this).apply {
    text = label
    textSize = 14f
    setTextColor(Color.parseColor("#8A94A6"))
    setPadding(dp(4), dp(4), dp(4), dp(4))
}

/** A row of buttons sharing the width equally. */
fun Context.buttonRow(vararg buttons: Button): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER
    buttons.forEach {
        addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
    }
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
}

/** A compact button for tight rows. */
fun Context.smallButton(label: String, color: Int = Color.parseColor("#1A1F27"), onClick: () -> Unit): Button =
    bigButton(label, color, onClick).apply {
        textSize = 14f
        minHeight = dp(46)
        minimumHeight = dp(46)
        setPadding(dp(4), 0, dp(4), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(color)
        }
    }
