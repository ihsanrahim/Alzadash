package com.alzadash.car

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
import com.alzadash.R
import com.alzadash.core.CommandTarget
import com.alzadash.core.Pkg
import com.alzadash.core.Radio
import com.alzadash.core.Shell
import com.alzadash.core.Station
import com.alzadash.core.TripTracker
import com.alzadash.core.VoiceCommands

/** The main car screen. The surface shows the dashboard, a web page or a real app; the buttons sit on top. */
class DashScreen(carContext: CarContext, private val surface: CarSurface) : Screen(carContext), CommandTarget {

    init {
        surface.onDock = { app -> openDock(app) }
        surface.onRadioStep = { delta -> Radio.step(carContext, delta) { say(it) } }
        surface.onStation = { st -> Radio.play(carContext, st) { say(it) } }
        surface.onSettings = { screenManager.push(SettingsScreen(carContext)) }
        surface.onLinkOpened = { invalidate() }
        surface.onOpenApp = { pkg ->
            DashboardView.showLyrics(false)
            val pm = carContext.packageManager
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            openDock(DockApp(label, pkg))
        }
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
        // Android Auto allows only one button with a text label here; the rest are icons.
        when (surface.mode) {
            Mode.DASH -> {
                strip.addAction(icon(R.drawable.ic_mic) { VoiceCommands.listen(carContext, this) })
                strip.addAction(icon(R.drawable.ic_radio) { screenManager.push(RadioScreen(carContext)) })
                strip.addAction(icon(R.drawable.ic_nav) { openMap() })
                strip.addAction(icon(R.drawable.ic_globe) { openBrowser() })
            }
            Mode.WEB -> {
                strip.addAction(Action.Builder().setTitle("Go").setOnClickListener {
                    screenManager.push(BrowserGoScreen(carContext, surface))
                }.build())
                strip.addAction(icon(R.drawable.ic_back) { surface.goBack() })
                strip.addAction(icon(R.drawable.ic_menu) { screenManager.push(BrowserMenuScreen(carContext, surface)) })
                strip.addAction(icon(R.drawable.ic_dash) { showVideo(false) })
            }
            Mode.SPLIT -> {
                strip.addAction(icon(R.drawable.ic_dash) { showVideo(false) })
                strip.addAction(icon(R.drawable.ic_mic) { VoiceCommands.listen(carContext, this) })
                strip.addAction(icon(R.drawable.ic_keyboard) { dictate() })
                strip.addAction(icon(R.drawable.ic_radio) { screenManager.push(RadioScreen(carContext)) })
            }
            Mode.APP -> {
                strip.addAction(icon(R.drawable.ic_dash) { showVideo(false) })
                strip.addAction(icon(R.drawable.ic_back) { surface.goBack() })
                strip.addAction(icon(R.drawable.ic_playpause) { surface.playPause() })
                strip.addAction(icon(R.drawable.ic_keyboard) { dictate() })
            }
        }
        return NavigationTemplate.Builder().setActionStrip(strip.build()).build()
    }

    private fun icon(res: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener { onClick() }
            .build()

    /**
     * Waze on the left with the dashboard beside it. Without Shizuku this falls back to the
     * old destination list, which hands over to Waze or Maps full screen.
     */
    private fun openMap() {
        when {
            !Shell.available() -> screenManager.push(GoScreen(carContext, this))
            !surface.openSplit() -> say("Install Waze or Google Maps on your phone")
            else -> invalidate()
        }
    }

    /** The built-in browser, opening on its start page of bookmarks. */
    fun openBrowser() {
        surface.openUrl(com.alzadash.core.Browser.HOME)
        invalidate()
    }

    /** A shortcut in the right-hand bar was tapped: open that app on the car screen. */
    fun openDock(app: DockApp) {
        val launch = carContext.packageManager.getLaunchIntentForPackage(app.pkg)?.component
        when {
            launch == null -> say("${app.label} isn't installed")
            Shell.available() -> {
                surface.openApp(launch.flattenToString())
                if (!TripTracker.isParked()) say("${app.label} shows once you're parked. Sound keeps playing.")
            }
            app.label.lowercase().startsWith("youtube") && !app.label.lowercase().contains("music") -> {
                surface.openUrl(DashPresentation.YOUTUBE)
                say("Start Shizuku on your phone to open the real ${app.label} app")
            }
            !Shell.running() -> say("Start Shizuku on your phone to open ${app.label} here")
            else -> say("Tap Allow Shizuku in the AlzaDash phone app to open ${app.label} here")
        }
        invalidate()
    }

    /** Voice typing into the app on the car screen. The phone's own keyboard also works. */
    private fun dictate() {
        VoiceCommands.dictate(carContext, { words ->
            surface.typeText(words)
            say("Typed: $words")
        }, { say(it) })
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
            Dock.load(carContext).firstOrNull()?.let { openDock(it) } ?: say("Pick shortcut apps in the AlzaDash phone app")
        } else {
            surface.setMode(Mode.DASH)
            invalidate()
        }
    }

    override fun browse(url: String) {
        surface.openUrl(url)
        invalidate()
    }

    override fun resetTrip() {
        TripTracker.reset()
        say("Trip reset")
    }

    override fun say(message: String) {
        CarToast.makeText(carContext, message, CarToast.LENGTH_LONG).show()
    }
}
