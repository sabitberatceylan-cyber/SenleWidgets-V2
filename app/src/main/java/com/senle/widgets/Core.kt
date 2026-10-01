package com.senle.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

object P {
    private fun sp(c: Context) = c.getSharedPreferences("senle_widgets_prefs", Context.MODE_PRIVATE)

    fun s(c: Context, id: Int, k: String, d: String): String {
        val prefs = sp(c)
        if (id != 0 && prefs.contains("${id}_$k")) {
            return prefs.getString("${id}_$k", d) ?: d
        }
        return prefs.getString("global_${k}", prefs.getString(k, d)) ?: d
    }

    fun b(c: Context, id: Int, k: String, d: Boolean): Boolean {
        val prefs = sp(c)
        if (id != 0 && prefs.contains("${id}_$k")) {
            return prefs.getBoolean("${id}_$k", d)
        }
        return prefs.getBoolean("global_${k}", prefs.getBoolean(k, d))
    }

    fun put(c: Context, id: Int, k: String, v: Any) {
        val e = sp(c).edit()
        val prefix = if (id != 0) "${id}_$k" else "global_$k"
        when (v) {
            is Boolean -> {
                e.putBoolean(prefix, v)
                if (id == 0) e.putBoolean(k, v)
            }
            else -> {
                e.putString(prefix, v.toString())
                if (id == 0) e.putString(k, v.toString())
            }
        }
        e.apply()
    }

    fun clear(c: Context, id: Int) {
        val e = sp(c).edit()
        sp(c).all.keys.filter { it.startsWith("${id}_") }.forEach { e.remove(it) }
        e.apply()
    }
}

fun col(s: String, d: Int): Int = try {
    Color.parseColor(s.trim())
} catch (e: Exception) {
    d
}

object U {

    fun bg(color: Int, cols: Int, rows: Int, isTransparent: Boolean): Bitmap {
        val w = cols * 240
        val h = rows * 240
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rad = minOf(w, h) * 0.16f
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

        if (isTransparent) {
            p.color = Color.parseColor("#15FFFFFF")
            cv.drawRoundRect(rect, rad, rad, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f
            p.color = Color.parseColor("#30FFFFFF")
            cv.drawRoundRect(rect, rad, rad, p)
        } else {
            p.color = color
            cv.drawRoundRect(rect, rad, rad, p)
        }
        return bmp
    }

    private fun pi(c: Context, id: Int, i: Intent) =
        PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun update(c: Context, m: AppWidgetManager, id: Int, type: String, cols: Int, rows: Int, cls: Class<*>) {
        val rv = when (type) {
            "clock" -> clock(c, id, cols, rows)
            "weather" -> weather(c, id, cols, rows, cls)
            "link" -> link(c, id, cols, rows)
            else -> date(c, id)
        }
        m.updateAppWidget(id, rv)
    }

    private fun clock(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_clock)
        val tc = col(P.s(c, id, "clock_text", "#FFFFFF"), Color.WHITE)
        val isTransp = P.b(c, id, "clock_transp", false)
        val bgColor = col(P.s(c, id, "clock_bg", "#1E1E2E"), Color.DKGRAY)

        rv.setImageViewBitmap(R.id.bg, bg(bgColor, cols, rows, isTransp))

        val tf = if (P.b(c, id, "clock_h24", true)) "HH:mm" else "h:mm"
        rv.setCharSequence(R.id.time, "setFormat24Hour", tf)
        rv.setCharSequence(R.id.time, "setFormat12Hour", tf)
        rv.setTextColor(R.id.time, tc)

        val df = if (P.b(c, id, "clock_wd", true)) "d MMMM EEEE" else "d MMMM yyyy"
        val show = P.b(c, id, "clock_showDate", true)
        val pos = P.s(c, id, "clock_pos", "bottom")

        for (v in listOf(R.id.dateTop, R.id.dateBottom)) {
            rv.setCharSequence(v, "setFormat24Hour", df)
            rv.setCharSequence(v, "setFormat12Hour", df)
            rv.setTextColor(v, tc)
        }

        rv.setViewVisibility(R.id.dateTop, if (show && pos == "top") View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.dateBottom, if (show && pos != "top") View.VISIBLE else View.GONE)

        val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        rv.setOnClickPendingIntent(R.id.root, pi(c, id, clockIntent))
        return rv
    }

    private fun mapWeather(code: Int): Pair<String, String> {
        return when (code) {
            0 -> Pair("☀️", "Açık")
            1, 2 -> Pair("🌤️", "Az Bulutlu")
            3 -> Pair("⛅", "Parçalı Bulutlu")
            45, 48 -> Pair("🌫️", "Sisli")
            51, 53, 55 -> Pair("🌦️", "Çisenti")
            56, 57 -> Pair("🌨️", "Dondurucu Çisenti")
            61, 63 -> Pair("🌧️", "Yağmurlu")
            65 -> Pair("🌧️", "Kuvvetli Yağmur")
            66, 67 -> Pair("🌨️", "Dondurucu Yağmur")
            71, 73, 75 -> Pair("❄️", "Kar Yağışlı")
            77 -> Pair("🌨️", "Kar Taneleri")
            80, 81, 82 -> Pair("🌧️", "Sağanak Yağış")
            85, 86 -> Pair("❄️", "Kar Sağanağı")
            95 -> Pair("⛈️", "Gök Gürültülü Fırtına")
            96, 99 -> Pair("⛈️", "Dolulu Fırtına")
            else -> Pair("🌡️", "Normal")
        }
    }

