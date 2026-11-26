package com.xm6.app.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.xm6.app.Xm6Application
import com.xm6.app.data.HeadphoneState
import com.xm6.app.data.NoiseControlMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * HyperOS / Android Quick Settings Tile for one-tap ANC / Ambient toggling
 * without opening the main app.
 */
class AncTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        val repo = (application as? Xm6Application)?.repository ?: return
        updateTileUi(repo.state.value)
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
        if (!state.isConnected) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "XM6"
            tile.subtitle = "点击连接"
        } else {
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
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
