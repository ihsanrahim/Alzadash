package com.alzadash.phone

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alzadash.core.Prefs
import com.alzadash.core.Radio
import com.alzadash.core.Station

/** Manage the Instant Radio stations: tap to play, long-press to delete, button to add. */
class StationsActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        col.addView(buttonRow(
            bigButton("Add station", getColor(com.alzadash.R.color.accent)) { addDialog() },
            bigButton("Stop radio") { Radio.stop(this) },
        ))
        col.addView(note("Paste a stream link (ending in .mp3, .aac, .m3u8 or similar). Find links at radio-browser.info. Tap to play, long-press to delete."))
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        val list = ListView(this).apply { adapter = this@StationsActivity.adapter }
        list.setOnItemClickListener { _, _, pos, _ -> Radio.play(this, prefs.stations[pos]) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val st = prefs.stations[pos]
            AlertDialog.Builder(this)
                .setMessage("Delete ${st.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    prefs.stations = prefs.stations.filterIndexed { i, _ -> i != pos }
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
        col.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(col)
        refresh()
    }

    private fun refresh() {
        adapter.clear()
        adapter.addAll(prefs.stations.map { it.name })
    }

    private fun addDialog() {
        val name = EditText(this).apply { hint = "Name, e.g. My FM" }
        val url = EditText(this).apply { hint = "Stream URL (https://…)" }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(name)
            addView(url)
        }
        AlertDialog.Builder(this)
            .setTitle("Add station")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                val u = url.text.toString().trim()
                if (n.isEmpty() || !(u.startsWith("http://") || u.startsWith("https://"))) {
                    Toast.makeText(this, "Enter a name and a link starting with http", Toast.LENGTH_LONG).show()
                } else {
                    prefs.stations = prefs.stations + Station(n, u)
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
