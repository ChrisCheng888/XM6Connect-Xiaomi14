package com.xm6.app.ui.theme

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.xm6.app.data.AppThemeMode
import com.xm6.app.system.ThemeScheduleReceiver
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Resolves light vs dark mode from [AppThemeMode] and local wall-clock time.
 * Daytime window in [AppThemeMode.AUTO_TIME]: 07:00 (inclusive) .. 19:00 (exclusive) -> Light.
 * Nighttime window in [AppThemeMode.AUTO_TIME]: 19:00 (inclusive) .. 07:00 (exclusive) -> Dark.
 */
object ThemeResolver {

    const val DAY_START_HOUR = 7
    const val NIGHT_START_HOUR = 19

    fun resolveIsDark(
        mode: AppThemeMode,
        localTime: LocalTime = LocalTime.now()
    ): Boolean = when (mode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.AUTO_TIME -> localTime.hour < DAY_START_HOUR || localTime.hour >= NIGHT_START_HOUR
    }

    fun nextBoundaryDateTime(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val todayDay = now.withHour(DAY_START_HOUR).withMinute(0).withSecond(0).withNano(0)
        val todayNight = now.withHour(NIGHT_START_HOUR).withMinute(0).withSecond(0).withNano(0)
        return when {
            now.isBefore(todayDay) -> todayDay
            now.isBefore(todayNight) -> todayNight
            else -> todayDay.plusDays(1)
        }
    }

    fun nextBoundaryEpochMillis(now: ZonedDateTime = ZonedDateTime.now()): Long =
        nextBoundaryDateTime(now).toInstant().toEpochMilli()

    fun delayUntilNextBoundaryMillis(now: ZonedDateTime = ZonedDateTime.now()): Long =
        (nextBoundaryEpochMillis(now) - now.toInstant().toEpochMilli()).coerceAtLeast(1_000L)
}

object ThemeScheduler {

    const val ACTION_THEME_BOUNDARY = "com.xm6.app.ACTION_THEME_BOUNDARY"
    private const val REQUEST_CODE = 6061

    fun syncSchedule(context: Context, mode: AppThemeMode) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ThemeScheduleReceiver::class.java).apply {
            action = ACTION_THEME_BOUNDARY
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (mode == AppThemeMode.AUTO_TIME) {
            val triggerAtMillis = ThemeResolver.nextBoundaryEpochMillis() + 500L
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAtMillis, pi)
            }
        } else {
            am.cancel(pi)
        }
    }
}
