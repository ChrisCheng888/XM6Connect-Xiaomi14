package com.xm6.app

import android.app.Application
import androidx.core.content.ContextCompat
import com.xm6.app.data.HeadphoneRepository
import com.xm6.app.data.UserPreferencesStore
import com.xm6.app.ldac.LdacShizukuManager
import com.xm6.app.system.AncTileService
import com.xm6.app.system.BluetoothConnectionReceiver
import com.xm6.app.system.CompanionAssociationHelper
import com.xm6.app.system.Xm6GlanceWidget
import com.xm6.app.ui.theme.ThemeScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class Xm6Application : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothReceiver = BluetoothConnectionReceiver()

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
            onStateBroadcast = {
                Xm6GlanceWidget.updateAllWidgets(this)
                AncTileService.requestRefresh(this)
            }
        )

        // Dynamic receiver guarantees immediate OS Bluetooth event delivery while the process is alive
        runCatching {
            ContextCompat.registerReceiver(
                this,
                bluetoothReceiver,
                BluetoothConnectionReceiver.createIntentFilter(),
                ContextCompat.RECEIVER_EXPORTED
            )
        }

        appScope.launch {
            val prefs = preferencesStore.prefsFlow.first()
            ThemeScheduler.syncSchedule(this@Xm6Application, prefs.themeMode)
            val boundMac = prefs.boundMac
            if (!boundMac.isNullOrBlank() && CompanionAssociationHelper.isAssociated(this@Xm6Application, boundMac)) {
                CompanionAssociationHelper.startObservingPresence(this@Xm6Application, boundMac)
            }
            // Immediately reconcile with live system Bluetooth on process cold start
            repository.syncWithSystemBluetooth(reason = "app_create", triggerAutoLdac = false)
        }
    }

    fun triggerBackgroundSync(reason: String, triggerAutoLdac: Boolean = false) {
        appScope.launch {
            repository.syncWithSystemBluetooth(reason = reason, triggerAutoLdac = triggerAutoLdac)
        }
    }
}
