package com.xm6.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xm6.app.Xm6Application
import com.xm6.app.ui.theme.ThemeScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Refreshes the 2x2 Glance desktop widget and reschedules the next 07:00 / 19:00 boundary
 * whenever local time crosses a day/night boundary, system time/timezone changes, or the device boots.
 */
class ThemeScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ThemeScheduler.ACTION_THEME_BOUNDARY &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_BOOT_COMPLETED
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val app = appContext as? Xm6Application
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val mode = app?.preferencesStore?.prefsFlow?.first()?.themeMode
                if (mode != null) {
                    ThemeScheduler.syncSchedule(appContext, mode)
                }
                Xm6GlanceWidget.updateAllWidgets(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
