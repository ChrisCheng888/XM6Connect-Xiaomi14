package com.xm6.app.audio

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class SoundPressureZone(val label: String) {
    SILENT("静默"),
    SAFE("安全"),
    MODERATE("适中"),
    HIGH("偏高")
}

data class LiveAcousticSnapshot(
    val bands: List<Float> = List(SpectrumDsp.BAND_COUNT) { 0f },
    val peaks: List<Float> = List(SpectrumDsp.BAND_COUNT) { 0f },
    val isPlaying: Boolean = false,
    val currentSplDb: Int? = null,
    val peakSplDb: Int? = null,
    val zone: SoundPressureZone = SoundPressureZone.SILENT,
    val isHardwareSpl: Boolean = false,
    val visualizerError: String? = null
)

/**
 * Pure-Kotlin real-time DSP math for 48-band high-density frequency spectrum analysis
 * with sub-bin interpolation, floating gravity peak-hold bricks, and WH-1000XM6-calibrated
 * sound pressure (dB SPL) + WHO exposure zone estimation.
 */
object SpectrumDsp {
    const val BAND_COUNT = 48
    const val MIN_FREQ_HZ = 50.0
    const val MAX_FREQ_HZ = 15_500.0

    // WH-1000XM6 acoustic ceiling reference at 0 dBFS digital full-scale & max A2DP volume (~104.5 dB SPL)
    const val XM6_FULL_SCALE_SPL_DB = 104.5f
    const val MIN_DISPLAY_SPL_DB = 35f
    const val MAX_DISPLAY_SPL_DB = 105f

    /**
     * Converts Android Visualizer FFT byte array into [BAND_COUNT] crisp normalized [0f..1f] amplitudes.
     * Uses fractional bin linear interpolation in the sub-bass range so dense 48-bar visualizers
     * stay fluid and distinct without flat staircase artifacts.
     */
    fun computeLogBands(
        fft: ByteArray,
        samplingRateHz: Int,
        bandCount: Int = BAND_COUNT
    ): FloatArray {
        val result = FloatArray(bandCount)
        val n = fft.size
        if (n < 8 || samplingRateHz <= 0) return result

        val halfN = n / 2
        val binHz = samplingRateHz.toDouble() / n.toDouble()
        val nyquistHz = samplingRateHz / 2.0
        val upperHz = MAX_FREQ_HZ.coerceAtMost(nyquistHz * 0.94)
        val lowerHz = MIN_FREQ_HZ.coerceAtMost(upperHz * 0.5)
        val ratio = upperHz / lowerHz

        fun binMag(k: Int): Double {
            val idx = k.coerceIn(1, halfN - 1)
            return hypot(fft[2 * idx].toDouble(), fft[2 * idx + 1].toDouble())
        }

        for (band in 0 until bandCount) {
            val fLow = lowerHz * ratio.pow(band.toDouble() / bandCount.toDouble())
            val fHigh = lowerHz * ratio.pow((band + 1).toDouble() / bandCount.toDouble())
            val fMid = sqrt(fLow * fHigh)

            val lowBinF = (fLow / binHz).coerceIn(1.0, (halfN - 1).toDouble())
            val highBinF = (fHigh / binHz).coerceIn(lowBinF, (halfN - 1).toDouble())

            val rmsMag = if (highBinF - lowBinF < 1.0) {
                // Smooth fractional interpolation between neighboring FFT bins
                val centerBinF = (fMid / binHz).coerceIn(1.0, (halfN - 1).toDouble())
                val lo = floor(centerBinF).toInt().coerceIn(1, halfN - 1)
                val hi = (lo + 1).coerceAtMost(halfN - 1)
                val frac = centerBinF - lo
                binMag(lo) * (1.0 - frac) + binMag(hi) * frac
            } else {
                val startBin = floor(lowBinF).toInt().coerceIn(1, halfN - 1)
                val endBin = max(startBin, floor(highBinF).toInt().coerceIn(1, halfN - 1))
                var sumSq = 0.0
                var count = 0
                for (k in startBin..endBin) {
                    val mag = binMag(k)
                    sumSq += mag * mag
                    count++
                }
                if (count > 0) sqrt(sumSq / count) else 0.0
            }

            if (rmsMag < 0.8) {
                result[band] = 0f
                continue
            }

            // Perceptual pink-noise compensation (+1.55 dB/octave) so highs sparkle crisply alongside bass
            val octavesAbove80 = (ln(fMid / 80.0) / ln(2.0)).coerceAtLeast(0.0)
            val tiltDb = octavesAbove80 * 1.55
            val db = 20.0 * log10(rmsMag / 128.0) + tiltDb

            val norm = ((db + 42.0) / 45.0).coerceIn(0.0, 1.0)
            result[band] = norm.pow(1.08).toFloat()
        }
        return result
    }

