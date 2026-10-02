package com.pip.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pip.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // One-time prompt so Pluto's floating notch (PipApplication.kt)
        // actually has permission to draw — "draw over other apps" can't be
        // granted through the normal runtime-permission dialog, only via
        // this Settings screen. No-op once already granted.
        if (BuildConfig.DEBUG && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        setContent {
            AppRoot()
        }
    }
}
