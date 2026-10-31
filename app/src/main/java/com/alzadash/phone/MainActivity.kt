package com.alzadash.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.alzadash.core.Chime
import com.alzadash.core.Launch
import com.alzadash.core.NowPlaying
import com.alzadash.core.Pkg
import com.alzadash.core.Prefs

/** The phone companion: quick launch buttons, tools and setup. */
class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val missing = it.filterValues { granted -> !granted }.keys
        toast(if (missing.isEmpty()) "All set" else "Some permissions were not allowed")
    }

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ApkInstaller.install(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val accent = getColor(com.alzadash.R.color.accent)

        col.addView(heading("QUICK LAUNCH"))
        col.addView(buttonRow(
            bigButton("Play Store") { Launch.app(this, Pkg.PLAY_STORE) },
            bigButton("Netflix") { Launch.app(this, Pkg.NETFLIX) },
        ))
        col.addView(buttonRow(
            bigButton("YouTube") { Launch.app(this, Pkg.YOUTUBE) },
            bigButton("Gemini", accent) { Launch.gemini(this) },
        ))
        col.addView(buttonRow(
            bigButton("Google Maps") { Launch.app(this, Pkg.MAPS) },
            bigButton("Waze") { Launch.app(this, Pkg.WAZE) },
        ))

        col.addView(heading("TOOLS"))
        col.addView(buttonRow(
            bigButton("All apps") { startActivity(Intent(this, AppsActivity::class.java)) },
            bigButton("Install APK") { installApk() },
        ))
        col.addView(buttonRow(
            bigButton("Radio stations") { startActivity(Intent(this, StationsActivity::class.java)) },
            bigButton("Test welcome sound") { Chime.play() },
        ))

        col.addView(heading("SETUP"))
        col.addView(buttonRow(
            bigButton("Allow permissions", accent) { requestPermissions() },
            bigButton("Show song on dash") { openNowPlayingAccess() },
        ))
        col.addView(toggle("Welcome sound", prefs.welcomeSound) { prefs.welcomeSound = it })
        col.addView(toggle("Auto start on car Bluetooth", prefs.autoStartOnBluetooth) { prefs.autoStartOnBluetooth = it })
        col.addView(toggle("Resume radio on connect", prefs.resumeRadioOnConnect) { prefs.resumeRadioOnConnect = it })
        col.addView(textField("Car Bluetooth name (blank = any)", prefs.carBluetoothName) { prefs.carBluetoothName = it })
        col.addView(textField("Home address", prefs.homeAddress) { prefs.homeAddress = it })

        col.addView(heading("ON THE CAR SCREEN"))
        col.addView(note(
            "In Android Auto settings, tap Version 10 times to unlock developer settings, " +
                "then turn on Unknown sources. AlzaDash then appears in the car's app list.\n\n" +
                "Video on the car screen plays only while the car is stopped. While moving, the picture is hidden and only sound continues."
        ))

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) = SwitchCompat(this).apply {
        text = label
        textSize = 17f
        isChecked = value
        minHeight = dp(56)
        setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun textField(hint: String, value: String, onChange: (String) -> Unit) = EditText(this).apply {
        this.hint = hint
        setText(value)
        isSingleLine = true
        setOnFocusChangeListener { _, focused -> if (!focused) onChange(text.toString()) }
        addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = onChange(s.toString())
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= 31) perms += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        askPermissions.launch(perms.toTypedArray())
    }

    private fun openNowPlayingAccess() {
        if (NowPlaying.hasAccess(this)) {
            toast("Already allowed")
            return
        }
        toast("Turn on AlzaDash in the list")
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun installApk() {
        if (!packageManager.canRequestPackageInstalls()) {
            toast("Allow AlzaDash to install apps, then tap Install APK again")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        pickApk.launch(arrayOf("application/vnd.android.package-archive"))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
