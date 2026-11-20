package com.aurora.launcher

import android.app.Application
import com.aurora.launcher.data.AppRepository
import com.aurora.launcher.data.IconThemeManager
import com.aurora.launcher.data.LayoutStore
import com.aurora.launcher.data.SettingsStore
import com.aurora.launcher.data.WidgetHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LauncherApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings by lazy { SettingsStore(this) }
    val layout by lazy { LayoutStore(this, appScope) }
    val repo by lazy { AppRepository(this, appScope, IconThemeManager(this), settings.state.value.iconTheme) }
    val widgets by lazy { WidgetHolder(this) }

    override fun onCreate() {
        super.onCreate()
        com.aurora.launcher.data.CrashLog.install(this)
        com.aurora.launcher.data.AppStyles.load(this)
    }
}
