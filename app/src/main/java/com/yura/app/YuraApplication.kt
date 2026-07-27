package com.yura.app

import android.app.Application
import com.yura.app.stats.ReadingStatsCoordinator

class YuraApplication : Application() {
    val readingStatsCoordinator: ReadingStatsCoordinator by lazy {
        ReadingStatsCoordinator(this).also { it.start() }
    }

    override fun onCreate() {
        super.onCreate()
        readingStatsCoordinator
    }
}
