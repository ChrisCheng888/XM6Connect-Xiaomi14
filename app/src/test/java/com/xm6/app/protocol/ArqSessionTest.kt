package com.xm6.app.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList

class ArqSessionTest {

    private class SimulatedTransport(
        private val onClientWrite: (ByteArray) -> Unit
    ) : ByteTransport {
        private val rxOut = PipedOutputStream()
        private val rxIn = PipedInputStream(rxOut, 4096)

        fun injectFromHeadset(bytes: ByteArray) {
            rxOut.write(bytes)
            rxOut.flush()
        }

        override fun write(data: ByteArray) {
            onClientWrite(data)
        }

        override fun read(buffer: ByteArray): Int = rxIn.read(buffer)

        override fun close() {
            runCatching { rxOut.close() }
            runCatching { rxIn.close() }
        }
    }

    @Test
    fun arqSession_alternatesSequenceAndHandlesAckAndIncomingNotify() = runBlocking {
        val sentFrames = CopyOnWriteArrayList<MdrFrame>()
        lateinit var transport: SimulatedTransport

        transport = SimulatedTransport { raw ->
            val frame = MdrFrameCodec.decode(raw) ?: return@SimulatedTransport
            sentFrames.add(frame)
            // Simulated XM6 headset auto-replies ACK for every DATA_MDR command
            if (frame.dataType == MdrDataType.DATA_MDR) {
                val ackSeq = 1 - (frame.seqNo and 0x01)
                transport.injectFromHeadset(MdrFrameCodec.encode(MdrDataType.ACK, ackSeq, ByteArray(0)))
            }
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val session = ArqSession(transport = transport, scope = scope, ackTimeoutMs = 500L)
        session.start()

        try {
            val ok1 = session.sendCommand(MdrDataType.DATA_MDR, byteArrayOf(0x00, 0x00), "Cmd1")
            val ok2 = session.sendCommand(MdrDataType.DATA_MDR, byteArrayOf(0x22, 0x00), "Cmd2")

            assertTrue(ok1)
            assertTrue(ok2)

            val dataSent = sentFrames.filter { it.dataType == MdrDataType.DATA_MDR }
            assertEquals(2, dataSent.size)
            assertEquals(0, dataSent[0].seqNo)
            assertEquals(1, dataSent[1].seqNo)

            // Verify incoming notification from headset is emitted AND triggers ACK from client
            val received = CompletableDeferred<MdrFrame>()
            val job = launch {
                session.incomingFrames.collect { frame ->
                    if (!received.isCompleted) received.complete(frame)
                }
            }
            val batteryNtfy = MdrFrameCodec.encode(
                MdrDataType.DATA_MDR,
                seqNo = 0,
                payload = byteArrayOf(0x25, 0x00, 88, 0x00)
            )
            transport.injectFromHeadset(batteryNtfy)

            val frame = withTimeout(1500L) { received.await() }
            job.cancel()
            assertEquals(88, frame.payload[2].toInt() and 0xFF)
            assertTrue(sentFrames.any { it.dataType == MdrDataType.ACK && it.seqNo == 1 })
        } finally {
            session.close()
            scope.cancel()
        }
    }
}
