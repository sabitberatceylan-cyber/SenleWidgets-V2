package com.senle.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

abstract class BaseP(val type: String, val cols: Int, val rows: Int) : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            U.update(c, m, id, type, cols, rows, this.javaClass)
        }
        if (type == "weather" || type == "ios_weather") {
            val pr = goAsync()
            Thread {
                try {
                    U.fetchWeather(c)
                    for (id in ids) {
                        U.update(c, m, id, type, cols, rows, this.javaClass)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pr.finish()
                }
            }.start()
        }
    }

    override fun onDeleted(c: Context, ids: IntArray) {
        ids.forEach { P.clear(c, it) }
    }
}

// Standart Widget'lar
class ClockW21 : BaseP("clock", 2, 1)
class ClockW22 : BaseP("clock", 2, 2)
class ClockW31 : BaseP("clock", 3, 1)
class ClockW32 : BaseP("clock", 3, 2)

class WeatherW21 : BaseP("weather", 2, 1)
class WeatherW22 : BaseP("weather", 2, 2)
class WeatherW31 : BaseP("weather", 3, 1)
class WeatherW32 : BaseP("weather", 3, 2)

class LinkW21 : BaseP("link", 2, 1)
class LinkW22 : BaseP("link", 2, 2)
class LinkW31 : BaseP("link", 3, 1)
class LinkW32 : BaseP("link", 3, 2)

class DateW22 : BaseP("date", 2, 2)

// Apple / iOS Tarzı Widget'lar (Samsung One UI 8.5 Entegre)
class IosCalendarW22 : BaseP("ios_calendar", 2, 2)
class IosWeatherW22 : BaseP("ios_weather", 2, 2)
class IosWeatherW21 : BaseP("ios_weather", 2, 1)
class IosBatteryW22 : BaseP("ios_battery", 2, 2)
class IosBatteryW21 : BaseP("ios_battery", 2, 1)
class IosClockW22 : BaseP("ios_clock", 2, 2)
class IosClockW21 : BaseP("ios_clock", 2, 1)
class IosNotesW22 : BaseP("ios_notes", 2, 2)

// 🎵 Spotify & Medya Çalar Widget'ları (1x3, 1x4 Yatay Banner, 2x2, 4x2 Kartlar & Kilit/AOD)
class MusicW41 : BaseP("music_41", 4, 1)
class MusicW31 : BaseP("music_41", 3, 1)
class MusicW22 : BaseP("music_22", 2, 2)
class MusicW33 : BaseP("music_22", 3, 3)
class MusicW44 : BaseP("music_22", 4, 4)
class MusicW42 : BaseP("music_22", 4, 2)
class MusicLockW21 : BaseP("music_lock", 2, 1)
