package com.xm6.app.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

interface ByteTransport {
    @Throws(IOException::class)
    fun write(data: ByteArray)

    @Throws(IOException::class)
    fun read(buffer: ByteArray): Int

    fun close()
}

data class ProtocolLogEntry(
    val timestampMs: Long,
    val direction: String, // "TX" or "RX"
    val table: String,     // "T1", "T2", "ACK", "SYS"
    val label: String,
    val hex: String
)

/**
 * Implements Sony MDR Protocol V2 Alternating-Bit Stop-and-Wait ARQ over a [ByteTransport].
 */
class ArqSession(
    private val transport: ByteTransport,
    private val scope: CoroutineScope,
    private val ackTimeoutMs: Long = 1500L,
    private val maxRetries: Int = 2,
    private val onLog: (ProtocolLogEntry) -> Unit = {},
    private val onDisconnect: (Throwable?) -> Unit = {}
) {
    private val sendMutex = Mutex()
    private val writeLock = Any()
    private var txSeq: Int = 0
    private var lastRxSeq: Int = -1

    @Volatile
    private var pendingAckExpectedSeq: Int = -1
    @Volatile
    private var pendingAckDeferred: CompletableDeferred<Boolean>? = null

    private val _incomingFrames = MutableSharedFlow<MdrFrame>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingFrames: SharedFlow<MdrFrame> = _incomingFrames.asSharedFlow()

    private var readerJob: Job? = null

    fun start() {
        if (readerJob?.isActive == true) return
        readerJob = scope.launch(Dispatchers.IO) {
            val parser = MdrStreamParser()
            val readBuffer = ByteArray(1024)
            var error: Throwable? = null
            try {
                while (isActive) {
                    val count = transport.read(readBuffer)
                    if (count < 0) break
                    if (count == 0) continue
                    val frames = parser.feed(readBuffer, count)
                    for (frame in frames) {
                        handleIncomingFrame(frame)
                    }
                }
            } catch (t: Throwable) {
                error = t
            } finally {
                pendingAckDeferred?.complete(false)
                onDisconnect(error)
            }
        }
    }

    private fun handleIncomingFrame(frame: MdrFrame) {
        if (frame.dataType == MdrDataType.ACK) {
            onLog(
                ProtocolLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    direction = "RX",
                    table = "ACK",
                    label = "ACK seq=${frame.seqNo}",
                    hex = MdrFrameCodec.toHexString(MdrFrameCodec.encode(frame))
                )
            )
            if (frame.seqNo == pendingAckExpectedSeq) {
                pendingAckDeferred?.complete(true)
            }
            return
        }

        val tableTag = when (frame.dataType) {
            MdrDataType.DATA_MDR -> "T1"
            MdrDataType.DATA_MDR_NO2 -> "T2"
            else -> frame.dataType.name
        }
        onLog(
            ProtocolLogEntry(
                timestampMs = System.currentTimeMillis(),
                direction = "RX",
                table = tableTag,
                label = "seq=${frame.seqNo} cmd=0x${frame.payload.firstOrNull()?.let { "%02X".format(it) } ?: "--"}",
                hex = MdrFrameCodec.toHexString(frame.payload)
            )
        )

        // Immediately reply with ACK indicating next expected sequence number
        val nextExpectedSeq = 1 - (frame.seqNo and 0x01)
        sendAck(nextExpectedSeq)

        // Deduplicate retransmitted packets with identical sequence number
        if (frame.seqNo == lastRxSeq && frame.dataType != MdrDataType.SHOT) {
            return
        }
        lastRxSeq = frame.seqNo
        _incomingFrames.tryEmit(frame)
    }

    private fun sendAck(nextExpectedSeq: Int) {
        val ackBytes = MdrFrameCodec.encode(MdrDataType.ACK, nextExpectedSeq, ByteArray(0))
        try {
            synchronized(writeLock) {
                transport.write(ackBytes)
            }
        } catch (_: IOException) {
            // Connection dropped; reader loop will notify disconnect
        }
    }

    /**
     * Reliably sends a Protocol V2 command frame and waits for matching ACK.
     */
    suspend fun sendCommand(
        dataType: MdrDataType,
        payload: ByteArray,
        label: String = ""
    ): Boolean = sendMutex.withLock {
        val currentSeq = txSeq
        val expectedAckSeq = 1 - currentSeq
        val wireBytes = MdrFrameCodec.encode(dataType, currentSeq, payload)
        val tableTag = if (dataType == MdrDataType.DATA_MDR_NO2) "T2" else "T1"

        repeat(maxRetries + 1) { attempt ->
            val deferred = CompletableDeferred<Boolean>()
            pendingAckExpectedSeq = expectedAckSeq
            pendingAckDeferred = deferred

            try {
                synchronized(writeLock) {
                    transport.write(wireBytes)
                }
                onLog(
                    ProtocolLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        direction = "TX",
                        table = tableTag,
                        label = buildString {
                            append(label.ifBlank { "cmd=0x${payload.firstOrNull()?.let { "%02X".format(it) } ?: "--"}" })
                            if (attempt > 0) append(" (retry $attempt)")
                        },
                        hex = MdrFrameCodec.toHexString(payload)
                    )
                )
            } catch (e: IOException) {
                pendingAckDeferred = null
                return@withLock false
            }

            val acked = withTimeoutOrNull(ackTimeoutMs) { deferred.await() } == true
            pendingAckDeferred = null
            if (acked) {
                txSeq = expectedAckSeq
                return@withLock true
            }
        }
        false
    }

    fun close() {
        readerJob?.cancel()
        readerJob = null
        pendingAckDeferred?.complete(false)
        try {
            transport.close()
        } catch (_: Throwable) {
        }
    }
}
