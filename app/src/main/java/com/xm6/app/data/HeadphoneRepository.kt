package com.xm6.app.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.xm6.app.ldac.LdacShizukuManager
import com.xm6.app.protocol.ArqSession
import com.xm6.app.protocol.EncodedCommand
import com.xm6.app.protocol.MdrDataType
import com.xm6.app.protocol.MdrFrameCodec
import com.xm6.app.protocol.ProtocolLogEntry
import com.xm6.app.protocol.RfcommConnector
import com.xm6.app.protocol.Xm6Event
import com.xm6.app.protocol.Xm6ProtocolV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class HeadphoneRepository(
    private val context: Context,
    val preferencesStore: UserPreferencesStore,
    val shizukuManager: LdacShizukuManager,
    private val onStateBroadcast: suspend (HeadphoneState) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()

    private val _state = MutableStateFlow(HeadphoneState())
    val state: StateFlow<HeadphoneState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<ProtocolLogEntry>>(emptyList())
    val logs: StateFlow<List<ProtocolLogEntry>> = _logs.asStateFlow()

    private var session: ArqSession? = null
    private var frameCollectorJob: Job? = null
    private var idleDisconnectJob: Job? = null
    private var isForeground: Boolean = false
    @Volatile
    private var verifiedV2: Boolean = false
    @Volatile
    private var cinemaModeEnabled: Boolean = false
    @Volatile
    private var bgmModeEnabled: Boolean = false
    @Volatile
    private var pendingListeningMode: ListeningMode? = null
    @Volatile
    private var pendingBgmRoom: BgmRoomSize? = null
    @Volatile
    private var listeningModeGuardUntilMs: Long = 0L

    init {
        scope.launch {
            shizukuManager.status.collect { st ->
                _state.update { it.copy(shizukuStatus = st) }
            }
        }
    }

    fun onAppForeground() {
        isForeground = true
        idleDisconnectJob?.cancel()
        shizukuManager.refreshStatus()
    }

    fun onAppBackground() {
        isForeground = false
        scheduleIdleDisconnect()
    }

    private fun scheduleIdleDisconnect(delayMs: Long = 60_000L) {
        idleDisconnectJob?.cancel()
        idleDisconnectJob = scope.launch {
            delay(delayMs)
            if (!isForeground) {
                enterIdleSleep()
            }
        }
    }

    /**
     * Releases the RFCOMM control socket after background inactivity to save battery,
     * while preserving the last synced telemetry so the 2x2 desktop widget doesn't flash offline
     * while the user is still wearing the headphones.
     */
    private suspend fun enterIdleSleep() = connectMutex.withLock {
        closeSessionInternal()
    }

    @SuppressLint("MissingPermission")
    suspend fun connectAuto(): Boolean {
        val prefs = preferencesStore.prefsFlow.first()
        val device = prefs.boundMac?.let { RfcommConnector.findDeviceByMac(context, it) }
            ?: RfcommConnector.findBondedXm6Devices(context).firstOrNull()
        if (device == null) {
            _state.update {
                it.copy(
                    connectionState = ConnectionState.ERROR,
                    errorMessage = "未找到已配对的 WH-1000XM6"
                )
            }
            return false
        }
        return connect(device)
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = connectMutex.withLock {
        if (session != null && _state.value.isConnected && _state.value.macAddress == device.address) {
            return@withLock true
        }
        closeSessionInternal()
        verifiedV2 = false
        pendingListeningMode = null
        pendingBgmRoom = null
        listeningModeGuardUntilMs = 0L

        val displayName = runCatching { device.name }.getOrNull().orEmpty().ifBlank { "WH-1000XM6" }
        _state.update {
            it.copy(
                connectionState = ConnectionState.CONNECTING,
                errorMessage = null,
                macAddress = device.address,
                deviceName = displayName
            )
        }

        val transport = try {
            withContext(Dispatchers.IO) { RfcommConnector.connect(device) }
        } catch (t: Throwable) {
            appendLog("SYS", "RFCOMM Connect Failed", t.message ?: "IOException")
            _state.update {
                it.copy(
                    connectionState = ConnectionState.ERROR,
                    errorMessage = "连接失败（请确认已关闭官方 Sound Connect）"
                )
            }
            return@withLock false
        }

        val newSession = ArqSession(
            transport = transport,
            scope = scope,
            onLog = { entry -> appendLogEntry(entry) },
            onDisconnect = { err ->
                scope.launch {
                    handleSessionDropped(err)
                }
            }
        )
        session = newSession
        frameCollectorJob = scope.launch {
            newSession.incomingFrames.collect { frame ->
                Xm6ProtocolV2.decodeFrame(frame)?.let { applyEvent(it) }
            }
        }
        newSession.start()

        preferencesStore.setBoundDevice(device.address, displayName)
        _state.update {
            it.copy(
                connectionState = ConnectionState.CONNECTED,
                errorMessage = null
            )
        }

        runHandshakeAndSync()
        broadcastWidgetState()
        true
    }

    private suspend fun runHandshakeAndSync() {
        // Step 1: Query Protocol Info FIRST before sending V2-specific opcodes like 0x22 (battery)
        sendInternal(Xm6ProtocolV2.buildGetProtocolInfo())
        delay(250L)

        sendInternal(Xm6ProtocolV2.buildGetModelName())
        sendInternal(Xm6ProtocolV2.buildGetFirmwareVersion())
        sendInternal(Xm6ProtocolV2.buildGetSupportFunctions(table = 1))
        sendInternal(Xm6ProtocolV2.buildGetSupportFunctions(table = 2))

        // Only send V2 battery opcode 0x22 when protocol >= V2 (or device name confirms WH-1000XM6)
        val isKnownXm6 = _state.value.deviceName.contains("XM6", ignoreCase = true) ||
            _state.value.deviceName.contains("XM5", ignoreCase = true) ||
            _state.value.deviceName.contains("XM4", ignoreCase = true)
        if (verifiedV2 || isKnownXm6) {
            sendInternal(Xm6ProtocolV2.buildGetBattery())
        }

        sendInternal(Xm6ProtocolV2.buildGetAudioCodec())
        sendInternal(Xm6ProtocolV2.buildGetNoiseControl())
        sendInternal(Xm6ProtocolV2.buildGetEqualizer())
        sendInternal(Xm6ProtocolV2.buildGetConnectionQuality())
        sendInternal(Xm6ProtocolV2.buildGetDsee())
        sendInternal(Xm6ProtocolV2.buildGetDseeStatus())
        sendInternal(Xm6ProtocolV2.buildGetCinemaMode())
        sendInternal(Xm6ProtocolV2.buildGetBgmMode())
        sendInternal(Xm6ProtocolV2.buildGetAutoPowerOff(withWearingDetection = true))
        sendInternal(Xm6ProtocolV2.buildGetSystemParam(0x0C, "SpeakToChat"))
        sendInternal(Xm6ProtocolV2.buildGetSpeakToChatExt())
        sendInternal(Xm6ProtocolV2.buildGetTouchSensor())
        sendInternal(Xm6ProtocolV2.buildGetSystemParam(0x05, "WearingPause"))
        sendInternal(Xm6ProtocolV2.buildGetMultipointDevices())
        sendInternal(Xm6ProtocolV2.buildGetMultipointEnabled())

        // Query system codec summary from Shizuku if ready
        val sysCodec = shizukuManager.querySystemCodecSummary(_state.value.macAddress)
        if (!sysCodec.isNullOrBlank() && !sysCodec.startsWith("ERR:")) {
            _state.update { it.copy(systemCodecSummary = sysCodec) }
            broadcastWidgetState()
        }
    }

    suspend fun disconnect() = connectMutex.withLock {
        closeSessionInternal()
        _state.update { it.copy(connectionState = ConnectionState.DISCONNECTED, errorMessage = null) }
        broadcastWidgetState()
    }

    private fun handleSessionDropped(err: Throwable?) {
        if (session == null) return
        appendLog("SYS", "RFCOMM Disconnected", err?.message ?: "closed")
        closeSessionInternal()
        _state.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
        scope.launch { broadcastWidgetState() }
    }

    private fun closeSessionInternal() {
        frameCollectorJob?.cancel()
        frameCollectorJob = null
        session?.close()
        session = null
    }

    // =========================================================================
    // High-level Controls
    // =========================================================================

    fun setNoiseControl(
        mode: NoiseControlMode,
        ambientLevel: Int = _state.value.ambientLevel,
        focusOnVoice: Boolean = _state.value.focusOnVoice,
        dispatchToHeadset: Boolean = true
    ) {
        val clamped = ambientLevel.coerceIn(1, 20)
        val autoAmb = _state.value.autoAmbientSound
        val autoSens = _state.value.autoAmbientSensitivity
        _state.update {
            it.copy(
                noiseMode = mode,
                ambientLevel = clamped,
                focusOnVoice = focusOnVoice,
                autoAmbientSound = autoAmb
            )
        }
        if (dispatchToHeadset) {
            scope.launch {
                sendInternal(
                    Xm6ProtocolV2.buildSetNoiseControl(
                        mode = mode,
                        ambientLevel = clamped,
                        focusOnVoice = focusOnVoice,
                        autoAmbientSound = autoAmb,
                        autoAmbientSensitivity = autoSens,
                        commitChange = true
                    )
                )
                broadcastWidgetState()
            }
        }
    }

    fun setAutoAmbientSound(enabled: Boolean, sensitivity: AutoAmbientSensitivity = _state.value.autoAmbientSensitivity) {
        val s = _state.value
        _state.update { it.copy(autoAmbientSound = enabled, autoAmbientSensitivity = sensitivity) }
        scope.launch {
            sendInternal(
                Xm6ProtocolV2.buildSetNoiseControl(
                    mode = NoiseControlMode.AMBIENT,
                    ambientLevel = s.ambientLevel,
                    focusOnVoice = s.focusOnVoice,
                    autoAmbientSound = enabled,
                    autoAmbientSensitivity = sensitivity,
                    commitChange = true
                )
            )
        }
    }

    fun setListeningMode(mode: ListeningMode, room: BgmRoomSize = _state.value.bgmRoom) {
        val current = _state.value
        if (current.listeningMode == mode && (mode != ListeningMode.BACKGROUND_MUSIC || current.bgmRoom == room)) {
            return
        }
        val wasCinema = cinemaModeEnabled || current.listeningMode == ListeningMode.CINEMA
        val wasBgm = bgmModeEnabled || current.listeningMode == ListeningMode.BACKGROUND_MUSIC
        cinemaModeEnabled = (mode == ListeningMode.CINEMA)
        bgmModeEnabled = (mode == ListeningMode.BACKGROUND_MUSIC)
        pendingListeningMode = mode
        pendingBgmRoom = room
        listeningModeGuardUntilMs = System.currentTimeMillis() + 450L
        _state.update { it.copy(listeningMode = mode, bgmRoom = room) }
        scope.launch {
            val cmds = Xm6ProtocolV2.buildSetListeningModeCommands(
                targetMode = mode,
                bgmRoom = room,
                fromCinema = wasCinema,
                fromBgm = wasBgm
            )
            cmds.forEach { cmd -> sendInternal(cmd) }
        }
    }

    fun setEqualizer10Band(
        presetId: String,
        sonyPresetId: Int,
        bands10: List<Int>,
        dispatchToHeadset: Boolean = true
    ) {
        val normalized = List(10) { idx -> bands10.getOrElse(idx) { 0 }.coerceIn(-6, 6) }
        _state.update { it.copy(activeEqPresetId = presetId, eqBands10 = normalized) }
        if (dispatchToHeadset) {
            scope.launch {
                sendInternal(Xm6ProtocolV2.buildSetEqualizer10Band(sonyPresetId, normalized))
            }
        }
    }

    fun setConnectionQuality(mode: ConnectionQualityMode) {
        _state.update {
            it.copy(
                connectionQuality = mode,
                activeCodec = when (mode) {
                    ConnectionQualityMode.SOUND_QUALITY -> AudioCodec.LDAC
                    ConnectionQualityMode.STABLE_CONNECTION -> AudioCodec.AAC
                    ConnectionQualityMode.LE_AUDIO_LOW_LATENCY -> AudioCodec.LC3
                }
            )
        }
        scope.launch {
            sendInternal(Xm6ProtocolV2.buildSetConnectionQuality(mode))
            broadcastWidgetState()
        }
    }

    fun setDseeExtreme(enabled: Boolean) {
        _state.update { it.copy(dseeExtreme = enabled) }
        scope.launch { sendInternal(Xm6ProtocolV2.buildSetDsee(enabled)) }
    }

    fun setSpeakToChat(enabled: Boolean) {
        _state.update { it.copy(speakToChat = enabled) }
        scope.launch { sendInternal(Xm6ProtocolV2.buildSetSpeakToChat(enabled)) }
    }

    fun setSpeakToChatTimeout(timeout: SpeakToChatTimeout) {
        _state.update { it.copy(speakToChatTimeout = timeout) }
        scope.launch { sendInternal(Xm6ProtocolV2.buildSetSpeakToChatTimeout(timeout)) }
    }

    fun setTouchSensor(enabled: Boolean) {
        _state.update { it.copy(touchSensor = enabled) }
        scope.launch { sendInternal(Xm6ProtocolV2.buildSetTouchSensor(enabled)) }
    }

    fun setWearingPause(enabled: Boolean) {
        _state.update { it.copy(pauseWhenTakenOff = enabled) }
        scope.launch { sendInternal(Xm6ProtocolV2.buildSetWearingPause(enabled)) }
    }

    fun setAutoPowerOff(option: AutoPowerOffOption) {
        val wearingOnly = _state.value.isAutoPowerOffWearingOnly
        _state.update { it.copy(autoPowerOff = option) }
        scope.launch {
            sendInternal(Xm6ProtocolV2.buildSetAutoPowerOff(option, useWearingDetectionSubtype = wearingOnly))
        }
    }

    fun setNcAmbButtonCycle(cycle: NcAmbButtonCycle) {
        _state.update { it.copy(ncAmbButtonCycle = cycle) }
        scope.launch { sendInternal(Xm6ProtocolV2.buildSetNcAmbButtonCycle(cycle)) }
    }

    fun setMultipointEnabled(enabled: Boolean) {
        _state.update { it.copy(multipointEnabled = enabled) }
        scope.launch { sendInternal(Xm6ProtocolV2.buildSetMultipointEnabled(enabled)) }
    }

    fun switchMultipointPeer(mac: String) {
        _state.update { st ->
            st.copy(
                connectedPeers = st.connectedPeers.map { it.copy(isActivePlayback = it.mac == mac) }
            )
        }
        scope.launch { sendInternal(Xm6ProtocolV2.buildSwitchMultipointDevice(mac)) }
    }

    fun disconnectMultipointPeer(mac: String) {
        _state.update { st ->
            st.copy(connectedPeers = st.connectedPeers.filterNot { it.mac == mac })
        }
        scope.launch { sendInternal(Xm6ProtocolV2.buildDisconnectMultipointDevice(mac)) }
    }

    suspend fun applyShizukuLdac(mode: LdacBitrateMode): String {
        val mac = _state.value.macAddress ?: preferencesStore.prefsFlow.first().boundMac
        val result = shizukuManager.applyLdacBitrate(mac, mode)
        appendLog("SYS", "Shizuku LDAC", result)
        val summary = shizukuManager.querySystemCodecSummary(mac)
        if (!summary.isNullOrBlank() && !summary.startsWith("ERR:")) {
            _state.update { it.copy(systemCodecSummary = summary) }
        } else if (!result.startsWith("ERR:")) {
            _state.update { it.copy(systemCodecSummary = "LDAC · ${mode.shortLabel}") }
        }
        broadcastWidgetState()
        return result
    }

    /**
     * Used by Quick Settings Tile / Glance Widget / CompanionService when the app UI is closed.
     */
    suspend fun executeTransientNoiseMode(targetMode: NoiseControlMode? = null): HeadphoneState {
        if (targetMode != null) {
            // Optimistic UI feedback for immediate widget highlight
            _state.update { it.copy(noiseMode = targetMode) }
            broadcastWidgetState()
        }
        if (session == null || !_state.value.isConnected) {
            connectAuto()
        }
        val current = _state.value
        if (current.isConnected) {
            val next = targetMode ?: when (current.ncAmbButtonCycle) {
                NcAmbButtonCycle.NC_AND_AMBIENT -> {
                    if (current.noiseMode == NoiseControlMode.ANC) NoiseControlMode.AMBIENT else NoiseControlMode.ANC
                }
                NcAmbButtonCycle.NC_AMBIENT_OFF -> when (current.noiseMode) {
                    NoiseControlMode.ANC -> NoiseControlMode.AMBIENT
                    NoiseControlMode.AMBIENT -> NoiseControlMode.OFF
                    NoiseControlMode.OFF -> NoiseControlMode.ANC
                }
            }
            _state.update { it.copy(noiseMode = next) }
            sendInternal(
                Xm6ProtocolV2.buildSetNoiseControl(
                    mode = next,
                    ambientLevel = current.ambientLevel,
                    focusOnVoice = current.focusOnVoice,
                    autoAmbientSound = current.autoAmbientSound,
                    autoAmbientSensitivity = current.autoAmbientSensitivity,
                    commitChange = true
                )
            )
            broadcastWidgetState()
            if (!isForeground) {
                scheduleIdleDisconnect(delayMs = 15_000L)
            }
        }
        return _state.value
    }

    suspend fun sendRawHexPayload(table: Int, hexPayload: String): Boolean {
        val bytes = MdrFrameCodec.parseHexString(hexPayload) ?: return false
        val dt = if (table == 2) MdrDataType.DATA_MDR_NO2 else MdrDataType.DATA_MDR
        return sendInternal(EncodedCommand(dt, bytes, "RawHex(T$table)"))
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun buildCapabilityDumpText(): String {
        val s = _state.value
        val t1 = s.table1Capabilities.sorted().joinToString(", ") { "0x%02X".format(it) }
        val t2 = s.table2Capabilities.sorted().joinToString(", ") { "0x%02X".format(it) }
        return buildString {
            appendLine("Device: ${s.deviceName} (FW ${s.firmwareVersion}, Protocol ${s.protocolVersion})")
            appendLine("Table 1 (0x0C) Capabilities [${s.table1Capabilities.size}]: $t1")
            appendLine("Table 2 (0x0E) Capabilities [${s.table2Capabilities.size}]: $t2")
        }
    }

    private suspend fun sendInternal(cmd: EncodedCommand): Boolean {
        val activeSession = session ?: return false
        return activeSession.sendCommand(cmd.dataType, cmd.payload, cmd.label)
    }

    private fun applyEvent(event: Xm6Event) {
        _state.update { s ->
            when (event) {
                is Xm6Event.ProtocolInfo -> {
                    if (event.versionMajor >= 2) verifiedV2 = true
                    s.copy(protocolVersion = "${event.versionMajor}.${event.versionMinor}")
                }
                is Xm6Event.DeviceModelName -> s.copy(deviceName = event.name)
                is Xm6Event.FirmwareVersion -> s.copy(firmwareVersion = event.version)
                is Xm6Event.SupportFunctions -> if (event.table == 1) {
                    s.copy(table1Capabilities = event.functionIds)
                } else {
                    s.copy(table2Capabilities = event.functionIds)
                }
                is Xm6Event.BatteryUpdate -> s.copy(batteryLevel = event.level, isCharging = event.charging)
                is Xm6Event.CodecUpdate -> s.copy(activeCodec = event.codec)
                is Xm6Event.NoiseControlUpdate -> s.copy(
                    noiseMode = event.mode,
                    ambientLevel = event.ambientLevel,
                    focusOnVoice = event.focusOnVoice,
                    autoAmbientSound = event.autoAmbientSound ?: s.autoAmbientSound,
                    autoAmbientSensitivity = event.autoAmbientSensitivity ?: s.autoAmbientSensitivity
                )
                is Xm6Event.AutoAmbientUpdate -> s.copy(
                    autoAmbientSound = event.enabled,
                    autoAmbientSensitivity = event.sensitivity
                )
                is Xm6Event.EqualizerUpdate -> s.copy(eqBands10 = event.bands10)
                is Xm6Event.ConnectionQualityUpdate -> s.copy(connectionQuality = event.mode)
                is Xm6Event.DseeUpdate -> s.copy(dseeExtreme = event.enabled)
                is Xm6Event.DseeStatusUpdate -> s.copy(dseeActive = event.active)
                is Xm6Event.SpeakToChatUpdate -> s.copy(speakToChat = event.enabled)
                is Xm6Event.SpeakToChatConfigUpdate -> s.copy(speakToChatTimeout = event.timeout)
                is Xm6Event.TouchSensorUpdate -> s.copy(touchSensor = event.enabled)
                is Xm6Event.WearingPauseUpdate -> s.copy(pauseWhenTakenOff = event.enabled)
                is Xm6Event.AutoPowerOffUpdate -> s.copy(autoPowerOff = event.option)
                is Xm6Event.NcButtonCycleUpdate -> s.copy(ncAmbButtonCycle = event.cycle)
                is Xm6Event.MultipointEnabledUpdate -> s.copy(multipointEnabled = event.enabled)
                is Xm6Event.MultipointDevicesUpdate -> s.copy(connectedPeers = event.devices)
                is Xm6Event.CinemaModeUpdate -> {
                    cinemaModeEnabled = event.enabled
                    val resolved = when {
                        cinemaModeEnabled -> ListeningMode.CINEMA
                        bgmModeEnabled -> ListeningMode.BACKGROUND_MUSIC
                        else -> ListeningMode.STANDARD
                    }
                    val guarded = resolveGuardedListeningMode(resolved, s.bgmRoom)
                    s.copy(listeningMode = guarded.first)
                }
                is Xm6Event.BgmModeUpdate -> {
                    bgmModeEnabled = event.enabled
                    val resolved = when {
                        bgmModeEnabled -> ListeningMode.BACKGROUND_MUSIC
                        cinemaModeEnabled -> ListeningMode.CINEMA
                        else -> ListeningMode.STANDARD
                    }
                    val guarded = resolveGuardedListeningMode(resolved, event.room)
                    s.copy(listeningMode = guarded.first, bgmRoom = guarded.second)
                }
                is Xm6Event.ListeningModeUpdate -> s.copy(listeningMode = event.mode, bgmRoom = event.bgmRoom)
            }
        }
        scope.launch { broadcastWidgetState() }
    }

    private fun resolveGuardedListeningMode(
        resolvedMode: ListeningMode,
        reportedRoom: BgmRoomSize
    ): Pair<ListeningMode, BgmRoomSize> {
        val pendingMode = pendingListeningMode
        val pendingRoom = pendingBgmRoom
        if (pendingMode != null && System.currentTimeMillis() < listeningModeGuardUntilMs) {
            val modeMatches = (resolvedMode == pendingMode)
            val roomMatches = (pendingMode != ListeningMode.BACKGROUND_MUSIC || pendingRoom == null || reportedRoom == pendingRoom)
            if (modeMatches && roomMatches) {
                return resolvedMode to reportedRoom
            }
            return pendingMode to (pendingRoom ?: reportedRoom)
        }
        pendingListeningMode = null
        pendingBgmRoom = null
        return resolvedMode to reportedRoom
    }

    private suspend fun broadcastWidgetState() {
        runCatching { onStateBroadcast(_state.value) }
    }

    private fun appendLog(table: String, label: String, info: String) {
        appendLogEntry(
            ProtocolLogEntry(
                timestampMs = System.currentTimeMillis(),
                direction = "SYS",
                table = table,
                label = label,
                hex = info
            )
        )
    }

    private fun appendLogEntry(entry: ProtocolLogEntry) {
        android.util.Log.d("XM6Proto", "${entry.direction} [${entry.table}] ${entry.label} | ${entry.hex}")
        _logs.update { current ->
            (listOf(entry) + current).take(300)
        }
    }
}
