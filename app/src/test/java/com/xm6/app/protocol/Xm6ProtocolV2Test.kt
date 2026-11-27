package com.xm6.app.protocol

import com.xm6.app.data.AudioCodec
import com.xm6.app.data.AutoPowerOffOption
import com.xm6.app.data.BgmRoomSize
import com.xm6.app.data.ConnectionQualityMode
import com.xm6.app.data.ListeningMode
import com.xm6.app.data.NcAmbButtonCycle
import com.xm6.app.data.NoiseControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Xm6ProtocolV2Test {

    @Test
    fun encodeAndDecode_noiseControlXm6Subtype19AndAutoAmbient() {
        val cmd = Xm6ProtocolV2.buildSetNoiseControl(
            mode = NoiseControlMode.AMBIENT,
            ambientLevel = 16,
            focusOnVoice = true,
            autoAmbientSound = true
        )
        assertEquals(MdrDataType.DATA_MDR, cmd.dataType)
        assertEquals(Xm6ProtocolV2.CMD_NCASM_SET_PARAM, cmd.payload[0])
        assertEquals(Xm6ProtocolV2.NCASM_TYPE_XM6, cmd.payload[1])

        // Convert SET (0x68) to RET (0x67) and verify round-trip decoding
        val retPayload = cmd.payload.copyOf()
        retPayload[0] = Xm6ProtocolV2.CMD_NCASM_RET_PARAM
        val event = Xm6ProtocolV2.decodeFrame(MdrFrame(MdrDataType.DATA_MDR, 0, retPayload))

        assertTrue(event is Xm6Event.NoiseControlUpdate)
        event as Xm6Event.NoiseControlUpdate
        assertEquals(NoiseControlMode.AMBIENT, event.mode)
        assertEquals(16, event.ambientLevel)
        assertEquals(true, event.focusOnVoice)
        assertEquals(true, event.autoAmbientSound)

        // Switching to ANC or OFF must commit (0x01) AND preserve ambient params (level, voice, autoAmbient, sensitivity)
        val ancCmd = Xm6ProtocolV2.buildSetNoiseControl(
            mode = NoiseControlMode.ANC,
            ambientLevel = 15,
            focusOnVoice = false,
            autoAmbientSound = true,
            autoAmbientSensitivity = com.xm6.app.data.AutoAmbientSensitivity.HIGH
        )
        assertTrue(ancCmd.payload.contentEquals(byteArrayOf(0x68, 0x19, 0x01, 0x01, 0x00, 0x00, 15, 0x01, 0x01)))

        val offCmd = Xm6ProtocolV2.buildSetNoiseControl(
            mode = NoiseControlMode.OFF,
            ambientLevel = 15,
            focusOnVoice = false,
            autoAmbientSound = true,
            autoAmbientSensitivity = com.xm6.app.data.AutoAmbientSensitivity.HIGH
        )
        assertTrue(offCmd.payload.contentEquals(byteArrayOf(0x68, 0x19, 0x01, 0x00, 0x00, 0x00, 15, 0x01, 0x01)))
    }

    @Test
    fun encodeAndDecode_xm6TenBandEqualizerMapsMinus6ToPlus6() {
        val gains = listOf(-6, -3, 0, 1, 2, 3, 4, 5, 6, -1)
        val cmd = Xm6ProtocolV2.buildSetEqualizer10Band(Xm6ProtocolV2.EQ_PRESET_CUSTOM_1, gains)

        assertEquals(14, cmd.payload.size) // 4 header + 10 bands
        assertEquals(0, cmd.payload[4].toInt() and 0xFF)   // -6 + 6 = 0
        assertEquals(12, cmd.payload[12].toInt() and 0xFF) // +6 + 6 = 12

        val retPayload = cmd.payload.copyOf()
        retPayload[0] = Xm6ProtocolV2.CMD_EQ_RET_PARAM
        val event = Xm6ProtocolV2.decodeFrame(MdrFrame(MdrDataType.DATA_MDR, 0, retPayload))

        assertTrue(event is Xm6Event.EqualizerUpdate)
        assertEquals(gains, (event as Xm6Event.EqualizerUpdate).bands10)
    }

    @Test
    fun decode_batteryCodecAndSystemControls() {
        val batEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(Xm6ProtocolV2.CMD_COMMON_RET_BATTERY, 0x00, 84, 0x01))
        ) as Xm6Event.BatteryUpdate
        assertEquals(84, batEvent.level)
        assertTrue(batEvent.charging)

        val ldacEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(Xm6ProtocolV2.CMD_COMMON_RET_AUDIO_CODEC, 0x00, 0x10))
        ) as Xm6Event.CodecUpdate
        assertEquals(AudioCodec.LDAC, ldacEvent.codec)

        val lc3Event = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(Xm6ProtocolV2.CMD_COMMON_NTFY_AUDIO_CODEC, 0x00, 0x30))
        ) as Xm6Event.CodecUpdate
        assertEquals(AudioCodec.LC3, lc3Event.codec)

        val leCmd = Xm6ProtocolV2.buildSetConnectionQuality(ConnectionQualityMode.LE_AUDIO_LOW_LATENCY)
        val leRet = leCmd.payload.copyOf().also { it[0] = Xm6ProtocolV2.CMD_AUDIO_RET_PARAM }
        val connEvent = Xm6ProtocolV2.decodeFrame(MdrFrame(MdrDataType.DATA_MDR, 0, leRet)) as Xm6Event.ConnectionQualityUpdate
        assertEquals(ConnectionQualityMode.LE_AUDIO_LOW_LATENCY, connEvent.mode)

        for (opt in AutoPowerOffOption.entries) {
            val apoCmd = Xm6ProtocolV2.buildSetAutoPowerOff(opt)
            assertEquals(Xm6ProtocolV2.CMD_POWER_SET_PARAM, apoCmd.payload[0])
            val apoRet = apoCmd.payload.copyOf().also { it[0] = Xm6ProtocolV2.CMD_POWER_RET_PARAM }
            val apoEvent = Xm6ProtocolV2.decodeFrame(MdrFrame(MdrDataType.DATA_MDR, 0, apoRet)) as Xm6Event.AutoPowerOffUpdate
            assertEquals(opt, apoEvent.option)

            val apoNtfy = apoCmd.payload.copyOf().also { it[0] = Xm6ProtocolV2.CMD_POWER_NTFY_PARAM }
            val ntfyEvent = Xm6ProtocolV2.decodeFrame(MdrFrame(MdrDataType.DATA_MDR, 0, apoNtfy)) as Xm6Event.AutoPowerOffUpdate
            assertEquals(opt, ntfyEvent.option)
        }

        // Verify exact WH-1000XM6 wearing-detection payloads (28 05 10 00 / 28 05 11 00)
        val xm6Off = Xm6ProtocolV2.buildSetAutoPowerOff(AutoPowerOffOption.OFF, useWearingDetectionSubtype = true)
        assertTrue(xm6Off.payload.contentEquals(byteArrayOf(0x28, 0x05, 0x11, 0x00)))
        val xm6TakenOff = Xm6ProtocolV2.buildSetAutoPowerOff(AutoPowerOffOption.WHEN_TAKEN_OFF, useWearingDetectionSubtype = true)
        assertTrue(xm6TakenOff.payload.contentEquals(byteArrayOf(0x28, 0x05, 0x10, 0x00)))

        // Verify live WH-1000XM6 firmware 3.1.5 reply (27 05 10 00 -> WHEN_TAKEN_OFF)
        val liveWearingOff = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0x27, 0x05, 0x10, 0x00))
        ) as Xm6Event.AutoPowerOffUpdate
        assertEquals(AutoPowerOffOption.WHEN_TAKEN_OFF, liveWearingOff.option)

        val btnCmd = Xm6ProtocolV2.buildSetNcAmbButtonCycle(NcAmbButtonCycle.NC_AND_AMBIENT)
        val btnRet = btnCmd.payload.copyOf().also { it[0] = Xm6ProtocolV2.CMD_SYSTEM_RET_PARAM }
        val btnEvent = Xm6ProtocolV2.decodeFrame(MdrFrame(MdrDataType.DATA_MDR, 0, btnRet)) as Xm6Event.NcButtonCycleUpdate
        assertEquals(NcAmbButtonCycle.NC_AND_AMBIENT, btnEvent.cycle)

        // Verified LIVE WH-1000XM6 Speak-to-Chat (Sony EnableDisable: 00=ON, 01=OFF)
        val stcOn = Xm6ProtocolV2.buildSetSpeakToChat(true)
        assertTrue(stcOn.payload.contentEquals(byteArrayOf(0xF8.toByte(), 0x0C, 0x00, 0x01)))
        val stcOff = Xm6ProtocolV2.buildSetSpeakToChat(false)
        assertTrue(stcOff.payload.contentEquals(byteArrayOf(0xF8.toByte(), 0x0C, 0x01, 0x01)))

        val stcOnEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xF9.toByte(), 0x0C, 0x00, 0x01))
        ) as Xm6Event.SpeakToChatUpdate
        assertTrue(stcOnEvent.enabled)
        val stcOffEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xF9.toByte(), 0x0C, 0x01, 0x01))
        ) as Xm6Event.SpeakToChatUpdate
        assertEquals(false, stcOffEvent.enabled)

        // Verified LIVE WH-1000XM6 Speak-to-Chat Extended Timeout (FA/FC 0C)
        val stcTimeoutCmd = Xm6ProtocolV2.buildSetSpeakToChatTimeout(com.xm6.app.data.SpeakToChatTimeout.STANDARD_15S)
        assertTrue(stcTimeoutCmd.payload.contentEquals(byteArrayOf(0xFC.toByte(), 0x0C, 0x00, 0x01)))
        val stcTimeoutEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xFB.toByte(), 0x0C, 0x00, 0x01))
        ) as Xm6Event.SpeakToChatConfigUpdate
        assertEquals(com.xm6.app.data.SpeakToChatTimeout.STANDARD_15S, stcTimeoutEvent.timeout)

        // Verified LIVE WH-1000XM6 Touch Sensor (F8 0F 00/01 -> F9 0F 00/01)
        val touchOff = Xm6ProtocolV2.buildSetTouchSensor(false)
        assertTrue(touchOff.payload.contentEquals(byteArrayOf(0xF8.toByte(), 0x0F, 0x00)))
        val touchEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xF9.toByte(), 0x0F, 0x01))
        ) as Xm6Event.TouchSensorUpdate
        assertTrue(touchEvent.enabled)

        // Verified LIVE WH-1000XM6 Wearing Status:
        // - GET reply (3 bytes): F2 10 -> F3 10 [00=WORN, 01=NOT_WORN]
        // - NTFY push (4 bytes): F5 10 01 [00=WORN, 01=NOT_WORN]
        val wearQuery = Xm6ProtocolV2.buildGetWearingStatus()
        assertTrue(wearQuery.payload.contentEquals(byteArrayOf(0xF2.toByte(), 0x10)))
        val retNotWorn = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xF3.toByte(), 0x10, 0x01))
        ) as Xm6Event.WearingStatusUpdate
        assertEquals(false, retNotWorn.isWorn)
        val retWorn = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xF3.toByte(), 0x10, 0x00))
        ) as Xm6Event.WearingStatusUpdate
        assertTrue(retWorn.isWorn)
        val notWornEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xF5.toByte(), 0x10, 0x01, 0x01))
        ) as Xm6Event.WearingStatusUpdate
        assertEquals(false, notWornEvent.isWorn)
        val wornEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xF5.toByte(), 0x10, 0x01, 0x00))
        ) as Xm6Event.WearingStatusUpdate
        assertTrue(wornEvent.isWorn)

        // Verified LIVE WH-1000XM6 off-head paused NCASM notification (69 19 00 00 01 01 14 00 00)
        val pausedEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0x69, 0x19, 0x00, 0x00, 0x01, 0x01, 0x14, 0x00, 0x00))
        ) as Xm6Event.NoiseControlUpdate
        assertTrue(pausedEvent.pausedOffHead)
        assertEquals(NoiseControlMode.AMBIENT, pausedEvent.mode)
        assertEquals(20, pausedEvent.ambientLevel)

        // Verified LIVE WH-1000XM6 DSEE status indicator (00=AVAILABLE, 01=RESTRICTED)
        val dseeAvail = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xE5.toByte(), 0x01, 0x00))
        ) as Xm6Event.DseeStatusUpdate
        assertTrue(dseeAvail.active)
        val dseePaused = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xE5.toByte(), 0x01, 0x01))
        ) as Xm6Event.DseeStatusUpdate
        assertEquals(false, dseePaused.active)
    }

    @Test
    fun decode_table2MultipointPeerListAndListeningMode() {
        val mac1 = "AA:14:98:21:0F:C1".toByteArray(Charsets.US_ASCII)
        val name1 = "Xiaomi 14".toByteArray(Charsets.UTF_8)
        val mac2 = "7C:D1:C3:88:4B:12".toByteArray(Charsets.US_ASCII)
        val name2 = "MacBook Pro".toByteArray(Charsets.UTF_8)

        val payload = byteArrayOf(Xm6ProtocolV2.CMD_T2_PERI_RET_PARAM, 0x00, 0x02) +
            mac1 + byteArrayOf(0x01, name1.size.toByte()) + name1 +
            mac2 + byteArrayOf(0x00, name2.size.toByte()) + name2

        val event = Xm6ProtocolV2.decodeFrame(MdrFrame(MdrDataType.DATA_MDR_NO2, 0, payload))
        assertTrue(event is Xm6Event.MultipointDevicesUpdate)
        val devices = (event as Xm6Event.MultipointDevicesUpdate).devices
        assertEquals(2, devices.size)
        assertEquals("Xiaomi 14", devices[0].name)
        assertTrue(devices[0].isActivePlayback)
        assertEquals("MacBook Pro", devices[1].name)

        // Verified LIVE WH-1000XM6 Atomic Single-Frame Listening Mode transitions (zero Standard gap!)
        val cinToStd = Xm6ProtocolV2.buildSetListeningModeCommands(
            ListeningMode.STANDARD, BgmRoomSize.LIVING_ROOM, fromCinema = true, fromBgm = false
        )
        assertEquals(1, cinToStd.size)
        assertTrue(cinToStd[0].payload.contentEquals(byteArrayOf(0xE8.toByte(), 0x04, 0x01)))

        val bgmToStd = Xm6ProtocolV2.buildSetListeningModeCommands(
            ListeningMode.STANDARD, BgmRoomSize.LIVING_ROOM, fromCinema = false, fromBgm = true
        )
        assertEquals(1, bgmToStd.size)
        assertTrue(bgmToStd[0].payload.contentEquals(byteArrayOf(0xE8.toByte(), 0x09, 0x01, 0x01)))

        val bgmCmds = Xm6ProtocolV2.buildSetListeningModeCommands(ListeningMode.BACKGROUND_MUSIC, BgmRoomSize.CAFE)
        assertEquals(1, bgmCmds.size)
        assertTrue(bgmCmds[0].payload.contentEquals(byteArrayOf(0xE8.toByte(), 0x09, 0x00, 0x02)))
        val bgmEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xE9.toByte(), 0x09, 0x00, 0x02))
        ) as Xm6Event.BgmModeUpdate
        assertTrue(bgmEvent.enabled)
        assertEquals(BgmRoomSize.CAFE, bgmEvent.room)

        val cinCmds = Xm6ProtocolV2.buildSetListeningModeCommands(ListeningMode.CINEMA, BgmRoomSize.LIVING_ROOM)
        assertEquals(1, cinCmds.size)
        assertTrue(cinCmds[0].payload.contentEquals(byteArrayOf(0xE8.toByte(), 0x04, 0x00)))
        val cinEvent = Xm6ProtocolV2.decodeFrame(
            MdrFrame(MdrDataType.DATA_MDR, 0, byteArrayOf(0xE7.toByte(), 0x04, 0x00))
        ) as Xm6Event.CinemaModeUpdate
        assertTrue(cinEvent.enabled)
    }
}
