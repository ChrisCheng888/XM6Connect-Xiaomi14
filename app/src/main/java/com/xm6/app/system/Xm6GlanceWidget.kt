package com.xm6.app.system

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.xm6.app.MainActivity
import com.xm6.app.R
import com.xm6.app.Xm6Application
import com.xm6.app.data.AudioCodec
import com.xm6.app.data.ConnectionQualityMode
import com.xm6.app.data.ConnectionState
import com.xm6.app.data.HeadphoneDisplayState
import com.xm6.app.data.HeadphoneState
import com.xm6.app.data.NoiseControlMode
import com.xm6.app.data.UserPrefs
import com.xm6.app.ui.theme.ThemeResolver
import com.xm6.app.ui.theme.ThemeScheduler
import com.xm6.app.ui.theme.resolveXm6ColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class Xm6GlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Xm6GlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val appContext = context.applicationContext
        val app = appContext as? Xm6Application ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val mode = app.preferencesStore.prefsFlow.first().themeMode
            ThemeScheduler.syncSchedule(appContext, mode)
            app.repository.syncWithSystemBluetooth(reason = "widget_enabled")
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        (context.applicationContext as? Xm6Application)?.triggerBackgroundSync(reason = "widget_update")
    }
}

private val MODE_PARAM_KEY = ActionParameters.Key<String>("noise_mode")
private val WIDGET_NONCE_KEY = longPreferencesKey("widget_refresh_nonce")

class SetNoiseModeWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val app = context.applicationContext as? Xm6Application ?: return
        val modeName = parameters[MODE_PARAM_KEY] ?: return
        val mode = runCatching { NoiseControlMode.valueOf(modeName) }.getOrNull() ?: return

        app.repository.executeTransientNoiseMode(targetMode = mode)
        Xm6GlanceWidget.updateAllWidgets(context)
    }
}

class SyncWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val app = context.applicationContext as? Xm6Application ?: return
        app.repository.syncWithSystemBluetooth(reason = "widget_tap", triggerAutoLdac = true)
        Xm6GlanceWidget.updateAllWidgets(context)
    }
}

