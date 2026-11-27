package com.xm6.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xm6.app.data.AppThemeMode
import com.xm6.app.data.ConnectionQualityMode
import com.xm6.app.system.CompanionAssociationHelper
import com.xm6.app.system.Xm6GlanceWidget
import com.xm6.app.ui.Xm6ViewModel
import com.xm6.app.ui.components.FloatingNavDock
import com.xm6.app.ui.components.MainTab
import com.xm6.app.ui.device.DeviceSettingsScreen
import com.xm6.app.ui.eq.EqScreen
import com.xm6.app.ui.home.HomeScreen
import com.xm6.app.ui.ldac.LdacAndDebugScreen
import com.xm6.app.ui.theme.ThemeResolver
import com.xm6.app.ui.theme.Xm6Theme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val xm6App: Xm6Application
        get() = application as Xm6Application

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: Xm6ViewModel = viewModel()
            val prefs by vm.userPrefs.collectAsStateWithLifecycle()
            var clockTick by remember { mutableIntStateOf(0) }

            // Re-evaluate automatically across 07:00 / 19:00 local time boundaries while App is open
            LaunchedEffect(prefs.themeMode) {
                if (prefs.themeMode == AppThemeMode.AUTO_TIME) {
                    while (true) {
                        delay(ThemeResolver.delayUntilNextBoundaryMillis() + 500L)
                        clockTick++
                        Xm6GlanceWidget.updateAllWidgets(applicationContext)
                    }
                }
            }

            // React immediately if user changes system clock or timezone
            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, i: Intent?) {
                        clockTick++
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }
                ContextCompat.registerReceiver(
                    this@MainActivity,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                onDispose {
                    runCatching { unregisterReceiver(receiver) }
                }
            }

            val isDark = remember(prefs.themeMode, clockTick) {
                ThemeResolver.resolveIsDark(prefs.themeMode)
            }

            LaunchedEffect(isDark) {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            Xm6Theme(darkTheme = isDark) {
                Xm6AppRoot(
                    isDarkTheme = isDark,
                    onOpenBluetoothSettings = { openBluetoothSettings() },
                    onOpenBatterySettings = { openAppBatterySettings() },
                    onOpenShizukuOrDownload = { openShizukuOrDownload() },
                    onCopyToClipboard = { label, text -> copyToClipboard(label, text) },
                    vm = vm
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        xm6App.repository.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        xm6App.repository.onAppBackground()
    }

    private fun openBluetoothSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    private fun openAppBatterySettings() {
        val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        runCatching { startActivity(appDetailsIntent) }
    }

    private fun openShizukuOrDownload() {
        val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launchIntent != null) {
            runCatching { startActivity(launchIntent) }
        } else {
            val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
            runCatching { startActivity(downloadIntent) }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

@Composable
private fun Xm6AppRoot(
    isDarkTheme: Boolean,
    onOpenBluetoothSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenShizukuOrDownload: () -> Unit,
    onCopyToClipboard: (label: String, text: String) -> Unit,
    vm: Xm6ViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.headphoneState.collectAsStateWithLifecycle()
    val prefs by vm.userPrefs.collectAsStateWithLifecycle()
    val presets by vm.allPresets.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val bondedDevices by vm.bondedDevices.collectAsStateWithLifecycle()
    val acoustic by vm.acousticSnapshot.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var isCompanionAssociated by remember {
        mutableStateOf(CompanionAssociationHelper.isAssociated(context, prefs.boundMac))
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isLifecycleStarted by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            vm.notifyUser("需要音频权限以读取播放频谱（不录制麦克风）")
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    isLifecycleStarted = true
                    hasAudioPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                }
                Lifecycle.Event.ON_STOP -> isLifecycleStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.setSpectrumActive(false)
        }
    }

    LaunchedEffect(isLifecycleStarted, selectedTab, hasAudioPermission, state.isConnected) {
        val shouldRun = isLifecycleStarted && selectedTab == MainTab.HOME && hasAudioPermission && state.isConnected
        vm.setSpectrumActive(shouldRun)
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.BLUETOOTH_CONNECT] == true
        if (granted) {
            vm.syncWithSystemBluetooth()
        } else {
            vm.notifyUser("需要蓝牙权限才能连接 WH-1000XM6")
        }
    }

    val companionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        isCompanionAssociated = CompanionAssociationHelper.isAssociated(context, state.macAddress ?: prefs.boundMac)
        val mac = state.macAddress ?: prefs.boundMac
        if (isCompanionAssociated && !mac.isNullOrBlank()) {
            CompanionAssociationHelper.startObservingPresence(context, mac)
            vm.notifyUser("伴生唤醒已绑定")
        }
    }

    // Request Android 12+ Bluetooth permission & auto-sync with live OS Bluetooth on startup
    LaunchedEffect(Unit) {
        val hasConnect = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        if (hasConnect) {
            vm.syncWithSystemBluetooth()
        } else {
            btPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
    }

    LaunchedEffect(state.macAddress, prefs.boundMac) {
        isCompanionAssociated = CompanionAssociationHelper.isAssociated(context, state.macAddress ?: prefs.boundMac)
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navTabs = remember {
        listOf(
            MainTab.HOME to Icons.Rounded.Headphones,
            MainTab.EQ to Icons.Rounded.Tune,
            MainTab.DEVICE to Icons.Rounded.Devices,
            MainTab.LDAC to Icons.Rounded.HighQuality
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        when (selectedTab) {
            MainTab.HOME -> HomeScreen(
                state = state,
                prefs = prefs,
                presets = presets,
                acoustic = acoustic,
                hasAudioPermission = hasAudioPermission,
                onRequestAudioPermission = {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onResetSplPeak = vm::resetSoundPressurePeak,
                onConnectClick = {
                    val hasConnect = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasConnect) {
                        vm.connectAuto()
                    } else {
                        btPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                            )
                        )
                    }
                },
                onCodecPillClick = { selectedTab = MainTab.LDAC },
                onFixLeAudioClick = { vm.setConnectionQuality(ConnectionQualityMode.SOUND_QUALITY) },
                onSelectNoiseMode = vm::selectNoiseMode,
                onAmbientLevelChange = vm::setAmbientLevelDebounced,
                onToggleFocusOnVoice = vm::toggleFocusOnVoice,
                onToggleAutoAmbient = vm::toggleAutoAmbient,
                onSelectAutoAmbientSensitivity = vm::selectAutoAmbientSensitivity,
                onSelectListeningMode = vm::selectListeningMode,
                onSelectBgmRoom = vm::selectBgmRoom,
                onToggleDsee = vm::toggleDsee,
                onToggleSpeakToChat = vm::toggleSpeakToChat,
                onCycleSpeakTimeout = vm::cycleSpeakToChatTimeout,
                onToggleTouchSensor = vm::toggleTouchSensor,
                onSelectEqPreset = vm::selectEqPreset,
                onOpenEqTab = { selectedTab = MainTab.EQ }
            )

            MainTab.EQ -> EqScreen(
                state = state,
                presets = presets,
                onSelectPreset = vm::selectEqPreset,
                onBandChange = vm::updateEqBand,
                onResetFlat = vm::resetEqFlat,
                onUpdateExistingPreset = vm::updateExistingPreset,
                onSaveCustomPreset = vm::saveCustomPreset,
                onDeleteCustomPreset = vm::deleteCustomPreset,
                onRestoreDefaults = vm::restoreDefaultPresets
            )

            MainTab.DEVICE -> DeviceSettingsScreen(
                state = state,
                themeMode = prefs.themeMode,
                isDarkTheme = isDarkTheme,
                bondedDevices = bondedDevices,
                isCompanionAssociated = isCompanionAssociated,
                onSelectThemeMode = vm::setThemeMode,
                onToggleMultipoint = vm::setMultipointEnabled,
                onSwitchPeer = vm::switchPeer,
                onDisconnectPeer = vm::disconnectPeer,
                onSelectNcCycle = vm::setNcAmbCycle,
                onToggleWearingPause = vm::setWearingPause,
                onSelectAutoPowerOff = vm::setAutoPowerOff,
                onConnectDevice = vm::connectDevice,
                onAssociateCompanion = {
                    CompanionAssociationHelper.startAssociation(
                        context = context,
                        onIntentSender = { sender ->
                            companionLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        },
                        onFailure = { err -> vm.notifyUser(err) }
                    )
                },
                onOpenBatterySettings = onOpenBatterySettings
            )

            MainTab.LDAC -> LdacAndDebugScreen(
                state = state,
                prefs = prefs,
                logs = logs,
                onSelectConnectionQuality = vm::setConnectionQuality,
                onToggleShizukuAuto = vm::setShizukuAutoLdac,
                onSelectBitrate = vm::setLdacBitrate,
                onRequestShizukuPermission = vm::requestShizukuPermission,
                onOpenShizukuOrDownload = onOpenShizukuOrDownload,
                onApplyLdacNow = vm::applyLdacNow,
                onOpenBluetoothSettings = onOpenBluetoothSettings,
                onToggleShowExperimental = vm::setShowExperimental,
                onCopyCapabilities = {
                    onCopyToClipboard("XM6 Capabilities", vm.buildCapabilityDumpText())
                    vm.notifyUser("已复制能力表")
                },
                onCopyLogs = {
                    val dump = logs.joinToString("\n") { "${it.direction} ${it.table} ${it.label}: ${it.hex}" }
                    onCopyToClipboard("XM6 Protocol Logs", dump)
                    vm.notifyUser("已复制 ${logs.size} 条协议日志")
                },
                onClearLogs = vm::clearLogs,
                onSendRawHex = vm::sendRawHex
            )
        }

        // Snackbar positioned cleanly above floating dock
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 86.dp, start = 16.dp, end = 16.dp)
        )

        // Floating Material 3 Expressive Navigation Dock
        FloatingNavDock(
            selectedTab = selectedTab,
            tabs = navTabs,
            onSelect = { tab ->
                selectedTab = tab
                if (tab == MainTab.DEVICE) vm.refreshBondedDevices()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        )
    }
}
