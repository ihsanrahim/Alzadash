package com.alzadash.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import com.alzadash.core.Feature
import com.alzadash.core.Features

/**
 * Turns dashboard features on or off. Android Auto caps how many rows a list may show,
 * so long lists continue on a "More settings" page.
 */
class SettingsScreen(carContext: CarContext, private val from: Int = 0) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val limit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }
        val all = Feature.values().toList()
        val fits = if (all.size - from <= limit) all.size - from else maxOf(1, limit - 1)
        val list = ItemList.Builder()
        all.subList(from, from + fits).forEach { f ->
            list.addItem(
                Row.Builder()
                    .setTitle(f.label)
                    .setToggle(Toggle.Builder { checked -> Features.set(carContext, f, checked) }
                        .setChecked(Features.on(carContext, f))
                        .build())
                    .build()
            )
        }
        if (from + fits < all.size) {
            list.addItem(Row.Builder().setTitle("More settings").setOnClickListener {
                screenManager.push(SettingsScreen(carContext, from + fits))
            }.build())
        }
        return ListTemplate.Builder()
            .setTitle("Settings")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
