package com.xm6.app.protocol

import com.xm6.app.data.AudioCodec
import com.xm6.app.data.AutoAmbientSensitivity
import com.xm6.app.data.AutoPowerOffOption
import com.xm6.app.data.BgmRoomSize
import com.xm6.app.data.ConnectedPeerDevice
import com.xm6.app.data.ConnectionQualityMode
import com.xm6.app.data.ListeningMode
import com.xm6.app.data.NcAmbButtonCycle
import com.xm6.app.data.NoiseControlMode
import com.xm6.app.data.SpeakToChatTimeout

/**
 * Structured events decoded from Sony MDR Protocol V2/V3 frames (Table 1 & Table 2)
 * calibrated against real Sony WH-1000XM6 (YY2984, Firmware 3.1.5).
 */
sealed interface Xm6Event {
    data class ProtocolInfo(val versionMajor: Int, val versionMinor: Int) : Xm6Event
    data class DeviceModelName(val name: String) : Xm6Event
    data class FirmwareVersion(val version: String) : Xm6Event
    data class SupportFunctions(val table: Int, val functionIds: Set<Int>) : Xm6Event
    data class BatteryUpdate(val level: Int, val charging: Boolean) : Xm6Event
    data class CodecUpdate(val codec: AudioCodec) : Xm6Event
    data class NoiseControlUpdate(
        val mode: NoiseControlMode,
        val ambientLevel: Int,
        val focusOnVoice: Boolean,
        val autoAmbientSound: Boolean? = null,
        val autoAmbientSensitivity: AutoAmbientSensitivity? = null,
        val pausedOffHead: Boolean = false
    ) : Xm6Event
    data class AutoAmbientUpdate(
        val enabled: Boolean,
        val sensitivity: AutoAmbientSensitivity
    ) : Xm6Event
    data class EqualizerUpdate(
        val presetId: Int,
        val bands10: List<Int>
    ) : Xm6Event
    data class ConnectionQualityUpdate(val mode: ConnectionQualityMode) : Xm6Event
    data class DseeUpdate(val enabled: Boolean) : Xm6Event
    data class DseeStatusUpdate(val active: Boolean) : Xm6Event
    data class SpeakToChatUpdate(val enabled: Boolean) : Xm6Event
    data class SpeakToChatConfigUpdate(val timeout: SpeakToChatTimeout) : Xm6Event
    data class TouchSensorUpdate(val enabled: Boolean) : Xm6Event
    data class WearingPauseUpdate(val enabled: Boolean) : Xm6Event
    data class WearingStatusUpdate(val isWorn: Boolean) : Xm6Event
    data class AutoPowerOffUpdate(val option: AutoPowerOffOption) : Xm6Event
    data class NcButtonCycleUpdate(val cycle: NcAmbButtonCycle) : Xm6Event
    data class MultipointEnabledUpdate(val enabled: Boolean) : Xm6Event
    data class MultipointDevicesUpdate(val devices: List<ConnectedPeerDevice>) : Xm6Event
    data class CinemaModeUpdate(val enabled: Boolean) : Xm6Event
    data class BgmModeUpdate(val enabled: Boolean, val room: BgmRoomSize) : Xm6Event
    data class ListeningModeUpdate(val mode: ListeningMode, val bgmRoom: BgmRoomSize) : Xm6Event
}

data class EncodedCommand(
    val dataType: MdrDataType,
    val payload: ByteArray,
    val label: String
)

/**
 * Sony MDR Protocol V2/V3 encoder & decoder calibrated for WH-1000XM6 (YY2984).
 */
object Xm6ProtocolV2 {

    // ===== Table 1 (0x0C) Command IDs =====
    const val CMD_CONNECT_GET_PROTOCOL_INFO: Byte = 0x00
    const val CMD_CONNECT_RET_PROTOCOL_INFO: Byte = 0x01
    const val CMD_CONNECT_GET_DEVICE_INFO: Byte = 0x04
    const val CMD_CONNECT_RET_DEVICE_INFO: Byte = 0x05
    const val CMD_CONNECT_GET_SUPPORT_FUNCTION: Byte = 0x06
    const val CMD_CONNECT_RET_SUPPORT_FUNCTION: Byte = 0x07

    const val CMD_COMMON_GET_AUDIO_CODEC: Byte = 0x18
    const val CMD_COMMON_RET_AUDIO_CODEC: Byte = 0x19
    const val CMD_COMMON_NTFY_AUDIO_CODEC: Byte = 0x1B

    // Verified LIVE on WH-1000XM6 (POWER family 0x20..0x29):
    // Battery: 22 00 -> 23 00 [level] [charging]
    // Auto Power Off: 20 04 -> 21 04 05 04 01 02 03 11, 26 04 -> 27 04 [active] [select]
    // Auto Power Off (Wearing Detection): 20 05 -> 21 05 02 10 11, 26 05 -> 27 05 [active] [select]
    const val CMD_POWER_GET_CAPABILITY: Byte = 0x20
    const val CMD_POWER_RET_CAPABILITY: Byte = 0x21
    const val CMD_COMMON_GET_BATTERY: Byte = 0x22
    const val CMD_COMMON_RET_BATTERY: Byte = 0x23
    const val CMD_COMMON_NTFY_BATTERY: Byte = 0x25
    const val CMD_POWER_GET_PARAM: Byte = 0x26
    const val CMD_POWER_RET_PARAM: Byte = 0x27
    const val CMD_POWER_SET_PARAM: Byte = 0x28
    const val CMD_POWER_NTFY_PARAM: Byte = 0x29

    // Verified LIVE on WH-1000XM6: 56 00 -> 57 00 A0 0A [10 bands 0..12]
    const val CMD_EQ_GET_PARAM: Byte = 0x56
    const val CMD_EQ_RET_PARAM: Byte = 0x57
    const val CMD_EQ_SET_PARAM: Byte = 0x58
    const val CMD_EQ_NTFY_PARAM: Byte = 0x59

    // Verified LIVE on WH-1000XM6: 66/67/68/69 with subtype 0x19
    const val CMD_NCASM_GET_PARAM: Byte = 0x66
    const val CMD_NCASM_RET_PARAM: Byte = 0x67
    const val CMD_NCASM_SET_PARAM: Byte = 0x68
    const val CMD_NCASM_NTFY_PARAM: Byte = 0x69

