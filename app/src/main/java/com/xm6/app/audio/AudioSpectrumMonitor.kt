package com.xm6.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10

/**
 * Foreground-only global audio output FFT spectrum & calibrated sound pressure monitor
 * built on Android's Visualizer(audioSession = 0). Automatically releases when leaving
 * the Home screen or going to background for zero background drain.
 */
class AudioSpectrumMonitor(private val context: Context) {

    companion object {
        private const val TAG = "XM6Proto"
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _snapshot = MutableStateFlow(LiveAcousticSnapshot())
    val snapshot: StateFlow<LiveAcousticSnapshot> = _snapshot.asStateFlow()

    private var visualizer: Visualizer? = null
    private var decayJob: Job? = null
    private var isRunning: Boolean = false

    @Volatile
    private var lastFrameAtMs: Long = 0L
    private var smoothBands = FloatArray(SpectrumDsp.BAND_COUNT)
    private var smoothPeaks = FloatArray(SpectrumDsp.BAND_COUNT)
    private var peakHoldFrames = IntArray(SpectrumDsp.BAND_COUNT)
    private var peakVelocities = FloatArray(SpectrumDsp.BAND_COUNT)
    private var smoothedSpl: Float? = null
    private var sessionPeakSpl: Int? = null
    private val peakRmsHolder = Visualizer.MeasurementPeakRms()

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Synchronized
    fun start() {
        if (isRunning) return
        if (!hasRecordAudioPermission()) {
            _snapshot.value = LiveAcousticSnapshot()
            return
        }

        try {
            val vis = Visualizer(0).apply {
                val range = Visualizer.getCaptureSizeRange()
                val desired = 1024.coerceIn(range[0], range[1])
                captureSize = desired
                runCatching {
                    measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
                }
                val rate = Visualizer.getMaxCaptureRate().coerceAtMost(20_000)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft != null && v != null) {
                                processFftFrame(v, fft, samplingRate / 1000)
                            }
                        }
                    },
                    rate,
                    false,
                    true
                )
                enabled = true
            }
            visualizer = vis
            isRunning = true
            lastFrameAtMs = System.currentTimeMillis()
            startIdleDecayWatcher()
            Log.d(TAG, "AudioSpectrumMonitor started (captureSize=${vis.captureSize}, rate=${Visualizer.getMaxCaptureRate()})")
        } catch (t: Throwable) {
            Log.w(TAG, "AudioSpectrumMonitor start failed: ${t.message}")
            stopInternal()
            _snapshot.value = LiveAcousticSnapshot(visualizerError = "系统音频通道暂不可用")
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning && visualizer == null) return
        stopInternal()
        smoothBands = FloatArray(SpectrumDsp.BAND_COUNT)
        smoothPeaks = FloatArray(SpectrumDsp.BAND_COUNT)
        peakHoldFrames = IntArray(SpectrumDsp.BAND_COUNT)
        peakVelocities = FloatArray(SpectrumDsp.BAND_COUNT)
        smoothedSpl = null
        _snapshot.value = LiveAcousticSnapshot(peakSplDb = sessionPeakSpl)
        Log.d(TAG, "AudioSpectrumMonitor stopped & released")
    }

    fun resetSessionPeak() {
        sessionPeakSpl = null
        _snapshot.value = _snapshot.value.copy(peakSplDb = null)
    }

    private fun stopInternal() {
        isRunning = false
        decayJob?.cancel()
        decayJob = null
        visualizer?.let { vis ->
            runCatching { vis.enabled = false }
            runCatching { vis.setDataCaptureListener(null, 0, false, false) }
            runCatching { vis.release() }
        }
        visualizer = null
    }

    private fun startIdleDecayWatcher() {
        decayJob?.cancel()
        decayJob = scope.launch {
            while (isActive && isRunning) {
                delay(65L)
                val silenceElapsed = System.currentTimeMillis() - lastFrameAtMs
                if (silenceElapsed > 260L && (smoothBands.any { it > 0.01f } || smoothPeaks.any { it > 0.01f } || smoothedSpl != null)) {
                    val zeros = FloatArray(SpectrumDsp.BAND_COUNT)
                    val (nb, np) = SpectrumDsp.applyBallisticsWithGravity(
                        target = zeros,
                        prevBands = smoothBands,
                        prevPeaks = smoothPeaks,
                        holdFrames = peakHoldFrames,
                        peakVelocities = peakVelocities
                    )
                    smoothBands = nb
                    smoothPeaks = np
                    if (nb.all { it <= 0.01f }) {
                        smoothedSpl = null
                    }
                    val curInt = SpectrumDsp.roundSpl(smoothedSpl)
                    _snapshot.value = LiveAcousticSnapshot(
                        bands = nb.toList(),
                        peaks = np.toList(),
                        isPlaying = false,
                        currentSplDb = curInt,
                        peakSplDb = sessionPeakSpl,
                        zone = SpectrumDsp.classifySoundPressureZone(curInt)
                    )
                }
            }
        }
    }

    private fun processFftFrame(vis: Visualizer, fft: ByteArray, samplingRateHz: Int) {
        lastFrameAtMs = System.currentTimeMillis()
        val rawBands = SpectrumDsp.computeLogBands(fft, samplingRateHz)
        val (nextBands, nextPeaks) = SpectrumDsp.applyBallisticsWithGravity(
            target = rawBands,
            prevBands = smoothBands,
            prevPeaks = smoothPeaks,
            holdFrames = peakHoldFrames,
            peakVelocities = peakVelocities
        )
        smoothBands = nextBands
        smoothPeaks = nextPeaks

        val musicActive = audioManager?.isMusicActive == true
        val hasEnergy = nextBands.any { it > 0.03f }

        // Prefer hardware AudioFlinger RMS (mB -> dBFS), falling back cleanly to FFT RMS
        val rmsDbfs = readMeasurementRmsDbfs(vis) ?: SpectrumDsp.estimateRmsDbfsFromFft(fft)
        val volDb = readBluetoothVolumeAttenuationDb()

        val rawSpl = SpectrumDsp.estimateSplDb(
            rmsDbfs = rmsDbfs,
            volumeAttenuationDb = volDb,
            isMusicActive = musicActive || hasEnergy
        )
        smoothedSpl = if (rawSpl != null) {
            SpectrumDsp.smoothSpl(smoothedSpl, rawSpl)
        } else {
            null
        }

        val splInt = SpectrumDsp.roundSpl(smoothedSpl)
        if (splInt != null) {
            val prevPeak = sessionPeakSpl
            if (prevPeak == null || splInt > prevPeak) {
                sessionPeakSpl = splInt
            }
        }

        _snapshot.value = LiveAcousticSnapshot(
            bands = nextBands.toList(),
            peaks = nextPeaks.toList(),
            isPlaying = musicActive || hasEnergy,
            currentSplDb = splInt,
            peakSplDb = sessionPeakSpl,
            zone = SpectrumDsp.classifySoundPressureZone(splInt),
            isHardwareSpl = false
        )
    }

    private fun readMeasurementRmsDbfs(vis: Visualizer): Float? {
        return runCatching {
            val status = vis.getMeasurementPeakRms(peakRmsHolder)
            if (status == Visualizer.SUCCESS && peakRmsHolder.mRms > -9500) {
                (peakRmsHolder.mRms / 100f).coerceIn(-96f, 0f)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun readBluetoothVolumeAttenuationDb(): Float {
        val am = audioManager ?: return -12f
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (cur <= 0) return -96f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val exact = runCatching {
                am.getStreamVolumeDb(
                    AudioManager.STREAM_MUSIC,
                    cur,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                )
            }.getOrNull()
            if (exact != null && exact.isFinite() && exact <= 0.5f && exact > -90f) {
                return exact.coerceAtMost(0f)
            }
        }
        // Smooth perceptual curve fallback: max volume = 0 dB, half volume ≈ -14 dB
        val frac = (cur.toFloat() / maxVol.toFloat()).coerceIn(0.01f, 1f)
        return (28.0 * log10(frac.toDouble())).toFloat().coerceIn(-60f, 0f)
    }
}
