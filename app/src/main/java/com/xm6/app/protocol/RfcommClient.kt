package com.xm6.app.protocol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
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

    @SuppressLint("MissingPermission")
    fun findBondedXm6Devices(context: Context): List<BluetoothDevice> {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
        val adapter: BluetoothAdapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        val bonded = adapter.bondedDevices.orEmpty()
        val xm6First = bonded.filter {
            val name = it.name.orEmpty()
            name.contains("WH-1000XM6", ignoreCase = true) ||
                name.contains("WH-1000X", ignoreCase = true) ||
                name.contains("Sony", ignoreCase = true)
        }
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
