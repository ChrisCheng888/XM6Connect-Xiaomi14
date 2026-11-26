package com.xm6.app.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

class Xm6GlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Xm6GlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val appContext = context.applicationContext
        val app = appContext as? Xm6Application ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val mode = app.preferencesStore.prefsFlow.first().themeMode
            ThemeScheduler.syncSchedule(appContext, mode)
        }
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

        // Optimistically push immediate UI highlight, then dispatch RFCOMM command
        app.repository.executeTransientNoiseMode(targetMode = mode)
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
            // Observe nonce so out-of-session updateAppWidgetState & time-boundary refreshes recompose immediately
            val nonce = currentState(key = WIDGET_NONCE_KEY) ?: 0L
            val state by stateFlow.collectAsState()
            val prefs by prefsFlow.collectAsState(initial = initialPrefs)

            // Follow the App's chosen theme (Light / Dark / Local-Time Auto) independent of system dark mode
            val isDark = remember(prefs.themeMode, nonce) {
                ThemeResolver.resolveIsDark(prefs.themeMode)
            }
            val colorScheme = remember(isDark, nonce) {
                resolveXm6ColorScheme(context, darkTheme = isDark)
            }
            val ringBitmap = remember(state.batteryLevel, state.isConnected, isDark, colorScheme.primary, nonce) {
                renderBatteryRingBitmap(
                    context = context,
                    batteryLevel = state.batteryLevel,
                    connected = state.isConnected,
                    isDark = isDark,
                    accentColor = colorScheme.primary.toArgb()
                )
            }

            Widget2x2Content(
                state = state,
                isDark = isDark,
                colors = colorScheme,
                ringBitmap = ringBitmap
            )
        }
    }

    @Composable
    private fun Widget2x2Content(
        state: HeadphoneState,
        isDark: Boolean,
        colors: ColorScheme,
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

        val codecBadge = when {
            !state.isConnected -> "离线"
            state.isLeAudioConflict -> "LC3"
            !state.systemCodecSummary.isNullOrBlank() -> state.systemCodecSummary.replace(" · ", " ")
            state.activeCodec != AudioCodec.UNKNOWN -> state.activeCodec.label
            state.connectionQuality == ConnectionQualityMode.SOUND_QUALITY -> "LDAC"
            else -> "AAC"
        }

        val modeSub = when {
            !state.isConnected -> "点击连接"
            state.noiseMode == NoiseControlMode.ANC -> "自适应降噪"
            state.noiseMode == NoiseControlMode.AMBIENT -> "环境声 ${state.ambientLevel}"
            else -> "降噪已关"
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(cardBgRes))
                .cornerRadius(26.dp)
                .padding(horizontal = 13.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            // 1. Top Header: XM6 title + compact Codec Pill
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            // 2. Center Hero Telemetry: Big Battery % + Crisp Circular Ring Gauge
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = if (state.isConnected) "${state.batteryLevel}%" else "--%",
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
                    modifier = GlanceModifier.size(44.dp)
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // 3. Bottom Recessed Pill Control Dock (ANC / Ambient / Off)
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
                    active = state.isConnected && state.noiseMode == NoiseControlMode.ANC,
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
                    active = state.isConnected && state.noiseMode == NoiseControlMode.AMBIENT,
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
                    active = state.isConnected && state.noiseMode == NoiseControlMode.OFF,
                    mode = NoiseControlMode.OFF,
                    activeBgRes = activeBtnRes,
                    activeTint = onPrimary,
                    inactiveTint = onSurfaceVariant,
                    modifier = GlanceModifier.defaultWeight()
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

    private fun renderBatteryRingBitmap(
        context: Context,
        batteryLevel: Int,
        connected: Boolean,
        isDark: Boolean,
        accentColor: Int
    ): Bitmap {
        val sizePx = 144
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val trackColor = if (isDark) 0x33FFFFFF else 0x1E0F172A
        val innerFillColor = if (isDark) 0x22FFFFFF else 0x120F172A

        val strokeW = 14f
        val pad = strokeW / 2f + 4f
        val rect = RectF(pad, pad, sizePx - pad, sizePx - pad)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = innerFillColor
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - strokeW - 6f, fillPaint)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            color = trackColor
        }
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        if (connected && batteryLevel > 0) {
            val sweep = (batteryLevel.coerceIn(0, 100) / 100f) * 360f
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeW
                strokeCap = Paint.Cap.ROUND
                color = accentColor
            }
            canvas.drawArc(rect, -90f, sweep, false, arcPaint)
        }

        ContextCompat.getDrawable(context, R.drawable.ic_widget_anc)?.mutate()?.let { drawable ->
            drawable.setTint(if (connected) accentColor else (if (isDark) 0x77FFFFFF else 0x550F172A))
            val iconSize = 48
            val left = (sizePx - iconSize) / 2
            val top = (sizePx - iconSize) / 2
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.draw(canvas)
        }

        return bitmap
    }
}
