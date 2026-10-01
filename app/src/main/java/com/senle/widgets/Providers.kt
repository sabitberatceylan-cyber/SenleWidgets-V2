package com.senle.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

abstract class BaseP(val type: String, val cols: Int, val rows: Int) : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            U.update(c, m, id, type, cols, rows, this.javaClass)
        }
        if (type == "weather") {
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
