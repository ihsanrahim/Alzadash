package com.alzadash.phone

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alzadash.car.Dock
import com.alzadash.core.Prefs

/** Pick which apps appear in the shortcut bar on the car screen (up to six, in the order ticked). */
class DockPickerActivity : AppCompatActivity() {
    private val chosen = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        chosen += Dock.load(this).map { it.pkg }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        col.addView(note("Tick up to ${Dock.MAX} apps. They appear on the car screen in the order you tick them."))
        for (app in Dock.launchable(this)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(ImageView(this).apply { setImageDrawable(Dock.icon(this@DockPickerActivity, app)) }, LinearLayout.LayoutParams(dp(40), dp(40)))
            row.addView(CheckBox(this).apply {
                text = app.label
                textSize = 17f
                setTextColor(Color.WHITE)
                isChecked = app.pkg in chosen
                setOnCheckedChangeListener { box, checked ->
                    if (checked) {
                        if (chosen.size >= Dock.MAX) {
                            box.isChecked = false
                            Toast.makeText(this@DockPickerActivity, "Up to ${Dock.MAX} apps", Toast.LENGTH_SHORT).show()
                            return@setOnCheckedChangeListener
                        }
                        if (app.pkg !in chosen) chosen += app.pkg
                    } else {
                        chosen -= app.pkg
                    }
                    prefs.dockPackages = chosen.toList()
                }
            })
            col.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
