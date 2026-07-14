package com.yura.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yura.app.sync.WebDavSettingsStore
import com.yura.app.sync.WebDavSyncWorker
import com.yura.app.ui.YuraApp
import com.yura.app.ui.theme.YuraTheme
import com.yura.app.util.applyDeviceOrientationPolicy

class MainActivity : ComponentActivity() {
    private var externalPublicationUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationPolicy()
        externalPublicationUri = intent.publicationUriOrNull()
        if (WebDavSettingsStore.load(this).enabled) {
            WebDavSyncWorker.enqueue(this)
        }
        enableEdgeToEdge()
        setContent {
            YuraTheme {
                YuraApp(
                    externalPublicationUri = externalPublicationUri,
                    onExternalPublicationHandled = ::clearExternalPublicationIntent,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalPublicationUri = intent.publicationUriOrNull()
    }

    private fun Intent.publicationUriOrNull(): Uri? {
        val uri = when (action) {
            Intent.ACTION_VIEW -> data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        } ?: return null
        val normalizedType = type.orEmpty().lowercase()
        val name = uri.lastPathSegment.orEmpty().substringBefore('?').lowercase()
        return uri.takeIf {
            normalizedType in SUPPORTED_PUBLICATION_TYPES ||
                name.endsWith(".epub") ||
                name.endsWith(".txt")
        }
    }

    private fun clearExternalPublicationIntent() {
        externalPublicationUri = null
        intent.apply {
            action = null
            data = null
            type = null
            removeExtra(Intent.EXTRA_STREAM)
        }
    }

    private companion object {
        val SUPPORTED_PUBLICATION_TYPES = setOf(
            "application/epub+zip",
            "application/x-epub+zip",
            "text/plain",
        )
    }
}
