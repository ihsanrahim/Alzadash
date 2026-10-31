package com.alzadash.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.alzadash.core.Prefs
import com.alzadash.core.Radio

/** Instant Radio: one tap on a saved station starts it. */
class RadioScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder().setNoItemsMessage("Add stations in the AlzaDash app on your phone")
        if (Radio.isPlaying) {
            list.addItem(Row.Builder().setTitle("Stop radio").setOnClickListener {
                Radio.stop(carContext)
                screenManager.pop()
            }.build())
        }
        Prefs(carContext).stations.forEach { st ->
            val row = Row.Builder().setTitle(st.name).setOnClickListener {
                Radio.play(carContext, st)
                screenManager.pop()
            }
            if (Radio.current?.name == st.name) row.addText("Playing")
            list.addItem(row.build())
        }
        return ListTemplate.Builder()
            .setTitle("Instant Radio")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
