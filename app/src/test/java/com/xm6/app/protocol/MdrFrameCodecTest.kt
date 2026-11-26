package com.xm6.app.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MdrFrameCodecTest {

    @Test
    fun encodeAndDecode_table1FrameRoundTrip() {
        val payload = byteArrayOf(0x68, 0x17, 0x02, 0x01, 0x01, 0x01, 0x00, 0x0E)
        val wire = MdrFrameCodec.encode(MdrDataType.DATA_MDR, seqNo = 1, payload = payload)

        assertEquals(MdrFrameCodec.START_MARKER, wire.first())
        assertEquals(MdrFrameCodec.END_MARKER, wire.last())

        val decoded = MdrFrameCodec.decode(wire)
        assertNotNull(decoded)
        assertEquals(MdrDataType.DATA_MDR, decoded!!.dataType)
        assertEquals(1, decoded.seqNo)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun encodeAndDecode_escapesSpecialControlBytesProperly() {
        // Contains all three special bytes: 0x3C, 0x3D, 0x3E
        val payload = byteArrayOf(0x58, 0x3C, 0x3D, 0x3E, 0x00)
        val wire = MdrFrameCodec.encode(MdrDataType.DATA_MDR_NO2, seqNo = 0, payload = payload)

        // Ensure no raw 0x3C or 0x3E appear inside the frame body
        for (i in 1 until wire.size - 1) {
            assert(wire[i] != MdrFrameCodec.START_MARKER) { "Unescaped 0x3E inside frame at $i" }
            assert(wire[i] != MdrFrameCodec.END_MARKER) { "Unescaped 0x3C inside frame at $i" }
        }

        val decoded = MdrFrameCodec.decode(wire)
        assertNotNull(decoded)
        assertEquals(MdrDataType.DATA_MDR_NO2, decoded!!.dataType)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun decode_rejectsCorruptedChecksum() {
        val wire = MdrFrameCodec.encode(MdrDataType.DATA_MDR, seqNo = 0, payload = byteArrayOf(0x22, 0x00))
        // Corrupt checksum byte right before trailing 0x3C
        wire[wire.size - 2] = (wire[wire.size - 2].toInt() xor 0xFF).toByte()
        assertNull(MdrFrameCodec.decode(wire))
    }

    @Test
    fun streamParser_handlesChunkedAndBackToBackFrames() {
        val f1 = MdrFrameCodec.encode(MdrDataType.ACK, seqNo = 1, payload = ByteArray(0))
        val f2 = MdrFrameCodec.encode(MdrDataType.DATA_MDR, seqNo = 0, payload = byteArrayOf(0x23, 0x00, 84, 0x00))
        val combined = byteArrayOf(0x00, 0xFF.toByte()) + f1 + f2

        val parser = MdrStreamParser()
        val parsed = mutableListOf<MdrFrame>()
        // Feed 3 bytes at a time to simulate RFCOMM fragmentation
        var idx = 0
        while (idx < combined.size) {
            val end = minOf(idx + 3, combined.size)
            parsed.addAll(parser.feed(combined.copyOfRange(idx, end)))
            idx = end
        }

        assertEquals(2, parsed.size)
        assertEquals(MdrDataType.ACK, parsed[0].dataType)
        assertEquals(1, parsed[0].seqNo)
        assertEquals(MdrDataType.DATA_MDR, parsed[1].dataType)
        assertEquals(84, parsed[1].payload[2].toInt() and 0xFF)
    }
}
