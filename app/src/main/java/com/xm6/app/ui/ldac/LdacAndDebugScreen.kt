package com.xm6.app.ui.ldac

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xm6.app.data.ConnectionQualityMode
import com.xm6.app.data.HeadphoneState
import com.xm6.app.data.LdacBitrateMode
import com.xm6.app.data.ShizukuStatus
import com.xm6.app.data.UserPrefs
import com.xm6.app.protocol.ProtocolLogEntry
import com.xm6.app.ui.components.ExpressiveCard
import com.xm6.app.ui.theme.LocalIsDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LdacAndDebugScreen(
    state: HeadphoneState,
    prefs: UserPrefs,
    logs: List<ProtocolLogEntry>,
    onSelectConnectionQuality: (ConnectionQualityMode) -> Unit,
    onToggleShizukuAuto: (Boolean) -> Unit,
    onSelectBitrate: (LdacBitrateMode) -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuOrDownload: () -> Unit,
    onApplyLdacNow: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onToggleShowExperimental: (Boolean) -> Unit,
    onCopyCapabilities: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onSendRawHex: (table: Int, hex: String) -> Unit
) {
    var debugExpanded by remember { mutableStateOf(false) }
    var rawTable by remember { mutableIntStateOf(1) }
    var rawHexInput by remember { mutableStateOf("") }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 104.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = "LDAC",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = state.systemCodecSummary ?: state.activeCodec.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        // 1. 3-Way Headphone Connection Quality
        ExpressiveCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "连接模式",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ConnectionQualityMode.entries.forEach { mode ->
                        val active = state.connectionQuality == mode
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onSelectConnectionQuality(mode) }
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .fillMaxWidth()
                                    .let { it },
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 2. Shizuku Auto-Lock LDAC Card
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Shizuku 锁定", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        val isDark = LocalIsDarkTheme.current
                        val isReady = state.shizukuStatus == ShizukuStatus.READY
                        Surface(
                            shape = CircleShape,
                            color = if (isReady) {
                                if (isDark) Color(0xFF103B26) else Color(0xFFD7F5E5)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = state.shizukuStatus == ShizukuStatus.PERMISSION_REQUIRED) {
                                    onRequestShizukuPermission()
                                }
                        ) {
                            Text(
                                text = state.shizukuStatus.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isReady) {
                                    if (isDark) Color(0xFF7CF0B0) else Color(0xFF095830)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Switch(
                        checked = prefs.shizukuAutoLdac,
                        onCheckedChange = onToggleShizukuAuto
                    )
                }

                // 990k / 660k / ABR selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LdacBitrateMode.entries.forEach { mode ->
                        val active = prefs.ldacBitrateMode == mode
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onSelectBitrate(mode) }
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }

                // Apply Now / System Bluetooth buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                when (state.shizukuStatus) {
                                    ShizukuStatus.READY -> onApplyLdacNow()
                                    ShizukuStatus.PERMISSION_REQUIRED -> onRequestShizukuPermission()
                                    ShizukuStatus.NOT_RUNNING, ShizukuStatus.NOT_INSTALLED -> onOpenShizukuOrDownload()
                                }
                            }
                    ) {
                        val actionLabel = when (state.shizukuStatus) {
                            ShizukuStatus.READY -> "立即应用"
                            ShizukuStatus.PERMISSION_REQUIRED -> "授权 Shizuku"
                            ShizukuStatus.NOT_RUNNING -> "打开 Shizuku"
                            ShizukuStatus.NOT_INSTALLED -> "下载 Shizuku"
                        }
                        Text(
                            text = actionLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenBluetoothSettings() }
                    ) {
                        Text(
                            text = "系统蓝牙",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }

        // 3. Experimental & Collapsible Protocol Debug Console
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
                    Text("XM6 实验性功能", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Switch(
                        checked = prefs.showExperimental,
                        onCheckedChange = onToggleShowExperimental
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { debugExpanded = !debugExpanded }
                        .padding(vertical = 4.dp)
                ) {
                    Text("协议调试台", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = if (debugExpanded) "收起" else "展开 ›",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = debugExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DebugMiniPill("复制能力表", onCopyCapabilities, Modifier.weight(1f))
                            DebugMiniPill("复制日志", onCopyLogs, Modifier.weight(1f))
                            DebugMiniPill("清空", onClearLogs, Modifier.weight(0.7f))
                        }

                        // Raw frame tester for HCI snoop calibration
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { rawTable = if (rawTable == 1) 2 else 1 }
                            ) {
                                Text(
                                    text = "T$rawTable",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
                                )
                            }
                            OutlinedTextField(
                                value = rawHexInput,
                                onValueChange = { rawHexInput = it },
                                singleLine = true,
                                placeholder = { Text("Hex 例: 66 17", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (rawHexInput.isNotBlank()) {
                                            onSendRawHex(rawTable, rawHexInput)
                                        }
                                    }
                            ) {
                                Text(
                                    text = "发",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                )
                            }
                        }

                        // Live Hex Log Stream
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 210.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp)
                        ) {
                            if (logs.isEmpty()) {
                                Text(
                                    text = "暂无帧日志",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                logs.take(40).forEach { entry ->
                                    val ts = timeFmt.format(Date(entry.timestampMs))
                                    Text(
                                        text = "[$ts] ${entry.direction} ${entry.table} ${entry.label}\n  ${entry.hex}",
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (entry.direction == "TX") MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun DebugMiniPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}