    // WH-1000XM6 Audio / DSEE / Connection Priority / Listening Mode family (Table 1 0xE0..0xE9)
    const val CMD_AUDIO_GET_STATUS: Byte = 0xE2.toByte()
    const val CMD_AUDIO_RET_STATUS: Byte = 0xE3.toByte()
    const val CMD_AUDIO_NTFY_STATUS: Byte = 0xE5.toByte()
    const val CMD_AUDIO_GET_PARAM: Byte = 0xE6.toByte()
    const val CMD_AUDIO_RET_PARAM: Byte = 0xE7.toByte()
    const val CMD_AUDIO_SET_PARAM: Byte = 0xE8.toByte()
    const val CMD_AUDIO_NTFY_PARAM: Byte = 0xE9.toByte()

    // WH-1000XM6 System family (Table 1 0xF0..0xFD)
    const val CMD_SYSTEM_GET_STATUS: Byte = 0xF2.toByte()
    const val CMD_SYSTEM_RET_STATUS: Byte = 0xF3.toByte()
    const val CMD_SYSTEM_NTFY_STATUS: Byte = 0xF5.toByte()
    const val CMD_SYSTEM_GET_PARAM: Byte = 0xF6.toByte()
    const val CMD_SYSTEM_RET_PARAM: Byte = 0xF7.toByte()
    const val CMD_SYSTEM_SET_PARAM: Byte = 0xF8.toByte()
    const val CMD_SYSTEM_NTFY_PARAM: Byte = 0xF9.toByte()
    const val CMD_SYSTEM_GET_EXTENDED_PARAM: Byte = 0xFA.toByte()
    const val CMD_SYSTEM_RET_EXTENDED_PARAM: Byte = 0xFB.toByte()
    const val CMD_SYSTEM_SET_EXTENDED_PARAM: Byte = 0xFC.toByte()
    const val CMD_SYSTEM_NTFY_EXTENDED_PARAM: Byte = 0xFD.toByte()

    // ===== Table 2 (0x0E) Command IDs (Verified LIVE on WH-1000XM6 FW 3.1.5) =====
    // 36 00 -> 37 00 [count] [mac17, connected, nameLen, name...] ; 36 01 -> 37 01 [multipointOn]
    const val CMD_T2_PERI_GET_PARAM: Byte = 0x36
    const val CMD_T2_PERI_RET_PARAM: Byte = 0x37
    const val CMD_T2_PERI_SET_PARAM: Byte = 0x38
    const val CMD_T2_PERI_NTFY_PARAM: Byte = 0x39

    const val CMD_T2_LISTENING_GET: Byte = 0xE2.toByte()
    const val CMD_T2_LISTENING_RET: Byte = 0xE3.toByte()
    const val CMD_T2_LISTENING_SET: Byte = 0xE4.toByte()
    const val CMD_T2_LISTENING_NTFY: Byte = 0xE5.toByte()

    // Inquired sub-types
    private const val DEVICE_INFO_MODEL_NAME: Byte = 0x01
    private const val DEVICE_INFO_FW_VERSION: Byte = 0x02

    // WH-1000XM6 native NC/ASM + Auto Ambient Sound subtype
    const val NCASM_TYPE_XM6: Byte = 0x19
    private const val NCASM_TYPE_LEGACY_V2: Byte = 0x17

    private const val EQ_TYPE_CUSTOM_BAND: Byte = 0x00
    const val EQ_PRESET_OFF: Int = 0x00
    const val EQ_PRESET_CUSTOM_1: Int = 0xA0
    const val EQ_PRESET_CUSTOM_2: Int = 0xA1

    private const val AUDIO_TYPE_CONNECTION_MODE: Byte = 0x00
    private const val AUDIO_TYPE_UPSCALING_DSEE: Byte = 0x01
    private const val AUDIO_TYPE_CONNECTION_MODE_LE: Byte = 0x02
    // Verified LIVE on WH-1000XM6 FW 3.1.5 (Sony EnableDisable polarity: 0x00 = ENABLE, 0x01 = DISABLE):
    // - 0x04 = Cinema mode (E8 04 00 = ON, E8 04 01 = OFF)
    // - 0x09 = Background Music mode (E8 09 00 [room] = ON, E8 09 01 [room] = OFF)
    private const val AUDIO_TYPE_CINEMA_MODE: Byte = 0x04
    private const val AUDIO_TYPE_BGM_MODE: Byte = 0x09

    // Verified LIVE on WH-1000XM6 (PowerInquiredType)
    const val POWER_TYPE_BATTERY: Byte = 0x00
    const val POWER_TYPE_AUTO_POWER_OFF: Byte = 0x04
    const val POWER_TYPE_AUTO_POWER_OFF_WITH_WEARING_DETECTION: Byte = 0x05

    // Verified LIVE on WH-1000XM6 via 20 04 (04,01,02,03,11) & 20 05 (10,11)
    private const val APO_ELEMENT_5_MIN: Byte = 0x00
    private const val APO_ELEMENT_30_MIN: Byte = 0x01
    private const val APO_ELEMENT_60_MIN: Byte = 0x02
    private const val APO_ELEMENT_180_MIN: Byte = 0x03
    private const val APO_ELEMENT_15_MIN: Byte = 0x04
    private const val APO_ELEMENT_WHEN_TAKEN_OFF: Byte = 0x10
    private const val APO_ELEMENT_DISABLE: Byte = 0x11

    // Verified LIVE on WH-1000XM6 FW 3.1.5:
    // - 0x0C (SMART_TALKING_MODE_TYPE2): F6 0C -> F7 0C [preview=00] [onOff=00/01], F8 0C 00 [00/01]
    // - 0x0D (SMART_TALKING_MODE_TYPE2_EXT): F6 0D -> F7 0D [sens] [flag] [timeout]
    // - 0x0F (TOUCH_SENSOR): F6 0F -> F7 0F [00/01], F8 0F [00/01] -> F9 0F [00/01]
    private const val SYS_TYPE_SPEAK_TO_CHAT_TYPE1: Byte = 0x02
    private const val SYS_TYPE_ASSIGNABLE_BUTTON: Byte = 0x03
    private const val SYS_TYPE_WEARING_PAUSE: Byte = 0x05
    private const val SYS_TYPE_SPEAK_TO_CHAT_XM6: Byte = 0x0C
    private const val SYS_TYPE_SPEAK_TO_CHAT_EXT: Byte = 0x0D
    private const val SYS_TYPE_TOUCH_SENSOR: Byte = 0x0F
    // Verified LIVE on WH-1000XM6 FW 3.1.5: F2 10 -> F3/F5 10 01 [00=WORN, 01=NOT_WORN]
    private const val SYS_TYPE_WEARING_STATUS: Byte = 0x10

