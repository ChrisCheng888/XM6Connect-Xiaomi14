package com.xm6.app.ui.theme

import com.xm6.app.data.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ThemeResolverTest {

    @Test
    fun manualModes_ignoreLocalClock() {
        assertFalse(ThemeResolver.resolveIsDark(AppThemeMode.LIGHT, LocalTime.of(2, 0)))
        assertFalse(ThemeResolver.resolveIsDark(AppThemeMode.LIGHT, LocalTime.of(22, 30)))
        assertTrue(ThemeResolver.resolveIsDark(AppThemeMode.DARK, LocalTime.of(9, 0)))
        assertTrue(ThemeResolver.resolveIsDark(AppThemeMode.DARK, LocalTime.of(15, 0)))
    }

    @Test
    fun autoTimeMode_switchesAt0700And1900() {
        assertTrue(ThemeResolver.resolveIsDark(AppThemeMode.AUTO_TIME, LocalTime.of(0, 0)))
        assertTrue(ThemeResolver.resolveIsDark(AppThemeMode.AUTO_TIME, LocalTime.of(6, 59)))
        assertFalse(ThemeResolver.resolveIsDark(AppThemeMode.AUTO_TIME, LocalTime.of(7, 0)))
        assertFalse(ThemeResolver.resolveIsDark(AppThemeMode.AUTO_TIME, LocalTime.of(15, 25)))
        assertFalse(ThemeResolver.resolveIsDark(AppThemeMode.AUTO_TIME, LocalTime.of(18, 59)))
        assertTrue(ThemeResolver.resolveIsDark(AppThemeMode.AUTO_TIME, LocalTime.of(19, 0)))
        assertTrue(ThemeResolver.resolveIsDark(AppThemeMode.AUTO_TIME, LocalTime.of(23, 59)))
    }

    @Test
    fun nextBoundaryDateTime_computesUpcomingTransition() {
        val zone = ZoneId.of("Asia/Shanghai")

        val earlyMorning = ZonedDateTime.of(2026, 11, 26, 5, 30, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 26, 7, 0, 0, 0, zone),
            ThemeResolver.nextBoundaryDateTime(earlyMorning)
        )

        val afternoon = ZonedDateTime.of(2026, 11, 26, 15, 30, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 26, 19, 0, 0, 0, zone),
            ThemeResolver.nextBoundaryDateTime(afternoon)
        )

        val lateNight = ZonedDateTime.of(2026, 11, 26, 21, 15, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 27, 7, 0, 0, 0, zone),
            ThemeResolver.nextBoundaryDateTime(lateNight)
        )
    }
}
