package com.senle.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.util.TypedValue
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

    fun i(c: Context, id: Int, k: String, d: Int): Int {
        val prefs = sp(c)
        if (id != 0 && prefs.contains("${id}_$k")) {
            return try { prefs.getInt("${id}_$k", d) } catch (e: Exception) {
                prefs.getString("${id}_$k", "$d")?.toIntOrNull() ?: d
            }
        }
        return try { prefs.getInt("global_${k}", prefs.getInt(k, d)) } catch (e: Exception) {
            prefs.getString("global_${k}", prefs.getString(k, "$d"))?.toIntOrNull() ?: d
        }
    }

    fun put(c: Context, id: Int, k: String, v: Any) {
        val e = sp(c).edit()
        val prefix = if (id != 0) "${id}_$k" else "global_$k"
        when (v) {
            is Boolean -> {
                e.putBoolean(prefix, v)
                if (id == 0) e.putBoolean(k, v)
            }
            is Int -> {
                e.putInt(prefix, v)
                if (id == 0) e.putInt(k, v)
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

fun applyOpacity(baseColor: Int, opacity: Int): Int {
    if (opacity <= 0) return Color.TRANSPARENT
    val alpha = (opacity * 255 / 100).coerceIn(0, 255)
    return Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
}

object U {

    // One UI 8.5 Squircle Arka Plan Üreteci
    fun bg(color: Int, cols: Int, rows: Int, opacity: Int): Bitmap {
        val w = cols * 240
        val h = rows * 240
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (opacity <= 0) {
            // Tam %100 transparan - sıfır renk ve sıfır çerçeve
            return bmp
        }

        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rad = minOf(w, h) * 0.18f // One UI Squircle corner
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

        val fillColor = applyOpacity(color, opacity)
        p.color = fillColor
        p.style = Paint.Style.FILL
        cv.drawRoundRect(rect, rad, rad, p)

        // Buzlu cam hissi veren hafif çizgi
        if (opacity in 15..95) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.5f
            p.color = Color.argb((opacity * 45 / 100).coerceIn(10, 60), 255, 255, 255)
            cv.drawRoundRect(rect, rad, rad, p)
        }

        return bmp
    }

    // Samsung One UI Paket Açıcı (Yüklü değilse standart Android uygulamasına geçer)
    fun launchSamsungOrFallback(c: Context, id: Int, samsungPkg: String, fallbackIntent: Intent): PendingIntent {
        val pm = c.packageManager
        val intent = pm.getLaunchIntentForPackage(samsungPkg) ?: fallbackIntent
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(c, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun pi(c: Context, id: Int, i: Intent) =
        PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun getBatteryInfo(c: Context): Pair<Int, Boolean> {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val b = c.registerReceiver(null, ifilter)
            val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 80
            Pair(pct, isCharging)
        } catch (e: Exception) {
            Pair(80, false)
        }
    }

    fun update(c: Context, m: AppWidgetManager, id: Int, type: String, cols: Int, rows: Int, cls: Class<*>) {
        val rv = when (type) {
            "clock" -> clock(c, id, cols, rows)
            "weather" -> weather(c, id, cols, rows, cls)
            "link" -> link(c, id, cols, rows)
            "date" -> date(c, id)
            "ios_calendar" -> iosCalendar(c, id, cols, rows)
            "ios_weather" -> iosWeather(c, id, cols, rows, cls)
            "ios_battery" -> iosBattery(c, id, cols, rows)
            "ios_clock" -> iosClock(c, id, cols, rows)
            "ios_notes" -> iosNotes(c, id, cols, rows)
            "music_41" -> music41(c, id, cols, rows)
            "music_22" -> music22(c, id, cols, rows)
            "music_lock" -> musicLock(c, id, cols, rows)
            else -> date(c, id)
        }
        m.updateAppWidget(id, rv)
    }

    // 1. STANDART SAAT
    private fun clock(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_clock)
        val tc = col(P.s(c, id, "clock_text", "#FFFFFF"), Color.WHITE)
        val opacity = P.i(c, id, "clock_opacity", 100)
        val bgColor = col(P.s(c, id, "clock_bg", "#1E1E2E"), Color.DKGRAY)

        rv.setImageViewBitmap(R.id.bg, bg(bgColor, cols, rows, opacity))

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

        val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        rv.setOnClickPendingIntent(R.id.root, launchSamsungOrFallback(c, id, "com.sec.android.app.clockpackage", clockIntent))
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
                        P.put(c, 0, "w_temp_num", "${Math.round(t)}°")
                        P.put(c, 0, "w_icon", icon)
                        P.put(c, 0, "w_desc", d)
                        P.put(c, 0, "w_city", city.uppercase())
                        return
                    }
                }
            }

            // Open-Meteo Servisi
            val u = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto")
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

                var high = Math.round(temp + 2).toString()
                var low = Math.round(temp - 4).toString()
                if (json.has("daily")) {
                    val daily = json.getJSONObject("daily")
                    val maxArr = daily.optJSONArray("temperature_2m_max")
                    val minArr = daily.optJSONArray("temperature_2m_min")
                    if (maxArr != null && maxArr.length() > 0) high = Math.round(maxArr.getDouble(0)).toString()
                    if (minArr != null && minArr.length() > 0) low = Math.round(minArr.getDouble(0)).toString()
                }

                P.put(c, 0, "w_temp", "$icon ${Math.round(temp)}°C")
                P.put(c, 0, "w_temp_num", "${Math.round(temp)}°")
                P.put(c, 0, "w_icon", icon)
                P.put(c, 0, "w_high_low", "Y: $high°  D: $low°")
                P.put(c, 0, "w_desc", desc)
                P.put(c, 0, "w_city", city.uppercase())
            } else {
                P.put(c, 0, "w_desc", "Bağlantı Hatası")
            }
        } catch (e: Exception) {
            P.put(c, 0, "w_desc", "Güncellenemedi")
        }
    }

    // 2. STANDART HAVA DURUMU
    private fun weather(c: Context, id: Int, cols: Int, rows: Int, cls: Class<*>): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_weather)
        val tc = col(P.s(c, id, "weather_text", "#FFFFFF"), Color.WHITE)
        val opacity = P.i(c, id, "weather_opacity", 100)
        val bgColor = col(P.s(c, id, "weather_bg", "#1565C0"), Color.parseColor("#1565C0"))

        rv.setImageViewBitmap(R.id.bg, bg(bgColor, cols, rows, opacity))
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

    // 3. STANDART WEB LİNK BUTONU
    private fun link(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_link)
        val tc = col(P.s(c, id, "link_text", "#FFFFFF"), Color.WHITE)
        val opacity = P.i(c, id, "link_opacity", 100)
        val bgColor = col(P.s(c, id, "link_bg", "#4F46E5"), Color.parseColor("#4F46E5"))

        rv.setImageViewBitmap(R.id.bg, bg(bgColor, cols, rows, opacity))

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
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        rv.setOnClickPendingIntent(R.id.root, pi(c, id, openIntent))
        return rv
    }

    // 4. STANDART TAKVİM
    private fun date(c: Context, id: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_date)
        val bmp = generateIosCalendarBitmap(c, id, 2, 2)
        rv.setImageViewBitmap(R.id.calendar_img, bmp)

        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("content://com.android.calendar/time/") }
        rv.setOnClickPendingIntent(R.id.root, launchSamsungOrFallback(c, id, "com.samsung.android.calendar", fallbackIntent))
        return rv
    }

    // ==========================================
    // 🍎 iOS / APPLE TARZI WIDGET ÇİZİCİLERİ
    // ==========================================

    // 🍎 1. iOS Klasik Takvim Kartı (2x2)
    fun generateIosCalendarBitmap(c: Context, id: Int, cols: Int, rows: Int): Bitmap {
        val w = cols * 260
        val h = rows * 260
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val opacity = P.i(c, id, "cal_opacity", 100)
        val cardBg = col(P.s(c, id, "cal_bg", "#FFFFFF"), Color.WHITE)
        val headerColor = col(P.s(c, id, "cal_head", "#E53935"), Color.parseColor("#E53935"))
        val todayCircleColor = col(P.s(c, id, "cal_circle", "#E53935"), headerColor)
        val textColor = col(P.s(c, id, "cal_text", "#1C1C1E"), Color.parseColor("#1C1C1E"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Arka plan çizimi (Opacity ve One UI Squircle)
        val cardRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val rad = minOf(w, h) * 0.18f

        if (opacity > 0) {
            paint.color = applyOpacity(cardBg, opacity)
            paint.style = Paint.Style.FILL
            cv.drawRoundRect(cardRect, rad, rad, paint)
            if (opacity in 15..95) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = Color.argb((opacity * 40 / 100).coerceIn(10, 60), 255, 255, 255)
                cv.drawRoundRect(cardRect, rad, rad, paint)
                paint.style = Paint.Style.FILL
            }
        }

        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val today = cal.get(Calendar.DAY_OF_MONTH)

        val monthNames = arrayOf("OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN", "TEMMUZ", "AĞUSTOS", "EYLÜL", "EKİM", "KASIM", "ARALIK")
        val monthTitle = "${monthNames[month]} $year"

        // Başlık
        paint.color = headerColor
        paint.textSize = 34f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.LEFT
        cv.drawText(monthTitle, 40f, 62f, paint)

        // Hafta günleri
        val weekDays = arrayOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz")
        paint.textSize = 21f
        paint.color = if (opacity < 50) Color.WHITE else Color.parseColor("#8E8E93")
        paint.textAlign = Paint.Align.CENTER

        val colWidth = (w - 70f) / 7f
        val startX = 35f + colWidth / 2f
        val weekY = 106f

        for (i in 0..6) {
            cv.drawText(weekDays[i], startX + i * colWidth, weekY, paint)
        }

        // Gün matrisi
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

        for (d in 1..daysInMonth) {
            val cx = startX + currentCol * colWidth
            val cy = gridStartY + currentRow * rowHeight

            if (d == today) {
                val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = todayCircleColor
                    style = Paint.Style.FILL
                }
                cv.drawCircle(cx, cy - 8f, 22f, circlePaint)

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

    // 🍎 2. Apple Hava Durumu Kartı (2x2 veya 2x1)
    fun generateIosWeatherBitmap(c: Context, id: Int, cols: Int, rows: Int): Bitmap {
        val w = cols * 260
        val h = rows * 260
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val opacity = P.i(c, id, "ios_w_opacity", 100)
        val cardBg = col(P.s(c, id, "ios_w_bg", "#1E3A8A"), Color.parseColor("#1E3A8A"))
        val textColor = col(P.s(c, id, "ios_w_text", "#FFFFFF"), Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val rad = minOf(w, h) * 0.18f

        if (opacity > 0) {
            paint.color = applyOpacity(cardBg, opacity)
            paint.style = Paint.Style.FILL
            cv.drawRoundRect(cardRect, rad, rad, paint)
            if (opacity in 15..95) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = Color.argb((opacity * 40 / 100).coerceIn(10, 60), 255, 255, 255)
                cv.drawRoundRect(cardRect, rad, rad, paint)
                paint.style = Paint.Style.FILL
            }
        }

        val city = P.s(c, 0, "w_city", P.s(c, 0, "city", "İSTANBUL").uppercase())
        val temp = P.s(c, 0, "w_temp_num", "22°")
        val icon = P.s(c, 0, "w_icon", "⛅")
        val desc = P.s(c, 0, "w_desc", "Parçalı Bulutlu")
        val highLow = P.s(c, 0, "w_high_low", "Y: 25°  D: 16°")

        paint.color = textColor
        paint.textAlign = Paint.Align.LEFT

        if (rows == 1) {
            // 2x1 Yatay Tasarım
            paint.textSize = 28f
            paint.isFakeBoldText = true
            cv.drawText(city, 36f, 62f, paint)

            paint.textSize = 20f
            paint.isFakeBoldText = false
            paint.color = Color.argb(200, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            cv.drawText(desc, 36f, 102f, paint)

            paint.color = textColor
            paint.textSize = 64f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.RIGHT
            cv.drawText(temp, w - 100f, 96f, paint)

            paint.textSize = 48f
            cv.drawText(icon, w - 30f, 96f, paint)
        } else {
            // 2x2 Kare Tasarım (Tam Apple Weather Kartı)
            paint.textSize = 30f
            paint.isFakeBoldText = true
            cv.drawText(city, 40f, 66f, paint)

            paint.textSize = 84f
            paint.isFakeBoldText = true
            cv.drawText(temp, 40f, 180f, paint)

            // İkon
            paint.textSize = 80f
            paint.textAlign = Paint.Align.RIGHT
            cv.drawText(icon, w - 40f, 170f, paint)

            // Alt durum ve Y: D: bilgisi
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 26f
            paint.isFakeBoldText = true
            cv.drawText(desc, 40f, 370f, paint)

            paint.textSize = 22f
            paint.isFakeBoldText = false
            paint.color = Color.argb(210, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            cv.drawText(highLow, 40f, 415f, paint)

            paint.textSize = 18f
            cv.drawText("Apple Weather Stili • One UI 8.5", 40f, 465f, paint)
        }

        return bmp
    }

    // 🍎 3. Apple Batarya Halka Kartı (2x2 veya 2x1)
    fun generateIosBatteryBitmap(c: Context, id: Int, cols: Int, rows: Int): Bitmap {
        val w = cols * 260
        val h = rows * 260
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val opacity = P.i(c, id, "ios_bat_opacity", 100)
        val cardBg = col(P.s(c, id, "ios_bat_bg", "#1C1C1E"), Color.parseColor("#1C1C1E"))
        val textColor = col(P.s(c, id, "ios_bat_text", "#FFFFFF"), Color.WHITE)
        val accentColor = col(P.s(c, id, "ios_bat_accent", "#34C759"), Color.parseColor("#34C759")) // Apple Green

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val rad = minOf(w, h) * 0.18f

        if (opacity > 0) {
            paint.color = applyOpacity(cardBg, opacity)
            paint.style = Paint.Style.FILL
            cv.drawRoundRect(cardRect, rad, rad, paint)
            if (opacity in 15..95) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = Color.argb((opacity * 40 / 100).coerceIn(10, 60), 255, 255, 255)
                cv.drawRoundRect(cardRect, rad, rad, paint)
                paint.style = Paint.Style.FILL
            }
        }

        val (pct, isCharging) = getBatteryInfo(c)
        val ringColor = if (pct <= 20) Color.parseColor("#FF3B30") else accentColor

        if (rows == 1) {
            // 2x1 Yatay
            val cx = 110f; val cy = h / 2f; val ringR = 64f
            val ringRect = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 14f
            paint.color = Color.parseColor("#33FFFFFF")
            cv.drawArc(ringRect, 0f, 360f, false, paint)

            paint.color = ringColor
            val sweep = (pct * 360f / 100f)
            cv.drawArc(ringRect, -90f, sweep, false, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 28f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            paint.color = textColor
            cv.drawText(if (isCharging) "⚡" else "$pct%", cx, cy + 10f, paint)

            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 30f
            paint.isFakeBoldText = true
            cv.drawText("Samsung Galaxy", 220f, cy - 8f, paint)

            paint.textSize = 22f
            paint.isFakeBoldText = false
            paint.color = Color.argb(200, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            cv.drawText(if (isCharging) "Şarj Oluyor • %$pct" else "Pil: %$pct", 220f, cy + 30f, paint)
        } else {
            // 2x2 Kare
            val cx = w / 2f; val cy = h / 2f - 30f; val ringR = 120f
            val ringRect = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)

            // Arka plan halkası
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 24f
            paint.color = Color.parseColor("#33FFFFFF")
            cv.drawArc(ringRect, 0f, 360f, false, paint)

            // Doluluk halkası
            paint.color = ringColor
            val sweep = (pct * 360f / 100f)
            cv.drawArc(ringRect, -90f, sweep, false, paint)

            // Halka içi simge ve yüzde
            paint.style = Paint.Style.FILL
            paint.textSize = 58f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            paint.color = textColor
            cv.drawText(if (isCharging) "⚡" else "🔋", cx, cy + 6f, paint)

            paint.textSize = 32f
            cv.drawText("%$pct", cx, cy + 54f, paint)

            // Alt etiket
            paint.textSize = 26f
            paint.color = textColor
            cv.drawText("One UI 8.5 Batarya", cx, h - 80f, paint)

            paint.textSize = 20f
            paint.isFakeBoldText = false
            paint.color = Color.argb(190, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            cv.drawText(if (isCharging) "Hızlı Şarj Bağlı" else "Kalan Durum Normal", cx, h - 48f, paint)
        }

        return bmp
    }

    // 🍎 4. Apple Minimalist Saat Kartı (2x2 veya 2x1)
    fun generateIosClockBitmap(c: Context, id: Int, cols: Int, rows: Int): Bitmap {
        val w = cols * 260
        val h = rows * 260
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val opacity = P.i(c, id, "ios_clk_opacity", 100)
        val cardBg = col(P.s(c, id, "ios_clk_bg", "#18181B"), Color.parseColor("#18181B"))
        val textColor = col(P.s(c, id, "ios_clk_text", "#FFFFFF"), Color.WHITE)
        val accentColor = col(P.s(c, id, "ios_clk_accent", "#FF9500"), Color.parseColor("#FF9500")) // Apple Orange

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val rad = minOf(w, h) * 0.18f

        if (opacity > 0) {
            paint.color = applyOpacity(cardBg, opacity)
            paint.style = Paint.Style.FILL
            cv.drawRoundRect(cardRect, rad, rad, paint)
            if (opacity in 15..95) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = Color.argb((opacity * 40 / 100).coerceIn(10, 60), 255, 255, 255)
                cv.drawRoundRect(cardRect, rad, rad, paint)
                paint.style = Paint.Style.FILL
            }
        }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val timeStr = String.format("%02d:%02d", hour, minute)

        val dayNames = arrayOf("Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi")
        val monthNames = arrayOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
        val dayOfWeek = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val month = monthNames[cal.get(Calendar.MONTH)]
        val dateStr = "$dayOfWeek, $dayOfMonth $month"

        paint.color = textColor
        paint.textAlign = Paint.Align.LEFT

        if (rows == 1) {
            paint.textSize = 24f
            paint.color = accentColor
            paint.isFakeBoldText = true
            cv.drawText("İSTANBUL", 38f, 54f, paint)

            paint.textSize = 58f
            paint.color = textColor
            cv.drawText(timeStr, 38f, 114f, paint)

            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = 22f
            paint.color = Color.argb(200, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            cv.drawText(dateStr, w - 38f, 100f, paint)
        } else {
            paint.textSize = 28f
            paint.color = accentColor
            paint.isFakeBoldText = true
            cv.drawText("DÜNYA SAATİ", 40f, 68f, paint)

            paint.textSize = 104f
            paint.color = textColor
            paint.isFakeBoldText = true
            cv.drawText(timeStr, 40f, 210f, paint)

            paint.textSize = 28f
            paint.isFakeBoldText = false
            cv.drawText(dateStr, 40f, 310f, paint)

            paint.textSize = 24f
            paint.color = Color.argb(190, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            cv.drawText("İSTANBUL  +0 SAAT", 40f, 390f, paint)
            cv.drawText("⏰ Alarm: Açık • One UI 8.5", 40f, 440f, paint)
        }

        return bmp
    }

    // 🍎 5. Apple Notlar / Hatırlatıcı Kartı (2x2)
    fun generateIosNotesBitmap(c: Context, id: Int, cols: Int, rows: Int): Bitmap {
        val w = cols * 260
        val h = rows * 260
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val opacity = P.i(c, id, "ios_notes_opacity", 100)
        val cardBg = col(P.s(c, id, "ios_notes_bg", "#1C1C1E"), Color.parseColor("#1C1C1E"))
        val textColor = col(P.s(c, id, "ios_notes_text", "#FFFFFF"), Color.WHITE)
        val accentColor = col(P.s(c, id, "ios_notes_accent", "#0A84FF"), Color.parseColor("#0A84FF")) // Apple Blue

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val rad = minOf(w, h) * 0.18f

        if (opacity > 0) {
            paint.color = applyOpacity(cardBg, opacity)
            paint.style = Paint.Style.FILL
            cv.drawRoundRect(cardRect, rad, rad, paint)
            if (opacity in 15..95) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = Color.argb((opacity * 40 / 100).coerceIn(10, 60), 255, 255, 255)
                cv.drawRoundRect(cardRect, rad, rad, paint)
                paint.style = Paint.Style.FILL
            }
        }

        paint.textAlign = Paint.Align.LEFT
        paint.color = accentColor
        paint.textSize = 34f
        paint.isFakeBoldText = true
        cv.drawText("📋 Hatırlatıcılar", 40f, 68f, paint)

        val note1 = P.s(c, id, "ios_note_1", "✓ Günlük Görevleri Tamamla")
        val note2 = P.s(c, id, "ios_note_2", "✓ Toplantı ve Planlar")
        val note3 = P.s(c, id, "ios_note_3", "✓ Samsung Notlara Git")

        paint.color = textColor
        paint.textSize = 26f
        paint.isFakeBoldText = false
        cv.drawText(note1, 40f, 170f, paint)
        cv.drawText(note2, 40f, 250f, paint)
        cv.drawText(note3, 40f, 330f, paint)

        paint.textSize = 20f
        paint.color = Color.argb(170, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
        cv.drawText("Dokununca Samsung Notlar açılır", 40f, 440f, paint)

        return bmp
    }

    private fun iosCalendar(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_ios_card)
        val bmp = generateIosCalendarBitmap(c, id, cols, rows)
        rv.setImageViewBitmap(R.id.card_img, bmp)

        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("content://com.android.calendar/time/") }
        rv.setOnClickPendingIntent(R.id.root, launchSamsungOrFallback(c, id, "com.samsung.android.calendar", fallbackIntent))
        return rv
    }

    private fun iosWeather(c: Context, id: Int, cols: Int, rows: Int, cls: Class<*>): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_ios_card)
        val bmp = generateIosWeatherBitmap(c, id, cols, rows)
        rv.setImageViewBitmap(R.id.card_img, bmp)

        val fallbackIntent = Intent(c, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
        rv.setOnClickPendingIntent(R.id.root, launchSamsungOrFallback(c, id, "com.sec.android.daemonapp", fallbackIntent))
        return rv
    }

    private fun iosBattery(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_ios_card)
        val bmp = generateIosBatteryBitmap(c, id, cols, rows)
        rv.setImageViewBitmap(R.id.card_img, bmp)

        val fallbackIntent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        rv.setOnClickPendingIntent(R.id.root, launchSamsungOrFallback(c, id, "com.samsung.android.lool", fallbackIntent))
        return rv
    }

    private fun iosClock(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_ios_card)
        val bmp = generateIosClockBitmap(c, id, cols, rows)
        rv.setImageViewBitmap(R.id.card_img, bmp)

        val fallbackIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        rv.setOnClickPendingIntent(R.id.root, launchSamsungOrFallback(c, id, "com.sec.android.app.clockpackage", fallbackIntent))
        return rv
    }

    private fun iosNotes(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_ios_card)
        val bmp = generateIosNotesBitmap(c, id, cols, rows)
        rv.setImageViewBitmap(R.id.card_img, bmp)

        val fallbackIntent = Intent(Intent.ACTION_MAIN)
        rv.setOnClickPendingIntent(R.id.root, launchSamsungOrFallback(c, id, "com.samsung.android.app.notes", fallbackIntent))
        return rv
    }

    private fun getSpotifyLaunchIntent(c: Context): PendingIntent {
        val pm = c.packageManager
        val intent = pm.getLaunchIntentForPackage("com.spotify.music")
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MUSIC)
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(c, 8888, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getAodMusicLaunchIntent(c: Context): PendingIntent {
        val intent = Intent(c, AodMusicActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(c, 8889, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getRoundedCornerBitmap(src: Bitmap, cornerRadius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun music41(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_music_41)
        val isPlaying = MediaHolder.isPlaying

        val hideIdle = P.b(c, id, "music_hide_idle", P.b(c, 0, "music_hide_idle", false))
        if (hideIdle && !isPlaying) {
            rv.setViewVisibility(R.id.root, View.GONE)
            return rv
        } else {
            rv.setViewVisibility(R.id.root, View.VISIBLE)
        }

        val tc = col(P.s(c, id, "music_text", "#FFFFFF"), Color.WHITE)
        val opacity = P.i(c, id, "music_opacity", 90)
        val bgColor = col(P.s(c, id, "music_bg", "#121212"), Color.parseColor("#121212"))
        val accentColor = col(P.s(c, id, "music_accent", "#1DB954"), Color.parseColor("#1DB954"))

        rv.setImageViewBitmap(R.id.music_bg, bg(bgColor, cols, rows, opacity))

        val title = P.s(c, 0, "music_title", MediaHolder.songTitle)
        val artist = P.s(c, 0, "music_artist", MediaHolder.artistName)
        rv.setTextViewText(R.id.song_title, title)
        rv.setTextViewText(R.id.artist_name, artist)
        rv.setTextColor(R.id.song_title, tc)
        val subColor = Color.argb(190, Color.red(tc), Color.green(tc), Color.blue(tc))
        rv.setTextColor(R.id.artist_name, subColor)

        val art = MediaHolder.albumArt
        if (art != null) {
            val roundedArt = getRoundedCornerBitmap(art, art.width * 0.15f)
            rv.setImageViewBitmap(R.id.album_art, roundedArt)
        } else {
            rv.setImageViewResource(R.id.album_art, R.drawable.ic_music_note)
        }

        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        rv.setImageViewResource(R.id.btn_play_pause, playIcon)

        try {
            rv.setInt(R.id.btn_prev, "setColorFilter", tc)
            rv.setInt(R.id.btn_next, "setColorFilter", tc)
            rv.setInt(R.id.btn_play_pause, "setColorFilter", accentColor)
        } catch (e: Exception) {}

        val prevIntent = Intent(c, MediaActionReceiver::class.java).apply {
            action = "com.senle.widgets.ACTION_MEDIA_PREV"
        }
        val prevPending = PendingIntent.getBroadcast(c, id * 10 + 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.btn_prev, prevPending)

        val playIntent = Intent(c, MediaActionReceiver::class.java).apply {
            action = "com.senle.widgets.ACTION_MEDIA_PLAY_PAUSE"
        }
        val playPending = PendingIntent.getBroadcast(c, id * 10 + 2, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.btn_play_pause, playPending)

        val nextIntent = Intent(c, MediaActionReceiver::class.java).apply {
            action = "com.senle.widgets.ACTION_MEDIA_NEXT"
        }
        val nextPending = PendingIntent.getBroadcast(c, id * 10 + 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.btn_next, nextPending)

        val spotifyPending = getSpotifyLaunchIntent(c)
        rv.setOnClickPendingIntent(R.id.root, spotifyPending)
        rv.setOnClickPendingIntent(R.id.album_art, spotifyPending)

        return rv
    }

    private fun music22(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_music_22)
        val isPlaying = MediaHolder.isPlaying

        val hideIdle = P.b(c, id, "music_hide_idle", P.b(c, 0, "music_hide_idle", false))
        if (hideIdle && !isPlaying) {
            rv.setViewVisibility(R.id.music_content, View.GONE)
            rv.setViewVisibility(R.id.music_bg, View.GONE)
            return rv
        } else {
            rv.setViewVisibility(R.id.music_content, View.VISIBLE)
            rv.setViewVisibility(R.id.music_bg, View.VISIBLE)
        }

        val tc = col(P.s(c, id, "music_text", "#FFFFFF"), Color.WHITE)
        val opacity = P.i(c, id, "music_opacity", 90)
        val bgColor = col(P.s(c, id, "music_bg", "#121212"), Color.parseColor("#121212"))
        val accentColor = col(P.s(c, id, "music_accent", "#1DB954"), Color.parseColor("#1DB954"))

        rv.setImageViewBitmap(R.id.music_bg, bg(bgColor, cols, rows, opacity))

        // Boyut ve Dolgu (Büyütme Ayarı)
        val defaultPad = if (cols >= 3) 6 else 10
        val paddingDp = P.i(c, id, "music_padding", P.i(c, 0, "music_padding", defaultPad))
        val padPx = (paddingDp * c.resources.displayMetrics.density).toInt()
        rv.setViewPadding(R.id.music_content, padPx, padPx, padPx, padPx)

        val scale = P.i(c, id, "music_scale", P.i(c, 0, "music_scale", 100))
        val baseTitleSize = if (cols >= 3) 18f else 15f
        val baseArtistSize = if (cols >= 3) 14f else 12f
        val titleSp = (baseTitleSize * scale / 100f).coerceIn(12f, 26f)
        val artistSp = (baseArtistSize * scale / 100f).coerceIn(10f, 20f)
        rv.setTextViewTextSize(R.id.song_title, TypedValue.COMPLEX_UNIT_SP, titleSp)
        rv.setTextViewTextSize(R.id.artist_name, TypedValue.COMPLEX_UNIT_SP, artistSp)

        val title = P.s(c, 0, "music_title", MediaHolder.songTitle)
        val artist = P.s(c, 0, "music_artist", MediaHolder.artistName)
        rv.setTextViewText(R.id.song_title, title)
        rv.setTextViewText(R.id.artist_name, artist)
        rv.setTextColor(R.id.song_title, tc)
        val subColor = Color.argb(190, Color.red(tc), Color.green(tc), Color.blue(tc))
        rv.setTextColor(R.id.artist_name, subColor)

        val art = MediaHolder.albumArt
        if (art != null) {
            val roundedArt = getRoundedCornerBitmap(art, art.width * 0.15f)
            rv.setImageViewBitmap(R.id.album_art, roundedArt)
        } else {
            rv.setImageViewResource(R.id.album_art, R.drawable.ic_music_note)
        }

        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        rv.setImageViewResource(R.id.btn_play_pause, playIcon)

        try {
            rv.setInt(R.id.btn_prev, "setColorFilter", tc)
            rv.setInt(R.id.btn_next, "setColorFilter", tc)
            rv.setInt(R.id.btn_play_pause, "setColorFilter", accentColor)
        } catch (e: Exception) {}

        val prevIntent = Intent(c, MediaActionReceiver::class.java).apply {
            action = "com.senle.widgets.ACTION_MEDIA_PREV"
        }
        val prevPending = PendingIntent.getBroadcast(c, id * 10 + 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.btn_prev, prevPending)

        val playIntent = Intent(c, MediaActionReceiver::class.java).apply {
            action = "com.senle.widgets.ACTION_MEDIA_PLAY_PAUSE"
        }
        val playPending = PendingIntent.getBroadcast(c, id * 10 + 2, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.btn_play_pause, playPending)

        val nextIntent = Intent(c, MediaActionReceiver::class.java).apply {
            action = "com.senle.widgets.ACTION_MEDIA_NEXT"
        }
        val nextPending = PendingIntent.getBroadcast(c, id * 10 + 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.btn_next, nextPending)

        // 🎵 Spotify Tıklaması (Stabil haline geri getirildi)
        val spotifyPending = getSpotifyLaunchIntent(c)
        rv.setOnClickPendingIntent(R.id.root, spotifyPending)
        rv.setOnClickPendingIntent(R.id.album_art, spotifyPending)

        return rv
    }

    private fun musicLock(c: Context, id: Int, cols: Int, rows: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.w_music_lockscreen)
        val isPlaying = MediaHolder.isPlaying

        val hideIdle = P.b(c, id, "music_hide_idle", P.b(c, 0, "music_hide_idle", false))
        if (hideIdle && !isPlaying) {
            rv.setViewVisibility(R.id.root, View.GONE)
            return rv
        } else {
            rv.setViewVisibility(R.id.root, View.VISIBLE)
        }

        val title = P.s(c, 0, "music_title", MediaHolder.songTitle)
        val artist = P.s(c, 0, "music_artist", MediaHolder.artistName)

        rv.setTextViewText(R.id.song_title, title)
        rv.setTextViewText(R.id.artist_name, artist)

        val art = MediaHolder.albumArt
        if (art != null) {
            val roundedArt = getRoundedCornerBitmap(art, art.width * 0.2f)
            rv.setImageViewBitmap(R.id.album_art, roundedArt)
        } else {
            rv.setImageViewResource(R.id.album_art, R.drawable.ic_music_note)
        }

        rv.setImageViewResource(R.id.btn_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

        val playIntent = Intent(c, MediaActionReceiver::class.java).apply {
            action = "com.senle.widgets.ACTION_MEDIA_PLAY_PAUSE"
        }
        val playPending = PendingIntent.getBroadcast(c, id * 10 + 2, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.btn_play_pause, playPending)

        val spotifyPending = getSpotifyLaunchIntent(c)
        rv.setOnClickPendingIntent(R.id.root, spotifyPending)
        rv.setOnClickPendingIntent(R.id.album_art, spotifyPending)

        return rv
    }

    fun generateMusicBitmap(c: Context, id: Int, cols: Int, rows: Int): Bitmap {
        val w = cols * 260
        val h = rows * 260
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val opacity = P.i(c, id, "music_opacity", 90)
        val bgColor = col(P.s(c, id, "music_bg", "#121212"), Color.parseColor("#121212"))
        val textColor = col(P.s(c, id, "music_text", "#FFFFFF"), Color.WHITE)
        val accentColor = col(P.s(c, id, "music_accent", "#1DB954"), Color.parseColor("#1DB954"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val rad = minOf(w, h) * 0.18f

        if (opacity > 0) {
            paint.color = applyOpacity(bgColor, opacity)
            paint.style = Paint.Style.FILL
            cv.drawRoundRect(cardRect, rad, rad, paint)
            if (opacity in 15..95) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = Color.argb((opacity * 40 / 100).coerceIn(10, 60), 255, 255, 255)
                cv.drawRoundRect(cardRect, rad, rad, paint)
                paint.style = Paint.Style.FILL
            }
        }

        val title = P.s(c, 0, "music_title", MediaHolder.songTitle).ifEmpty { "Spotify'da Şarkı Başlat" }
        val artist = P.s(c, 0, "music_artist", MediaHolder.artistName).ifEmpty { "Dokun ve Çalmaya Başla" }
        val isPlaying = MediaHolder.isPlaying

        if (cols >= 3 && rows == 1) {
            val artSize = h * 0.68f
            val artRect = RectF(28f, (h - artSize) / 2f, 28f + artSize, (h + artSize) / 2f)
            val art = MediaHolder.albumArt
            if (art != null) {
                val roundArt = getRoundedCornerBitmap(art, art.width * 0.18f)
                cv.drawBitmap(roundArt, null, artRect, paint)
            } else {
                paint.color = Color.parseColor("#282828")
                cv.drawRoundRect(artRect, 18f, 18f, paint)
                paint.color = accentColor
                paint.textSize = artSize * 0.45f
                paint.textAlign = Paint.Align.CENTER
                cv.drawText("🎵", artRect.centerX(), artRect.centerY() + 16f, paint)
            }

            val textLeft = artRect.right + 26f
            paint.textAlign = Paint.Align.LEFT
            paint.color = textColor
            paint.textSize = 28f
            paint.isFakeBoldText = true
            val titleTrimmed = if (title.length > 20) title.take(18) + "…" else title
            cv.drawText(titleTrimmed, textLeft, h * 0.45f, paint)

            paint.color = Color.argb(190, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            paint.textSize = 22f
            paint.isFakeBoldText = false
            val artistTrimmed = if (artist.length > 24) artist.take(22) + "…" else artist
            cv.drawText(artistTrimmed, textLeft, h * 0.72f, paint)

            val btnY = h / 2f
            val nextX = w - 60f
            val playX = nextX - 70f
            val prevX = playX - 70f

            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 34f
            paint.color = textColor
            cv.drawText("⏮", prevX, btnY + 12f, paint)

            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.FILL
            }
            cv.drawCircle(playX, btnY, 32f, circlePaint)
            paint.color = Color.WHITE
            paint.textSize = 28f
            cv.drawText(if (isPlaying) "⏸" else "▶", playX + 2f, btnY + 10f, paint)

            paint.color = textColor
            paint.textSize = 34f
            cv.drawText("⏭", nextX, btnY + 12f, paint)
        } else {
            val artSize = minOf(w * 0.52f, h * 0.46f)
            val artLeft = (w - artSize) / 2f
            val artTop = 40f
            val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
            val art = MediaHolder.albumArt
            if (art != null) {
                val roundArt = getRoundedCornerBitmap(art, art.width * 0.18f)
                cv.drawBitmap(roundArt, null, artRect, paint)
            } else {
                paint.color = Color.parseColor("#282828")
                cv.drawRoundRect(artRect, 24f, 24f, paint)
                paint.color = accentColor
                paint.textSize = artSize * 0.42f
                paint.textAlign = Paint.Align.CENTER
                cv.drawText("🎵", artRect.centerX(), artRect.centerY() + 20f, paint)
            }

            paint.textAlign = Paint.Align.CENTER
            paint.color = textColor
            paint.textSize = 28f
            paint.isFakeBoldText = true
            val titleTrimmed = if (title.length > 22) title.take(20) + "…" else title
            cv.drawText(titleTrimmed, w / 2f, artRect.bottom + 42f, paint)

            paint.color = Color.argb(190, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            paint.textSize = 21f
            paint.isFakeBoldText = false
            val artistTrimmed = if (artist.length > 26) artist.take(24) + "…" else artist
            cv.drawText(artistTrimmed, w / 2f, artRect.bottom + 76f, paint)

            val btnY = h - 60f
            val playX = w / 2f
            val prevX = playX - 85f
            val nextX = playX + 85f

            paint.color = textColor
            paint.textSize = 36f
            cv.drawText("⏮", prevX, btnY + 12f, paint)

            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.FILL
            }
            cv.drawCircle(playX, btnY, 34f, circlePaint)
            paint.color = Color.WHITE
            paint.textSize = 30f
            cv.drawText(if (isPlaying) "⏸" else "▶", playX + 2f, btnY + 11f, paint)

            paint.color = textColor
            paint.textSize = 36f
            cv.drawText("⏭", nextX, btnY + 12f, paint)
        }

        return bmp
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
            DateW22::class.java to ("date" to (2 to 2)),

            // iOS Widgets
            IosCalendarW22::class.java to ("ios_calendar" to (2 to 2)),
            IosWeatherW22::class.java to ("ios_weather" to (2 to 2)),
            IosWeatherW21::class.java to ("ios_weather" to (2 to 1)),
            IosBatteryW22::class.java to ("ios_battery" to (2 to 2)),
            IosBatteryW21::class.java to ("ios_battery" to (2 to 1)),
            IosClockW22::class.java to ("ios_clock" to (2 to 2)),
            IosClockW21::class.java to ("ios_clock" to (2 to 1)),
            IosNotesW22::class.java to ("ios_notes" to (2 to 2)),

            // 🎵 Spotify & Medya Çalar (Banner, Kare Kartlar & Kilit/AOD)
            MusicW41::class.java to ("music_41" to (4 to 1)),
            MusicW31::class.java to ("music_41" to (3 to 1)),
            MusicW22::class.java to ("music_22" to (2 to 2)),
            MusicW33::class.java to ("music_22" to (3 to 3)),
            MusicW44::class.java to ("music_22" to (4 to 4)),
            MusicW42::class.java to ("music_22" to (4 to 2)),
            MusicLockW21::class.java to ("music_lock" to (2 to 1))
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
