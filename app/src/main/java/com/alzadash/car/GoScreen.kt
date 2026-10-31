package com.alzadash.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.alzadash.core.Pkg
import com.alzadash.core.Prefs

/** Shortcuts: Maps, Waze, home, Gemini and trip reset. */
class GoScreen(carContext: CarContext, private val dash: DashScreen) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val home = Prefs(carContext).homeAddress
        val list = ItemList.Builder()
        if (home.isNotEmpty()) list.addItem(row("Drive home", home) { dash.navigate(home, null) })
        list.addItem(row("Google Maps", null) { dash.navigate("", Pkg.MAPS) })
        list.addItem(row("Waze", null) { dash.navigate("", Pkg.WAZE) })
        list.addItem(row("Gemini", "Say \"Hey Google\" or hold the steering voice button") {
            dash.say("Say \"Hey Google\" or press and hold the voice button on the steering wheel to talk to Gemini")
        })
        list.addItem(row("Reset trip", null) { dash.resetTrip() })
        return ListTemplate.Builder()
            .setTitle("Go")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }

    private fun row(title: String, sub: String?, onClick: () -> Unit): Row {
        val r = Row.Builder().setTitle(title).setOnClickListener {
            screenManager.pop()
            onClick()
        }
        if (sub != null) r.addText(sub)
        return r.build()
    }
}
