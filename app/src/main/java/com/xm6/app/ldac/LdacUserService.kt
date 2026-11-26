package com.xm6.app.ldac

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

/**
 * Runs inside Shizuku's privileged `shell` UID process to invoke hidden A2DP codec APIs
 * (`BluetoothA2dp.setCodecConfigPreference`) without root.
 */
@Keep
class LdacUserService : ILdacService.Stub {

    private val context: Context?

    @Keep
    constructor() {
        this.context = null
        exemptHiddenApis()
    }

    @Keep
    constructor(context: Context) {
        this.context = context
        exemptHiddenApis()
    }

    private fun exemptHiddenApis() {
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    @SuppressLint("MissingPermission")
    override fun applyLdacConfig(
        macAddress: String?,
        bitrateMode: Int,
        sampleRate: Int,
        bitsPerSample: Int
    ): String {
        val ctx = context ?: return "ERR: Missing Shizuku Context"
        val btManager = ctx.getSystemService(BluetoothManager::class.java)
            ?: return "ERR: BluetoothManager unavailable"
        val adapter = btManager.adapter ?: return "ERR: BluetoothAdapter unavailable"

        val a2dp = acquireA2dpProxy(ctx, adapter) ?: return "ERR: Unable to bind BluetoothA2dp"
        return try {
            val device = resolveTargetDevice(a2dp, adapter, macAddress)
                ?: return "ERR: No connected A2DP device"

            val codecConfig = buildLdacCodecConfig(
                bitrateMode = bitrateMode,
                sampleRate = sampleRate,
                bitsPerSample = bitsPerSample
            ) ?: return "ERR: Failed to construct BluetoothCodecConfig"

            val setMethod = BluetoothA2dp::class.java.getMethod(
                "setCodecConfigPreference",
                BluetoothDevice::class.java,
                Class.forName("android.bluetooth.BluetoothCodecConfig")
            )
            setMethod.isAccessible = true
            setMethod.invoke(a2dp, device, codecConfig)

            val modeName = when (bitrateMode) {
                1000 -> "990kbps"
                1001 -> "660kbps"
                1002 -> "330kbps"
                else -> "ABR"
            }
            "OK: LDAC $modeName (${device.name ?: device.address})"
        } catch (t: Throwable) {
            "ERR: ${t.cause?.message ?: t.message ?: t.javaClass.simpleName}"
        } finally {
            runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp) }
        }
    }

    @SuppressLint("MissingPermission")
    override fun getCurrentCodecSummary(macAddress: String?): String {
        val ctx = context ?: return "ERR: Missing Context"
        val btManager = ctx.getSystemService(BluetoothManager::class.java) ?: return "--"
        val adapter = btManager.adapter ?: return "--"
        val a2dp = acquireA2dpProxy(ctx, adapter) ?: return "--"
        return try {
            val device = resolveTargetDevice(a2dp, adapter, macAddress) ?: return "未连接 A2DP"
            val getStatus = BluetoothA2dp::class.java.getMethod("getCodecStatus", BluetoothDevice::class.java)
            val status = getStatus.invoke(a2dp, device) ?: return "--"
            val getCfg = status.javaClass.getMethod("getCodecConfig")
            val cfg = getCfg.invoke(status) ?: return "--"
            val codecType = (cfg.javaClass.getMethod("getCodecType").invoke(cfg) as? Int) ?: -1
            val specific1 = (cfg.javaClass.getMethod("getCodecSpecific1").invoke(cfg) as? Long) ?: 0L
            val codecName = when (codecType) {
                0 -> "SBC"
                1 -> "AAC"
                2 -> "aptX"
                3 -> "aptX HD"
                4 -> "LDAC"
                else -> "Codec($codecType)"
            }
            if (codecType == 4) {
                val br = when (specific1) {
                    1000L -> "990k"
                    1001L -> "660k"
                    1002L -> "330k"
                    else -> "ABR"
                }
                "$codecName · $br"
            } else {
                codecName
            }
        } catch (t: Throwable) {
            "ERR: ${t.cause?.message ?: t.message}"
        } finally {
            runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveTargetDevice(
        a2dp: BluetoothA2dp,
        adapter: android.bluetooth.BluetoothAdapter,
        macAddress: String?
    ): BluetoothDevice? {
        if (!macAddress.isNullOrBlank()) {
            val matched = a2dp.connectedDevices.firstOrNull {
                it.address.equals(macAddress, ignoreCase = true)
            }
            if (matched != null) return matched
            return runCatching { adapter.getRemoteDevice(macAddress) }.getOrNull()
        }
        return a2dp.connectedDevices.firstOrNull()
    }

    private fun acquireA2dpProxy(
        ctx: Context,
        adapter: android.bluetooth.BluetoothAdapter
    ): BluetoothA2dp? {
        val latch = CountDownLatch(1)
        val proxyRef = AtomicReference<BluetoothA2dp?>(null)
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP && proxy is BluetoothA2dp) {
                    proxyRef.set(proxy)
                }
                latch.countDown()
            }

            override fun onServiceDisconnected(profile: Int) {
                proxyRef.set(null)
            }
        }

        val mainLooper = Looper.getMainLooper()
        if (Looper.myLooper() == mainLooper) {
            adapter.getProfileProxy(ctx, listener, BluetoothProfile.A2DP)
        } else {
            Handler(mainLooper).post {
                adapter.getProfileProxy(ctx, listener, BluetoothProfile.A2DP)
            }
        }
        latch.await(2500L, TimeUnit.MILLISECONDS)
        return proxyRef.get()
    }

    private fun buildLdacCodecConfig(
        bitrateMode: Int,
        sampleRate: Int,
        bitsPerSample: Int
    ): Any? {
        val codecTypeLdac = 4
        val codecPriorityHighest = 1_000_000
        val effectiveRate = if (sampleRate == 0) 0x0 else sampleRate
        val effectiveBits = if (bitsPerSample == 0) 0x0 else bitsPerSample
        val channelModeNone = 0x0

        val configClass = Class.forName("android.bluetooth.BluetoothCodecConfig")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val builderClass = Class.forName("android.bluetooth.BluetoothCodecConfig\$Builder")
            val builder = builderClass.getDeclaredConstructor().newInstance()
            builderClass.getMethod("setCodecType", Int::class.javaPrimitiveType)
                .invoke(builder, codecTypeLdac)
            builderClass.getMethod("setCodecPriority", Int::class.javaPrimitiveType)
                .invoke(builder, codecPriorityHighest)
            builderClass.getMethod("setSampleRate", Int::class.javaPrimitiveType)
                .invoke(builder, effectiveRate)
            builderClass.getMethod("setBitsPerSample", Int::class.javaPrimitiveType)
                .invoke(builder, effectiveBits)
            builderClass.getMethod("setChannelMode", Int::class.javaPrimitiveType)
                .invoke(builder, channelModeNone)
            builderClass.getMethod("setCodecSpecific1", Long::class.javaPrimitiveType)
                .invoke(builder, bitrateMode.toLong())
            builderClass.getMethod("build").invoke(builder)
        } else {
            val ctor = configClass.getConstructor(
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            )
            ctor.newInstance(
                codecTypeLdac,
                codecPriorityHighest,
                effectiveRate,
                effectiveBits,
                channelModeNone,
                bitrateMode.toLong(),
                0L,
                0L,
                0L
            )
        }
    }
}
