package com.yura.app

import android.os.Bundle
import com.yura.app.util.applyDeviceOrientationPolicy
import com.yura.app.sync.WebDavSettingsStore
import com.yura.app.sync.WebDavSyncWorker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yura.app.ui.YuraApp
import com.yura.app.ui.theme.YuraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationPolicy()
        if (WebDavSettingsStore.load(this).enabled) {
            WebDavSyncWorker.enqueue(this)
        }
        enableEdgeToEdge()
        setContent {
            YuraTheme {
                YuraApp()
            }
        }
    }
}
