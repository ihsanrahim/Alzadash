package com.autodash.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import com.autodash.core.Bookmark
import com.autodash.core.Browser
import com.autodash.core.VoiceCommands

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

    private fun say(s: String) = CarToast.makeText(carContext, s, CarToast.LENGTH_SHORT).show()

    override fun onGetTemplate(): Template {
        val b = surface.browser
        val list = ItemList.Builder()
        list.addItem(row("Voice search") {
            VoiceCommands.dictate(carContext, { words -> surface.openUrl(Browser.toUrl(words)) }, { say(it) })
        })
        list.addItem(row("Type a web address") { surface.showAddressKeyboard() })
        list.addItem(row("Reload") { b?.reload() })
        list.addItem(row("Start page") { surface.openUrl(Browser.HOME) })
        list.addItem(row("Bookmark this page") {
            val page = b?.page
            if (page == null) say("Open a website first") else {
                Browser.addBookmark(carContext, Bookmark(page.first.take(40), page.second))
                say("Bookmarked")
            }
        })
        val zoom = Browser.textZoom(carContext)
        list.addItem(Row.Builder().setTitle("Text size: $zoom%").addText("Tap to change").setOnClickListener {
            val sizes = listOf(80, 100, 125, 150, 175)
            Browser.setTextZoom(carContext, sizes[(sizes.indexOf(zoom) + 1) % sizes.size])
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
