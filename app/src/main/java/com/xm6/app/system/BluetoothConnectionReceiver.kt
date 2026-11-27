package com.xm6.app.system

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.xm6.app.Xm6Application
import com.xm6.app.protocol.RfcommConnector
import com.xm6.app.ui.theme.ThemeScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real-time system Bluetooth connection monitor (registered both statically in AndroidManifest
 * and dynamically in Xm6Application) so XM6 Connect and its 2x2 desktop widget automatically
 * track WH-1000XM6 power-on auto-connect, power-off disconnect, and Bluetooth toggles
 * without requiring manual tapping.
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "XM6Proto"

        fun createIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as? Xm6Application ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_500L) {
                    handleEvent(app, intent, action)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "BluetoothConnectionReceiver error for $action: ${t.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleEvent(app: Xm6Application, intent: Intent, action: String) {
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val prefs = app.preferencesStore.prefsFlow.first()
                ThemeScheduler.syncSchedule(app, prefs.themeMode)
                val mac = prefs.boundMac
                if (!mac.isNullOrBlank() && CompanionAssociationHelper.isAssociated(app, mac)) {
                    CompanionAssociationHelper.startObservingPresence(app, mac)
                }
                app.repository.syncWithSystemBluetooth(reason = "boot_completed", triggerAutoLdac = true)
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (btState) {
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                        Log.d(TAG, "System Bluetooth OFF -> marking XM6 disconnected")
                        app.repository.onBluetoothDisabled()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        Log.d(TAG, "System Bluetooth ON -> checking connected XM6")
                        app.repository.syncWithSystemBluetooth(reason = "bt_adapter_on", triggerAutoLdac = true)
                    }
                }
            }

            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val device = extractBluetoothDevice(intent) ?: return
                val boundMac = app.preferencesStore.prefsFlow.first().boundMac
                if (!RfcommConnector.isMatchingTargetDevice(device, boundMac)) return

                val profileState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                val isA2dp = (action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                when (profileState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Profile connected (a2dp=$isA2dp) for ${device.address} -> auto-syncing")
                        app.repository.onSystemBluetoothConnected(
                            device = device,
                            triggerAutoLdac = isA2dp,
                            awaitInitialSync = true
                        )
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        // Only mark offline if the device is no longer connected on any Bluetooth profile
                        if (!RfcommConnector.isDeviceSystemConnected(app, device)) {
                            Log.d(TAG, "Profile disconnected (a2dp=$isA2dp) & ACL gone for ${device.address}")
                            app.repository.onSystemBluetoothDisconnected(device)
                        }
                    }
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = extractBluetoothDevice(intent) ?: return
                val boundMac = app.preferencesStore.prefsFlow.first().boundMac
                if (!RfcommConnector.isMatchingTargetDevice(device, boundMac)) return
                Log.d(TAG, "ACL connected for ${device.address} -> scheduling auto-connect")
                app.repository.onSystemBluetoothConnected(
                    device = device,
                    triggerAutoLdac = true,
                    awaitInitialSync = true
                )
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = extractBluetoothDevice(intent) ?: return
                val boundMac = app.preferencesStore.prefsFlow.first().boundMac
                if (!RfcommConnector.isMatchingTargetDevice(device, boundMac)) return
                Log.d(TAG, "ACL disconnected for ${device.address} -> marking offline immediately")
                app.repository.onSystemBluetoothDisconnected(device)
            }
        }
    }

    private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }
}
