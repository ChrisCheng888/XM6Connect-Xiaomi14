package com.xm6.app.ui.eq

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xm6.app.data.EqPreset
import com.xm6.app.data.HeadphoneState
import com.xm6.app.data.Xm6Defaults
import com.xm6.app.ui.components.ExpressiveCard
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EqScreen(
    state: HeadphoneState,
    presets: List<EqPreset>,
    onSelectPreset: (EqPreset) -> Unit,
    onBandChange: (bandIndex: Int, db: Int) -> Unit,
    onResetFlat: () -> Unit,
    onUpdateExistingPreset: (presetId: String, newName: String) -> Unit,
    onSaveCustomPreset: (name: String) -> Unit,
    onDeleteCustomPreset: (presetId: String) -> Unit,
    onRestoreDefaults: () -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var pendingDeletePreset by remember { mutableStateOf<EqPreset?>(null) }

    val activePreset = presets.firstOrNull { it.id == state.activeEqPresetId }
    val hasUnsavedChanges = activePreset != null && activePreset.bands10 != state.eqBands10
    val hasMissingDefaults = remember(presets) {
        val currentIds = presets.map { it.id }.toSet()
        Xm6Defaults.BUILTIN_EQ_PRESETS.any { it.id !in currentIds }
    }

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
                text = "均衡器",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasMissingDefaults) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(onClick = onRestoreDefaults)
                    ) {
                        Text(
                            text = "恢复默认",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .combinedClickable(onClick = onResetFlat)
                ) {
                    Text(
                        text = "重置",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .combinedClickable(onClick = {
                            presetNameInput = activePreset?.name ?: "自定义 ${presets.size + 1}"
                            showSaveDialog = true
                        })
                ) {
                    Text(
                        text = if (hasUnsavedChanges) "保存修改" else "+ 保存",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Presets Row (ALL presets—including initial defaults—are editable and deletable)
        if (presets.isEmpty()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onRestoreDefaults() }
            ) {
                Text(
                    text = "暂无预设 · 点击恢复默认预设",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets, key = { it.id }) { preset ->
                    val active = state.activeEqPresetId == preset.id
                    Surface(
                        shape = CircleShape,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { onSelectPreset(preset) },
                                onLongClick = { pendingDeletePreset = preset }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
                        ) {
                            Text(
                                text = if (active && hasUnsavedChanges) "${preset.name} *" else preset.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { pendingDeletePreset = preset }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "删除预设",
                                    tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Studio Acoustic Analyzer Curve Card
        Surface(
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            val curveBg = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                    MaterialTheme.colorScheme.surface
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(curveBg)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                EqResponseCurve(
                    bands10 = state.eqBands10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                )
            }
        }

        // 10-Band Vertical Studio Faders (XM6: 31Hz..16kHz, -6..+6 dB)
        ExpressiveCard {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 18.dp)
            ) {
                Xm6Defaults.EQ_BAND_LABELS.forEachIndexed { idx, freq ->
                    val db = state.eqBands10.getOrElse(idx) { 0 }.coerceIn(-6, 6)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (db > 0) "+$db" else "$db",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        VerticalStudioFader(
                            valueDb = db,
                            onValueChange = { onBandChange(idx, it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(164.dp)
                        )
                        Text(
                            text = freq,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(if (activePreset != null) "保存预设" else "新建预设", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it.take(16) },
                    singleLine = true,
                    placeholder = { Text("预设名称") }
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (activePreset != null) {
                        TextButton(
                            onClick = {
                                val trimmed = presetNameInput.trim().ifEmpty { activePreset.name }
                                onUpdateExistingPreset(activePreset.id, trimmed)
                                showSaveDialog = false
                            }
                        ) { Text("覆盖当前", fontWeight = FontWeight.Bold) }
                    }
                    TextButton(
                        onClick = {
                            val trimmed = presetNameInput.trim()
                            if (trimmed.isNotEmpty()) {
                                onSaveCustomPreset(trimmed)
                            }
                            showSaveDialog = false
                        }
                    ) { Text(if (activePreset != null) "另存为新" else "保存", fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            }
        )
    }

    pendingDeletePreset?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeletePreset = null },
            title = { Text("删除预设", fontWeight = FontWeight.Bold) },
            text = { Text("确认删除预设「${target.name}」？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomPreset(target.id)
                        pendingDeletePreset = null
                    }
                ) { Text("删除", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePreset = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EqResponseCurve(
    bands10: List<Int>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val padX = 12.dp.toPx()
        val padY = 12.dp.toPx()
        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
        val midY = padY + usableH / 2f

        // Grid hairlines (+6, 0, -6)
        drawLine(gridColor.copy(alpha = 0.2f), Offset(padX, padY), Offset(size.width - padX, padY), strokeWidth = 1.dp.toPx())
        drawLine(
            gridColor.copy(alpha = 0.5f),
            Offset(padX, midY),
            Offset(size.width - padX, midY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        )
        drawLine(gridColor.copy(alpha = 0.2f), Offset(padX, size.height - padY), Offset(size.width - padX, size.height - padY), strokeWidth = 1.dp.toPx())

        val pts = (0 until 10).map { i ->
            val db = bands10.getOrElse(i) { 0 }.coerceIn(-6, 6)
            val x = padX + (i / 9f) * usableW
            val y = padY + ((6 - db) / 12f) * usableH
            Offset(x, y)
        }

        val linePath = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 0 until pts.size - 1) {
                val cx = (pts[i].x + pts[i + 1].x) / 2f
                cubicTo(cx, pts[i].y, cx, pts[i + 1].y, pts[i + 1].x, pts[i + 1].y)
            }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.34f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = primary,
            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
        )

        pts.forEach { pt ->
            drawCircle(color = primary, radius = 4.2.dp.toPx(), center = pt)
            drawCircle(color = surface, radius = 2.2.dp.toPx(), center = pt)
        }
    }
}

@Composable
private fun VerticalStudioFader(
    valueDb: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = ((6 - valueDb.coerceIn(-6, 6)) / 12f).coerceIn(0f, 1f)

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ratio = (offset.y / size.height).coerceIn(0f, 1f)
                    val db = (6f - ratio * 12f).roundToInt().coerceIn(-6, 6)
                    onValueChange(db)
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    val ratio = (change.position.y / size.height).coerceIn(0f, 1f)
                    val db = (6f - ratio * 12f).roundToInt().coerceIn(-6, 6)
                    onValueChange(db)
                }
            }
    ) {
        val trackHeight = maxHeight - 16.dp
        val thumbCenterY = 8.dp + trackHeight * fraction
        val midY = 8.dp + trackHeight * 0.5f
        val fillTop = minOf(thumbCenterY, midY)
        val fillHeight = abs((thumbCenterY - midY).value).dp

        // Track background
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(6.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        // Active fill bar from 0 dB center to thumb
        Box(
            modifier = Modifier
                .offset(y = fillTop)
                .width(6.dp)
                .height(fillHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )

        // Thumb
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .offset(y = thumbCenterY - 8.dp)
                .size(16.dp)
        ) {}
    }
}
