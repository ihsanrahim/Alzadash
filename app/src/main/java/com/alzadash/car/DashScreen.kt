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

/** The main car screen. The surface shows the dashboard or video; the buttons sit on the side. */
class DashScreen(carContext: CarContext, private val surface: CarSurface) : Screen(carContext), CommandTarget {

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
        // Android Auto allows only one button with a text label here; the rest are icons.
        if (surface.mode == Mode.DASH) {
            strip.addAction(icon(R.drawable.ic_mic) { VoiceCommands.listen(carContext, this) })
            strip.addAction(icon(R.drawable.ic_radio) { screenManager.push(RadioScreen(carContext)) })
            strip.addAction(icon(R.drawable.ic_nav) { screenManager.push(GoScreen(carContext, this)) })
            strip.addAction(icon(R.drawable.ic_video) { showVideo(true) })
        } else {
            strip.addAction(Action.Builder().setTitle("Sites").setOnClickListener {
                screenManager.push(SitesScreen(carContext, surface))
            }.build())
            strip.addAction(icon(R.drawable.ic_back) { surface.goBack() })
            strip.addAction(icon(R.drawable.ic_dash) { showVideo(false) })
        }
        return NavigationTemplate.Builder().setActionStrip(strip.build()).build()
    }

    private fun icon(res: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener(onClick)
            .build()

    override fun playRadio(station: Station) {
        Radio.play(carContext, station)
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
        surface.setMode(if (on) Mode.VIDEO else Mode.DASH)
        if (on && !TripTracker.isParked()) say("Video plays only while parked")
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
