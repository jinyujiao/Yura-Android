package com.yura.app.util

import android.app.Activity
import android.content.pm.ActivityInfo
import com.yura.app.R

fun Activity.applyDeviceOrientationPolicy() {
    requestedOrientation = if (resources.getBoolean(R.bool.is_tablet)) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
