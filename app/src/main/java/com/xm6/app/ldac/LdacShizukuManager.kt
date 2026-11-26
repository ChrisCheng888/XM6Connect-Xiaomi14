package com.xm6.app.ldac

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.AttributionSource
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.Process
import android.util.Log
import com.xm6.app.data.LdacBitrateMode
import com.xm6.app.data.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Invokes Android's privileged IBluetoothA2dp Binder directly via [ShizukuBinderWrapper]
 * using the app's own BluetoothA2dp proxy, avoiding UserService Context issues on Android 14-16.
 */
class LdacShizukuManager(private val context: Context) {

    companion object {
        private const val TAG = "XM6Proto"
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 10042
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }

    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshStatus() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refreshStatus() }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            refreshStatus()
        }
    }

    init {
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/bluetooth/",
                "Landroid/content/",
                "Lcom/android/modules/utils/"
            )
        }
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }
        refreshStatus()
    }

    fun refreshStatus(): ShizukuStatus {
        val next = try {
            if (!Shizuku.pingBinder()) {
                if (isShizukuInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
            } else if (Shizuku.isPreV11()) {
                ShizukuStatus.NOT_RUNNING
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.READY
            } else {
                ShizukuStatus.PERMISSION_REQUIRED
            }
        } catch (_: Throwable) {
            if (isShizukuInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
        }
        _status.value = next
        return next
    }

    fun requestPermission(): Boolean {
        if (refreshStatus() != ShizukuStatus.PERMISSION_REQUIRED) return false
        return runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    suspend fun applyLdacBitrate(macAddress: String?, mode: LdacBitrateMode): String =
        withContext(Dispatchers.IO) {
            if (refreshStatus() != ShizukuStatus.READY) {
                return@withContext "ERR: Shizuku ${status.value.label}"
            }
            val btManager = context.getSystemService(BluetoothManager::class.java)
                ?: return@withContext "ERR: BluetoothManager unavailable"
            val adapter = btManager.adapter
                ?: return@withContext "ERR: BluetoothAdapter unavailable"
            val a2dp = acquireA2dpProxy(adapter)
                ?: return@withContext "ERR: 无法获取 BluetoothA2dp 代理"

            try {
                val device = resolveTargetDevice(a2dp, adapter, macAddress)
                    ?: return@withContext "ERR: 未检测到已连接的 A2DP 耳机"

                val privilegedIface = getPrivilegedA2dpInterface(a2dp)
                    ?: return@withContext "ERR: 无法包装 IBluetoothA2dp Binder"

                val codecConfig = buildLdacCodecConfig(mode.aospValue)
                    ?: return@withContext "ERR: 无法构造 BluetoothCodecConfig"

                invokeSetCodecConfigPreference(privilegedIface, device, codecConfig)
                Log.d(TAG, "Shizuku setCodecConfigPreference OK -> LDAC ${mode.label} (${device.name})")
                "已锁定 LDAC ${mode.label}"
            } catch (t: Throwable) {
                val rootMsg = t.cause?.message ?: t.message ?: t.javaClass.simpleName
                Log.e(TAG, "Shizuku applyLdacBitrate failed", t)
                "ERR: $rootMsg"
            } finally {
                runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp) }
            }
        }

    @SuppressLint("MissingPermission")
    suspend fun querySystemCodecSummary(macAddress: String?): String? =
        withContext(Dispatchers.IO) {
            if (refreshStatus() != ShizukuStatus.READY) return@withContext null
            val btManager = context.getSystemService(BluetoothManager::class.java) ?: return@withContext null
            val adapter = btManager.adapter ?: return@withContext null
            val a2dp = acquireA2dpProxy(adapter) ?: return@withContext null
            try {
                val device = resolveTargetDevice(a2dp, adapter, macAddress) ?: return@withContext null
                val privilegedIface = getPrivilegedA2dpInterface(a2dp) ?: return@withContext null
                val codecStatus = invokeGetCodecStatus(privilegedIface, device) ?: return@withContext null
                val cfg = codecStatus.javaClass.getMethod("getCodecConfig").invoke(codecStatus)
                    ?: return@withContext null
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
                Log.w(TAG, "querySystemCodecSummary failed: ${t.cause?.message ?: t.message}")
                null
            } finally {
                runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp) }
            }
        }

    private fun getPrivilegedA2dpInterface(a2dp: BluetoothA2dp): Any? {
        // Extract raw IBluetoothA2dp proxy from BluetoothA2dp
        val rawInterface: Any? = runCatching {
            val m = BluetoothA2dp::class.java.getDeclaredMethod("getService")
            m.isAccessible = true
            m.invoke(a2dp)
        }.getOrNull() ?: runCatching {
            val f = BluetoothA2dp::class.java.getDeclaredField("mService")
            f.isAccessible = true
            f.get(a2dp)
        }.getOrNull() ?: runCatching {
            val f = BluetoothA2dp::class.java.getDeclaredField("mProfileConnector")
            f.isAccessible = true
            val connector = f.get(a2dp)
            connector?.javaClass?.getDeclaredMethod("getService")?.apply { isAccessible = true }?.invoke(connector)
        }.getOrNull()

        val rawBinder: IBinder = when (rawInterface) {
            is IInterface -> rawInterface.asBinder()
            is IBinder -> rawInterface
            else -> {
                Log.e(TAG, "Could not locate IBluetoothA2dp inside BluetoothA2dp (fields=${BluetoothA2dp::class.java.declaredFields.map { it.name }})")
                return null
            }
        }

        val wrappedBinder = ShizukuBinderWrapper(rawBinder)
        val stubClass = Class.forName("android.bluetooth.IBluetoothA2dp\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, wrappedBinder)
    }

    private fun buildShellAttributionSource(): AttributionSource {
        return AttributionSource.Builder(Process.SHELL_UID)
            .setPackageName("com.android.shell")
            .build()
    }

    private fun invokeSetCodecConfigPreference(
        iA2dp: Any,
        device: BluetoothDevice,
        codecConfig: Any
    ) {
        val methods = iA2dp.javaClass.methods.filter { it.name == "setCodecConfigPreference" }
        Log.d(TAG, "IBluetoothA2dp.setCodecConfigPreference overloads: ${methods.map { it.parameterTypes.map { c -> c.simpleName } }}")

        val attrSource = buildShellAttributionSource()
        val method = methods.firstOrNull()
            ?: throw NoSuchMethodException("IBluetoothA2dp.setCodecConfigPreference not found")

        val params = method.parameterTypes
        when (params.size) {
            2 -> method.invoke(iA2dp, device, codecConfig)
            3 -> {
                if (params[2].name.contains("AttributionSource")) {
                    method.invoke(iA2dp, device, codecConfig, attrSource)
                } else {
                    method.invoke(iA2dp, device, codecConfig, null)
                }
            }
            4 -> {
                val recv = createSynchronousResultReceiver()
                method.invoke(iA2dp, device, codecConfig, attrSource, recv)
                awaitReceiver(recv)
            }
            else -> throw IllegalStateException("Unsupported setCodecConfigPreference arity=${params.size}")
        }
    }

    private fun invokeGetCodecStatus(iA2dp: Any, device: BluetoothDevice): Any? {
        val method = iA2dp.javaClass.methods.firstOrNull { it.name == "getCodecStatus" } ?: return null
        val params = method.parameterTypes
        val attrSource = buildShellAttributionSource()
        return when (params.size) {
            1 -> method.invoke(iA2dp, device)
            2 -> method.invoke(iA2dp, device, attrSource)
            3 -> {
                val recv = createSynchronousResultReceiver()
                method.invoke(iA2dp, device, attrSource, recv)
                awaitReceiver(recv)
            }
            else -> null
        }
    }

    private fun createSynchronousResultReceiver(): Any {
        val cls = Class.forName("com.android.modules.utils.SynchronousResultReceiver")
        return cls.getMethod("get").invoke(null)
    }

    private fun awaitReceiver(receiver: Any): Any? {
        val result = receiver.javaClass
            .getMethod("awaitResultNoInterrupt", Duration::class.java)
            .invoke(receiver, Duration.ofSeconds(3))
        return result?.javaClass?.getMethod("getValue", Any::class.java)?.invoke(result, null)
    }

    @SuppressLint("MissingPermission")
    private fun resolveTargetDevice(
        a2dp: BluetoothA2dp,
        adapter: BluetoothAdapter,
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

    private fun acquireA2dpProxy(adapter: BluetoothAdapter): BluetoothA2dp? {
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
            adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
        } else {
            Handler(mainLooper).post {
                adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
            }
        }
        latch.await(2500L, TimeUnit.MILLISECONDS)
        return proxyRef.get()
    }

    private fun buildLdacCodecConfig(bitrateMode: Int): Any? {
        val codecTypeLdac = 4
        val codecPriorityHighest = 1_000_000
        val configClass = Class.forName("android.bluetooth.BluetoothCodecConfig")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val builderClass = Class.forName("android.bluetooth.BluetoothCodecConfig\$Builder")
            val builder = builderClass.getDeclaredConstructor().newInstance()
            builderClass.getMethod("setCodecType", Int::class.javaPrimitiveType)
                .invoke(builder, codecTypeLdac)
            builderClass.getMethod("setCodecPriority", Int::class.javaPrimitiveType)
                .invoke(builder, codecPriorityHighest)
            builderClass.getMethod("setSampleRate", Int::class.javaPrimitiveType)
                .invoke(builder, 0)
            builderClass.getMethod("setBitsPerSample", Int::class.javaPrimitiveType)
                .invoke(builder, 0)
            builderClass.getMethod("setChannelMode", Int::class.javaPrimitiveType)
                .invoke(builder, 0)
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
                0,
                0,
                0,
                bitrateMode.toLong(),
                0L,
                0L,
                0L
            )
        }
    }

    private fun isShizukuInstalled(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }
}
