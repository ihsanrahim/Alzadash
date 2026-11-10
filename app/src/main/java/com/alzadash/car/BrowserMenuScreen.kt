package com.alzadash.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import com.alzadash.core.Browser
import com.alzadash.core.VoiceCommands

/** The browser's menu: forward, reload, bookmarks, voice search and settings. */
class BrowserMenuScreen(
    carContext: CarContext,
    private val surface: CarSurface,
) : Screen(carContext) {
    private fun row(title: String, action: () -> Unit) =
        Row.Builder().setTitle(title).setOnClickListener {
            action()
            screenManager.pop()
        }.build()

    private fun next(list: List<String>, cur: String) = list[(list.indexOf(cur) + 1) % list.size]

    private fun say(s: String) = CarToast.makeText(carContext, s, CarToast.LENGTH_SHORT).show()

    override fun onGetTemplate(): Template {
        val b = surface.browser
        val list = ItemList.Builder()
        list.addItem(row("Voice search") {
            VoiceCommands.dictate(carContext, { words -> surface.openUrl(Browser.toUrl(words)) }, { say(it) })
        })
        list.addItem(row("Forward") { b?.goForward() })
        list.addItem(Row.Builder().setTitle("Page theme").addText(Browser.theme(carContext)).setOnClickListener {
            Browser.setTheme(carContext, next(Browser.THEMES, Browser.theme(carContext)))
            b?.applySettings()
            invalidate()
        }.build())
        list.addItem(Row.Builder().setTitle("Page zoom").addText("${Browser.zoom(carContext)}% · tap to reset").setOnClickListener {
            Browser.setZoom(carContext, 100)
            b?.applySettings()
            invalidate()
        }.build())
        list.addItem(Row.Builder().setTitle("Desktop site").setToggle(Toggle.Builder { on ->
            Browser.setDesktop(carContext, on)
            b?.applySettings()
        }.setChecked(Browser.desktop(carContext)).build()).build())
        list.addItem(Row.Builder().setTitle("Block ads").setToggle(Toggle.Builder { on ->
            Browser.setAdBlock(carContext, on)
            b?.reload()
        }.setChecked(Browser.adBlock(carContext)).build()).build())
        list.addItem(Row.Builder().setTitle("Navigation bar").addText("Colour, see-through, size, position, buttons")
            .setBrowsable(true)
            .setOnClickListener { screenManager.push(BrowserBarScreen(carContext, surface)) }.build())
        list.addItem(row("Clear history and cookies") {
            b?.clearData()
            say("Cleared")
        })
        return ListTemplate.Builder()
            .setTitle("Browser")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
