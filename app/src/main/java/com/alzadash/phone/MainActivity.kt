package com.alzadash.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.alzadash.car.CarSurface
import com.alzadash.core.Background
import com.alzadash.core.Bookmark
import com.alzadash.core.Browser
import com.alzadash.core.Chime
import com.alzadash.core.Launch
import com.alzadash.core.NowPlaying
import com.alzadash.core.Pkg
import com.alzadash.core.Prefs
import com.alzadash.core.Feature
import com.alzadash.core.Features
import com.alzadash.core.Shell

/** The phone companion: quick launch buttons, tools and setup. */
class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var shizukuStatus: android.widget.TextView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

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
            setPadding(dp(12), dp(12), dp(12), dp(24))
        }
        val accent = getColor(com.alzadash.R.color.accent)

        col.addView(TextView(this).apply {
            text = "AlzaDash"
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(4), 0, dp(4), dp(4))
        })
        // What still needs doing, with the fix next to it. Hidden once everything is set up.
        shizukuStatus = note("")
        col.addView(shizukuStatus)
        fixRow = buttonRow()
        col.addView(fixRow)

        section(col, "DASHBOARD", true) {
            it.addView(buttonRow(
                smallButton("Edit layout", accent) { startActivity(Intent(this, LayoutEditorActivity::class.java)) },
                smallButton("Shortcuts") { startActivity(Intent(this, DockPickerActivity::class.java)) },
                smallButton("Background") { chooseBackground() },
            ))
        }

        // One box for everything typed for the car: text for the app on screen, or a web address.
        section(col, "SEND TO CAR", true) {
            val box = EditText(this).apply {
                hint = "Text, search or web address"
                textSize = 16f
                isSingleLine = true
            }
            it.addView(box)
            it.addView(buttonRow(
                smallButton("Type", accent) {
                    val surface = CarSurface.active?.get()
                    when {
                        surface == null || surface.displayId < 0 -> toast("Connect to the car first")
                        !Shell.available() -> toast("Shizuku is needed for this")
                        else -> {
                            surface.typeText(box.text.toString())
                            box.setText("")
                        }
                    }
                },
                smallButton("Enter") { CarSurface.active?.get()?.let { s -> Shell.key(s.displayId, 66) } },
                smallButton("Open web") {
                    val surface = CarSurface.active?.get()
                    if (surface == null) toast("Connect to the car first") else {
                        surface.openUrl(Browser.toUrl(box.text.toString()))
                        box.setText("")
                    }
                },
                smallButton("Bookmark") {
                    val text = box.text.toString().trim()
                    if (text.isEmpty()) toast("Type a web address first") else {
                        Browser.addBookmark(this, Bookmark(text.removePrefix("https://").removePrefix("www.").take(30), Browser.toUrl(text)))
                        toast("Added to the car browser's start page")
                        box.setText("")
                    }
                },
            ))
        }

        section(col, "QUICK LAUNCH", false) {
            it.addView(buttonRow(
                smallButton("Play Store") { Launch.app(this, Pkg.PLAY_STORE) },
                smallButton("YouTube") { Launch.app(this, Pkg.YOUTUBE) },
                smallButton("Netflix") { Launch.app(this, Pkg.NETFLIX) },
            ))
            it.addView(buttonRow(
                smallButton("Hotstar") { Launch.app(this, "in.startv.hotstar.dplus") },
                smallButton("Maps") { Launch.app(this, Pkg.MAPS) },
                smallButton("Waze") { Launch.app(this, Pkg.WAZE) },
            ))
            it.addView(buttonRow(
                smallButton("Gemini", accent) { Launch.gemini(this) },
                smallButton("All apps") { startActivity(Intent(this, AppsActivity::class.java)) },
                smallButton("Install APK") { installApk() },
            ))
        }

        // Same switches as the ⚙ button on the car screen, two to a row.
        section(col, "SETTINGS", false) {
            settingsBoxes.clear()
            Feature.values().toList().chunked(2).forEach { pair ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEach { f ->
                    val cb = CheckBox(this).apply {
                        text = f.label
                        textSize = 14f
                        isChecked = Features.on(this@MainActivity, f)
                        setOnCheckedChangeListener { _, checked -> Features.set(this@MainActivity, f, checked) }
                    }
                    settingsBoxes[f] = cb
                    row.addView(cb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
                it.addView(row)
            }
        }

        section(col, "SETUP", false) {
            it.addView(buttonRow(
                smallButton("Allow Shizuku", accent) { allowShizuku() },
                smallButton("Permissions") { requestPermissions() },
                smallButton("Song access") { openNowPlayingAccess() },
            ))
            it.addView(buttonRow(
                smallButton("Radio stations") { startActivity(Intent(this, StationsActivity::class.java)) },
                smallButton("Test sound") { Chime.play() },
            ))
            it.addView(textField("Car Bluetooth name (blank = any)", prefs.carBluetoothName) { v -> prefs.carBluetoothName = v })
            it.addView(textField("Home address", prefs.homeAddress) { v -> prefs.homeAddress = v })
        }

        section(col, "HELP", false) {
            it.addView(note(
                "Install updates with KingInstaller (Shizuku Trick) so Android Auto lists AlzaDash.\n\n" +
                    "Video and web pages on the car screen show only while the car is stopped; sound keeps playing.\n\n" +
                    "Opening real apps (YouTube, Spotify, Waze) on the car screen and typing into them need Shizuku running and allowed."
            ))
        }

        setContentView(ScrollView(this).apply { addView(col) })

        // Ask for everything the car screen needs the first time the app is opened.
        if (!prefs.askedPermissions) {
            prefs.askedPermissions = true
            requestPermissions()
        }
    }

    private lateinit var fixRow: LinearLayout

    /**
     * A heading that folds its section away when tapped; whether each is open is remembered.
     */
    private fun section(parent: LinearLayout, title: String, openByDefault: Boolean, fill: (LinearLayout) -> Unit) {
        val key = "section_$title"
        val sp = getSharedPreferences("alzadash", MODE_PRIVATE)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fill(body)
        val head = heading("")
        fun show(open: Boolean) {
            body.visibility = if (open) android.view.View.VISIBLE else android.view.View.GONE
            head.text = (if (open) "▾  " else "▸  ") + title
        }
        show(sp.getBoolean(key, openByDefault))
        head.setOnClickListener {
            val open = body.visibility != android.view.View.VISIBLE
            sp.edit().putBoolean(key, open).apply()
            show(open)
        }
        parent.addView(head)
        parent.addView(body)
    }

    private fun allowShizuku() {
        if (!Shell.running()) toast("Open Shizuku and tap Start first") else Shell.requestPermission()
        handler.postDelayed({ updateShizuku() }, 1500)
    }

    private val settingsBoxes = HashMap<Feature, CheckBox>()

    override fun onResume() {
        super.onResume()
        settingsBoxes.forEach { (f, box) -> box.isChecked = Features.on(this, f) }
        updateShizuku()
    }

    /** Lists only what isn't set up yet, each with its fix button. */
    private fun updateShizuku() {
        val problems = mutableListOf<String>()
        fixRow.removeAllViews()
        val accent = getColor(com.alzadash.R.color.accent)
        if (!Shell.available()) {
            problems += if (Shell.running()) "Shizuku isn't allowed yet." else "Shizuku isn't running (needed for apps on the car screen)."
            fixRow.addView(smallButton("Allow Shizuku", accent) { allowShizuku() }, weight())
        }
        if (!NowPlaying.hasAccess(this)) {
            problems += "Song names and lyrics need notification access."
            fixRow.addView(smallButton("Song access", accent) { openNowPlayingAccess() }, weight())
        }
        shizukuStatus.text = if (problems.isEmpty()) "✓ All set up" else problems.joinToString("\n")
        fixRow.visibility = if (problems.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun weight() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(dp(6), dp(6), dp(6), dp(6))
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

    private val pickBackground = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) toast(if (Background.fromUri(this, uri)) "Background set" else "Couldn't use that picture")
    }

    /** Uses the phone's wallpaper if Android allows it, otherwise lets you pick a picture. */
    private fun chooseBackground() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Dashboard background")
            .setItems(arrayOf("Use my phone wallpaper", "Pick a picture", "Plain dark")) { _, which ->
                when (which) {
                    0 -> if (Background.fromWallpaper(this)) {
                        toast("Background set")
                    } else {
                        toast("Android doesn't let apps read the wallpaper. Pick the same picture from your gallery instead.")
                        pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    1 -> pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    else -> { Background.clear(this); toast("Background removed") }
                }
            }
            .show()
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
