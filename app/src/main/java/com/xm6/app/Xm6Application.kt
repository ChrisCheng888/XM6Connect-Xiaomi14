package com.xm6.app

import android.app.Application
import com.xm6.app.data.HeadphoneRepository
import com.xm6.app.data.UserPreferencesStore
import com.xm6.app.ldac.LdacShizukuManager
import com.xm6.app.system.Xm6GlanceWidget
import com.xm6.app.ui.theme.ThemeScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class Xm6Application : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var preferencesStore: UserPreferencesStore
        private set

    lateinit var shizukuManager: LdacShizukuManager
        private set

    lateinit var repository: HeadphoneRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesStore = UserPreferencesStore(this)
        shizukuManager = LdacShizukuManager(this)
        repository = HeadphoneRepository(
            context = this,
            preferencesStore = preferencesStore,
            shizukuManager = shizukuManager,
            onStateBroadcast = { Xm6GlanceWidget.updateAllWidgets(this) }
        )
        appScope.launch {
            val mode = preferencesStore.prefsFlow.first().themeMode
            ThemeScheduler.syncSchedule(this@Xm6Application, mode)
        }
    }
}
