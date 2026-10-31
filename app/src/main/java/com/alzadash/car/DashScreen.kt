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
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
        // Android Auto allows only one button with a text label here; the rest are icons.
        when (surface.mode) {
            Mode.DASH -> {
                strip.addAction(icon(R.drawable.ic_mic) { VoiceCommands.listen(carContext, this) })
                strip.addAction(icon(R.drawable.ic_radio) { screenManager.push(RadioScreen(carContext)) })
                strip.addAction(icon(R.drawable.ic_nav) { screenManager.push(GoScreen(carContext, this)) })
                strip.addAction(icon(R.drawable.ic_video) { screenManager.push(SitesScreen(carContext, surface) { invalidate() }) })
            }
            Mode.WEB -> {
                strip.addAction(Action.Builder().setTitle("Sites").setOnClickListener {
                    screenManager.push(SitesScreen(carContext, surface) { invalidate() })
                }.build())
                strip.addAction(icon(R.drawable.ic_back) { surface.goBack() })
                strip.addAction(icon(R.drawable.ic_dash) { showVideo(false) })
            }
            Mode.APP -> {
                strip.addAction(icon(R.drawable.ic_dash) { showVideo(false) })
                strip.addAction(icon(R.drawable.ic_back) { surface.goBack() })
                strip.addAction(icon(R.drawable.ic_playpause) { surface.playPause() })
                strip.addAction(icon(R.drawable.ic_radio) { screenManager.push(RadioScreen(carContext)) })
            }
        }
        return NavigationTemplate.Builder().setActionStrip(strip.build()).build()
    }

    private fun icon(res: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener { onClick() }
            .build()

    /** A shortcut in the right-hand bar was tapped. */
    fun openDock(app: DockApp) {
        val pkg = Dock.installed(carContext, app)
        if (!app.isVideo) {
            navigate("", pkg ?: app.packages.first())
            return
        }
        val launch = pkg?.let { carContext.packageManager.getLaunchIntentForPackage(it)?.component }
        if (launch != null && Shell.available()) {
            surface.openApp(launch.flattenToString())
            if (app.label != "YouTube") say("${app.label} may show a black picture here because of its copy protection. Sound still works.")
            else if (!TripTracker.isParked()) say("Video plays only while parked")
        } else {
            app.webUrl?.let { surface.openUrl(it) }
            when {
                launch == null -> say("${app.label} isn't installed, opening the website")
                !Shell.running() -> say("Start Shizuku on your phone to open the real ${app.label} app")
                else -> say("Allow Shizuku in the AlzaDash phone app to open the real ${app.label} app")
            }
        }
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
            openDock(Dock.apps.first())
        } else {
            surface.setMode(Mode.DASH)
            invalidate()
        }
    }

    override fun resetTrip() {
        TripTracker.reset()
        say("Trip reset")
    }

    override fun say(message: String) {
        CarToast.makeText(carContext, message, CarToast.LENGTH_LONG).show()
    }
}
