package com.xm6.app.data

import kotlinx.serialization.Serializable

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class AudioCodec(val label: String) {
    LDAC("LDAC"),
    AAC("AAC"),
    SBC("SBC"),
    LC3("LC3"),
    UNKNOWN("--")
}

enum class ConnectionQualityMode(val label: String) {
    SOUND_QUALITY("LDAC 音质"),
    STABLE_CONNECTION("AAC 稳定"),
    LE_AUDIO_LOW_LATENCY("LE 低延迟")
}

enum class LdacBitrateMode(val label: String, val shortLabel: String, val aospValue: Int) {
    QUALITY_990("990k", "990k", 1000),
    BALANCED_660("660k", "660k", 1001),
    ADAPTIVE_ABR("ABR", "ABR", 1003)
}

enum class NoiseControlMode(val label: String) {
    ANC("降噪"),
    AMBIENT("环境声"),
    OFF("关闭")
}

enum class AutoAmbientSensitivity(val label: String) {
    LOW("低"),
    STANDARD("中"),
    HIGH("高")
}

enum class ListeningMode(val label: String) {
    STANDARD("标准"),
    BACKGROUND_MUSIC("背景音乐"),
    CINEMA("电影")
}

enum class BgmRoomSize(val label: String) {
    MY_ROOM("房间"),
    LIVING_ROOM("客厅"),
    CAFE("咖啡馆")
}

enum class SpeakToChatTimeout(val label: String) {
    SHORT_5S("5s"),
    STANDARD_15S("15s"),
    LONG_30S("30s"),
    DO_NOT_CLOSE("不关")
}

enum class AutoPowerOffOption(val label: String) {
    WHEN_TAKEN_OFF("摘下"),
    MIN_15("15m"),
    MIN_30("30m"),
    HOUR_1("1h"),
    HOUR_3("3h"),
    OFF("关")
}

enum class NcAmbButtonCycle(val label: String) {
    NC_AND_AMBIENT("降噪 ↔ 环境"),
    NC_AMBIENT_OFF("三档")
}

@Serializable
data class EqPreset(
    val id: String,
    val name: String,
    val bands10: List<Int>,
    val builtIn: Boolean = false,
    val sonyPresetId: Int = 0xA0
)

data class ConnectedPeerDevice(
    val mac: String,
    val name: String,
    val isActivePlayback: Boolean
)

enum class ShizukuStatus(val label: String) {
    NOT_INSTALLED("未安装"),
    NOT_RUNNING("未启动"),
    PERMISSION_REQUIRED("待授权"),
    READY("已授权")
}

enum class AppThemeMode(val label: String) {
    LIGHT("浅色"),
    DARK("深色"),
    AUTO_TIME("随时间")
}

data class HeadphoneState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val errorMessage: String? = null,
    val macAddress: String? = null,
    val deviceName: String = "WH-1000XM6",
    val firmwareVersion: String = "--",
    val protocolVersion: String = "--",
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val activeCodec: AudioCodec = AudioCodec.UNKNOWN,
    val connectionQuality: ConnectionQualityMode = ConnectionQualityMode.SOUND_QUALITY,
    val noiseMode: NoiseControlMode = NoiseControlMode.ANC,
    val ambientLevel: Int = 14,
    val focusOnVoice: Boolean = false,
    val autoAmbientSound: Boolean = false,
    val autoAmbientSensitivity: AutoAmbientSensitivity = AutoAmbientSensitivity.STANDARD,
    val listeningMode: ListeningMode = ListeningMode.STANDARD,
    val bgmRoom: BgmRoomSize = BgmRoomSize.LIVING_ROOM,
    val dseeExtreme: Boolean = true,
    val dseeActive: Boolean = false,
    val speakToChat: Boolean = false,
    val speakToChatTimeout: SpeakToChatTimeout = SpeakToChatTimeout.STANDARD_15S,
    val touchSensor: Boolean = true,
    val pauseWhenTakenOff: Boolean = true,
    val autoPowerOff: AutoPowerOffOption = AutoPowerOffOption.WHEN_TAKEN_OFF,
    val ncAmbButtonCycle: NcAmbButtonCycle = NcAmbButtonCycle.NC_AND_AMBIENT,
    val multipointEnabled: Boolean = true,
    val connectedPeers: List<ConnectedPeerDevice> = emptyList(),
    val activeEqPresetId: String = "custom_1",
    val eqBands10: List<Int> = List(10) { 0 },
    val table1Capabilities: Set<Int> = emptySet(),
    val table2Capabilities: Set<Int> = emptySet(),
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    val systemCodecSummary: String? = null
) {
    val isConnected: Boolean
        get() = connectionState == ConnectionState.CONNECTED

    val isLeAudioConflict: Boolean
        get() = connectionQuality == ConnectionQualityMode.LE_AUDIO_LOW_LATENCY ||
            activeCodec == AudioCodec.LC3

    /**
     * WH-1000XM6 (FW 3.1.5) advertises Table 1 capability 0x25 (AUTO_POWER_OFF_WITH_WEARING_DETECTION)
     * without 0x24 (timed AUTO_POWER_OFF), supporting only "Off when taken off" (0x10) and "Do not turn off" (0x11).
     */
    val isAutoPowerOffWearingOnly: Boolean
        get() = if (table1Capabilities.isEmpty()) {
            true
        } else {
            (0x25 in table1Capabilities) && (0x24 !in table1Capabilities)
        }
}

data class UserPrefs(
    val boundMac: String? = null,
    val boundName: String? = null,
    val shizukuAutoLdac: Boolean = true,
    val ldacBitrateMode: LdacBitrateMode = LdacBitrateMode.QUALITY_990,
    val showExperimental: Boolean = true,
    val eqPresets: List<EqPreset> = Xm6Defaults.BUILTIN_EQ_PRESETS,
    val themeMode: AppThemeMode = AppThemeMode.AUTO_TIME
)

object Xm6Defaults {
    val EQ_BAND_LABELS = listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    val BUILTIN_EQ_PRESETS = listOf(
        EqPreset("off", "关闭", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), builtIn = false, sonyPresetId = 0x00),
        EqPreset("vocal", "人声", listOf(-1, -1, 0, 1, 2, 3, 2, 1, 0, 0), builtIn = false, sonyPresetId = 0xA0),
        EqPreset("bass", "低音", listOf(5, 4, 3, 1, 0, 0, 1, 1, 0, 0), builtIn = false, sonyPresetId = 0xA0),
        EqPreset("custom_1", "自定义 1", listOf(2, 1, 0, 0, -1, 0, 1, 2, 2, 1), builtIn = false, sonyPresetId = 0xA0),
        EqPreset("custom_2", "自定义 2", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), builtIn = false, sonyPresetId = 0xA1)
    )
}
