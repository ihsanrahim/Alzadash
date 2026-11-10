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
import com.alzadash.core.Radio
import com.alzadash.core.Station
import com.alzadash.core.TripTracker
import com.alzadash.core.VoiceCommands

/** The main car screen. The surface shows the dashboard, a web page or a real app; the buttons sit on top. */
class DashScreen(carContext: CarContext, private val surface: CarSurface) : Screen(carContext), CommandTarget {

    init {
        surface.onRadioStep = { delta -> Radio.step(carContext, delta) { say(it) } }
        surface.onStation = { st -> Radio.play(carContext, st) { say(it) } }
        surface.onSettings = { screenManager.push(SettingsScreen(carContext)) }
        surface.onMessage = { say(it) }
        surface.onQuick = { kind ->
            when (kind) {
                "mic" -> VoiceCommands.listen(carContext, this)
                "radio" -> screenManager.push(RadioScreen(carContext))
                "nav" -> openMap()
                "web" -> openBrowser()
                "mirror" -> {
                    surface.openMirror()
                    invalidate()
                }
            }
        }
        surface.onLinkOpened = { invalidate() }
        surface.onBrowserChanged = { invalidate() }
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
        // Android Auto allows only one button with a text label here; the rest are icons.
        when (surface.mode) {
            Mode.DASH -> {
                // Android Auto insists on at least one button up here; the rest are drawn
                // small at the bottom right of the dashboard.
                strip.addAction(icon(R.drawable.ic_mic) { VoiceCommands.listen(carContext, this) })
            }
            Mode.WEB -> {
                strip.addAction(Action.Builder().setTitle("Go").setOnClickListener {
                    screenManager.push(BrowserGoScreen(carContext, surface))
                }.build())
                strip.addAction(icon(R.drawable.ic_back) { surface.goBack() })
                strip.addAction(icon(R.drawable.ic_menu) { screenManager.push(BrowserMenuScreen(carContext, surface)) })
                strip.addAction(icon(R.drawable.ic_dash) { showVideo(false) })
            }
            Mode.MIRROR -> {
                strip.addAction(icon(R.drawable.ic_dash) { showVideo(false) })
                strip.addAction(icon(R.drawable.ic_back) { surface.goBack() })
                strip.addAction(icon(R.drawable.ic_home) { surface.mirrorHome() })
                strip.addAction(icon(R.drawable.ic_mic) { VoiceCommands.listen(carContext, this) })
            }
        }
        return NavigationTemplate.Builder().setActionStrip(strip.build()).build()
    }

    private fun icon(res: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener { onClick() }
            .build()

    /** Drive home, Google Maps, Waze, Gemini and trip reset. */
    private fun openMap() = screenManager.push(GoScreen(carContext, this))

    /** The built-in browser, opening on its start page of shortcuts. */
    fun openBrowser() {
        surface.openUrl(com.alzadash.core.Browser.HOME)
        invalidate()
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
            openBrowser()
            if (!TripTracker.isParked()) say("The browser shows once you're parked. Sound keeps playing.")
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