    /**
     * Computes an overall digital RMS level in dBFS (-96..0 dBFS) from raw Visualizer FFT bytes.
     */
    fun estimateRmsDbfsFromFft(fft: ByteArray): Float {
        val n = fft.size
        if (n < 8) return -96f
        val halfN = n / 2
        var sumSq = 0.0
        for (k in 1 until halfN) {
            val re = fft[2 * k].toDouble()
            val im = fft[2 * k + 1].toDouble()
            sumSq += re * re + im * im
        }
        val meanSq = sumSq / (halfN - 1).coerceAtLeast(1)
        val rms = sqrt(meanSq)
        if (rms < 0.35) return -96f
        val db = 20.0 * log10(rms / 64.0)
        return db.toFloat().coerceIn(-96f, 0f)
    }

    /**
     * Fast-attack, crisp-decay bar ballistics paired with authentic peak-hold hang + gravity drop
     * so floating peak bricks briefly suspend above peaks before gracefully falling.
     */
    fun applyBallisticsWithGravity(
        target: FloatArray,
        prevBands: FloatArray,
        prevPeaks: FloatArray,
        holdFrames: IntArray,
        peakVelocities: FloatArray,
        attackAlpha: Float = 0.72f,
        decayAlpha: Float = 0.34f,
        hangFrameCount: Int = 6,
        gravityAccel: Float = 0.0075f
    ): Pair<FloatArray, FloatArray> {
        val nextBands = FloatArray(target.size)
        val nextPeaks = FloatArray(target.size)
        for (i in target.indices) {
            val prev = prevBands.getOrElse(i) { 0f }
            val raw = target[i].coerceIn(0f, 1f)
            val smoothed = if (raw >= prev) {
                prev + (raw - prev) * attackAlpha
            } else {
                prev + (raw - prev) * decayAlpha
            }
            nextBands[i] = if (smoothed < 0.01f) 0f else smoothed

            val prevPeak = prevPeaks.getOrElse(i) { 0f }
            if (nextBands[i] >= prevPeak) {
                nextPeaks[i] = nextBands[i]
                if (i < holdFrames.size) holdFrames[i] = hangFrameCount
                if (i < peakVelocities.size) peakVelocities[i] = 0f
            } else {
                val remainingHold = if (i < holdFrames.size) holdFrames[i] else 0
                if (remainingHold > 0) {
                    holdFrames[i] = remainingHold - 1
                    nextPeaks[i] = prevPeak
                } else {
                    val v = (if (i < peakVelocities.size) peakVelocities[i] else 0f) + gravityAccel
                    if (i < peakVelocities.size) peakVelocities[i] = v
                    val dropped = (prevPeak - v).coerceAtLeast(nextBands[i])
                    nextPeaks[i] = if (dropped < 0.01f) 0f else dropped
                }
            }
        }
        return nextBands to nextPeaks
    }

    fun estimateSplDb(
        rmsDbfs: Float,
        volumeAttenuationDb: Float,
        isMusicActive: Boolean
    ): Float? {
        if (!isMusicActive && rmsDbfs < -65f) return null
        if (rmsDbfs < -74f || volumeAttenuationDb <= -80f) return null
        val spl = XM6_FULL_SCALE_SPL_DB + volumeAttenuationDb.coerceAtMost(0f) + rmsDbfs.coerceIn(-74f, 0f)
        return spl.coerceIn(MIN_DISPLAY_SPL_DB, MAX_DISPLAY_SPL_DB)
    }

    fun classifySoundPressureZone(splDb: Int?): SoundPressureZone {
        if (splDb == null) return SoundPressureZone.SILENT
        return when {
            splDb < 75 -> SoundPressureZone.SAFE
            splDb < 85 -> SoundPressureZone.MODERATE
            else -> SoundPressureZone.HIGH
        }
    }

    fun smoothSpl(previous: Float?, target: Float, alpha: Float = 0.26f): Float {
        if (previous == null) return target
        return previous + (target - previous) * alpha
    }

    fun splProgress(splDb: Int?): Float {
        if (splDb == null) return 0f
        return ((splDb - MIN_DISPLAY_SPL_DB) / (MAX_DISPLAY_SPL_DB - MIN_DISPLAY_SPL_DB)).coerceIn(0f, 1f)
    }

    fun roundSpl(value: Float?): Int? = value?.roundToInt()
}
