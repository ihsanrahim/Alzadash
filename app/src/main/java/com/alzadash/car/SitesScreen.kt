package com.alzadash.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/** Picks what the video browser opens. */
class SitesScreen(
    carContext: CarContext,
    private val surface: CarSurface,
    private val after: () -> Unit = {},
) : Screen(carContext) {
    private val sites = listOf(
        "YouTube" to DashPresentation.YOUTUBE,
        "Netflix" to DashPresentation.NETFLIX,
        "Disney+ Hotstar" to "https://www.hotstar.com/my",
        "Google" to "https://www.google.com",
    )

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        sites.forEach { (name, url) ->
            val row = Row.Builder().setTitle(name).setOnClickListener {
                surface.openUrl(url)
                screenManager.pop()
                after()
            }
            if (name != "YouTube" && name != "Google") row.addText("May not play in the car browser")
            list.addItem(row.build())
        }
        return ListTemplate.Builder()
            .setTitle("Websites (video plays when parked)")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
