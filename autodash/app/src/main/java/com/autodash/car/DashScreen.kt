package com.autodash.car

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import com.autodash.R
import com.autodash.core.Browser
import com.autodash.core.CommandTarget
import com.autodash.core.Radio
import com.autodash.core.Station
import com.autodash.core.TripTracker
import com.autodash.core.VoiceCommands
import com.autodash.dash.DashboardView
import com.autodash.dash.DockApp

/** The main car screen. The surface shows the dashboard or the browser; Android Auto's buttons sit on top. */
class DashScreen(carContext: CarContext, private val surface: CarSurface) : Screen(carContext), CommandTarget {

    init {
        surface.onDock = { app -> openDock(app) }
        surface.onRadioStep = { delta -> Radio.step(carContext, delta) { say(it) } }
        surface.onStation = { st -> Radio.play(carContext, st) { say(it) } }
        surface.onSettings = { screenManager.push(SettingsScreen(carContext)) }
        surface.onYouTube = { kind -> openYouTube(kind) }
        surface.onChanged = { invalidate() }
        surface.onQuick = { kind ->
            when (kind) {
                "mic" -> VoiceCommands.listen(carContext, this)
                "radio" -> screenManager.push(RadioScreen(carContext))
                "nav" -> screenManager.push(GoScreen(carContext, this))
                "web" -> openBrowser()
                "apps", "sites" -> screenManager.push(BrowserGoScreen(carContext, surface))
            }
        }
        surface.onOpenApp = { pkg ->
            DashboardView.showLyrics(false)
            val pm = carContext.packageManager
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            openDock(DockApp(label, pkg))
        }
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
        when (surface.mode) {
            // Android Auto insists on at least one button up here; the rest are drawn on our own screen.
            Mode.DASH -> strip.addAction(icon(R.drawable.ic_mic) { VoiceCommands.listen(carContext, this) })
            Mode.WEB -> strip.addAction(icon(R.drawable.ic_menu) { screenManager.push(BrowserMenuScreen(carContext, surface)) })
        }
        return NavigationTemplate.Builder().setActionStrip(strip.build()).build()
    }

    private fun icon(res: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener { onClick() }
            .build()

    /** The built-in browser, opening on its start page of bookmarks. */
    fun openBrowser() {
        surface.openUrl(Browser.HOME)
        if (!TripTracker.isParked()) say("The browser shows once you're parked")
    }

    /**
     * A shortcut in the right-hand bar was tapped. Phone apps can't be drawn on the car screen
     * without Shizuku, so YouTube opens in the car browser and other apps are pointed to their
     * own Android Auto icon.
     */
    fun openDock(app: DockApp) {
        val name = app.label.lowercase()
        when {
            carContext.packageManager.getLaunchIntentForPackage(app.pkg) == null -> say("${app.label} isn't installed")
            name.startsWith("youtube") && !name.contains("music") -> openYouTube("home")
            else -> say("Open ${app.label} from the Android Auto app bar")
        }
    }

    /** The YouTube card: search by voice, or jump to Home, Subscriptions or History, in the car browser. */
    private fun openYouTube(kind: String) {
        fun go(url: String) {
            surface.openUrl(url)
            if (!TripTracker.isParked()) say("YouTube shows once you're parked. Sound keeps playing.")
        }
        when (kind) {
            "search" -> VoiceCommands.dictate(carContext, { words ->
                go("https://m.youtube.com/results?search_query=" + Uri.encode(words))
            }, { say(it) })
            "subs" -> go("https://m.youtube.com/feed/subscriptions")
            "history" -> go("https://m.youtube.com/feed/history")
            else -> go(DashPresentation.YOUTUBE)
        }
    }

    override fun playRadio(station: Station) {
        Radio.play(carContext, station) { say(it) }
        say("Playing ${station.name}")
    }

    override fun stopRadio() {
        Radio.stop(carContext)
        say("Radio off")
    }

    /** Hands navigation to Google Maps or Waze. */
    override fun navigate(query: String, pkg: String?) {
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
        val intent = Intent(CarContext.ACTION_NAVIGATE, uri)
        if (pkg != null) intent.setPackage(pkg)
        try {
            carContext.startCarApp(intent)
        } catch (e: Exception) {
            try {
                carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
            } catch (e2: Exception) {
                say("Couldn't open navigation. Use the Android Auto app bar.")
            }
        }
    }

    override fun showVideo(on: Boolean) {
        if (on) {
            openYouTube("home")
        } else {
            surface.setMode(Mode.DASH)
        }
    }

    override fun browse(url: String) {
        surface.openUrl(url)
    }

    override fun resetTrip() {
        TripTracker.reset()
        say("Trip reset")
    }

    override fun say(message: String) {
        CarToast.makeText(carContext, message, CarToast.LENGTH_LONG).show()
    }
}
