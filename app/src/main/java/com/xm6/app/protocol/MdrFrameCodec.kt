package com.xm6.app.protocol

import java.io.ByteArrayOutputStream

/**
 * Sony MDR Protocol V2 frame data types.
 */
enum class MdrDataType(val value: Byte) {
    DATA(0x00),
    ACK(0x01),
    DATA_MC_NO1(0x02),
    DATA_ICD(0x09),
    DATA_EV(0x0A),
    DATA_MDR(0x0C),       // Table 1: Core commands (Connect, Battery, Codec, NC/ASM, EQ, Audio, System)
    DATA_COMMON(0x0D),
    DATA_MDR_NO2(0x0E),   // Table 2: Multipoint peer device management & newer XM5/XM6 subsystems
    SHOT(0x10),
    LARGE_DATA_COMMON(0x2D);

    companion object {
        fun fromByte(b: Byte): MdrDataType? = entries.firstOrNull { it.value == b }
    }
}

data class MdrFrame(
    val dataType: MdrDataType,
    val seqNo: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MdrFrame) return false
        return dataType == other.dataType &&
            seqNo == other.seqNo &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = dataType.hashCode()
        result = 31 * result + seqNo
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Encodes and decodes Sony MDR Protocol V2 frames:
 *
 * Wire format:
 *   [0x3E] [escaped(DataType, SeqNo, Length(4B BE), Payload..., Checksum(1B))] [0x3C]
 *
 * Byte-stuffing rule:
 *   0x3C -> 0x3D 0x2C
 *   0x3D -> 0x3D 0x2D
 *   0x3E -> 0x3D 0x2E
 */
object MdrFrameCodec {
    const val START_MARKER: Byte = 0x3E
    const val END_MARKER: Byte = 0x3C
    const val ESCAPE_BYTE: Byte = 0x3D
    private const val ESCAPE_MASK: Int = 0x10

    fun encode(frame: MdrFrame): ByteArray = encode(frame.dataType, frame.seqNo, frame.payload)

    fun encode(dataType: MdrDataType, seqNo: Int, payload: ByteArray): ByteArray {
        val len = payload.size
        val raw = ByteArray(6 + len + 1)
        raw[0] = dataType.value
        raw[1] = (seqNo and 0xFF).toByte()
        raw[2] = ((len ushr 24) and 0xFF).toByte()
        raw[3] = ((len ushr 16) and 0xFF).toByte()
        raw[4] = ((len ushr 8) and 0xFF).toByte()
        raw[5] = (len and 0xFF).toByte()
        if (len > 0) {
            System.arraycopy(payload, 0, raw, 6, len)
        }
        raw[6 + len] = computeChecksum(raw, 0, 6 + len)

        val out = ByteArrayOutputStream(raw.size + 16)
        out.write(START_MARKER.toInt())
        for (b in raw) {
            if (b == START_MARKER || b == END_MARKER || b == ESCAPE_BYTE) {
                out.write(ESCAPE_BYTE.toInt())
                out.write((b.toInt() and 0xFF) xor ESCAPE_MASK)
            } else {
                out.write(b.toInt() and 0xFF)
            }
        }
        out.write(END_MARKER.toInt())
        return out.toByteArray()
    }

    /**
     * Decodes a complete frame (including leading 0x3E and trailing 0x3C).
     * Returns null if markers, escaping, length, dataType, or checksum are invalid.
     */
    fun decode(wireFrame: ByteArray): MdrFrame? {
        if (wireFrame.size < 9) return null
        if (wireFrame.first() != START_MARKER || wireFrame.last() != END_MARKER) return null
        return decodeEscapedBody(wireFrame, 1, wireFrame.size - 1)
    }

    /**
     * Decodes the escaped body strictly between START_MARKER (0x3E) and END_MARKER (0x3C).
     */
    fun decodeEscapedBody(buffer: ByteArray, startOffset: Int, endOffsetExclusive: Int): MdrFrame? {
        val unescaped = ByteArrayOutputStream(endOffsetExclusive - startOffset)
        var i = startOffset
        while (i < endOffsetExclusive) {
            val b = buffer[i]
            if (b == ESCAPE_BYTE) {
                i++
                if (i >= endOffsetExclusive) return null
                val next = buffer[i].toInt() and 0xFF
                val restored = (next xor ESCAPE_MASK).toByte()
                if (restored != START_MARKER && restored != END_MARKER && restored != ESCAPE_BYTE) {
                    return null
                }
                unescaped.write(restored.toInt() and 0xFF)
            } else {
                unescaped.write(b.toInt() and 0xFF)
            }
            i++
        }

        val raw = unescaped.toByteArray()
        if (raw.size < 7) return null

        val dataType = MdrDataType.fromByte(raw[0]) ?: return null
        val seqNo = raw[1].toInt() and 0xFF
        val payloadLen = ((raw[2].toInt() and 0xFF) shl 24) or
            ((raw[3].toInt() and 0xFF) shl 16) or
            ((raw[4].toInt() and 0xFF) shl 8) or
            (raw[5].toInt() and 0xFF)

        if (payloadLen < 0 || raw.size != 6 + payloadLen + 1) return null

        val expectedChecksum = computeChecksum(raw, 0, 6 + payloadLen)
        val actualChecksum = raw[6 + payloadLen]
        if (expectedChecksum != actualChecksum) return null

        val payload = if (payloadLen > 0) {
            raw.copyOfRange(6, 6 + payloadLen)
        } else {
            ByteArray(0)
        }
        return MdrFrame(dataType = dataType, seqNo = seqNo, payload = payload)
    }

    fun computeChecksum(data: ByteArray, offset: Int, length: Int): Byte {
        var sum = 0
        for (i in offset until (offset + length)) {
            sum = (sum + (data[i].toInt() and 0xFF)) and 0xFF
        }
        return sum.toByte()
    }

    fun toHexString(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    fun parseHexString(hex: String): ByteArray? {
        val cleaned = hex.replace(Regex("[^0-9A-Fa-f]"), "")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

/**
 * Stateful stream deframer for chunked RFCOMM reads.
 */
class MdrStreamParser {
    private val frameBuf = ByteArrayOutputStream(256)
    private var inFrame = false

    fun feed(chunk: ByteArray, length: Int = chunk.size): List<MdrFrame> {
        val frames = mutableListOf<MdrFrame>()
        for (i in 0 until length) {
            val b = chunk[i]
            when {
                b == MdrFrameCodec.START_MARKER -> {
                    frameBuf.reset()
                    inFrame = true
                }
                b == MdrFrameCodec.END_MARKER && inFrame -> {
                    val body = frameBuf.toByteArray()
                    inFrame = false
                    frameBuf.reset()
                    MdrFrameCodec.decodeEscapedBody(body, 0, body.size)?.let { frames.add(it) }
                }
                inFrame -> {
                    if (frameBuf.size() < 8192) {
                        frameBuf.write(b.toInt() and 0xFF)
                    } else {
                        inFrame = false
                        frameBuf.reset()
                    }
                }
            }
        }
        return frames
    }
}