    fun fetchWeather(c: Context) {
        try {
            val source = P.s(c, 0, "weather_source", "open_meteo")
            val lat = P.s(c, 0, "lat", "41.0082")
            val lon = P.s(c, 0, "lon", "28.9784")
            val city = P.s(c, 0, "city", "İSTANBUL")

            if (source == "google") {
                val key = P.s(c, 0, "google_apikey", "")
                if (key.isNotEmpty()) {
                    val u = URL("https://weather.googleapis.com/v1/currentConditions:lookup?key=$key&location.latitude=$lat&location.longitude=$lon&languageCode=tr")
                    val con = u.openConnection() as HttpURLConnection
                    con.connectTimeout = 8000
                    con.readTimeout = 8000
                    if (con.responseCode == 200) {
                        val j = JSONObject(con.inputStream.bufferedReader().readText())
                        val t = j.getJSONObject("temperature").getDouble("degrees")
                        val wc = j.getJSONObject("weatherCondition")
                        val d = wc.optJSONObject("description")?.optString("text") ?: "Açık"
                        val type = wc.optString("type")
                        val icon = when {
                            type.contains("THUNDER") -> "⛈️"
                            type.contains("SNOW") -> "❄️"
                            type.contains("RAIN") -> "🌧️"
                            type.contains("CLOUDY") -> "⛅"
                            else -> "☀️"
                        }
                        P.put(c, 0, "w_temp", "$icon ${Math.round(t)}°C")
                        P.put(c, 0, "w_desc", d)
                        P.put(c, 0, "w_city", city.uppercase())
                        return
                    }
                }
            }

            // Varsayılan & API anahtarsız Open-Meteo Servisi
            val u = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto")
            val con = u.openConnection() as HttpURLConnection
            con.connectTimeout = 8000
            con.readTimeout = 8000
            if (con.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(con.inputStream))
                val json = JSONObject(reader.readText())
                val current = json.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")
                val (icon, desc) = mapWeather(code)

                P.put(c, 0, "w_temp", "$icon ${Math.round(temp)}°C")
                P.put(c, 0, "w_desc", desc)
                P.put(c, 0, "w_city", city.uppercase())
            } else {
                P.put(c, 0, "w_desc", "Bağlantı Hatası (${con.responseCode})")
            }
        } catch (e: Exception) {
            P.put(c, 0, "w_desc", "Güncellenemedi")
        }
    }

    private fun weather(c: Context, id: Int, cols: Int, rows: Int, cls: Class<*>): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_weather)
        val tc = col(P.s(c, id, "weather_text", "#FFFFFF"), Color.WHITE)
        val isTransp = P.b(c, id, "weather_transp", false)
        val bgColor = col(P.s(c, id, "weather_bg", "#1565C0"), Color.parseColor("#1565C0"))

        rv.setImageViewBitmap(R.id.bg, bg(bgColor, cols, rows, isTransp))
        rv.setTextViewText(R.id.city, P.s(c, 0, "w_city", P.s(c, 0, "city", "İSTANBUL").uppercase()))
        rv.setTextViewText(R.id.temp, P.s(c, 0, "w_temp", "⛅ 21°C"))
        rv.setTextViewText(R.id.desc, P.s(c, 0, "w_desc", "Parçalı Bulutlu"))
        rv.setTextColor(R.id.city, tc)
        rv.setTextColor(R.id.temp, tc)
        rv.setTextColor(R.id.desc, tc)

        val i = Intent(c, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
        rv.setOnClickPendingIntent(R.id.root, PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return rv
    }

    private fun link(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_link)
        val tc = col(P.s(c, id, "link_text", "#FFFFFF"), Color.WHITE)
        val isTransp = P.b(c, id, "link_transp", false)
        val bgColor = col(P.s(c, id, "link_bg", "#4F46E5"), Color.parseColor("#4F46E5"))

        rv.setImageViewBitmap(R.id.bg, bg(bgColor, cols, rows, isTransp))

        val topText = P.s(c, id, "link_top", "TIKLA")
        rv.setTextViewText(R.id.top, topText)
        val lb = P.s(c, id, "link_label", "Web'i Aç")
        rv.setTextViewText(R.id.label, lb)
        rv.setViewVisibility(R.id.label, if (lb.isEmpty()) View.GONE else View.VISIBLE)
        rv.setTextColor(R.id.top, tc)
        rv.setTextColor(R.id.label, tc)

        var url = P.s(c, id, "link_url", "https://www.google.com")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        rv.setOnClickPendingIntent(R.id.root, pi(c, id, openIntent))
        return rv
    }

    // iPhone Tarzı Klasik Takvim Kartı (2x2 Optimize)
    fun generateCalendarBitmap(c: Context, id: Int): Bitmap {
        val size = 520
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val isTransp = P.b(c, id, "cal_transp", false)
        val cardBg = col(P.s(c, id, "cal_bg", "#FFFFFF"), Color.WHITE)
        val headerColor = col(P.s(c, id, "cal_head", "#E53935"), Color.parseColor("#E53935"))
        val todayCircleColor = col(P.s(c, id, "cal_circle", "#E53935"), headerColor)
        val textColor = col(P.s(c, id, "cal_text", "#1C1C1E"), Color.parseColor("#1C1C1E"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Arka plan kartı
        val cardRect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val rad = 72f
        if (isTransp) {
            paint.color = Color.parseColor("#20FFFFFF")
            cv.drawRoundRect(cardRect, rad, rad, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.parseColor("#35FFFFFF")
            cv.drawRoundRect(cardRect, rad, rad, paint)
            paint.style = Paint.Style.FILL
        } else {
            paint.color = cardBg
            cv.drawRoundRect(cardRect, rad, rad, paint)
        }

        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val today = cal.get(Calendar.DAY_OF_MONTH)

        val monthNames = arrayOf("OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN", "TEMMUZ", "AĞUSTOS", "EYLÜL", "EKİM", "KASIM", "ARALIK")
        val monthTitle = "${monthNames[month]} $year"

        // Başlık (Ay & Yıl)
        paint.color = headerColor
        paint.textSize = 34f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.LEFT
        cv.drawText(monthTitle, 40f, 62f, paint)

        // Hafta günleri başlığı
        val weekDays = arrayOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz")
        paint.textSize = 21f
        paint.isFakeBoldText = true
        paint.color = if (isTransp) Color.parseColor("#B0FFFFFF") else Color.parseColor("#8E8E93")
        paint.textAlign = Paint.Align.CENTER

        val colWidth = (size - 70f) / 7f
        val startX = 35f + colWidth / 2f
        val weekY = 106f

        for (i in 0..6) {
            cv.drawText(weekDays[i], startX + i * colWidth, weekY, paint)
        }

        // Gün matrisi hesaplama
        val tempCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDow = tempCal.get(Calendar.DAY_OF_WEEK)
        val startCol = (firstDow + 5) % 7 // Pazartesi = 0

        var currentCol = startCol
        var currentRow = 0
        val rowHeight = 62f
        val gridStartY = 162f

        paint.textSize = 24f
        paint.isFakeBoldText = false

        for (d in 1..daysInMonth) {
            val cx = startX + currentCol * colWidth
            val cy = gridStartY + currentRow * rowHeight

            if (d == today) {
                // Bugün için daire çiz
                val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = todayCircleColor
                    style = Paint.Style.FILL
                }
                cv.drawCircle(cx, cy - 8f, 22f, circlePaint)

                // Beyaz gün sayısı
                paint.color = Color.WHITE
                paint.isFakeBoldText = true
                cv.drawText(d.toString(), cx, cy, paint)
                paint.isFakeBoldText = false
            } else {
                paint.color = textColor
                cv.drawText(d.toString(), cx, cy, paint)
            }

            currentCol++
            if (currentCol > 6) {
                currentCol = 0
                currentRow++
            }
        }

        return bmp
    }

    private fun date(c: Context, id: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_date)
        val bmp = generateCalendarBitmap(c, id)
        rv.setImageViewBitmap(R.id.calendar_img, bmp)

        // Samsung Takvim veya sistem takvimi intent'i
        val launchIntent = c.packageManager.getLaunchIntentForPackage("com.samsung.android.calendar")
            ?: Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("content://com.android.calendar/time/")
            }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        rv.setOnClickPendingIntent(R.id.root, pi(c, id, launchIntent))
        return rv
    }

    fun updateAll(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        val providers = listOf(
            ClockW21::class.java to ("clock" to (2 to 1)),
            ClockW22::class.java to ("clock" to (2 to 2)),
            ClockW31::class.java to ("clock" to (3 to 1)),
            ClockW32::class.java to ("clock" to (3 to 2)),
            WeatherW21::class.java to ("weather" to (2 to 1)),
            WeatherW22::class.java to ("weather" to (2 to 2)),
            WeatherW31::class.java to ("weather" to (3 to 1)),
            WeatherW32::class.java to ("weather" to (3 to 2)),
            LinkW21::class.java to ("link" to (2 to 1)),
            LinkW22::class.java to ("link" to (2 to 2)),
            LinkW31::class.java to ("link" to (3 to 1)),
            LinkW32::class.java to ("link" to (3 to 2)),
            DateW22::class.java to ("date" to (2 to 2))
        )
        for ((cls, info) in providers) {
            val (type, size) = info
            val (cols, rows) = size
            val ids = m.getAppWidgetIds(ComponentName(c, cls))
            for (id in ids) {
                update(c, m, id, type, cols, rows, cls)
            }
        }
    }
}
