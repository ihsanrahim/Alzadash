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

/** The phone companion: quick launch buttons, tools and setup. */
class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var setupStatus: android.widget.TextView
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
            text = "Autodash"
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(4), 0, dp(4), dp(4))
        })
        // What still needs doing, with the fix next to it. Hidden once everything is set up.
        setupStatus = note("")
        col.addView(setupStatus)
        fixRow = buttonRow()
        col.addView(fixRow)

        section(col, "DASHBOARD", true) {
            it.addView(buttonRow(
                smallButton("Edit layout", accent) { startActivity(Intent(this, LayoutEditorActivity::class.java)) },
                smallButton("Background") { chooseBackground() },
            ))
            // Car settings: welcome tone, size of the car screen's buttons, card beside the map.
            it.addView(buttonRow(
                smallButton("Welcome sound") { pick("Welcome sound", com.alzadash.core.Look.TONES, com.alzadash.core.Look.tone(this)) { v ->
                    com.alzadash.core.Look.setTone(this, v); Chime.play(v) } },
                smallButton("Button size") {
                    val sizes = listOf("70%", "85%", "100%", "120%", "150%")
                    val cur = "${(com.alzadash.core.Look.buttonScale(this) * 100).toInt()}%"
                    pick("Car button size", sizes, cur) { v -> com.alzadash.core.Look.setButtonScale(this, v.removeSuffix("%").toFloat() / 100f) }
                },
                smallButton("Map card") { pick("Card next to the map", com.alzadash.core.Look.SPLIT_CARDS, com.alzadash.core.Look.splitCard(this)) { v ->
                    com.alzadash.core.Look.setSplitCard(this, v) } },
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
                smallButton("Open web", accent) {
                    val text = box.text.toString()
                    if (text.isBlank()) return@smallButton toast("Type a web address or search first")
                    val url = Browser.toUrl(text)
                    // Also kept as "From phone" on the car browser's Sites page.
                    Browser.setPhoneUrl(this, url)
                    val surface = CarSurface.active?.get()
                    if (surface == null) toast("Saved as From phone on the car's Sites page") else surface.openUrl(url)
                    box.setText("")
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

        // The car browser's settings, the same as Browser > ⋮ on the car screen.
        section(col, "BROWSER", false) {
            it.addView(buttonRow(
                smallButton("Theme") { pick("Page theme", Browser.THEMES, Browser.theme(this)) { v -> Browser.setTheme(this, v); refreshBrowser() } },
                smallButton("Zoom") {
                    val z = listOf("75%", "90%", "100%", "110%", "125%", "150%", "175%", "200%")
                    pick("Page zoom", z, "${Browser.zoom(this)}%") { v -> Browser.setZoom(this, v.removeSuffix("%").toInt()); refreshBrowser() }
                },
                smallButton("Desktop") {
                    pick("Website version", listOf("Mobile", "Desktop"), if (Browser.desktop(this)) "Desktop" else "Mobile") { v ->
                        Browser.setDesktop(this, v == "Desktop"); refreshBrowser() }
                },
            ))
            it.addView(buttonRow(
                smallButton("Bar colour") { pick("Bar colour", Browser.BAR_COLOURS.map { c -> c.first }, Browser.barColour(this)) { v ->
                    Browser.setBarColour(this, v); refreshBrowser(false) } },
                smallButton("Opacity") {
                    val o = listOf("40%", "55%", "70%", "85%", "100%")
                    pick("Bar opacity", o, "${Browser.barOpacity(this)}%") { v -> Browser.setBarOpacity(this, v.removeSuffix("%").toInt()); refreshBrowser(false) }
                },
                smallButton("Bar size") {
                    val o = listOf("75%", "100%", "125%", "150%")
                    pick("Bar size", o, "${Browser.barSize(this)}%") { v -> Browser.setBarSize(this, v.removeSuffix("%").toInt()); refreshBrowser(false) }
                },
            ))
            it.addView(buttonRow(
                smallButton("Bar buttons") { chooseBarButtons() },
                smallButton("Bar position") {
                    val spots = listOf("Bottom centre", "Bottom left", "Bottom right", "Top centre", "Top left", "Top right")
                    pick("Bar position", spots, "") { v ->
                        val x = when { v.endsWith("left") -> 0f; v.endsWith("right") -> 1f; else -> 0.5f }
                        Browser.setBarPos(this, x, if (v.startsWith("Top")) 0f else 1f)
                        refreshBrowser(false)
                    }
                },
                smallButton("Ad block") {
                    pick("Block ads", listOf("On", "Off"), if (Browser.adBlock(this)) "On" else "Off") { v ->
                        Browser.setAdBlock(this, v == "On"); refreshBrowser() }
                },
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
                smallButton("Permissions") { requestPermissions() },
                smallButton("Song access") { openNowPlayingAccess() },
            ))
            // These two switches let it rotate the phone and pass taps.
            it.addView(buttonRow(
                smallButton("Mirror: taps") {
                    if (com.alzadash.mirror.MirrorTouch.enabled(this)) toast("Already on") else {
                        toast("Find Autodash car mirroring and turn it on. If it's greyed out: Settings > Apps > Autodash > ⋮ > Allow restricted settings")
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                },
                smallButton("Mirror: rotate") {
                    if (Settings.canDrawOverlays(this)) toast("Already allowed") else {
                        toast("Allow Autodash to display over other apps")
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    }
                },
            ))
            it.addView(buttonRow(
                smallButton("Radio stations") { startActivity(Intent(this, StationsActivity::class.java)) },
                smallButton("Test sound") { Chime.play(com.alzadash.core.Look.tone(this)) },
            ))
            it.addView(textField("Car Bluetooth name (blank = any)", prefs.carBluetoothName) { v -> prefs.carBluetoothName = v })
            it.addView(textField("Home address", prefs.homeAddress) { v -> prefs.homeAddress = v })
        }

        section(col, "HELP", false) {
            it.addView(note(
                "Install Autodash from Google Play so Android Auto lists it.\n\n" +
                    "Video and web pages on the car screen show only while the car is stopped; sound keeps playing.\n\n" +
                    "To see a phone app on the car screen, use phone mirroring (the cast button above ⚙ on the car screen). Turn on Mirror: taps and Mirror: rotate in Setup once."
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

    private val settingsBoxes = HashMap<Feature, CheckBox>()

    override fun onResume() {
        super.onResume()
        settingsBoxes.forEach { (f, box) -> box.isChecked = Features.on(this, f) }
        updateSetup()
    }

    /** Lists only what isn't set up yet, each with its fix button. */
    private fun updateSetup() {
        val problems = mutableListOf<String>()
        fixRow.removeAllViews()
        val accent = getColor(com.alzadash.R.color.accent)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            problems += "Location isn't allowed, so the car screen can't tell when you're parked."
            fixRow.addView(smallButton("Location", accent) { requestPermissions() }, weight())
        } else if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            problems += "Location: choose \"Allow all the time\" so parked video works with the phone screen off."
            fixRow.addView(smallButton("Location", accent) { askAlwaysLocation() }, weight())
        }
        if (!NowPlaying.hasAccess(this)) {
            problems += "Song names and lyrics need notification access."
            fixRow.addView(smallButton("Song access", accent) { openNowPlayingAccess() }, weight())
        }
        setupStatus.text = if (problems.isEmpty()) "✓ All set up" else problems.joinToString("\n")
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
            Manifest.permission.READ_CALENDAR,
            "com.google.android.gms.permission.CAR_SPEED",
        )
        if (Build.VERSION.SDK_INT >= 31) perms += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        askPermissions.launch(perms.toTypedArray())
    }

    /** Android only offers "Allow all the time" on the app's settings page. */
    private fun askAlwaysLocation() {
        toast("Tap Location, then Allow all the time")
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    /** A one-choice list; [onPick] gets the chosen item. */
    private fun pick(title: String, items: List<String>, current: String, onPick: (String) -> Unit) {
        android.app.AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), items.indexOf(current)) { d, i ->
                onPick(items[i])
                d.dismiss()
            }.show()
    }

    private fun openNowPlayingAccess() {
        if (NowPlaying.hasAccess(this)) {
            toast("Already allowed")
            return
        }
        toast("Turn on Autodash in the list")
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun installApk() {
        if (!packageManager.canRequestPackageInstalls()) {
            toast("Allow Autodash to install apps, then tap Install APK again")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        pickApk.launch(arrayOf("application/vnd.android.package-archive"))
    }

    /** Shows changed browser settings on the car screen straight away. */
    private fun refreshBrowser(reload: Boolean = true) {
        val b = CarSurface.active?.get()?.browser ?: return
        if (reload) b.applySettings() else b.applyBar()
    }

    private fun chooseBarButtons() {
        val keys = Browser.BAR_BUTTONS
        val shown = Browser.barButtons(this)
        android.app.AlertDialog.Builder(this).setTitle("Buttons on the bar")
            .setMultiChoiceItems(keys.map { it.second }.toTypedArray(), keys.map { it.first in shown }.toBooleanArray()) { _, i, on ->
                Browser.setBarButton(this, keys[i].first, on)
            }
            .setPositiveButton("Done") { _, _ -> refreshBrowser(false) }
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
