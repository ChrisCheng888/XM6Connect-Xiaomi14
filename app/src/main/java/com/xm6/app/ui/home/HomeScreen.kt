package com.xm6.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.geometry.Offset
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
import com.xm6.app.data.AudioCodec
import com.xm6.app.data.AutoAmbientSensitivity
import com.xm6.app.data.BgmRoomSize
import com.xm6.app.data.ConnectionQualityMode
import com.xm6.app.data.ConnectionState
import com.xm6.app.data.EqPreset
import com.xm6.app.data.HeadphoneState
import com.xm6.app.data.ListeningMode
import com.xm6.app.data.NoiseControlMode
import com.xm6.app.data.UserPrefs
import com.xm6.app.ui.components.AcousticWaveRibbon
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
    val controlsAlpha = if (connected) 1f else 0.45f

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 104.dp)
    ) {
        // 1. Acoustic Hero Status Card
        HeroStatusCard(
            state = state,
            prefs = prefs,
            onConnectClick = onConnectClick,
            onCodecPillClick = onCodecPillClick,
            onFixLeAudioClick = onFixLeAudioClick
        )

        // 2. Sound Control Card
        ExpressiveCard(modifier = Modifier.alpha(controlsAlpha)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                SlidingNoiseModeSelector(
                    selectedMode = state.noiseMode,
                    enabled = connected,
                    onSelect = onSelectNoiseMode
                )

                AnimatedVisibility(visible = state.noiseMode == NoiseControlMode.ANC) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "自适应降噪",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "运行中",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
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

        // 3. Silky 1-Tap Direct Listening Mode Card (Standard / BGM / Cinema + BGM Room Selector)
        ExpressiveCard(modifier = Modifier.alpha(controlsAlpha)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(14.dp)
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "空间距离",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BgmRoomSize.entries.forEach { room ->
                                val active = state.bgmRoom == room
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(enabled = connected) { onSelectBgmRoom(room) }
                                ) {
                                    Text(
                                        text = room.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. 3-Column Quick Action Toggle Row (DSEE / Speak-to-Chat / Touch Panel)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alpha(controlsAlpha)
        ) {
            QuickActionTile(
                title = "DSEE",
                status = when {
                    !state.dseeExtreme -> "关闭"
                    !state.dseeActive -> "已暂停"
                    else -> "开启"
                },
                active = state.dseeExtreme,
                icon = Icons.Rounded.GraphicEq,
                enabled = connected,
                onClick = onToggleDsee,
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                title = "智能免摘",
                status = if (state.speakToChat) state.speakToChatTimeout.label else "关闭",
                active = state.speakToChat,
                icon = Icons.Rounded.ChatBubble,
                enabled = connected,
                onClick = onToggleSpeakToChat,
                statusPillClick = if (state.speakToChat) onCycleSpeakTimeout else null,
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                title = "触控面板",
                status = if (state.touchSensor) "开启" else "关闭",
                active = state.touchSensor,
                icon = Icons.Rounded.TouchApp,
                enabled = connected,
                onClick = onToggleTouchSensor,
                modifier = Modifier.weight(1f)
            )
        }

        // 4. Spacious Expressive EQ Studio Deck (utilizes bottom viewport cleanly)
        ExpressiveCard(modifier = Modifier.alpha(controlsAlpha)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "均衡器预设",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val activeName = presets.firstOrNull { it.id == state.activeEqPresetId }?.name
                        if (!activeName.isNullOrBlank()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                            ) {
                                Text(
                                    text = activeName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onOpenEqTab() }
                    ) {
                        Text(
                            text = "10 段微调 ›",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                // Live 10-band EQ mini response preview (tap to open full faders)
                val previewBg = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(previewBg)
                        .clickable { onOpenEqTab() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    HomeMiniEqCurve(
                        bands10 = state.eqBands10,
                        lineColor = MaterialTheme.colorScheme.primary,
                        showGrid = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Generous 2-column preset tile grid
                if (presets.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenEqTab() }
                    ) {
                        Text(
                            text = "+ 添加或恢复均衡器预设",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        presets.chunked(2).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    val connected = state.isConnected
    val isConnecting = state.connectionState == ConnectionState.CONNECTING
    val heroBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
            MaterialTheme.colorScheme.surface
        )
    )

    Surface(
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroBrush)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    CircularBatteryGauge(
                        batteryLevel = state.batteryLevel,
                        connected = connected
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
                                    .background(
                                        if (connected) Color(0xFF10B981)
                                        else if (isConnecting) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val subtitle = when {
                            connected -> {
                                val peerText = if (state.connectedPeers.isNotEmpty()) " · ${state.connectedPeers.size} 台设备" else ""
                                "v${state.firmwareVersion}$peerText"
                            }
                            isConnecting -> "连接中..."
                            state.errorMessage != null -> state.errorMessage
                            else -> "未连接"
                        }
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (connected) {
                    val isDark = LocalIsDarkTheme.current
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
                } else {
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            AcousticWaveRibbon(
                connected = connected,
                noiseMode = state.noiseMode,
                ambientLevel = state.ambientLevel
            )
        }
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
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(24.dp))
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
