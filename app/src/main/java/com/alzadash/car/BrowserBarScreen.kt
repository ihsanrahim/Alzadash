package com.alzadash.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import com.alzadash.core.Browser

/** The browser's floating bar: colour, how see-through it is, size, position and which buttons it shows. */
class BrowserBarScreen(carContext: CarContext, private val surface: CarSurface) : Screen(carContext) {
    private fun changed() {
        surface.browser?.applyBar()
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val c = carContext
        val list = ItemList.Builder()
        val colours = Browser.BAR_COLOURS.map { it.first }
        list.addItem(Row.Builder().setTitle("Colour").addText(Browser.barColour(c)).setOnClickListener {
            Browser.setBarColour(c, colours[(colours.indexOf(Browser.barColour(c)) + 1) % colours.size])
            changed()
        }.build())
        list.addItem(Row.Builder().setTitle("Opacity").addText("${Browser.barOpacity(c)}%").setOnClickListener {
            val o = Browser.barOpacity(c)
            Browser.setBarOpacity(c, if (o >= 100) 40 else o + 15)
            changed()
        }.build())
        list.addItem(Row.Builder().setTitle("Size").addText("${Browser.barSize(c)}%").setOnClickListener {
            val z = Browser.barSize(c)
            Browser.setBarSize(c, if (z >= 150) 75 else z + 25)
            changed()
        }.build())
        list.addItem(Row.Builder().setTitle("Position").addText("Bottom centre · or use the Move button on the bar").setOnClickListener {
            Browser.setBarPos(c, 0.5f, 1f)
            changed()
        }.build())
        val shown = Browser.barButtons(c)
        for ((key, name) in Browser.BAR_BUTTONS) {
            list.addItem(Row.Builder().setTitle(name).setToggle(Toggle.Builder { on ->
                Browser.setBarButton(c, key, on)
                surface.browser?.applyBar()
            }.setChecked(key in shown).build()).build())
        }
        return ListTemplate.Builder()
            .setTitle("Navigation bar")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