    // Table 2 0x36/0x37/0x38/0x39 Peripheral / Multipoint (verified LIVE on XM6)
    private const val PERI_TYPE_CONNECTED_DEVICES: Byte = 0x00
    private const val PERI_TYPE_MULTIPOINT_SWITCH: Byte = 0x01
    private const val PERI_TYPE_SWITCH_PLAYBACK: Byte = 0x02
    private const val PERI_TYPE_DISCONNECT_PEER: Byte = 0x03

    // =========================================================================
    // Handshake & Query Builders
    // =========================================================================

    fun buildGetProtocolInfo() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_CONNECT_GET_PROTOCOL_INFO, 0x00),
        "GetProtocolInfo"
    )

    fun buildGetModelName() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_CONNECT_GET_DEVICE_INFO, DEVICE_INFO_MODEL_NAME),
        "GetModelName"
    )

    fun buildGetFirmwareVersion() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_CONNECT_GET_DEVICE_INFO, DEVICE_INFO_FW_VERSION),
        "GetFirmwareVersion"
    )

    fun buildGetSupportFunctions(table: Int) = EncodedCommand(
        if (table == 2) MdrDataType.DATA_MDR_NO2 else MdrDataType.DATA_MDR,
        byteArrayOf(CMD_CONNECT_GET_SUPPORT_FUNCTION, 0x00),
        "GetSupportFunctions(T$table)"
    )

    fun buildGetBattery() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_COMMON_GET_BATTERY, 0x00),
        "GetBattery(V2)"
    )

    fun buildGetAudioCodec() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_COMMON_GET_AUDIO_CODEC, 0x00),
        "GetAudioCodec"
    )

    fun buildGetNoiseControl() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_NCASM_GET_PARAM, NCASM_TYPE_XM6),
        "GetNoiseControl(XM6 0x19)"
    )

    fun buildGetEqualizer() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_EQ_GET_PARAM, EQ_TYPE_CUSTOM_BAND),
        "GetEqualizer"
    )

    fun buildGetConnectionQuality() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_AUDIO_GET_PARAM, AUDIO_TYPE_CONNECTION_MODE),
        "GetConnectionQuality(E6 00)"
    )

    fun buildGetDsee() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_AUDIO_GET_PARAM, AUDIO_TYPE_UPSCALING_DSEE),
        "GetDSEE(E6 01)"
    )

    fun buildGetDseeStatus() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_AUDIO_GET_STATUS, AUDIO_TYPE_UPSCALING_DSEE),
        "GetDSEEStatus(E2 01)"
    )

    fun buildGetCinemaMode() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_AUDIO_GET_PARAM, AUDIO_TYPE_CINEMA_MODE),
        "GetCinemaMode(E6 04)"
    )

    fun buildGetBgmMode() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_AUDIO_GET_PARAM, AUDIO_TYPE_BGM_MODE),
        "GetBgmMode(E6 09)"
    )

    fun buildGetSpeakToChatExt() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_SYSTEM_GET_EXTENDED_PARAM, SYS_TYPE_SPEAK_TO_CHAT_XM6),
        "GetSpeakToChatExt(FA 0C)"
    )

    fun buildGetTouchSensor() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_SYSTEM_GET_PARAM, SYS_TYPE_TOUCH_SENSOR),
        "GetTouchSensor(F6 0F)"
    )

    fun buildGetWearingStatus() = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_SYSTEM_GET_STATUS, SYS_TYPE_WEARING_STATUS),
        "GetWearingStatus(F2 10)"
    )

    fun buildGetMultipointEnabled() = EncodedCommand(
        MdrDataType.DATA_MDR_NO2,
        byteArrayOf(CMD_T2_PERI_GET_PARAM, PERI_TYPE_MULTIPOINT_SWITCH),
        "GetMultipoint(T2 36 01)"
    )

    fun buildGetAutoPowerOff(withWearingDetection: Boolean = false) = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(
            CMD_POWER_GET_PARAM,
            if (withWearingDetection) POWER_TYPE_AUTO_POWER_OFF_WITH_WEARING_DETECTION else POWER_TYPE_AUTO_POWER_OFF
        ),
        if (withWearingDetection) "GetAutoPowerOffWearing(26 05)" else "GetAutoPowerOff(26 04)"
    )

    fun buildGetSystemParam(subType: Byte, name: String) = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_SYSTEM_GET_PARAM, subType),
        "Get$name(F6 %02X)".format(subType.toInt() and 0xFF)
    )

    fun buildGetMultipointDevices() = EncodedCommand(
        MdrDataType.DATA_MDR_NO2,
        byteArrayOf(CMD_T2_PERI_GET_PARAM, PERI_TYPE_CONNECTED_DEVICES),
        "GetMultipointDevices"
    )

    // =========================================================================
    // Control Command Builders
    // =========================================================================

    /**
     * WH-1000XM6 native NC/ASM + Auto Ambient Sound (subtype 0x19, verified LIVE on FW 3.1.5):
     *   [0x68, 0x19, valueChangeStatus(0x01=CHANGED, 0x00=UNDER_CHANGING), effect(0/1),
     *    mode(0=NC,1=ASM), voice(0/1), level(1..20), autoAmb(0/1), autoSens(0/1/2)]
     *
     * IMPORTANT: WH-1000XM6 ignores ambient parameter changes when valueChangeStatus == 0x00,
     * and natively preserves (voice, level, autoAmb, autoSens) across NC/ASM/OFF switches.
     * Always commit with 0x01 and preserve the user's ambient parameters even when switching to ANC/OFF.
     */
    fun buildSetNoiseControl(
        mode: NoiseControlMode,
        ambientLevel: Int,
        focusOnVoice: Boolean,
        autoAmbientSound: Boolean = false,
        autoAmbientSensitivity: AutoAmbientSensitivity = AutoAmbientSensitivity.STANDARD,
        commitChange: Boolean = true
    ): EncodedCommand {
        val clampedLevel = ambientLevel.coerceIn(1, 20)
        val changeStatus: Byte = if (commitChange) 0x01 else 0x00
        val effect: Byte = if (mode == NoiseControlMode.OFF) 0x00 else 0x01
        val ncAsmMode: Byte = if (mode == NoiseControlMode.AMBIENT) 0x01 else 0x00
        val voiceMode: Byte = if (focusOnVoice) 0x01 else 0x00
        val autoAmbByte: Byte = if (autoAmbientSound) 0x01 else 0x00
        val sensByte: Byte = when (autoAmbientSensitivity) {
            AutoAmbientSensitivity.STANDARD -> 0x00
            AutoAmbientSensitivity.HIGH -> 0x01
            AutoAmbientSensitivity.LOW -> 0x02
        }
        val payload = byteArrayOf(
            CMD_NCASM_SET_PARAM,
            NCASM_TYPE_XM6,
            changeStatus,
            effect,
            ncAsmMode,
            voiceMode,
            clampedLevel.toByte(),
            autoAmbByte,
            sensByte
        )
        return EncodedCommand(
            MdrDataType.DATA_MDR,
            payload,
            "SetNCASM19(${mode.name}, lvl=$clampedLevel, voice=$focusOnVoice, auto=$autoAmbientSound, sens=${autoAmbientSensitivity.name})"
        )
    }

    /**
     * WH-1000XM6 10-band graphic EQ (31, 63, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz).
     * UI gain range: -6..+6 mapped to protocol raw step 0..12 (center = 6).
     */
    fun buildSetEqualizer10Band(
        presetId: Int,
        gainsDb: List<Int>
    ): EncodedCommand {
        val bands = List(10) { idx ->
            val db = gainsDb.getOrElse(idx) { 0 }.coerceIn(-6, 6)
            (db + 6).toByte()
        }
        val payload = ByteArray(4 + bands.size)
        payload[0] = CMD_EQ_SET_PARAM
        payload[1] = EQ_TYPE_CUSTOM_BAND
        payload[2] = (presetId and 0xFF).toByte()
        payload[3] = bands.size.toByte()
        bands.forEachIndexed { i, raw -> payload[4 + i] = raw }
        return EncodedCommand(
            MdrDataType.DATA_MDR,
            payload,
            "SetEQ10(preset=0x${"%02X".format(presetId)})"
        )
    }

    fun buildSetConnectionQuality(mode: ConnectionQualityMode): EncodedCommand {
        val (subType, value) = when (mode) {
            ConnectionQualityMode.SOUND_QUALITY -> AUDIO_TYPE_CONNECTION_MODE to 0x00.toByte()
            ConnectionQualityMode.STABLE_CONNECTION -> AUDIO_TYPE_CONNECTION_MODE to 0x01.toByte()
            ConnectionQualityMode.LE_AUDIO_LOW_LATENCY -> AUDIO_TYPE_CONNECTION_MODE_LE to 0x02.toByte()
        }
        return EncodedCommand(
            MdrDataType.DATA_MDR,
            byteArrayOf(CMD_AUDIO_SET_PARAM, subType, value),
            "SetConnectionQuality(${mode.name})"
        )
    }

    fun buildSetDsee(enabled: Boolean) = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_AUDIO_SET_PARAM, AUDIO_TYPE_UPSCALING_DSEE, if (enabled) 0x01 else 0x00),
        "SetDSEE(E8 01 ${if (enabled) "01" else "00"})"
    )

    /**
     * WH-1000XM6 Speak-to-Chat ON/OFF (verified LIVE on FW 3.1.5):
     * Uses Sony MDR V2 EnableDisable polarity: 0x00 = ENABLE (ON), 0x01 = DISABLE (OFF)!
     *   - Byte 2 = mode (0x00 = ON, 0x01 = OFF)
     *   - Byte 3 = previewMode (0x01 = DISABLE/NORMAL)
     *   ON : F8 0C 00 01 -> F9 0C 00 01 (+ F5 0C 00 00 voice detector armed)
     *   OFF: F8 0C 01 01 -> F9 0C 01 01
     */
    fun buildSetSpeakToChat(enabled: Boolean): EncodedCommand {
        val modeByte: Byte = if (enabled) 0x00 else 0x01
        return EncodedCommand(
            MdrDataType.DATA_MDR,
            byteArrayOf(CMD_SYSTEM_SET_PARAM, SYS_TYPE_SPEAK_TO_CHAT_XM6, modeByte, 0x01),
            "SetSpeakToChat(F8 0C %02X 01)".format(modeByte.toInt() and 0xFF)
        )
    }

    /**
     * WH-1000XM6 Speak-to-Chat Extended Timeout (FA/FC 0C: [detectSensitivity=00(AUTO), modeOutTime]).
     */
    fun buildSetSpeakToChatTimeout(timeout: SpeakToChatTimeout): EncodedCommand {
        val timeoutByte: Byte = when (timeout) {
            SpeakToChatTimeout.SHORT_5S -> 0x00
            SpeakToChatTimeout.STANDARD_15S -> 0x01
            SpeakToChatTimeout.LONG_30S -> 0x02
            SpeakToChatTimeout.DO_NOT_CLOSE -> 0x03
        }
        return EncodedCommand(
            MdrDataType.DATA_MDR,
            byteArrayOf(CMD_SYSTEM_SET_EXTENDED_PARAM, SYS_TYPE_SPEAK_TO_CHAT_XM6, 0x00, timeoutByte),
            "SetSpeakToChatTimeout(FC 0C 00 %02X)".format(timeoutByte.toInt() and 0xFF)
        )
    }

    /**
     * WH-1000XM6 Touch Sensor Control Panel (verified LIVE on FW 3.1.5):
     *   F8 0F 00 -> F9 0F 00 (OFF)
     *   F8 0F 01 -> F9 0F 01 (ON)
     */
    fun buildSetTouchSensor(enabled: Boolean) = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_SYSTEM_SET_PARAM, SYS_TYPE_TOUCH_SENSOR, if (enabled) 0x01 else 0x00),
        "SetTouchSensor(F8 0F ${if (enabled) "01" else "00"})"
    )

    fun buildSetWearingPause(enabled: Boolean) = EncodedCommand(
        MdrDataType.DATA_MDR,
        byteArrayOf(CMD_SYSTEM_SET_PARAM, SYS_TYPE_WEARING_PAUSE, if (enabled) 0x01 else 0x00),
        "SetWearingPause($enabled)"
    )

    /**
     * WH-1000XM6 Auto Power Off via Sony MDR V2 POWER_SET_PARAM (0x28):
     * - Wearing-detection (subtype 0x05, LIVE verified on WH-1000XM6 FW 3.1.5):
     *     [0x28, 0x05, 0x10, 0x00] -> Off when taken off (reply: 29 05 10 00)
     *     [0x28, 0x05, 0x11, 0x00] -> Do not turn off    (reply: 29 05 11 00)
     * - Legacy time-based (subtype 0x04, for models advertising capability 0x24):
     *     15m=0x04, 30m=0x01, 1h=0x02, 3h=0x03, Disable=0x11
     */
    fun buildSetAutoPowerOff(
        option: AutoPowerOffOption,
        useWearingDetectionSubtype: Boolean = true
    ): EncodedCommand {
        val (inquiredType, activeElement, selectElement) = when (option) {
            AutoPowerOffOption.WHEN_TAKEN_OFF -> Triple(
                POWER_TYPE_AUTO_POWER_OFF_WITH_WEARING_DETECTION,
                APO_ELEMENT_WHEN_TAKEN_OFF,
                0x00.toByte()
            )
            AutoPowerOffOption.MIN_15 -> Triple(POWER_TYPE_AUTO_POWER_OFF, APO_ELEMENT_15_MIN, APO_ELEMENT_15_MIN)
            AutoPowerOffOption.MIN_30 -> Triple(POWER_TYPE_AUTO_POWER_OFF, APO_ELEMENT_30_MIN, APO_ELEMENT_30_MIN)
            AutoPowerOffOption.HOUR_1 -> Triple(POWER_TYPE_AUTO_POWER_OFF, APO_ELEMENT_60_MIN, APO_ELEMENT_60_MIN)
            AutoPowerOffOption.HOUR_3 -> Triple(POWER_TYPE_AUTO_POWER_OFF, APO_ELEMENT_180_MIN, APO_ELEMENT_180_MIN)
            AutoPowerOffOption.OFF -> if (useWearingDetectionSubtype) {
                Triple(POWER_TYPE_AUTO_POWER_OFF_WITH_WEARING_DETECTION, APO_ELEMENT_DISABLE, 0x00.toByte())
            } else {
                Triple(POWER_TYPE_AUTO_POWER_OFF, APO_ELEMENT_DISABLE, APO_ELEMENT_15_MIN)
            }
        }
        return EncodedCommand(
            MdrDataType.DATA_MDR,
            byteArrayOf(CMD_POWER_SET_PARAM, inquiredType, activeElement, selectElement),
            "SetAutoPowerOff($option -> 28 %02X %02X %02X)".format(
                inquiredType.toInt() and 0xFF,
                activeElement.toInt() and 0xFF,
                selectElement.toInt() and 0xFF
            )
        )
    }

    fun buildSetNcAmbButtonCycle(cycle: NcAmbButtonCycle): EncodedCommand {
        val patternByte: Byte = when (cycle) {
            NcAmbButtonCycle.NC_AND_AMBIENT -> 0x01
            NcAmbButtonCycle.NC_AMBIENT_OFF -> 0x02
        }
        return EncodedCommand(
            MdrDataType.DATA_MDR,
            byteArrayOf(CMD_SYSTEM_SET_PARAM, SYS_TYPE_ASSIGNABLE_BUTTON, patternByte),
            "SetNcAmbButtonCycle($cycle)"
        )
    }

    fun buildSetMultipointEnabled(enabled: Boolean) = EncodedCommand(
        MdrDataType.DATA_MDR_NO2,
        byteArrayOf(CMD_T2_PERI_SET_PARAM, PERI_TYPE_MULTIPOINT_SWITCH, if (enabled) 0x01 else 0x00),
        "SetMultipoint(T2 38 01 ${if (enabled) "01" else "00"})"
    )

    fun buildSwitchMultipointDevice(macAddress: String): EncodedCommand {
        val macBytes = macAddress.toByteArray(Charsets.US_ASCII)
        val payload = ByteArray(3 + macBytes.size)
        payload[0] = CMD_T2_PERI_SET_PARAM
        payload[1] = PERI_TYPE_SWITCH_PLAYBACK
        payload[2] = macBytes.size.toByte()
        System.arraycopy(macBytes, 0, payload, 3, macBytes.size)
        return EncodedCommand(MdrDataType.DATA_MDR_NO2, payload, "SwitchPeer($macAddress)")
    }

    fun buildDisconnectMultipointDevice(macAddress: String): EncodedCommand {
        val macBytes = macAddress.toByteArray(Charsets.US_ASCII)
        val payload = ByteArray(3 + macBytes.size)
        payload[0] = CMD_T2_PERI_SET_PARAM
        payload[1] = PERI_TYPE_DISCONNECT_PEER
        payload[2] = macBytes.size.toByte()
        System.arraycopy(macBytes, 0, payload, 3, macBytes.size)
        return EncodedCommand(MdrDataType.DATA_MDR_NO2, payload, "DisconnectPeer($macAddress)")
    }

    /**
     * WH-1000XM6 Atomic Single-Frame 3-Way Listening Mode (verified LIVE on FW 3.1.5):
     * Firmware atomically clears the mutually exclusive mode in one single DSP transition
     * with ZERO intermediate Standard-audio gap:
     *   - Any -> Cinema (电影):        [E8 04 00] (auto-clears BGM E9 09 01 xx in ~38ms)
     *   - Any -> BGM (背景音乐):       [E8 09 00 <room>] (auto-clears Cinema E9 04 01 in ~30ms)
     *   - Cinema -> Standard (标准):   [E8 04 01]
     *   - BGM -> Standard (标准):      [E8 09 01 <room>]
     */
    fun buildSetListeningModeCommands(
        targetMode: ListeningMode,
        bgmRoom: BgmRoomSize,
        fromCinema: Boolean = false,
        fromBgm: Boolean = false
    ): List<EncodedCommand> {
        val roomByte: Byte = when (bgmRoom) {
            BgmRoomSize.MY_ROOM -> 0x00
            BgmRoomSize.LIVING_ROOM -> 0x01
            BgmRoomSize.CAFE -> 0x02
        }
        return when (targetMode) {
            ListeningMode.BACKGROUND_MUSIC -> listOf(
                EncodedCommand(
                    MdrDataType.DATA_MDR,
                    byteArrayOf(CMD_AUDIO_SET_PARAM, AUDIO_TYPE_BGM_MODE, 0x00, roomByte),
                    "SetBgmOn(E8 09 00 %02X)".format(roomByte.toInt() and 0xFF)
                )
            )
            ListeningMode.CINEMA -> listOf(
                EncodedCommand(
                    MdrDataType.DATA_MDR,
                    byteArrayOf(CMD_AUDIO_SET_PARAM, AUDIO_TYPE_CINEMA_MODE, 0x00),
                    "SetCinemaOn(E8 04 00)"
                )
            )
            ListeningMode.STANDARD -> buildList {
                if (fromCinema || (!fromCinema && !fromBgm)) {
                    add(
                        EncodedCommand(
                            MdrDataType.DATA_MDR,
                            byteArrayOf(CMD_AUDIO_SET_PARAM, AUDIO_TYPE_CINEMA_MODE, 0x01),
                            "SetCinemaOff(E8 04 01)"
                        )
                    )
                }
                if (fromBgm || (!fromCinema && !fromBgm)) {
                    add(
                        EncodedCommand(
                            MdrDataType.DATA_MDR,
                            byteArrayOf(CMD_AUDIO_SET_PARAM, AUDIO_TYPE_BGM_MODE, 0x01, roomByte),
                            "SetBgmOff(E8 09 01 %02X)".format(roomByte.toInt() and 0xFF)
                        )
                    )
                }
            }
        }
    }

    // =========================================================================
    // Frame Decoder
    // =========================================================================

    fun decodeFrame(frame: MdrFrame): Xm6Event? {
        val p = frame.payload
        if (p.isEmpty()) return null
        val cmd = p[0]

        if (frame.dataType == MdrDataType.DATA_MDR_NO2) {
            return decodeTable2(cmd, p)
        }

        return when (cmd) {
            CMD_CONNECT_RET_PROTOCOL_INFO -> {
                if (p.size >= 4) {
                    Xm6Event.ProtocolInfo(
                        versionMajor = p[2].toInt() and 0xFF,
                        versionMinor = p[3].toInt() and 0xFF
                    )
                } else null
            }

            CMD_CONNECT_RET_DEVICE_INFO -> {
                if (p.size >= 3) {
                    val infoType = p[1]
                    val strLen = p[2].toInt() and 0xFF
                    if (p.size >= 3 + strLen) {
                        val value = String(p, 3, strLen, Charsets.UTF_8)
                        when (infoType) {
                            DEVICE_INFO_MODEL_NAME -> Xm6Event.DeviceModelName(value)
                            DEVICE_INFO_FW_VERSION -> Xm6Event.FirmwareVersion(value)
                            else -> null
                        }
                    } else null
                } else null
            }

            CMD_CONNECT_RET_SUPPORT_FUNCTION -> {
                if (p.size >= 3) {
                    val count = p[2].toInt() and 0xFF
                    val ids = mutableSetOf<Int>()
                    // WH-1000XM6 returns pairs of (funcId, priority)
                    for (i in 0 until count) {
                        val idx = 3 + i * 2
                        if (idx < p.size) ids.add(p[idx].toInt() and 0xFF)
                    }
                    Xm6Event.SupportFunctions(table = 1, functionIds = ids)
                } else null
            }

            CMD_COMMON_RET_BATTERY, CMD_COMMON_NTFY_BATTERY -> {
                if (p.size >= 4 && p[1] == POWER_TYPE_BATTERY) {
                    val level = (p[2].toInt() and 0xFF).coerceIn(0, 100)
                    val charging = (p[3].toInt() and 0xFF) != 0
                    Xm6Event.BatteryUpdate(level = level, charging = charging)
                } else null
            }

            CMD_POWER_RET_PARAM, CMD_POWER_NTFY_PARAM -> {
                if (p.size >= 3 && (p[1] == POWER_TYPE_AUTO_POWER_OFF || p[1] == POWER_TYPE_AUTO_POWER_OFF_WITH_WEARING_DETECTION)) {
                    val opt = when (p[2]) {
                        APO_ELEMENT_WHEN_TAKEN_OFF -> AutoPowerOffOption.WHEN_TAKEN_OFF
                        APO_ELEMENT_15_MIN, APO_ELEMENT_5_MIN -> AutoPowerOffOption.MIN_15
                        APO_ELEMENT_30_MIN -> AutoPowerOffOption.MIN_30
                        APO_ELEMENT_60_MIN -> AutoPowerOffOption.HOUR_1
                        APO_ELEMENT_180_MIN -> AutoPowerOffOption.HOUR_3
                        APO_ELEMENT_DISABLE, 0xFF.toByte() -> AutoPowerOffOption.OFF
                        else -> null
                    }
                    opt?.let { Xm6Event.AutoPowerOffUpdate(it) }
                } else null
            }

            CMD_COMMON_RET_AUDIO_CODEC, CMD_COMMON_NTFY_AUDIO_CODEC -> {
                if (p.size >= 3) {
                    val codec = when (p[2].toInt() and 0xFF) {
                        0x01 -> AudioCodec.SBC
                        0x02 -> AudioCodec.AAC
                        0x10 -> AudioCodec.LDAC
                        0x30 -> AudioCodec.LC3
                        else -> AudioCodec.UNKNOWN
                    }
                    Xm6Event.CodecUpdate(codec)
                } else null
            }

            CMD_NCASM_RET_PARAM, CMD_NCASM_NTFY_PARAM -> {
                when {
                    // WH-1000XM6 native 0x19 dialect (9 bytes):
                    // [cmd, 0x19, flag, effect, ncAsmMode, focusVoice, level, autoAmb, autoSens]
                    p.size >= 7 && p[1] == NCASM_TYPE_XM6 -> {
                        val flag = p[2].toInt() and 0xFF
                        val effect = (p[3].toInt() and 0xFF) != 0
                        val isAsm = (p[4].toInt() and 0xFF) == 0x01
                        val focusVoice = (p[5].toInt() and 0xFF) == 0x01
                        val level = (p[6].toInt() and 0xFF).coerceIn(1, 20)
                        val autoAmb = if (p.size >= 8) (p[7].toInt() and 0xFF) != 0 else null
                        val autoSens = if (p.size >= 9) {
                            when (p[8].toInt() and 0xFF) {
                                0x01 -> AutoAmbientSensitivity.HIGH
                                0x02 -> AutoAmbientSensitivity.LOW
                                else -> AutoAmbientSensitivity.STANDARD
                            }
                        } else null
                        // Verified LIVE: when taken off head, XM6 pauses NC/ASM temporarily:
                        // - NTFY: 69 19 00 00 [savedMode] ...
                        // - RET while off-head in Ambient: 67 19 01 00 01 [voice] [level] ...
                        val pausedOffHead = (cmd == CMD_NCASM_NTFY_PARAM && flag == 0x00 && !effect) ||
                            (cmd == CMD_NCASM_RET_PARAM && !effect && isAsm)
                        val mode = when {
                            pausedOffHead -> if (isAsm) NoiseControlMode.AMBIENT else NoiseControlMode.ANC
                            !effect -> NoiseControlMode.OFF
                            isAsm -> NoiseControlMode.AMBIENT
                            else -> NoiseControlMode.ANC
                        }
                        Xm6Event.NoiseControlUpdate(
                            mode = mode,
                            ambientLevel = level,
                            focusOnVoice = focusVoice,
                            autoAmbientSound = autoAmb,
                            autoAmbientSensitivity = autoSens,
                            pausedOffHead = pausedOffHead
                        )
                    }

                    // Legacy V2 0x17 fallback
                    p.size >= 8 && p[1] == NCASM_TYPE_LEGACY_V2 -> {
                        val effect = (p[3].toInt() and 0xFF) != 0
                        val isAsm = (p[4].toInt() and 0xFF) == 0x01
                        val focusVoice = (p[6].toInt() and 0xFF) == 0x01
                        val level = (p[7].toInt() and 0xFF).coerceIn(1, 20)
                        val mode = when {
                            !effect -> NoiseControlMode.OFF
                            isAsm -> NoiseControlMode.AMBIENT
                            else -> NoiseControlMode.ANC
                        }
                        Xm6Event.NoiseControlUpdate(mode, level, focusVoice)
                    }

                    else -> null
                }
            }

            CMD_EQ_RET_PARAM, CMD_EQ_NTFY_PARAM -> {
                if (p.size >= 4) {
                    val presetId = p[2].toInt() and 0xFF
                    val count = p[3].toInt() and 0xFF
                    if (count > 0 && p.size >= 4 + count) {
                        val rawBands = (0 until count).map { i ->
                            ((p[4 + i].toInt() and 0xFF) - 6).coerceIn(-6, 6)
                        }
                        val bands10 = if (rawBands.size == 10) {
                            rawBands
                        } else {
                            List(10) { idx -> rawBands.getOrElse(idx) { 0 } }
                        }
                        Xm6Event.EqualizerUpdate(presetId, bands10)
                    } else null
                } else null
            }

            CMD_AUDIO_RET_STATUS, CMD_AUDIO_NTFY_STATUS -> {
                // Verified LIVE on WH-1000XM6: E3/E5 01 [00=AVAILABLE/ACTIVE, 01=RESTRICTED/PAUSED]
                if (p.size >= 3 && p[1] == AUDIO_TYPE_UPSCALING_DSEE) {
                    Xm6Event.DseeStatusUpdate((p[2].toInt() and 0xFF) == 0)
                } else null
            }

            CMD_AUDIO_RET_PARAM, CMD_AUDIO_NTFY_PARAM -> {
                if (p.size >= 3) {
                    when (p[1]) {
                        AUDIO_TYPE_CONNECTION_MODE, AUDIO_TYPE_CONNECTION_MODE_LE -> {
                            val mode = when (p[2].toInt() and 0xFF) {
                                0x00 -> ConnectionQualityMode.SOUND_QUALITY
                                0x01 -> ConnectionQualityMode.STABLE_CONNECTION
                                0x02 -> ConnectionQualityMode.LE_AUDIO_LOW_LATENCY
                                else -> ConnectionQualityMode.SOUND_QUALITY
                            }
                            Xm6Event.ConnectionQualityUpdate(mode)
                        }
                        AUDIO_TYPE_UPSCALING_DSEE -> {
                            Xm6Event.DseeUpdate((p[2].toInt() and 0xFF) != 0)
                        }
                        AUDIO_TYPE_CINEMA_MODE -> {
                            // 0x00 = Cinema ENABLE (ON), 0x01 = Cinema DISABLE (OFF)
                            Xm6Event.CinemaModeUpdate((p[2].toInt() and 0xFF) == 0x00)
                        }
                        AUDIO_TYPE_BGM_MODE -> {
                            if (p.size >= 4) {
                                // 0x00 = BGM ENABLE (ON), 0x01 = BGM DISABLE (OFF)
                                val enabled = (p[2].toInt() and 0xFF) == 0x00
                                val room = when (p[3].toInt() and 0xFF) {
                                    0x00 -> BgmRoomSize.MY_ROOM
                                    0x02 -> BgmRoomSize.CAFE
                                    else -> BgmRoomSize.LIVING_ROOM
                                }
                                Xm6Event.BgmModeUpdate(enabled, room)
                            } else null
                        }
                        else -> null
                    }
                } else null
            }

            CMD_SYSTEM_RET_STATUS, CMD_SYSTEM_NTFY_STATUS -> {
                // Verified LIVE on WH-1000XM6 FW 3.1.5:
                // - F2 10 -> F3 10 [00=WORN, 01=NOT_WORN] (3 bytes)
                // - Unsolicited -> F5 10 01 [00=WORN, 01=NOT_WORN] (4 bytes)
                if (p.size >= 3 && p[1] == SYS_TYPE_WEARING_STATUS) {
                    val stateByte = (if (p.size >= 4) p[3] else p[2]).toInt() and 0xFF
                    Xm6Event.WearingStatusUpdate(isWorn = (stateByte == 0x00))
                } else null
            }

            CMD_SYSTEM_RET_PARAM, CMD_SYSTEM_NTFY_PARAM -> {
                if (p.size >= 3) {
                    val v = p[2].toInt() and 0xFF
                    when (p[1]) {
                        // Verified LIVE on WH-1000XM6: F7/F9 0C [mode: 00=ON, 01=OFF] [preview: 00=ON, 01=OFF]
                        SYS_TYPE_SPEAK_TO_CHAT_XM6, SYS_TYPE_SPEAK_TO_CHAT_TYPE1 -> {
                            Xm6Event.SpeakToChatUpdate(v == 0x00)
                        }
                        SYS_TYPE_TOUCH_SENSOR -> if (p.size == 3) Xm6Event.TouchSensorUpdate(v != 0) else null
                        SYS_TYPE_WEARING_PAUSE -> if (p.size == 3) Xm6Event.WearingPauseUpdate(v != 0) else null
                        SYS_TYPE_ASSIGNABLE_BUTTON -> {
                            val cycle = if (v == 0x01) NcAmbButtonCycle.NC_AND_AMBIENT else NcAmbButtonCycle.NC_AMBIENT_OFF
                            Xm6Event.NcButtonCycleUpdate(cycle)
                        }
                        else -> null
                    }
                } else null
            }

            CMD_SYSTEM_RET_EXTENDED_PARAM, CMD_SYSTEM_NTFY_EXTENDED_PARAM -> {
                if (p.size >= 4 && (p[1] == SYS_TYPE_SPEAK_TO_CHAT_XM6 || p[1] == SYS_TYPE_SPEAK_TO_CHAT_TYPE1)) {
                    val timeout = when (p[3].toInt() and 0xFF) {
                        0x00 -> SpeakToChatTimeout.SHORT_5S
                        0x01 -> SpeakToChatTimeout.STANDARD_15S
                        0x02 -> SpeakToChatTimeout.LONG_30S
                        else -> SpeakToChatTimeout.DO_NOT_CLOSE
                    }
                    Xm6Event.SpeakToChatConfigUpdate(timeout)
                } else null
            }

            else -> null
        }
    }

    private fun decodeTable2(cmd: Byte, p: ByteArray): Xm6Event? {
        return when (cmd) {
            CMD_CONNECT_RET_SUPPORT_FUNCTION -> {
                if (p.size >= 3) {
                    val count = p[2].toInt() and 0xFF
                    val ids = mutableSetOf<Int>()
                    for (i in 0 until count) {
                        val idx = 3 + i * 2
                        if (idx < p.size) ids.add(p[idx].toInt() and 0xFF)
                    }
                    Xm6Event.SupportFunctions(table = 2, functionIds = ids)
                } else null
            }

            CMD_T2_PERI_RET_PARAM, CMD_T2_PERI_NTFY_PARAM -> {
                when {
                    // Verified LIVE on WH-1000XM6 FW 3.1.5 (37 00 / 39 00):
                    // [cmd, 0x00, count, (mac17, connStatus(0=offline,1=connected), nameLen, nameUtf8) * count, activePlaybackIdx(1-based)]
                    p.size >= 3 && p[1] == PERI_TYPE_CONNECTED_DEVICES -> {
                        val count = p[2].toInt() and 0xFF
                        var offset = 3
                        val rawEntries = mutableListOf<Triple<String, Int, String>>()
                        repeat(count) {
                            if (offset + 19 <= p.size) {
                                val mac = String(p, offset, 17, Charsets.US_ASCII)
                                offset += 17
                                val connStatus = p[offset++].toInt() and 0xFF
                                val nameLen = p[offset++].toInt() and 0xFF
                                if (offset + nameLen <= p.size) {
                                    val name = String(p, offset, nameLen, Charsets.UTF_8)
                                    offset += nameLen
                                    rawEntries.add(Triple(mac, connStatus, name))
                                }
                            }
                        }
                        val activePlaybackIdx = if (offset < p.size) (p[offset].toInt() and 0xFF) else 0
                        val connectedCount = rawEntries.count { it.second != 0 }
                        val devices = rawEntries.mapIndexed { idx, (mac, connStatus, name) ->
                            val isPeerConnected = connStatus != 0
                            val isActive = isPeerConnected && when {
                                activePlaybackIdx in 1..rawEntries.size -> (idx == activePlaybackIdx - 1)
                                connStatus >= 2 -> true
                                connectedCount == 1 -> true
                                else -> false
                            }
                            ConnectedPeerDevice(
                                mac = mac,
                                name = name,
                                isConnected = isPeerConnected,
                                isActivePlayback = isActive
                            )
                        }
                        Xm6Event.MultipointDevicesUpdate(devices)
                    }
                    p.size >= 3 && p[1] == PERI_TYPE_MULTIPOINT_SWITCH -> {
                        Xm6Event.MultipointEnabledUpdate((p[2].toInt() and 0xFF) != 0)
                    }
                    else -> null
                }
            }

            CMD_T2_LISTENING_RET, CMD_T2_LISTENING_NTFY -> {
                if (p.size >= 4) {
                    val mode = when (p[2].toInt() and 0xFF) {
                        0x01 -> ListeningMode.BACKGROUND_MUSIC
                        0x02 -> ListeningMode.CINEMA
                        else -> ListeningMode.STANDARD
                    }
                    val room = when (p[3].toInt() and 0xFF) {
                        0x00 -> BgmRoomSize.MY_ROOM
                        0x02 -> BgmRoomSize.CAFE
                        else -> BgmRoomSize.LIVING_ROOM
                    }
                    Xm6Event.ListeningModeUpdate(mode, room)
                } else null
            }

            else -> null
        }
    }
}
