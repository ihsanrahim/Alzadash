package com.autodash.phone

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
import com.autodash.core.Background
import com.autodash.core.Bookmark
import com.autodash.core.Browser
import com.autodash.core.Chime
import com.autodash.core.Launch
import com.autodash.core.NowPlaying
import com.autodash.core.Pkg
import com.autodash.core.Prefs
import com.autodash.core.Feature
import com.autodash.core.Features

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
        val accent = getColor(com.autodash.R.color.accent)

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
        col.addView(buttonRow(
            bigButton("Open dashboard", accent) { startActivity(Intent(this, DashActivity::class.java)) },
            bigButton("Browser") { startActivity(Intent(this, BrowserActivity::class.java)) },
        ))

        section(col, "DASHBOARD", true) {
            it.addView(buttonRow(
                smallButton("Edit layout", accent) { startActivity(Intent(this, LayoutEditorActivity::class.java)) },
                smallButton("Shortcuts") { startActivity(Intent(this, DockPickerActivity::class.java)) },
                smallButton("Background") { chooseBackground() },
            ))
            // Welcome tone and the size of the dashboard's buttons.
            it.addView(buttonRow(
                smallButton("Welcome sound") { pick("Welcome sound", com.autodash.core.Look.TONES, com.autodash.core.Look.tone(this)) { v ->
                    com.autodash.core.Look.setTone(this, v); Chime.play(v) } },
                smallButton("Button size") {
                    val sizes = listOf("70%", "85%", "100%", "120%", "150%")
                    val cur = "${(com.autodash.core.Look.buttonScale(this) * 100).toInt()}%"
                    pick("Dashboard button size", sizes, cur) { v -> com.autodash.core.Look.setButtonScale(this, v.removeSuffix("%").toFloat() / 100f) }
                },
            ))
        }

        section(col, "BROWSER", false) {
            val box = EditText(this).apply {
                hint = "Search or web address"
                textSize = 16f
                isSingleLine = true
            }
            it.addView(box)
            it.addView(buttonRow(
                smallButton("Open", accent) {
                    startActivity(Intent(this, BrowserActivity::class.java)
                        .putExtra(BrowserActivity.EXTRA_URL, Browser.toUrl(box.text.toString())))
                    box.setText("")
                },
                smallButton("Bookmark") {
                    val text = box.text.toString().trim()
                    if (text.isEmpty()) toast("Type a web address first") else {
                        Browser.addBookmark(this, Bookmark(text.removePrefix("https://").removePrefix("www.").take(30), Browser.toUrl(text)))
                        toast("Added to the browser's start page")
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

        // Dashboard switches, two to a row.
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
                smallButton("Permissions", accent) { requestPermissions() },
                smallButton("Song access") { openNowPlayingAccess() },
            ))
            it.addView(buttonRow(
                smallButton("Radio stations") { startActivity(Intent(this, StationsActivity::class.java)) },
                smallButton("Test sound") { Chime.play(com.autodash.core.Look.tone(this)) },
            ))
            it.addView(textField("Car Bluetooth name (blank = any)", prefs.carBluetoothName) { v -> prefs.carBluetoothName = v })
            it.addView(textField("Home address", prefs.homeAddress) { v -> prefs.homeAddress = v })
        }

        section(col, "HELP", false) {
            it.addView(note(
                "Autodash is a normal app: install the APK and it works. No Shizuku or KingInstaller.\n\n" +
                    "Mount your phone in landscape and tap Open dashboard (or the Autodash Dashboard icon). Shortcut apps open on the phone as usual.\n\n" +
                    "On the car screen, Android Auto shows Autodash Radio in its media apps. Android Auto doesn't list sideloaded dashboard apps, so the dashboard itself runs on the phone.\n\n" +
                    "The browser hides the page while the car is moving; sound keeps playing and the page comes back when you stop."
            ))
        }

        setContentView(ScrollView(this).apply { addView(col) })

        // Ask for everything the dashboard needs the first time the app is opened.
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
        val accent = getColor(com.autodash.R.color.accent)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            problems += "Location isn't allowed, so speed, trip and prayer times can't work."
            fixRow.addView(smallButton("Location", accent) { requestPermissions() }, weight())
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
        )
        if (Build.VERSION.SDK_INT >= 31) perms += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        askPermissions.launch(perms.toTypedArray())
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

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
