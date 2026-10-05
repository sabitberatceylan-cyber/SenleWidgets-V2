package com.senle.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_config)

        val appWidgetInfo = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
        val className = appWidgetInfo?.provider?.className ?: ""

        val isIos = className.contains("Ios")
        val type = when {
            className.contains("Calendar") -> "calendar"
            className.contains("Weather") -> "weather"
            className.contains("Battery") -> "battery"
            className.contains("Clock") -> "clock"
            className.contains("Notes") -> "notes"
            className.contains("Link") -> "link"
            className.contains("Music") -> "music"
            else -> "calendar"
        }

        val titleView = findViewById<TextView>(R.id.configTitle)
        val container = findViewById<LinearLayout>(R.id.fieldsContainer)
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveConfig)

        val saves = ArrayList<() -> Unit>()

        fun addEditText(label: String, key: String, defVal: String): TextInputEditText {
            val til = TextInputLayout(this, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox).apply {
                hint = label
                boxStrokeColor = Color.parseColor("#6366F1")
                setPadding(0, 8, 0, 8)
            }
            val et = TextInputEditText(til.context).apply {
                setText(P.s(this@ConfigActivity, appWidgetId, key, defVal))
                setTextColor(Color.parseColor("#F8FAFC"))
            }
            til.addView(et)
            container.addView(til)
            saves.add { P.put(this@ConfigActivity, appWidgetId, key, et.text.toString().trim()) }
            return et
        }

        fun addOpacitySeeker(key: String, defVal: Int) {
            val tv = TextView(this).apply {
                val cur = P.i(this@ConfigActivity, appWidgetId, key, defVal)
                text = "Arka Plan Opaklığı: %$cur"
                setTextColor(Color.parseColor("#38BDF8"))
                setPadding(0, 12, 0, 4)
            }
            container.addView(tv)
            val sb = SeekBar(this).apply {
                max = 100
                progress = P.i(this@ConfigActivity, appWidgetId, key, defVal)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        tv.text = if (progress == 0) "Arka Plan: %0 (Tamamen Şeffaf)" else "Arka Plan Opaklığı: %$progress"
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            container.addView(sb)
            saves.add { P.put(this@ConfigActivity, appWidgetId, key, sb.progress) }
        }

        titleView.text = if (isIos) "🍎 iOS Widget Yapılandırması" else "⚙️ Widget Yapılandırması"

        when (type) {
            "calendar" -> {
                addEditText("Kart Arka Plan Rengi", if (isIos) "cal_bg" else "cal_bg", "#FFFFFF")
                addEditText("Başlık / Vurgu Rengi", "cal_head", "#E53935")
                addEditText("Gün Sayıları Rengi", "cal_text", "#1C1C1E")
                addOpacitySeeker("cal_opacity", 100)
            }
            "weather" -> {
                addEditText("Şehir Adı", "city", "İSTANBUL")
                addEditText("Arka Plan Rengi", if (isIos) "ios_w_bg" else "weather_bg", if (isIos) "#1E3A8A" else "#1565C0")
                addEditText("Yazı Rengi", if (isIos) "ios_w_text" else "weather_text", "#FFFFFF")
                addOpacitySeeker(if (isIos) "ios_w_opacity" else "weather_opacity", 100)
            }
            "battery" -> {
                addEditText("Arka Plan Rengi", "ios_bat_bg", "#1C1C1E")
                addEditText("Yazı Rengi", "ios_bat_text", "#FFFFFF")
                addEditText("Vurgu Rengi", "ios_bat_accent", "#34C759")
                addOpacitySeeker("ios_bat_opacity", 100)
            }
            "clock" -> {
                addEditText("Arka Plan Rengi", if (isIos) "ios_clk_bg" else "clock_bg", if (isIos) "#18181B" else "#1E1E2E")
                addEditText("Yazı Rengi", if (isIos) "ios_clk_text" else "clock_text", "#FFFFFF")
                addOpacitySeeker(if (isIos) "ios_clk_opacity" else "clock_opacity", 100)
            }
            "notes" -> {
                addEditText("Arka Plan Rengi", "ios_notes_bg", "#1C1C1E")
                addEditText("Yazı Rengi", "ios_notes_text", "#FFFFFF")
                addEditText("1. Hatırlatıcı", "ios_note_1", "✓ Günlük Görevleri Tamamla")
                addEditText("2. Hatırlatıcı", "ios_note_2", "✓ Toplantı ve Planlar")
                addOpacitySeeker("ios_notes_opacity", 100)
            }
            "link" -> {
                addEditText("Web Adresi (URL)", "link_url", "https://www.google.com")
                addEditText("Buton Yazısı", "link_top", "TIKLA")
                addEditText("Arka Plan Rengi", "link_bg", "#4F46E5")
                addEditText("Yazı Rengi", "link_text", "#FFFFFF")
                addOpacitySeeker("link_opacity", 100)
            }
            "music" -> {
                addEditText("Kart Arka Plan Rengi", "music_bg", "#121212")
                addEditText("Yazı / Buton Rengi", "music_text", "#FFFFFF")
                addEditText("Vurgu Rengi (Spotify Yeşili)", "music_accent", "#1DB954")
                addOpacitySeeker("music_opacity", 90)
            }
        }

        btnSave.setOnClickListener {
            saves.forEach { it() }

            if (type == "weather") {
                Thread { U.fetchWeather(this) }.start()
            }

            try {
                val cls = Class.forName(className)
                sendBroadcast(Intent(this, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}
