package com.alzadash.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import com.alzadash.core.Browser

/**
 * Type a web address or a search with the car's keyboard (Android Auto offers it while
 * parked), or pick a bookmark.
 */
class BrowserGoScreen(carContext: CarContext, private val surface: CarSurface) : Screen(carContext) {
    private var typed = ""

    private fun go(input: String) {
        surface.openUrl(Browser.toUrl(input))
        screenManager.pop()
    }

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        if (typed.isNotBlank()) {
            val url = Browser.toUrl(typed)
            val title = if (url.contains("google.com/search")) "Search Google for \"$typed\"" else "Go to $url"
            list.addItem(Row.Builder().setTitle(title).setOnClickListener { go(typed) }.build())
        }
        list.addItem(Row.Builder().setTitle("Start page").setOnClickListener { go("") }.build())
        Browser.bookmarks(carContext)
            .filter { typed.isBlank() || it.title.contains(typed, true) || it.url.contains(typed, true) }
            .forEach { b ->
                list.addItem(Row.Builder().setTitle(b.title).addText(b.url).setOnClickListener { go(b.url) }.build())
            }
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                typed = searchText
                invalidate()
            }

            override fun onSearchSubmitted(searchTerm: String) = go(searchTerm)
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Search or type a web address")
            .setShowKeyboardByDefault(false)
            .setItemList(list.build())
            .build()
    }
}
