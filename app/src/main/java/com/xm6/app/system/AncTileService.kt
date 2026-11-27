package com.xm6.app.system

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.xm6.app.Xm6Application
import com.xm6.app.data.ConnectionState
import com.xm6.app.data.HeadphoneDisplayState
import com.xm6.app.data.HeadphoneState
import com.xm6.app.data.NoiseControlMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * HyperOS / Android Quick Settings Tile for one-tap ANC / Ambient toggling
 * without opening the main app.
 */
class AncTileService : TileService() {

    companion object {
        fun requestRefresh(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, AncTileService::class.java)
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateCollectJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val app = application as? Xm6Application ?: return
        val repo = app.repository
        updateTileUi(repo.state.value)
        stateCollectJob?.cancel()
        stateCollectJob = scope.launch {
            repo.state.collect { st -> updateTileUi(st) }
        }
        if (!repo.state.value.isConnected) {
            app.triggerBackgroundSync(reason = "qs_tile")
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        stateCollectJob?.cancel()
        stateCollectJob = null
    }

    override fun onClick() {
        super.onClick()
        val repo = (application as? Xm6Application)?.repository ?: return
        scope.launch {
            qsTile?.let { tile ->
                tile.subtitle = "切换中..."
                tile.updateTile()
            }
            val updated = repo.executeTransientNoiseMode(targetMode = null)
            updateTileUi(updated)
        }
    }

    private fun updateTileUi(state: HeadphoneState) {
        val tile = qsTile ?: return
        when (state.displayState) {
            HeadphoneDisplayState.DISCONNECTED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "XM6"
                tile.subtitle = if (state.connectionState == ConnectionState.CONNECTING) "自动同步中..." else "未连接"
            }
            HeadphoneDisplayState.NOT_WORN -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "XM6 · 未佩戴"
                tile.subtitle = "戴上恢复 · ${state.batteryLevel}%"
            }
            HeadphoneDisplayState.WEARING -> {
                tile.state = if (state.noiseMode == NoiseControlMode.OFF) {
                    Tile.STATE_INACTIVE
                } else {
                    Tile.STATE_ACTIVE
                }
                tile.label = "XM6 · ${state.noiseMode.label}"
                tile.subtitle = when (state.noiseMode) {
                    NoiseControlMode.ANC -> "自适应 · ${state.batteryLevel}%"
                    NoiseControlMode.AMBIENT -> "${state.ambientLevel}级 · ${state.batteryLevel}%"
                    NoiseControlMode.OFF -> "${state.batteryLevel}%"
                }
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
