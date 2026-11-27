package com.xm6.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xm6.app.audio.LiveAcousticSnapshot
import com.xm6.app.audio.SoundPressureZone
import com.xm6.app.audio.SpectrumDsp
import com.xm6.app.data.AudioCodec
import com.xm6.app.data.AutoAmbientSensitivity
import com.xm6.app.data.BgmRoomSize
import com.xm6.app.data.ConnectionQualityMode
import com.xm6.app.data.ConnectionState
import com.xm6.app.data.EqPreset
import com.xm6.app.data.HeadphoneDisplayState
import com.xm6.app.data.HeadphoneState
import com.xm6.app.data.ListeningMode
import com.xm6.app.data.NoiseControlMode
import com.xm6.app.data.UserPrefs
import com.xm6.app.ui.components.AmbientThickScrubBar
import com.xm6.app.ui.components.CircularBatteryGauge
import com.xm6.app.ui.components.ExpressiveCard
import com.xm6.app.ui.components.SlidingNoiseModeSelector
import com.xm6.app.ui.theme.LocalIsDarkTheme

@Composable
fun HomeScreen(
    state: HeadphoneState,
    prefs: UserPrefs,
    presets: List<EqPreset>,
    acoustic: LiveAcousticSnapshot,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    onResetSplPeak: () -> Unit,
    onConnectClick: () -> Unit,
    onCodecPillClick: () -> Unit,
    onFixLeAudioClick: () -> Unit,
    onSelectNoiseMode: (NoiseControlMode) -> Unit,
    onAmbientLevelChange: (Int) -> Unit,
    onToggleFocusOnVoice: () -> Unit,
    onToggleAutoAmbient: () -> Unit,
    onSelectAutoAmbientSensitivity: (AutoAmbientSensitivity) -> Unit,
    onSelectListeningMode: (ListeningMode) -> Unit,
    onSelectBgmRoom: (BgmRoomSize) -> Unit,
    onToggleDsee: () -> Unit,
    onToggleSpeakToChat: () -> Unit,
    onCycleSpeakTimeout: () -> Unit,
    onToggleTouchSensor: () -> Unit,
    onSelectEqPreset: (EqPreset) -> Unit,
    onOpenEqTab: () -> Unit
) {
    val connected = state.isConnected
    val displayState = state.displayState
    val controlsAlpha = when (displayState) {
        HeadphoneDisplayState.WEARING -> 1f
        HeadphoneDisplayState.NOT_WORN -> 0.86f
        HeadphoneDisplayState.DISCONNECTED -> 0.42f
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 104.dp)
    ) {
        // 1. Compact 3-State Hero Status Card
        HeroStatusCard(
            state = state,
            prefs = prefs,
            onConnectClick = onConnectClick,
            onCodecPillClick = onCodecPillClick,
            onFixLeAudioClick = onFixLeAudioClick
        )

        // 2. Studio-Grade Live Frequency Spectrum & Sound Pressure Card
        LiveAcousticMonitorCard(
            displayState = displayState,
            acoustic = acoustic,
            hasAudioPermission = hasAudioPermission,
            onRequestAudioPermission = onRequestAudioPermission,
            onResetSplPeak = onResetSplPeak,
            modifier = Modifier.alpha(controlsAlpha)
        )

        // 3. Sound Control Card (Clean — zero redundant ANC status row)
        ExpressiveCard(modifier = Modifier.alpha(controlsAlpha)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                SlidingNoiseModeSelector(
                    selectedMode = state.noiseMode,
                    enabled = connected,
                    onSelect = onSelectNoiseMode
                )

                AnimatedVisibility(
                    visible = displayState == HeadphoneDisplayState.NOT_WORN && state.noiseMode != NoiseControlMode.OFF
                ) {
                    val isDark = LocalIsDarkTheme.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF3E2C0E) else Color(0xFFFDEBC2)
                        ) {
                            Text(
                                text = "摘下暂挂",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = state.noiseMode == NoiseControlMode.AMBIENT) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AmbientThickScrubBar(
                            level = state.ambientLevel,
                            enabled = connected,
                            dimmed = state.autoAmbientSound,
                            onLevelChange = onAmbientLevelChange
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ToggleChipPill(
                                label = "关注语音",
                                active = state.focusOnVoice,
                                enabled = connected,
                                onClick = onToggleFocusOnVoice
                            )
                            if (prefs.showExperimental) {
                                ToggleChipPill(
                                    label = "自动环境声",
                                    active = state.autoAmbientSound,
                                    enabled = connected,
                                    onClick = onToggleAutoAmbient
                                )
                                if (state.autoAmbientSound) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        AutoAmbientSensitivity.entries.forEach { sens ->
                                            val sel = state.autoAmbientSensitivity == sens
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable(enabled = connected) {
                                                        onSelectAutoAmbientSensitivity(sens)
                                                    }
                                            ) {
                                                Text(
                                                    text = sens.label,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Silky 1-Tap Direct Listening Mode Card (3-way slider + clean BGM room pills)
        ExpressiveCard(modifier = Modifier.alpha(controlsAlpha)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                ListeningModeSlidingSelector(
                    selectedMode = state.listeningMode,
                    enabled = connected,
                    onSelect = onSelectListeningMode
                )

                AnimatedVisibility(
                    visible = state.listeningMode == ListeningMode.BACKGROUND_MUSIC,
                    enter = fadeIn(animationSpec = tween(180)) + expandVertically(
                        animationSpec = spring(stiffness = 440f, dampingRatio = 0.88f)
                    ),
                    exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(
                        animationSpec = spring(stiffness = 480f, dampingRatio = 0.9f)
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BgmRoomSize.entries.forEach { room ->
                            val active = state.bgmRoom == room
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = connected) { onSelectBgmRoom(room) }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                ) {
                                    Text(
                                        text = room.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. 3-Column Quick Action Toggle Row (DSEE / Speak-to-Chat / Touch Panel)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alpha(controlsAlpha)
        ) {
            QuickActionTile(
                title = "DSEE",
                status = when {
                    !state.dseeExtreme -> "关"
                    !state.dseeActive -> "暂停"
                    else -> "开"
                },
                active = state.dseeExtreme,
                icon = Icons.Rounded.GraphicEq,
                enabled = connected,
                onClick = onToggleDsee,
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                title = "智能免摘",
                status = if (state.speakToChat) state.speakToChatTimeout.label else "关",
                active = state.speakToChat,
                icon = Icons.Rounded.ChatBubble,
                enabled = connected,
                onClick = onToggleSpeakToChat,
                statusPillClick = if (state.speakToChat) onCycleSpeakTimeout else null,
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                title = "触控",
                status = if (state.touchSensor) "开" else "关",
                active = state.touchSensor,
                icon = Icons.Rounded.TouchApp,
                enabled = connected,
                onClick = onToggleTouchSensor,
                modifier = Modifier.weight(1f)
            )
        }

        // 6. Clean Expressive EQ Preset Deck (compact tiles with mini waveforms — no large wave preview)
        ExpressiveCard(modifier = Modifier.alpha(controlsAlpha)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "均衡器",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onOpenEqTab() }
                    ) {
                        Text(
                            text = "微调 ›",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                        )
                    }
                }

                // Generous 2-column preset tile grid (each tile carries its own crisp mini EQ waveform)
                if (presets.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenEqTab() }
                    ) {
                        Text(
                            text = "+ 添加预设",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        presets.chunked(2).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { preset ->
                                    val active = state.activeEqPresetId == preset.id
                                    HomeEqPresetTile(
                                        preset = preset,
                                        active = active,
                                        enabled = connected,
                                        onClick = { onSelectEqPreset(preset) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatusCard(
    state: HeadphoneState,
    prefs: UserPrefs,
    onConnectClick: () -> Unit,
    onCodecPillClick: () -> Unit,
    onFixLeAudioClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val displayState = state.displayState
    val isConnecting = state.connectionState == ConnectionState.CONNECTING

    val heroBrush = when (displayState) {
        HeadphoneDisplayState.WEARING -> Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                MaterialTheme.colorScheme.surface
            )
        )
        HeadphoneDisplayState.NOT_WORN -> Brush.linearGradient(
            colors = if (isDark) {
                listOf(Color(0xFF33250F), MaterialTheme.colorScheme.surface)
            } else {
                listOf(Color(0xFFFFF2D6), MaterialTheme.colorScheme.surface)
            }
        )
        HeadphoneDisplayState.DISCONNECTED -> Brush.linearGradient(
            colors = if (isDark) {
                listOf(Color(0xFF1E232E), MaterialTheme.colorScheme.surface)
            } else {
                listOf(Color(0xFFECF1F8), MaterialTheme.colorScheme.surface)
            }
        )
    }

    val cardBorderColor = when (displayState) {
        HeadphoneDisplayState.NOT_WORN -> if (isDark) Color(0x44F59E0B) else Color(0x44D97706)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }

    Surface(
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = displayState,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "heroDisplayState"
        ) { currentState ->
            when (currentState) {
                HeadphoneDisplayState.DISCONNECTED -> {
                    DisconnectedHeroContent(
                        state = state,
                        isConnecting = isConnecting,
                        heroBrush = heroBrush,
                        onConnectClick = onConnectClick
                    )
                }

                HeadphoneDisplayState.NOT_WORN -> {
                    NotWornHeroContent(
                        state = state,
                        prefs = prefs,
                        isDark = isDark,
                        heroBrush = heroBrush,
                        onCodecPillClick = onCodecPillClick,
                        onFixLeAudioClick = onFixLeAudioClick
                    )
                }

                HeadphoneDisplayState.WEARING -> {
                    WearingHeroContent(
                        state = state,
                        prefs = prefs,
                        isDark = isDark,
                        heroBrush = heroBrush,
                        onCodecPillClick = onCodecPillClick,
                        onFixLeAudioClick = onFixLeAudioClick
                    )
                }
            }
        }
    }
}

@Composable
private fun WearingHeroContent(
    state: HeadphoneState,
    prefs: UserPrefs,
    isDark: Boolean,
    heroBrush: Brush,
    onCodecPillClick: () -> Unit,
    onFixLeAudioClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(heroBrush)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            CircularBatteryGauge(
                batteryLevel = state.batteryLevel,
                connected = true
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = state.deviceName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                val peerText = if (state.connectedPeers.isNotEmpty()) " · ${state.connectedPeers.size}台" else ""
                Text(
                    text = "v${state.firmwareVersion}$peerText",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        HeroCodecPill(
            state = state,
            prefs = prefs,
            isDark = isDark,
            onCodecPillClick = onCodecPillClick,
            onFixLeAudioClick = onFixLeAudioClick
        )
    }
}

@Composable
private fun NotWornHeroContent(
    state: HeadphoneState,
    prefs: UserPrefs,
    isDark: Boolean,
    heroBrush: Brush,
    onCodecPillClick: () -> Unit,
    onFixLeAudioClick: () -> Unit
) {
    val amberBg = if (isDark) Color(0xFF422E0D) else Color(0xFFFDE8B9)
    val amberText = if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(heroBrush)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            CircularBatteryGauge(
                batteryLevel = state.batteryLevel,
                connected = true
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = state.deviceName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = amberBg
                    ) {
                        Text(
                            text = "未佩戴",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = amberText,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "待机 · v${state.firmwareVersion}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        HeroCodecPill(
            state = state,
            prefs = prefs,
            isDark = isDark,
            onCodecPillClick = onCodecPillClick,
            onFixLeAudioClick = onFixLeAudioClick
        )
    }
}

@Composable
private fun DisconnectedHeroContent(
    state: HeadphoneState,
    isConnecting: Boolean,
    heroBrush: Brush,
    onConnectClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(heroBrush)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnecting) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnecting) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = "未连接",
                        tint = if (isConnecting) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = state.deviceName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (isConnecting) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (isConnecting) "同步中" else "未连接",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isConnecting) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isConnecting -> "同步中..."
                        state.errorMessage != null -> state.errorMessage
                        else -> "自动同步"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = !isConnecting) { onConnectClick() }
        ) {
            Text(
                text = if (isConnecting) "..." else "连接",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun HeroCodecPill(
    state: HeadphoneState,
    prefs: UserPrefs,
    isDark: Boolean,
    onCodecPillClick: () -> Unit,
    onFixLeAudioClick: () -> Unit
) {
    val isLe = state.isLeAudioConflict
    val codecText = when {
        isLe -> "LC3 · 切LDAC"
        !state.systemCodecSummary.isNullOrBlank() -> state.systemCodecSummary
        state.activeCodec == AudioCodec.LDAC -> "LDAC · ${prefs.ldacBitrateMode.shortLabel}"
        state.activeCodec != AudioCodec.UNKNOWN -> state.activeCodec.label
        state.connectionQuality == ConnectionQualityMode.SOUND_QUALITY -> "LDAC"
        else -> "AAC"
    }
    Surface(
        shape = CircleShape,
        color = if (isLe) {
            if (isDark) Color(0xFF473308) else Color(0xFFFFF1D6)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(CircleShape)
            .clickable {
                if (isLe) onFixLeAudioClick() else onCodecPillClick()
            }
    ) {
        Text(
            text = codecText,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isLe) {
                if (isDark) Color(0xFFFFD899) else Color(0xFF7A4B00)
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun LiveAcousticMonitorCard(
    displayState: HeadphoneDisplayState,
    acoustic: LiveAcousticSnapshot,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    onResetSplPeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val connected = displayState != HeadphoneDisplayState.DISCONNECTED

    val zoneAccent = when (acoustic.zone) {
        SoundPressureZone.SAFE -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
        SoundPressureZone.MODERATE -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
        SoundPressureZone.HIGH -> if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
        SoundPressureZone.SILENT -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val zonePillBg = when (acoustic.zone) {
        SoundPressureZone.SAFE -> if (isDark) Color(0xFF0D3828) else Color(0xFFD1FAE5)
        SoundPressureZone.MODERATE -> if (isDark) Color(0xFF422E0D) else Color(0xFFFEF3C7)
        SoundPressureZone.HIGH -> if (isDark) Color(0xFF451414) else Color(0xFFFEE2E2)
        SoundPressureZone.SILENT -> MaterialTheme.colorScheme.surfaceVariant
    }

    val statusDotColor = when {
        !connected -> MaterialTheme.colorScheme.outline
        displayState == HeadphoneDisplayState.NOT_WORN -> if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
        acoustic.isPlaying -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }

    val splProgress by animateFloatAsState(
        targetValue = if (connected && hasAudioPermission) SpectrumDsp.splProgress(acoustic.currentSplDb) else 0f,
        animationSpec = spring(stiffness = 340f, dampingRatio = 0.85f),
        label = "splBarProgress"
    )

    val stageBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Color(0xFF121826), Color(0xFF171F30))
        } else {
            listOf(Color(0xFFF3F7FE), Color(0xFFFFFFFF))
        }
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        shadowElevation = 2.dp,
        border = BorderStroke(
            1.dp,
            if (isDark) Color(0x22FFFFFF) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(stageBrush)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Minimalist 1-Line Telemetry Header: dB + Safety Pill | Peak or Enable Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (connected && hasAudioPermission && acoustic.currentSplDb != null) "${acoustic.currentSplDb}" else "--",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (connected && hasAudioPermission && acoustic.currentSplDb != null) zoneAccent
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "dB",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    if (connected && hasAudioPermission && acoustic.zone != SoundPressureZone.SILENT) {
                        Surface(
                            shape = CircleShape,
                            color = zonePillBg
                        ) {
                            Text(
                                text = acoustic.zone.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = zoneAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!hasAudioPermission) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onRequestAudioPermission() }
                    ) {
                        Text(
                            text = "开启频谱",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                } else if (acoustic.peakSplDb != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.55f else 0.85f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onResetSplPeak() }
                    ) {
                        Text(
                            text = "MAX ${acoustic.peakSplDb}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Enlarged 48-Band High-Density Sharp Peak-Hold Spectrum Stage
            LiveSpectrumCanvas(
                bands = if (connected && hasAudioPermission) acoustic.bands else List(SpectrumDsp.BAND_COUNT) { 0f },
                peaks = if (connected && hasAudioPermission) acoustic.peaks else List(SpectrumDsp.BAND_COUNT) { 0f },
                isDark = isDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
            )

            // Ultra-Slim Integrated WHO Sound Pressure Gauge Bar
            SoundPressureGaugeBar(
                progress = splProgress,
                activeColor = zoneAccent,
                isDark = isDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.5.dp)
            )
        }
    }
}

@Composable
private fun LiveSpectrumCanvas(
    bands: List<Float>,
    peaks: List<Float>,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = if (isDark) Color(0xF5FFFFFF) else Color(0xEB1E293B)
    val barBaseColor = if (isDark) Color(0xB8E2E8F0) else Color(0xB0334155)
    val idleBarColor = if (isDark) Color(0x38FFFFFF) else Color(0x2A334155)
    val peakBrickColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F172A)
    val warmNebula = if (isDark) Color(0x36D95338) else Color(0x22EA580C)
    val coolNebula = if (isDark) Color(0x382563EB) else Color(0x202563EB)
    val violetNebula = if (isDark) Color(0x286D28D9) else Color(0x166D28D9)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
    ) {
        val w = size.width
        val h = size.height
        val count = SpectrumDsp.BAND_COUNT
        if (w <= 0f || h <= 0f || count <= 0) return@Canvas

        // Atmospheric warm-crimson / deep-sapphire nebula ambient glow behind sharp bars
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(warmNebula, Color.Transparent),
                center = Offset(w * 0.18f, h * 0.36f),
                radius = w * 0.55f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(violetNebula, Color.Transparent),
                center = Offset(w * 0.48f, h * 0.65f),
                radius = w * 0.45f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(coolNebula, Color.Transparent),
                center = Offset(w * 0.84f, h * 0.32f),
                radius = w * 0.58f
            )
        )

        val padX = 4.dp.toPx()
        val padBottom = 3.dp.toPx()
        val padTop = 6.dp.toPx()
        val usableW = (w - padX * 2f).coerceAtLeast(1f)
        val usableH = (h - padTop - padBottom).coerceAtLeast(1f)
        val baseY = h - padBottom

        // High-density crisp rectangular bars with tight uniform hairline gaps
        val gap = 2.0.dp.toPx()
        val barW = ((usableW - gap * (count - 1)) / count).coerceAtLeast(1.8.dp.toPx())
        val minBarH = 2.5.dp.toPx()
        val brickH = 2.2.dp.toPx()
        val brickGap = 2.5.dp.toPx()

        val activeBarBrush = Brush.verticalGradient(
            colors = listOf(barColor, barBaseColor),
            startY = padTop,
            endY = baseY
        )

        for (i in 0 until count) {
            val amp = bands.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val peak = peaks.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val x = padX + i * (barW + gap)

            val barH = (minBarH + amp * (usableH - minBarH - brickH - brickGap)).coerceIn(minBarH, usableH)
            val topY = baseY - barH

            if (amp > 0.015f) {
                drawRect(
                    brush = activeBarBrush,
                    topLeft = Offset(x, topY),
                    size = Size(barW, barH)
                )
            } else {
                drawRect(
                    color = idleBarColor,
                    topLeft = Offset(x, topY),
                    size = Size(barW, barH)
                )
            }

            // Crisp horizontal floating peak-hold brick hanging above the bar
            if (peak > 0.03f) {
                val peakH = minBarH + peak * (usableH - minBarH - brickH - brickGap)
                val brickY = (baseY - peakH - brickH - brickGap).coerceIn(padTop, baseY - brickH)
                drawRect(
                    color = peakBrickColor,
                    topLeft = Offset(x, brickY),
                    size = Size(barW, brickH)
                )
            }
        }
    }
}

@Composable
private fun SoundPressureGaugeBar(
    progress: Float,
    activeColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val safeColor = if (isDark) Color(0xFF10B981) else Color(0xFF10B981)
    val modColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFF59E0B)
    val highColor = if (isDark) Color(0xFFEF4444) else Color(0xFFEF4444)
    val thumbRim = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = h / 2f

        // Background 3-zone WHO reference track (35..75 safe, 75..85 moderate, 85..105 high)
        val trackBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to safeColor.copy(alpha = if (isDark) 0.24f else 0.20f),
                0.57f to safeColor.copy(alpha = if (isDark) 0.24f else 0.20f),
                0.60f to modColor.copy(alpha = if (isDark) 0.28f else 0.24f),
                0.71f to modColor.copy(alpha = if (isDark) 0.28f else 0.24f),
                0.74f to highColor.copy(alpha = if (isDark) 0.30f else 0.25f),
                1.00f to highColor.copy(alpha = if (isDark) 0.30f else 0.25f)
            )
        )
        drawRoundRect(
            brush = trackBrush,
            cornerRadius = CornerRadius(r, r)
        )

        // Live active fill
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped > 0.01f) {
            val fillW = (w * clamped).coerceAtLeast(h)
            val fillBrush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to safeColor,
                    0.57f to safeColor,
                    0.71f to modColor,
                    0.85f to highColor,
                    1.00f to highColor
                ),
                startX = 0f,
                endX = w
            )
            drawRoundRect(
                brush = fillBrush,
                size = Size(fillW, h),
                cornerRadius = CornerRadius(r, r)
            )

            // Crisp indicator dot at live dB position
            val cx = (w * clamped).coerceIn(r, w - r)
            drawCircle(color = thumbRim, radius = r + 1.5.dp.toPx(), center = Offset(cx, h / 2f))
            drawCircle(color = activeColor, radius = r, center = Offset(cx, h / 2f))
        }

        // Subtle vertical WHO 85 dB threshold tick (~71.4% along 35..105 dB)
        val tickX = w * ((85f - SpectrumDsp.MIN_DISPLAY_SPL_DB) / (SpectrumDsp.MAX_DISPLAY_SPL_DB - SpectrumDsp.MIN_DISPLAY_SPL_DB))
        drawLine(
            color = thumbRim.copy(alpha = 0.85f),
            start = Offset(tickX, 0f),
            end = Offset(tickX, h),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@Composable
private fun ToggleChipPill(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    status: String,
    active: Boolean,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    statusPillClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val activeBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    )
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled) { onClick() }
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .then(if (active) Modifier.background(activeBrush) else Modifier)
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (statusPillClick != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = enabled) { statusPillClick() }
                    ) {
                        Text(
                            text = status,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Text(
                        text = status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ListeningModeSlidingSelector(
    selectedMode: ListeningMode,
    enabled: Boolean,
    onSelect: (ListeningMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple(ListeningMode.STANDARD, "标准", Icons.Rounded.Headphones),
        Triple(ListeningMode.BACKGROUND_MUSIC, "背景音乐", Icons.Rounded.MusicNote),
        Triple(ListeningMode.CINEMA, "电影", Icons.Rounded.Movie)
    )
    val selectedIndex = items.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        val segWidth = maxWidth / 3f
        val offsetX by animateDpAsState(
            targetValue = segWidth * selectedIndex,
            animationSpec = spring(stiffness = 460f, dampingRatio = 0.82f),
            label = "listeningModeSlide"
        )

        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(segWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEach { (mode, label, icon) ->
                val active = mode == selectedMode
                val contentColor by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 160),
                    label = "listeningModeTint_${mode.name}"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(15.dp))
                        .clickable(enabled = enabled) { onSelect(mode) }
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = contentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeEqPresetTile(
    preset: EqPreset,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
        )
    )
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (active) Modifier.background(activeBrush) else Modifier)
            .clickable(enabled = enabled) { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = preset.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            HomeMiniEqCurve(
                bands10 = preset.bands10,
                lineColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                showGrid = false,
                modifier = Modifier
                    .width(42.dp)
                    .height(22.dp)
            )
        }
    }
}

@Composable
private fun HomeMiniEqCurve(
    bands10: List<Int>,
    lineColor: Color,
    showGrid: Boolean,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val padX = if (showGrid) 6.dp.toPx() else 2.dp.toPx()
        val padY = if (showGrid) 6.dp.toPx() else 3.dp.toPx()
        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
        val midY = padY + usableH / 2f

        if (showGrid) {
            drawLine(
                color = gridColor.copy(alpha = 0.4f),
                start = Offset(padX, midY),
                end = Offset(size.width - padX, midY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
            )
        }

        val pts = (0 until 10).map { i ->
            val db = bands10.getOrElse(i) { 0 }.coerceIn(-6, 6)
            val x = padX + (i / 9f) * usableW
            val y = padY + ((6 - db) / 12f) * usableH
            Offset(x, y)
        }

        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 0 until pts.size - 1) {
                val cx = (pts[i].x + pts[i + 1].x) / 2f
                cubicTo(cx, pts[i].y, cx, pts[i + 1].y, pts[i + 1].x, pts[i + 1].y)
            }
        }

        if (showGrid) {
            val fillPath = Path().apply {
                addPath(path)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
                )
            )
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = if (showGrid) 2.4.dp.toPx() else 1.8.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
