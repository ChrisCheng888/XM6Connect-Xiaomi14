package com.xm6.app.protocol

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

/**
 * Classic Bluetooth RFCOMM transport for Sony MDR Protocol V2.
 */
class RfcommTransport(private val socket: BluetoothSocket) : ByteTransport {
    private val input = socket.inputStream
    private val output = socket.outputStream

    override fun write(data: ByteArray) {
        output.write(data)
        output.flush()
    }

    override fun read(buffer: ByteArray): Int = input.read(buffer)

    override fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }
}

object RfcommConnector {
    private const val TAG = "XM6Proto"

    // Sony MDR Protocol V2 RFCOMM Service UUID (WH-1000XM5 / WH-1000XM6 / modern Sony audio)
    val SONY_V2_UUID: UUID = UUID.fromString("956C7B26-D49A-4BA8-B03F-B17D393CB6E2")
    // Sony MDR Protocol V1 legacy UUID fallback
    val SONY_V1_UUID: UUID = UUID.fromString("96CC203E-5068-46AD-B32D-E316F5E069BA")

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return false
        return manager.adapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun isXm6DeviceName(name: String?): Boolean {
        val n = name.orEmpty()
        return n.contains("WH-1000XM6", ignoreCase = true) ||
            n.contains("WH-1000X", ignoreCase = true) ||
            n.contains("Sony", ignoreCase = true)
    }

    @SuppressLint("MissingPermission")
    fun isMatchingTargetDevice(device: BluetoothDevice, boundMac: String?): Boolean {
        if (!boundMac.isNullOrBlank()) {
            return device.address.equals(boundMac, ignoreCase = true)
        }
        return isXm6DeviceName(runCatching { device.name }.getOrNull())
    }

    /**
     * Checks whether the Bluetooth device currently has an active ACL/profile link at the Android OS level.
     * Uses Android's BluetoothDevice.isConnected() (exempted via HiddenApiBypass) with profile fallback.
     */
    @SuppressLint("MissingPermission")
    fun isDeviceSystemConnected(context: Context, device: BluetoothDevice): Boolean {
        if (!hasBluetoothConnectPermission(context) || !isBluetoothEnabled(context)) return false
        val direct = runCatching {
            val m = BluetoothDevice::class.java.getMethod("isConnected")
            m.invoke(device) as? Boolean
        }.getOrNull()
        if (direct != null) return direct

        // Fallback: check if any classic audio profile is connected when reflection is unavailable
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
            adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
    }

    /**
     * Returns the target WH-1000XM6 device ONLY if it is currently connected to Android Bluetooth.
     */
    @SuppressLint("MissingPermission")
    fun findSystemConnectedXm6Device(context: Context, boundMac: String?): BluetoothDevice? {
        if (!hasBluetoothConnectPermission(context) || !isBluetoothEnabled(context)) return null
        if (!boundMac.isNullOrBlank()) {
            val bound = findDeviceByMac(context, boundMac)
            if (bound != null && isDeviceSystemConnected(context, bound)) {
                return bound
            }
        }
        val candidates = findBondedXm6Devices(context)
        return candidates.firstOrNull { isXm6DeviceName(runCatching { it.name }.getOrNull()) && isDeviceSystemConnected(context, it) }
    }

    @SuppressLint("MissingPermission")
    fun findBondedXm6Devices(context: Context): List<BluetoothDevice> {
        if (!hasBluetoothConnectPermission(context)) return emptyList()
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
        val adapter: BluetoothAdapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        val bonded = adapter.bondedDevices.orEmpty()
        val xm6First = bonded.filter { isXm6DeviceName(it.name) }
        return if (xm6First.isNotEmpty()) xm6First else bonded.toList()
    }

    @SuppressLint("MissingPermission")
    fun findDeviceByMac(context: Context, mac: String): BluetoothDevice? {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        val adapter = manager.adapter ?: return null
        return runCatching { adapter.getRemoteDevice(mac) }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    fun connect(device: BluetoothDevice): RfcommTransport {
        val cachedUuids = runCatching { device.uuids?.map { it.uuid }.orEmpty() }.getOrDefault(emptyList())
        Log.d(TAG, "Device ${device.name} (${device.address}) cached UUIDs: $cachedUuids")

        val candidates = linkedSetOf<UUID>().apply {
            add(SONY_V2_UUID)
            // Include any non-standard 128-bit vendor UUIDs advertised by Sony
            cachedUuids.filter { uuid ->
                val s = uuid.toString().lowercase()
                !s.endsWith("-0000-1000-8000-00805f9b34fb") && (s.startsWith("956c7b26") || s.startsWith("96cc203e"))
            }.forEach { add(it) }
            add(SONY_V1_UUID)
        }

        var lastError: IOException? = null
        for (uuid in candidates) {
            Log.d(TAG, "Trying RFCOMM connect with UUID=$uuid")
            try {
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                Log.d(TAG, "RFCOMM connected (secure) with UUID=$uuid")
                return RfcommTransport(socket)
            } catch (secureErr: IOException) {
                lastError = secureErr
                Log.w(TAG, "Secure RFCOMM failed for $uuid: ${secureErr.message}")
                try {
                    val insecure = device.createInsecureRfcommSocketToServiceRecord(uuid)
                    insecure.connect()
                    Log.d(TAG, "RFCOMM connected (insecure) with UUID=$uuid")
                    return RfcommTransport(insecure)
                } catch (insecureErr: IOException) {
                    lastError = insecureErr
                    Log.w(TAG, "Insecure RFCOMM failed for $uuid: ${insecureErr.message}")
                }
            }
        }
        throw lastError ?: IOException("No compatible Sony MDR RFCOMM UUID found")
    }
}
