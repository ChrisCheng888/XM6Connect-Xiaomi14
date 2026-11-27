package com.xm6.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumDspTest {

    @Test
    fun computeLogBands_mapsLowAndHighFrequencyBinsInto48Bands() {
        val fft = ByteArray(1024)
        // Inject strong ~94 Hz bass energy (bin k=2 at 48kHz / 1024 = 46.875 Hz/bin)
        fft[4] = 90
        fft[5] = 0
        // Inject strong ~4.5 kHz treble energy (bin k=96)
        fft[192] = 60
        fft[193] = 20

        val bands = SpectrumDsp.computeLogBands(fft, samplingRateHz = 48_000)
        assertEquals(48, bands.size)
        // Low bands should show strong bass energy
        assertTrue(bands.slice(0..8).max() > 0.5f)
        // High bands should show treble energy
        assertTrue(bands.slice(32..40).max() > 0.4f)
        // Unused mid-band should remain near zero
        assertTrue(bands[20] < 0.05f)
    }

    @Test
    fun estimateSplDb_scalesWithVolumeAndClassifiesWhoZones() {
        assertNull(SpectrumDsp.estimateSplDb(rmsDbfs = -90f, volumeAttenuationDb = -10f, isMusicActive = false))

        val quiet = SpectrumDsp.estimateSplDb(rmsDbfs = -14f, volumeAttenuationDb = -24f, isMusicActive = true)!!
        assertEquals(SoundPressureZone.SAFE, SpectrumDsp.classifySoundPressureZone(SpectrumDsp.roundSpl(quiet)))

        val moderate = SpectrumDsp.estimateSplDb(rmsDbfs = -12f, volumeAttenuationDb = -12f, isMusicActive = true)!!
        assertEquals(SoundPressureZone.MODERATE, SpectrumDsp.classifySoundPressureZone(SpectrumDsp.roundSpl(moderate)))

        val loud = SpectrumDsp.estimateSplDb(rmsDbfs = -8f, volumeAttenuationDb = -3f, isMusicActive = true)!!
        assertEquals(SoundPressureZone.HIGH, SpectrumDsp.classifySoundPressureZone(SpectrumDsp.roundSpl(loud)))
    }

    @Test
    fun applyBallisticsWithGravity_hangsPeaksBrieflyThenDropsUnderGravity() {
        val zeros = FloatArray(SpectrumDsp.BAND_COUNT)
        val holds = IntArray(SpectrumDsp.BAND_COUNT)
        val vels = FloatArray(SpectrumDsp.BAND_COUNT)
        val spike = FloatArray(SpectrumDsp.BAND_COUNT) { if (it == 12) 0.92f else 0f }

        val (b1, p1) = SpectrumDsp.applyBallisticsWithGravity(spike, zeros, zeros, holds, vels, hangFrameCount = 2)
        assertTrue(b1[12] > 0.6f)
        assertEquals(b1[12], p1[12], 0.001f)

        // Frame 2: bar drops, peak hangs suspended!
        val (b2, p2) = SpectrumDsp.applyBallisticsWithGravity(zeros, b1, p1, holds, vels, hangFrameCount = 2)
        assertTrue(b2[12] < b1[12])
        assertEquals(p1[12], p2[12], 0.001f)

        // Frame 3: still hanging (second hang frame)
        val (b3, p3) = SpectrumDsp.applyBallisticsWithGravity(zeros, b2, p2, holds, vels, hangFrameCount = 2)
        assertEquals(p1[12], p3[12], 0.001f)

        // Frame 4: hold expired -> peak falls gracefully under gravity
        val (_, p4) = SpectrumDsp.applyBallisticsWithGravity(zeros, b3, p3, holds, vels, hangFrameCount = 2)
        assertTrue(p4[12] < p3[12])
    }
}
