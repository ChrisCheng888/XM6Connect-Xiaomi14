package com.xm6.app.system

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.content.Context
import android.content.IntentSender
import android.os.Build
import com.xm6.app.Xm6Application
import com.xm6.app.protocol.RfcommConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * Android 12+ CompanionDeviceService:
 * Automatically woken by the OS when the associated WH-1000XM6 connects —
 * applies Shizuku LDAC 990kbps lock and refreshes the Glance desktop widget
 * without needing a persistent foreground notification.
 */
class Xm6CompanionService : CompanionDeviceService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        super.onDeviceAppeared(associationInfo)
        val mac = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            associationInfo.deviceMacAddress?.toString()
        } else null
        handleDeviceAppeared(mac)
    }

    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        super.onDeviceAppeared(address)
        handleDeviceAppeared(address)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        super.onDeviceDisappeared(associationInfo)
        handleDeviceDisappeared()
    }

    @Suppress("DEPRECATION")
    override fun onDeviceDisappeared(address: String) {
        super.onDeviceDisappeared(address)
        handleDeviceDisappeared()
    }

    private fun handleDeviceAppeared(mac: String?) {
        val app = application as? Xm6Application ?: return
        scope.launch {
            val prefs = app.preferencesStore.prefsFlow.first()
            val targetMac = mac ?: prefs.boundMac
            val device = targetMac?.let { RfcommConnector.findDeviceByMac(app, it) }
            if (device != null) {
                app.repository.onSystemBluetoothConnected(
                    device = device,
                    triggerAutoLdac = true,
                    awaitInitialSync = true
                )
            } else {
                app.repository.syncWithSystemBluetooth(
                    reason = "companion_appeared",
                    triggerAutoLdac = true
                )
            }
        }
    }

    private fun handleDeviceDisappeared() {
        val app = application as? Xm6Application ?: return
        scope.launch {
            app.repository.onSystemBluetoothDisconnected(device = null)
        }
    }
}

object CompanionAssociationHelper {

    fun isAssociated(context: Context, targetMac: String?): Boolean {
        val mgr = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val associations = mgr.myAssociations
            if (targetMac.isNullOrBlank()) {
                associations.isNotEmpty()
            } else {
                associations.any {
                    it.deviceMacAddress?.toString().equals(targetMac, ignoreCase = true)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val macs = mgr.associations
            if (targetMac.isNullOrBlank()) macs.isNotEmpty()
            else macs.any { it.equals(targetMac, ignoreCase = true) }
        }
    }

    fun startAssociation(
        context: Context,
        onIntentSender: (IntentSender) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val mgr = context.getSystemService(CompanionDeviceManager::class.java)
        if (mgr == null) {
            onFailure("系统不支持 CompanionDeviceManager")
            return
        }
        val filter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile(".*(WH-1000XM6|WH-1000X|Sony).*"))
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .build()

        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                onIntentSender(intentSender)
            }

            @Deprecated("Deprecated in API 33")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                onIntentSender(chooserLauncher)
            }

            override fun onFailure(error: CharSequence?) {
                onFailure(error?.toString() ?: "伴生设备关联失败")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mgr.associate(request, context.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            mgr.associate(request, callback, null)
        }
    }

    @SuppressLint("MissingPermission")
    fun startObservingPresence(context: Context, mac: String) {
        val mgr = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        runCatching {
            @Suppress("DEPRECATION")
            mgr.startObservingDevicePresence(mac)
        }
    }
}
