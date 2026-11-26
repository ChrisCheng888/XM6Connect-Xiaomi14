package com.xm6.app.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xm6.app.Xm6Application
import com.xm6.app.data.AppThemeMode
import com.xm6.app.data.AutoAmbientSensitivity
import com.xm6.app.data.AutoPowerOffOption
import com.xm6.app.data.BgmRoomSize
import com.xm6.app.data.ConnectionQualityMode
import com.xm6.app.data.EqPreset
import com.xm6.app.data.HeadphoneState
import com.xm6.app.data.LdacBitrateMode
import com.xm6.app.data.ListeningMode
import com.xm6.app.data.NcAmbButtonCycle
import com.xm6.app.data.NoiseControlMode
import com.xm6.app.data.SpeakToChatTimeout
import com.xm6.app.data.UserPrefs
import com.xm6.app.data.Xm6Defaults
import com.xm6.app.protocol.ProtocolLogEntry
import com.xm6.app.protocol.RfcommConnector
import com.xm6.app.system.Xm6GlanceWidget
import com.xm6.app.ui.theme.ThemeScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class Xm6ViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as Xm6Application
    private val repo = app.repository
    private val prefsStore = app.preferencesStore

    val headphoneState: StateFlow<HeadphoneState> = repo.state
    val logs: StateFlow<List<ProtocolLogEntry>> = repo.logs

    val userPrefs: StateFlow<UserPrefs> = prefsStore.prefsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPrefs()
    )

    val allPresets: StateFlow<List<EqPreset>> = prefsStore.prefsFlow
        .map { it.eqPresets }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Xm6Defaults.BUILTIN_EQ_PRESETS
        )

    private val _bondedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BluetoothDevice>> = _bondedDevices.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var ambientDebounceJob: Job? = null
    private var eqDebounceJob: Job? = null

    fun refreshBondedDevices() {
        _bondedDevices.value = RfcommConnector.findBondedXm6Devices(app)
    }

    fun connectAuto() {
        viewModelScope.launch {
            refreshBondedDevices()
            repo.connectAuto()
        }
    }

    fun connectDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            val ok = repo.connect(device)
            if (!ok) _messages.tryEmit("连接失败，请确认未开启官方 Sound Connect")
        }
    }

    fun selectNoiseMode(mode: NoiseControlMode) {
        ambientDebounceJob?.cancel()
        repo.setNoiseControl(mode)
    }

    fun setAmbientLevelDebounced(level: Int) {
        val clamped = level.coerceIn(1, 20)
        // Immediate 120Hz UI state update; debounce RFCOMM write
        repo.setNoiseControl(NoiseControlMode.AMBIENT, ambientLevel = clamped, dispatchToHeadset = false)
        ambientDebounceJob?.cancel()
        ambientDebounceJob = viewModelScope.launch {
            delay(85L)
            repo.setNoiseControl(NoiseControlMode.AMBIENT, ambientLevel = clamped, dispatchToHeadset = true)
        }
    }

    fun toggleFocusOnVoice() {
        ambientDebounceJob?.cancel()
        val s = headphoneState.value
        repo.setNoiseControl(s.noiseMode, s.ambientLevel, !s.focusOnVoice)
    }

    fun toggleAutoAmbient() {
        ambientDebounceJob?.cancel()
        val s = headphoneState.value
        repo.setAutoAmbientSound(!s.autoAmbientSound, s.autoAmbientSensitivity)
    }

    fun selectAutoAmbientSensitivity(sensitivity: AutoAmbientSensitivity) {
        ambientDebounceJob?.cancel()
        repo.setAutoAmbientSound(true, sensitivity)
    }

    fun selectListeningMode(mode: ListeningMode) {
        repo.setListeningMode(mode)
    }

    fun selectBgmRoom(room: BgmRoomSize) {
        repo.setListeningMode(ListeningMode.BACKGROUND_MUSIC, room)
    }

    fun toggleDsee() = repo.setDseeExtreme(!headphoneState.value.dseeExtreme)

    fun toggleSpeakToChat() = repo.setSpeakToChat(!headphoneState.value.speakToChat)

    fun cycleSpeakToChatTimeout() {
        val opts = SpeakToChatTimeout.entries
        val next = opts[(opts.indexOf(headphoneState.value.speakToChatTimeout) + 1) % opts.size]
        repo.setSpeakToChatTimeout(next)
    }

    fun toggleTouchSensor() = repo.setTouchSensor(!headphoneState.value.touchSensor)

    fun selectEqPreset(preset: EqPreset) {
        repo.setEqualizer10Band(preset.id, preset.sonyPresetId, preset.bands10)
    }

    fun updateEqBand(index: Int, db: Int) {
        val current = headphoneState.value.eqBands10.toMutableList()
        if (index !in current.indices) return
        current[index] = db.coerceIn(-6, 6)
        val activeId = headphoneState.value.activeEqPresetId
        val activePreset = allPresets.value.firstOrNull { it.id == activeId }
        val targetSonyId = if (activePreset?.sonyPresetId == 0xA1) 0xA1 else 0xA0
        repo.setEqualizer10Band(activeId, targetSonyId, current, dispatchToHeadset = false)
        eqDebounceJob?.cancel()
        eqDebounceJob = viewModelScope.launch {
            delay(110L)
            repo.setEqualizer10Band(activeId, targetSonyId, current, dispatchToHeadset = true)
        }
    }

    fun resetEqFlat() {
        val flat = List(10) { 0 }
        val activeId = headphoneState.value.activeEqPresetId
        val sonyId = if (activeId == "off") 0x00 else 0xA0
        repo.setEqualizer10Band(activeId, sonyId, flat)
        _messages.tryEmit("已重置平坦曲线")
    }

    fun updateExistingPreset(presetId: String, newName: String) {
        val existing = allPresets.value.firstOrNull { it.id == presetId }
        val bands = headphoneState.value.eqBands10
        val isFlat = bands.all { it == 0 }
        val sonyId = when {
            existing?.sonyPresetId == 0x00 && isFlat -> 0x00
            existing?.sonyPresetId == 0xA1 -> 0xA1
            else -> 0xA0
        }
        val updated = EqPreset(
            id = presetId,
            name = newName,
            bands10 = bands,
            builtIn = false,
            sonyPresetId = sonyId
        )
        viewModelScope.launch {
            prefsStore.saveOrUpdatePreset(updated)
            repo.setEqualizer10Band(updated.id, updated.sonyPresetId, updated.bands10)
            _messages.tryEmit("已更新「$newName」")
        }
    }

    fun saveCustomPreset(name: String) {
        val preset = EqPreset(
            id = "preset_${System.currentTimeMillis()}",
            name = name,
            bands10 = headphoneState.value.eqBands10,
            builtIn = false,
            sonyPresetId = 0xA0
        )
        viewModelScope.launch {
            prefsStore.saveOrUpdatePreset(preset)
            repo.setEqualizer10Band(preset.id, preset.sonyPresetId, preset.bands10)
            _messages.tryEmit("已保存「$name」")
        }
    }

    fun deleteCustomPreset(presetId: String) {
        viewModelScope.launch {
            prefsStore.deletePreset(presetId)
            if (headphoneState.value.activeEqPresetId == presetId) {
                val remaining = allPresets.value.filterNot { it.id == presetId }
                val fallback = remaining.firstOrNull()
                if (fallback != null) {
                    repo.setEqualizer10Band(fallback.id, fallback.sonyPresetId, headphoneState.value.eqBands10, dispatchToHeadset = false)
                } else {
                    repo.setEqualizer10Band("", 0xA0, headphoneState.value.eqBands10, dispatchToHeadset = false)
                }
            }
            _messages.tryEmit("已删除预设")
        }
    }

    fun restoreDefaultPresets() {
        viewModelScope.launch {
            prefsStore.restoreDefaultPresets()
            _messages.tryEmit("已恢复默认预设")
        }
    }

    fun setMultipointEnabled(enabled: Boolean) = repo.setMultipointEnabled(enabled)
    fun switchPeer(mac: String) = repo.switchMultipointPeer(mac)
    fun disconnectPeer(mac: String) = repo.disconnectMultipointPeer(mac)
    fun setNcAmbCycle(cycle: NcAmbButtonCycle) = repo.setNcAmbButtonCycle(cycle)
    fun setWearingPause(enabled: Boolean) = repo.setWearingPause(enabled)
    fun setAutoPowerOff(option: AutoPowerOffOption) = repo.setAutoPowerOff(option)

    fun setConnectionQuality(mode: ConnectionQualityMode) {
        repo.setConnectionQuality(mode)
        _messages.tryEmit("连接模式 · ${mode.label}")
    }

    fun setShizukuAutoLdac(enabled: Boolean) {
        viewModelScope.launch { prefsStore.setShizukuAutoLdac(enabled) }
    }

    fun setLdacBitrate(mode: LdacBitrateMode) {
        viewModelScope.launch { prefsStore.setLdacBitrate(mode) }
    }

    fun setShowExperimental(show: Boolean) {
        viewModelScope.launch { prefsStore.setShowExperimental(show) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            prefsStore.setThemeMode(mode)
            ThemeScheduler.syncSchedule(app, mode)
            Xm6GlanceWidget.updateAllWidgets(app)
        }
    }

    fun requestShizukuPermission() {
        if (!app.shizukuManager.requestPermission()) {
            _messages.tryEmit("请先启动 Shizuku 服务")
        }
    }

    fun applyLdacNow() {
        if (headphoneState.value.isLeAudioConflict) {
            _messages.tryEmit("当前处于 LE Audio，请先切回 LDAC 音质")
            return
        }
        viewModelScope.launch {
            val res = repo.applyShizukuLdac(userPrefs.value.ldacBitrateMode)
            _messages.tryEmit(res)
        }
    }

    fun sendRawHex(table: Int, hex: String) {
        viewModelScope.launch {
            val ok = repo.sendRawHexPayload(table, hex)
            _messages.tryEmit(if (ok) "帧已发送 (ACK)" else "发送失败或格式错误")
        }
    }

    fun clearLogs() = repo.clearLogs()
    fun buildCapabilityDumpText(): String = repo.buildCapabilityDumpText()
    fun notifyUser(msg: String) { _messages.tryEmit(msg) }
}
