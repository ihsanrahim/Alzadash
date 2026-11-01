package com.alzadash.mirror

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

/** Invisible screen that asks Android for permission to cast the phone's screen, then hands it to [MirrorService]. */
class MirrorStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(mpm.createScreenCaptureIntent(), 1)
        } catch (_: Exception) {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, MirrorService::class.java).putExtra("code", resultCode).putExtra("data", data))
            if (!MirrorTouch.enabled(this)) {
                Toast.makeText(this, "To tap from the car screen, turn on AlzaDash in Accessibility (AlzaDash phone app > Mirroring)", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }
}