class Xm6GlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        suspend fun updateAllWidgets(context: Context) {
            runCatching {
                val manager = GlanceAppWidgetManager(context)
                val widget = Xm6GlanceWidget()
                val ids = manager.getGlanceIds(Xm6GlanceWidget::class.java)
                val now = System.currentTimeMillis()
                ids.forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[WIDGET_NONCE_KEY] = now
                    }
                    widget.update(context, glanceId)
                }
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? Xm6Application
        val stateFlow = app?.repository?.state ?: MutableStateFlow(HeadphoneState())
        val prefsFlow = app?.preferencesStore?.prefsFlow ?: MutableStateFlow(UserPrefs())
        val initialPrefs = runCatching { prefsFlow.first() }.getOrDefault(UserPrefs())

        provideContent {
            val nonce = currentState(key = WIDGET_NONCE_KEY) ?: 0L
            val state by stateFlow.collectAsState()
            val prefs by prefsFlow.collectAsState(initial = initialPrefs)

            val isDark = remember(prefs.themeMode, nonce) {
                ThemeResolver.resolveIsDark(prefs.themeMode)
            }
            val colorScheme = remember(isDark, nonce) {
                resolveXm6ColorScheme(context, darkTheme = isDark)
            }

            when (state.displayState) {
                HeadphoneDisplayState.WEARING -> {
                    val dotBitmap = remember(isDark, nonce) {
                        renderStatusDotBitmap(coreColor = 0xFF10B981.toInt(), haloAlpha = 0x44)
                    }
                    val ringBitmap = remember(state.batteryLevel, isDark, colorScheme.primary, nonce) {
                        renderActiveRingBitmap(
                            batteryLevel = state.batteryLevel,
                            isDark = isDark,
                            accentColor = colorScheme.primary.toArgb()
                        )
                    }
                    WearingWidgetContent(
                        state = state,
                        isDark = isDark,
                        colors = colorScheme,
                        dotBitmap = dotBitmap,
                        ringBitmap = ringBitmap
                    )
                }

                HeadphoneDisplayState.NOT_WORN -> {
                    val amberColor = if (isDark) 0xFFF59E0B.toInt() else 0xFFD97706.toInt()
                    val dotBitmap = remember(isDark, nonce) {
                        renderStatusDotBitmap(coreColor = amberColor, haloAlpha = 0x48)
                    }
                    val standbyRing = remember(state.batteryLevel, isDark, nonce) {
                        renderStandbyRingBitmap(
                            batteryLevel = state.batteryLevel,
                            isDark = isDark,
                            amberColor = amberColor
                        )
                    }
                    NotWornWidgetContent(
                        state = state,
                        isDark = isDark,
                        colors = colorScheme,
                        dotBitmap = dotBitmap,
                        standbyRingBitmap = standbyRing
                    )
                }

                HeadphoneDisplayState.DISCONNECTED -> {
                    val isConnecting = state.connectionState == ConnectionState.CONNECTING
                    val dotColor = if (isConnecting) colorScheme.primary.toArgb()
                    else if (isDark) 0xFF64748B.toInt() else 0xFF94A3B8.toInt()
                    val dotBitmap = remember(isConnecting, isDark, colorScheme.primary, nonce) {
                        renderStatusDotBitmap(coreColor = dotColor, haloAlpha = if (isConnecting) 0x48 else 0x26)
                    }
                    val heroBitmap = remember(isConnecting, isDark, colorScheme.primary, nonce) {
                        renderOfflineHeroBitmap(
                            isConnecting = isConnecting,
                            isDark = isDark,
                            accentColor = colorScheme.primary.toArgb()
                        )
                    }
                    OfflineWidgetContent(
                        isConnecting = isConnecting,
                        isDark = isDark,
                        colors = colorScheme,
                        dotBitmap = dotBitmap,
                        heroBitmap = heroBitmap
                    )
                }
            }
        }
    }

    // =========================================================================
    // 1. State A: 已连接（佩戴中）— Active Studio Control Deck
    // =========================================================================

    @Composable
    private fun WearingWidgetContent(
        state: HeadphoneState,
        isDark: Boolean,
        colors: ColorScheme,
        dotBitmap: Bitmap,
        ringBitmap: Bitmap
    ) {
        val cardBgRes = if (isDark) R.drawable.widget_bg_card_dark else R.drawable.widget_bg_card_light
        val dockBgRes = if (isDark) R.drawable.widget_pill_dock_dark else R.drawable.widget_pill_dock_light
        val activeBtnRes = if (isDark) R.drawable.widget_btn_active_dark else R.drawable.widget_btn_active_light

        val onSurface = ColorProvider(colors.onSurface)
        val onSurfaceVariant = ColorProvider(colors.onSurfaceVariant)
        val primaryContainer = ColorProvider(colors.primaryContainer)
        val onPrimaryContainer = ColorProvider(colors.onPrimaryContainer)
        val onPrimary = ColorProvider(colors.onPrimary)

        val codecBadge = resolveCodecLabel(state)
        val modeSub = when (state.noiseMode) {
            NoiseControlMode.ANC -> "自适应降噪"
            NoiseControlMode.AMBIENT -> "环境声 · ${state.ambientLevel}"
            NoiseControlMode.OFF -> "降噪已关"
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(cardBgRes))
                .cornerRadius(26.dp)
                .padding(horizontal = 13.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(dotBitmap),
                    contentDescription = "已连接",
                    modifier = GlanceModifier.size(10.dp)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = "XM6",
                    style = TextStyle(
                        color = onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier
                        .background(primaryContainer)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = codecBadge,
                        style = TextStyle(
                            color = onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${state.batteryLevel}%",
                        style = TextStyle(
                            color = onSurface,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = modeSub,
                        style = TextStyle(
                            color = onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Image(
                    provider = ImageProvider(ringBitmap),
                    contentDescription = "电量环",
                    modifier = GlanceModifier.size(48.dp)
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(ImageProvider(dockBgRes))
                    .cornerRadius(18.dp)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockModeButton(
                    iconRes = R.drawable.ic_widget_anc,
                    contentDesc = "降噪",
                    active = state.noiseMode == NoiseControlMode.ANC,
                    mode = NoiseControlMode.ANC,
                    activeBgRes = activeBtnRes,
                    activeTint = onPrimary,
                    inactiveTint = onSurfaceVariant,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(2.dp))
                DockModeButton(
                    iconRes = R.drawable.ic_widget_ambient,
                    contentDesc = "环境声",
                    active = state.noiseMode == NoiseControlMode.AMBIENT,
                    mode = NoiseControlMode.AMBIENT,
                    activeBgRes = activeBtnRes,
                    activeTint = onPrimary,
                    inactiveTint = onSurfaceVariant,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(2.dp))
                DockModeButton(
                    iconRes = R.drawable.ic_widget_off,
                    contentDesc = "关闭",
                    active = state.noiseMode == NoiseControlMode.OFF,
                    mode = NoiseControlMode.OFF,
                    activeBgRes = activeBtnRes,
                    activeTint = onPrimary,
                    inactiveTint = onSurfaceVariant,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }

    // =========================================================================
    // 2. State B: 未佩戴（已连接待机）— Warm Amber Standby Card
    // =========================================================================

    @Composable
    private fun NotWornWidgetContent(
        state: HeadphoneState,
        isDark: Boolean,
        colors: ColorScheme,
        dotBitmap: Bitmap,
        standbyRingBitmap: Bitmap
    ) {
        val cardBgRes = if (isDark) R.drawable.widget_bg_standby_dark else R.drawable.widget_bg_standby_light
        val pillBgRes = if (isDark) R.drawable.widget_pill_standby_dark else R.drawable.widget_pill_standby_light

        val onSurface = ColorProvider(colors.onSurface)
        val amberPillBg = ColorProvider(if (isDark) Color(0xFF422E0D) else Color(0xFFFDE8B9))
        val amberText = ColorProvider(if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309))
        val subText = ColorProvider(if (isDark) Color(0xFFD6B98A) else Color(0xFF8C5B1A))
        val codecLabel = resolveCodecLabel(state)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(cardBgRes))
                .cornerRadius(26.dp)
                .padding(horizontal = 13.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(dotBitmap),
                    contentDescription = "未佩戴",
                    modifier = GlanceModifier.size(10.dp)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = "XM6",
                    style = TextStyle(
                        color = onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier
                        .background(amberPillBg)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "未佩戴",
                        style = TextStyle(
                            color = amberText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${state.batteryLevel}%",
                        style = TextStyle(
                            color = onSurface,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "戴上自动恢复",
                        style = TextStyle(
                            color = subText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Image(
                    provider = ImageProvider(standbyRingBitmap),
                    contentDescription = "摘下待机",
                    modifier = GlanceModifier.size(48.dp)
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(ImageProvider(pillBgRes))
                    .cornerRadius(16.dp)
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    text = "$codecLabel · 摘下待机",
                    style = TextStyle(
                        color = amberText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }

    // =========================================================================
    // 3. State C: 未连接（离线 / 自动同步中）— Frosted Matte Off-Duty Card
    // =========================================================================

    @Composable
    private fun OfflineWidgetContent(
        isConnecting: Boolean,
        isDark: Boolean,
        colors: ColorScheme,
        dotBitmap: Bitmap,
        heroBitmap: Bitmap
    ) {
        val cardBgRes = if (isDark) R.drawable.widget_bg_offline_dark else R.drawable.widget_bg_offline_light
        val pillBgRes = if (isDark) R.drawable.widget_pill_dock_dark else R.drawable.widget_pill_dock_light

        val onSurface = ColorProvider(colors.onSurface)
        val mutedText = ColorProvider(colors.onSurfaceVariant)
        val badgeBg = ColorProvider(
            if (isConnecting) colors.primaryContainer
            else if (isDark) Color(0xFF252A36) else Color(0xFFE2E8F0)
        )
        val badgeText = ColorProvider(
            if (isConnecting) colors.onPrimaryContainer
            else colors.onSurfaceVariant
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(cardBgRes))
                .cornerRadius(26.dp)
                .padding(horizontal = 13.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(dotBitmap),
                    contentDescription = if (isConnecting) "同步中" else "未连接",
                    modifier = GlanceModifier.size(10.dp)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = "XM6",
                    style = TextStyle(
                        color = onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier
                        .background(badgeBg)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isConnecting) "同步中" else "未连接",
                        style = TextStyle(
                            color = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(heroBitmap),
                    contentDescription = "WH-1000XM6",
                    modifier = GlanceModifier.size(52.dp)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = if (isConnecting) "正在同步" else "已断开",
                        style = TextStyle(
                            color = onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = if (isConnecting) "读取耳机状态..." else "开机自动连接",
                        style = TextStyle(
                            color = mutedText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(ImageProvider(pillBgRes))
                    .cornerRadius(16.dp)
                    .clickable(actionRunCallback<SyncWidgetAction>())
            ) {
                Text(
                    text = if (isConnecting) "同步中..." else "轻触刷新同步",
                    style = TextStyle(
                        color = if (isConnecting) ColorProvider(colors.primary) else mutedText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun DockModeButton(
        @DrawableRes iconRes: Int,
        contentDesc: String,
        active: Boolean,
        mode: NoiseControlMode,
        @DrawableRes activeBgRes: Int,
        activeTint: ColorProvider,
        inactiveTint: ColorProvider,
        modifier: GlanceModifier
    ) {
        val baseModifier = modifier
            .height(34.dp)
            .cornerRadius(15.dp)
            .clickable(
                actionRunCallback<SetNoiseModeWidgetAction>(
                    actionParametersOf(MODE_PARAM_KEY to mode.name)
                )
            )
        Box(
            contentAlignment = Alignment.Center,
            modifier = if (active) {
                baseModifier.background(ImageProvider(activeBgRes))
            } else {
                baseModifier
            }
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = contentDesc,
                colorFilter = ColorFilter.tint(if (active) activeTint else inactiveTint),
                modifier = GlanceModifier.size(17.dp)
            )
        }
    }

    private fun resolveCodecLabel(state: HeadphoneState): String = when {
        state.isLeAudioConflict -> "LC3"
        !state.systemCodecSummary.isNullOrBlank() -> state.systemCodecSummary.replace(" · ", " ")
        state.activeCodec != AudioCodec.UNKNOWN -> state.activeCodec.label
        state.connectionQuality == ConnectionQualityMode.SOUND_QUALITY -> "LDAC"
        else -> "AAC"
    }

    // =========================================================================
    // High-DPI Anti-Aliased Canvas Renderers (168x168px crisp icons & rings)
    // =========================================================================

    private fun renderStatusDotBitmap(coreColor: Int, haloAlpha: Int): Bitmap {
        val sz = 32
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = sz / 2f
        val haloColor = (haloAlpha shl 24) or (coreColor and 0x00FFFFFF)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = haloColor
        c.drawCircle(cx, cx, sz * 0.46f, paint)
        paint.color = coreColor
        c.drawCircle(cx, cx, sz * 0.28f, paint)
        return bmp
    }

    private fun renderActiveRingBitmap(
        batteryLevel: Int,
        isDark: Boolean,
        accentColor: Int
    ): Bitmap {
        val sz = 168
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = sz / 2f
        val strokeW = 14f
        val pad = strokeW / 2f + 8f
        val rect = RectF(pad, pad, sz - pad, sz - pad)

        // Subtle outer glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = (0x16 shl 24) or (accentColor and 0x00FFFFFF)
        }
        c.drawCircle(cx, cx, sz / 2f - 4f, glowPaint)

        // Inner medallion
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDark) 0x26FFFFFF else 0x100F172A
        }
        c.drawCircle(cx, cx, sz / 2f - strokeW - 10f, innerPaint)

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            color = if (isDark) 0x30FFFFFF else 0x1C0F172A
        }
        c.drawArc(rect, -90f, 360f, false, trackPaint)

        // Battery progress arc
        val clamped = batteryLevel.coerceIn(0, 100)
        if (clamped > 0) {
            val sweep = (clamped / 100f) * 360f
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeW
                strokeCap = Paint.Cap.ROUND
                color = accentColor
            }
            c.drawArc(rect, -90f, sweep, false, arcPaint)

            // Specular highlight dot at arc tip
            val rad = Math.toRadians((-90f + sweep).toDouble())
            val r = (rect.width() / 2f)
            val tipX = cx + (r * cos(rad)).toFloat()
            val tipY = cx + (r * sin(rad)).toFloat()
            val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xCCFFFFFF.toInt()
            }
            c.drawCircle(tipX, tipY, 3.2f, tipPaint)
        }

        drawSleekXm6Glyph(c, cx, cx, scale = 0.86f, color = accentColor)
        return bmp
    }

    private fun renderStandbyRingBitmap(
        batteryLevel: Int,
        isDark: Boolean,
        amberColor: Int
    ): Bitmap {
        val sz = 168
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = sz / 2f
        val strokeW = 13f
        val pad = strokeW / 2f + 8f
        val rect = RectF(pad, pad, sz - pad, sz - pad)

        // Warm soft medallion fill
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ((if (isDark) 0x24 else 0x1C) shl 24) or (amberColor and 0x00FFFFFF)
        }
        c.drawCircle(cx, cx, sz / 2f - strokeW - 10f, innerPaint)

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            color = if (isDark) 0x2CF59E0B else 0x26D97706
        }
        c.drawArc(rect, -90f, 360f, false, trackPaint)

        // Warm amber battery arc
        val clamped = batteryLevel.coerceIn(0, 100)
        if (clamped > 0) {
            val sweep = (clamped / 100f) * 360f
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeW
                strokeCap = Paint.Cap.ROUND
                color = amberColor
            }
            c.drawArc(rect, -90f, sweep, false, arcPaint)
        }

        // Resting headphone glyph shifted slightly up-left to balance pause badge
        drawSleekXm6Glyph(c, cx - 2f, cx - 2f, scale = 0.80f, color = amberColor)

        // Crisp mini pause medallion in bottom-right corner
        val badgeCx = sz - 34f
        val badgeCy = sz - 34f
        val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = amberColor
        }
        c.drawCircle(badgeCx, badgeCy, 20f, badgeBg)

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDark) 0xFF1F1607.toInt() else 0xFFFFFFFF.toInt()
        }
        c.drawRoundRect(RectF(badgeCx - 8f, badgeCy - 8.5f, badgeCx - 2.5f, badgeCy + 8.5f), 2.5f, 2.5f, barPaint)
        c.drawRoundRect(RectF(badgeCx + 2.5f, badgeCy - 8.5f, badgeCx + 8f, badgeCy + 8.5f), 2.5f, 2.5f, barPaint)

        return bmp
    }

    private fun renderOfflineHeroBitmap(
        isConnecting: Boolean,
        isDark: Boolean,
        accentColor: Int
    ): Bitmap {
        val sz = 168
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = sz / 2f

        val baseColor = when {
            isConnecting -> accentColor
            isDark -> 0xFF94A3B8.toInt()
            else -> 0xFF64748B.toInt()
        }

        // Outer soft concentric halo ring
        val outerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = ((if (isConnecting) 0x38 else 0x22) shl 24) or (baseColor and 0x00FFFFFF)
        }
        c.drawCircle(cx, cx, sz * 0.45f, outerRing)

        // Middle subtle fill medallion
        val midFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ((if (isConnecting) 0x24 else 0x16) shl 24) or (baseColor and 0x00FFFFFF)
        }
        c.drawCircle(cx, cx, sz * 0.35f, midFill)

        // Inner crisp ring
        val innerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = ((if (isConnecting) 0x66 else 0x36) shl 24) or (baseColor and 0x00FFFFFF)
        }
        c.drawCircle(cx, cx, sz * 0.35f, innerRing)

        drawSleekXm6Glyph(c, cx, cx + 1f, scale = 0.94f, color = baseColor)
        return bmp
    }

    /**
     * Draws a bespoke, crisp Sony WH-1000XM6 over-ear headphone vector silhouette directly on Canvas.
     */
    private fun drawSleekXm6Glyph(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
        color: Int
    ) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)

        // Smooth curved headband arch
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8.5f
            strokeCap = Paint.Cap.ROUND
            this.color = color
        }
        val archRect = RectF(-30f, -29f, 30f, 31f)
        canvas.drawArc(archRect, 188f, 164f, false, bandPaint)

        // Tapered slim yokes connecting headband to earcups
        val yokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            strokeCap = Paint.Cap.ROUND
            this.color = color
        }
        val leftYoke = Path().apply {
            moveTo(-29.5f, 1f)
            lineTo(-28.5f, 12f)
        }
        val rightYoke = Path().apply {
            moveTo(29.5f, 1f)
            lineTo(28.5f, 12f)
        }
        canvas.drawPath(leftYoke, yokePaint)
        canvas.drawPath(rightYoke, yokePaint)

        // Sculpted oval WH-1000XM6 earcups
        val cupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        canvas.drawRoundRect(RectF(-35f, 2f, -18f, 31f), 9f, 9f, cupPaint)
        canvas.drawRoundRect(RectF(18f, 2f, 35f, 31f), 9f, 9f, cupPaint)

        // Subtle metallic accent seam on earcups
        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            strokeCap = Paint.Cap.ROUND
            this.color = 0x55FFFFFF
        }
        canvas.drawLine(-29f, 9f, -29f, 24f, seamPaint)
        canvas.drawLine(29f, 9f, 29f, 24f, seamPaint)

        canvas.restore()
    }
}
