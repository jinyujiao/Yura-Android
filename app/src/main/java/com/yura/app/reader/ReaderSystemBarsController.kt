package com.yura.app.reader

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal class ReaderSystemBarsController(private val window: Window) {
    private var visible = true

    fun setSystemBarsVisible(visible: Boolean) {
        this.visible = visible
        applyVisibility()
    }

    fun reapply() {
        applyVisibility()
    }

    private fun applyVisibility() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
