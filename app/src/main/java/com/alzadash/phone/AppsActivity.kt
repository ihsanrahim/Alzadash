package com.alzadash.phone

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** A plain launcher listing every installed app. */
class AppsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = packageManager
        val apps: List<ResolveInfo> = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val list = ListView(this)
        list.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(position: Int) = apps[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? LinearLayout) ?: LinearLayout(this@AppsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    addView(ImageView(context), LinearLayout.LayoutParams(dp(48), dp(48)))
                    addView(TextView(context).apply {
                        textSize = 18f
                        setTextColor(Color.WHITE)
                        setPadding(dp(16), 0, 0, 0)
                    })
                }
                val info = apps[position]
                (row.getChildAt(0) as ImageView).setImageDrawable(info.loadIcon(pm))
                (row.getChildAt(1) as TextView).text = info.loadLabel(pm)
                return row
            }
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val a = apps[position].activityInfo
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(a.packageName, a.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        setContentView(list)
    }
}
