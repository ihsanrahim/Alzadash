package com.autodash.phone

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.autodash.core.Browser
import com.autodash.core.Chime
import com.autodash.core.CommandTarget
import com.autodash.core.DashLayout
import com.autodash.core.Feature
import com.autodash.core.Features
import com.autodash.core.Look
import com.autodash.core.Pkg
import com.autodash.core.Prefs
import com.autodash.core.Radio
import com.autodash.core.Station
import com.autodash.core.TripTracker
import com.autodash.core.VoiceCommands
import com.autodash.dash.DashboardView
import com.autodash.dash.Dock
import com.autodash.dash.DockApp

/**
 * The dashboard, full screen on the phone itself. Mount the phone in the car and this is the
 * car display: clock, prayer times, weather, speed and trip, radio, now playing, news and the
 * shortcut bar. Shortcut apps open normally on the phone, no extra tools needed.
 */
class DashActivity : AppCompatActivity(), CommandTarget {
    private lateinit var dash: DashboardView
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            TripTracker.start(this@DashActivity) // picks up location allowed after opening
            dash.invalidate()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dash = DashboardView(this).apply {
            onDockTap = { app -> openApp(app) }
            onRadioStep = { d -> Radio.step(this@DashActivity, d) { say(it) } }
            onStationTap = { st -> Radio.play(this@DashActivity, st) { say(it) } }
            onSettingsTap = { startActivity(Intent(this@DashActivity, MainActivity::class.java)) }
            onQuick = { kind -> quick(kind) }
            onYouTube = { kind -> openYouTube(kind) }
            onOpenApp = { pkg -> DashboardView.showLyrics(false); launch(pkg) }
            onOpenLink = { url -> DashboardView.showNews(false); browse(url) }
        }
        setContentView(dash)
        // The layout editor previews the dashboard at this screen's shape.
        dash.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.height > 0) DashLayout.saveCarAspect(this, v.width.toFloat() / v.height)
        }
        TripTracker.start(this)
        if (savedInstanceState == null && Features.on(this, Feature.WELCOME)) Chime.play(Look.tone(this))
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            DashboardView.newsOpen -> DashboardView.showNews(false)
            DashboardView.lyricsOpen -> DashboardView.showLyrics(false)
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun quick(kind: String) {
        when (kind) {
            "mic" -> {
                say("Listening…")
                VoiceCommands.listen(this, this)
            }
            "radio" -> pickStation()
            "nav" -> openMap()
            "web" -> browse(Browser.HOME)
            "apps" -> startActivity(Intent(this, AppsActivity::class.java))
        }
    }

    private fun pickStation() {
        val stations = Prefs(this).orderedStations
        if (stations.isEmpty()) {
            startActivity(Intent(this, StationsActivity::class.java))
            return
        }
        val names = stations.map { it.name } + "Stop radio" + "Edit stations…"
        AlertDialog.Builder(this).setTitle("Radio")
            .setItems(names.toTypedArray()) { _, i ->
                when {
                    i < stations.size -> playRadio(stations[i])
                    i == stations.size -> stopRadio()
                    else -> startActivity(Intent(this, StationsActivity::class.java))
                }
            }.show()
    }

    /** Waze if it's installed, otherwise Google Maps. */
    private fun openMap() {
        val pkg = listOf(Pkg.WAZE, Pkg.MAPS).firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }
        if (pkg == null) say("Install Waze or Google Maps") else launch(pkg)
    }

    private fun openApp(app: DockApp) {
        if (!launch(app.pkg)) say("${app.label} isn't installed")
    }

    private fun launch(pkg: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** The YouTube card: opens the YouTube app (or a patched one) at that page, else the browser. */
    private fun openYouTube(kind: String) {
        fun go(url: String) {
            val pkg = listOf(Pkg.YOUTUBE, "app.revanced.android.youtube", "app.rvx.android.youtube", "app.morphe.android.youtube")
                .firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }
            if (pkg != null) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))
                    return
                } catch (_: Exception) {
                }
            }
            browse(url.replace("://www.", "://m."))
        }
        when (kind) {
            "search" -> VoiceCommands.dictate(this, { words ->
                go("https://www.youtube.com/results?search_query=" + Uri.encode(words))
            }, { say(it) })
            "subs" -> go("https://www.youtube.com/feed/subscriptions")
            "history" -> go("https://www.youtube.com/feed/history")
            else -> go("https://www.youtube.com/")
        }
    }

    override fun playRadio(station: Station) {
        Radio.play(this, station) { say(it) }
        say("Playing ${station.name}")
    }

    override fun stopRadio() {
        Radio.stop(this)
        say("Radio off")
    }

    override fun navigate(query: String, pkg: String?) {
        if (query.isBlank()) {
            if (pkg == null || !launch(pkg)) say("That app isn't installed")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(query)))
        if (pkg == Pkg.WAZE) {
            intent.data = Uri.parse("https://waze.com/ul?q=" + Uri.encode(query) + "&navigate=yes")
        }
        if (pkg != null) intent.setPackage(pkg)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))
            } catch (_: Exception) {
                say("Install Google Maps or Waze")
            }
        }
    }

    override fun browse(url: String) {
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
    }

    override fun showVideo(on: Boolean) {
        if (!on) return
        Dock.load(this).firstOrNull()?.let { openApp(it) } ?: say("Pick shortcut apps in Autodash settings")
    }

    override fun resetTrip() {
        TripTracker.reset()
        say("Trip reset")
    }

    override fun say(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }
}
