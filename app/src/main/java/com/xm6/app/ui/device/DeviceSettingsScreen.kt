package com.xm6.app.ui.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xm6.app.data.AppThemeMode
import com.xm6.app.data.AutoPowerOffOption
import com.xm6.app.data.ConnectedPeerDevice
import com.xm6.app.data.HeadphoneState
import com.xm6.app.data.NcAmbButtonCycle
import com.xm6.app.ui.components.ExpressiveCard

@SuppressLint("MissingPermission")
@Composable
fun DeviceSettingsScreen(
    state: HeadphoneState,
    themeMode: AppThemeMode,
    isDarkTheme: Boolean,
    bondedDevices: List<BluetoothDevice>,
    isCompanionAssociated: Boolean,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleMultipoint: (Boolean) -> Unit,
    onSwitchPeer: (String) -> Unit,
    onDisconnectPeer: (String) -> Unit,
    onSelectNcCycle: (NcAmbButtonCycle) -> Unit,
    onToggleWearingPause: (Boolean) -> Unit,
    onSelectAutoPowerOff: (AutoPowerOffOption) -> Unit,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onAssociateCompanion: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 104.dp)
    ) {
        Text(
            text = "设备",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // 1. Multipoint Card
        ExpressiveCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("多点连接", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Switch(
                        checked = state.multipointEnabled,
                        onCheckedChange = onToggleMultipoint
                    )
                }

                if (state.multipointEnabled) {
                    if (state.connectedPeers.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (state.isConnected) "当前连接 1 台设备" else "连接耳机后显示多点设备",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.connectedPeers.forEach { peer ->
                                PeerDeviceRow(
                                    peer = peer,
                                    canDisconnect = state.connectedPeers.size > 1,
                                    onSwitch = { onSwitchPeer(peer.mac) },
                                    onDisconnect = { onDisconnectPeer(peer.mac) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Physical Button & Power Card
        ExpressiveCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("NC/AMB 键", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NcAmbButtonCycle.entries.forEach { cycle ->
                            val active = state.ncAmbButtonCycle == cycle
                            Surface(
                                shape = CircleShape,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onSelectNcCycle(cycle) }
                            ) {
                                Text(
                                    text = cycle.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("摘下自动暂停", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Switch(
                        checked = state.pauseWhenTakenOff,
                        onCheckedChange = onToggleWearingPause
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (state.isAutoPowerOffWearingOnly) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("自动关机", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                AutoPowerOffOption.WHEN_TAKEN_OFF to "摘下后关机",
                                AutoPowerOffOption.OFF to "不自动关机"
                            ).forEach { (opt, label) ->
                                val active = state.autoPowerOff == opt
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onSelectAutoPowerOff(opt) }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("自动关机", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                AutoPowerOffOption.MIN_15,
                                AutoPowerOffOption.MIN_30,
                                AutoPowerOffOption.HOUR_1,
                                AutoPowerOffOption.HOUR_3,
                                AutoPowerOffOption.OFF
                            ).forEach { opt ->
                                val active = state.autoPowerOff == opt
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onSelectAutoPowerOff(opt) }
                                ) {
                                    Text(
                                        text = opt.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Appearance Theme Card (Synced with 2x2 Desktop Widget)
        ExpressiveCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("外观主题", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = when (themeMode) {
                            AppThemeMode.LIGHT -> "浅色 · 同步小组件"
                            AppThemeMode.DARK -> "深色 · 同步小组件"
                            AppThemeMode.AUTO_TIME -> if (isDarkTheme) "19:00–07:00 深色" else "07:00–19:00 浅色"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                ThemeModeSlidingSelector(
                    selectedMode = themeMode,
                    onSelect = onSelectThemeMode
                )
            }
        }

        // 4. HyperOS Companion Wakeup Card
        ExpressiveCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isCompanionAssociated) Color(0xFF10B981) else MaterialTheme.colorScheme.outline)
                    )
                    Text("HyperOS 伴生唤醒", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!isCompanionAssociated) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onAssociateCompanion() }
                        ) {
                            Text(
                                text = "绑定",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onOpenBatterySettings() }
                    ) {
                        Text(
                            text = "省电设置 ›",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 4. Bonded Bluetooth Device Switcher
        if (bondedDevices.isNotEmpty()) {
            ExpressiveCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "已配对蓝牙设备",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    bondedDevices.take(5).forEach { dev ->
                        val isCurrent = state.macAddress.equals(dev.address, ignoreCase = true) && state.isConnected
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onConnectDevice(dev) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = dev.name ?: dev.address,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = if (isCurrent) "已连接" else "连接",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerDeviceRow(
    peer: ConnectedPeerDevice,
    canDisconnect: Boolean,
    onSwitch: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isLaptop = peer.name.contains("Mac", true) ||
        peer.name.contains("PC", true) ||
        peer.name.contains("Book", true) ||
        peer.name.contains("Windows", true)
    val bgBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (peer.isActivePlayback) Modifier.background(bgBrush)
                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (peer.isActivePlayback) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
            ) {
                Icon(
                    imageVector = if (isLaptop) Icons.Rounded.Laptop else Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = if (peer.isActivePlayback) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
            Text(
                text = peer.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (peer.isActivePlayback) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (peer.isActivePlayback) {
                Text(
                    text = "播放中",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onSwitch() }
                ) {
                    Text(
                        text = "切换",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (canDisconnect) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .clickable { onDisconnect() }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "断开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeSlidingSelector(
    selectedMode: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit
) {
    val items = listOf(
        Triple(AppThemeMode.LIGHT, "浅色", Icons.Rounded.LightMode),
        Triple(AppThemeMode.DARK, "深色", Icons.Rounded.DarkMode),
        Triple(AppThemeMode.AUTO_TIME, "随时间", Icons.Rounded.Schedule)
    )
    val selectedIndex = items.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        val segWidth = maxWidth / 3f
        val offsetX by animateDpAsState(
            targetValue = segWidth * selectedIndex,
            animationSpec = spring(stiffness = 460f, dampingRatio = 0.82f),
            label = "themeSlide"
        )

        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(segWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEach { (mode, label, icon) ->
                val active = mode == selectedMode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(mode) }
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
