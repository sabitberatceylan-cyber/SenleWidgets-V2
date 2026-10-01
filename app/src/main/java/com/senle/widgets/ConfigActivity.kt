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

        val type = when {
            className.contains("Clock") -> "clock"
            className.contains("Weather") -> "weather"
            className.contains("Link") -> "link"
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

        fun addSwitch(label: String, key: String, defVal: Boolean): SwitchMaterial {
            val sw = SwitchMaterial(this).apply {
                text = label
                setTextColor(Color.parseColor("#F8FAFC"))
                isChecked = P.b(this@ConfigActivity, appWidgetId, key, defVal)
                setPadding(0, 12, 0, 12)
            }
            container.addView(sw)
            saves.add { P.put(this@ConfigActivity, appWidgetId, key, sw.isChecked) }
            return sw
        }

        when (type) {
            "clock" -> {
                titleView.text = "🕒 Saat Widget Ayarları"
                addSwitch("Tarihi Göster", "clock_showDate", true)

                val labelPos = TextView(this).apply {
                    text = "Tarih Konumu"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, 12, 0, 4)
                }
                container.addView(labelPos)

                val rg = RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL }
                val rbBottom = RadioButton(this).apply { text = "Saatin Altında"; setTextColor(Color.WHITE) }
                val rbTop = RadioButton(this).apply { text = "Saatin Üstünde"; setTextColor(Color.WHITE) }
                rg.addView(rbBottom)
                rg.addView(rbTop)
                container.addView(rg)

                val curPos = P.s(this, appWidgetId, "clock_pos", "bottom")
                if (curPos == "top") rbTop.isChecked = true else rbBottom.isChecked = true
                saves.add { P.put(this, appWidgetId, "clock_pos", if (rbTop.isChecked) "top" else "bottom") }

                addSwitch("24 Saat Formatı", "clock_h24", true)
                addSwitch("Şeffaf Arka Plan", "clock_transp", false)
                addEditText("Arka Plan Rengi (#RRGGBB)", "clock_bg", "#1E1E2E")
                addEditText("Yazı Rengi (#RRGGBB)", "clock_text", "#FFFFFF")
            }
            "weather" -> {
                titleView.text = "⛅ Hava Durumu Ayarları"
                addEditText("Şehir Adı", "city", "İSTANBUL")
                addSwitch("Şeffaf Arka Plan", "weather_transp", false)
                addEditText("Arka Plan Rengi (#RRGGBB)", "weather_bg", "#1565C0")
                addEditText("Yazı Rengi (#RRGGBB)", "weather_text", "#FFFFFF")
            }
            "link" -> {
                titleView.text = "🔗 Web Butonu Ayarları"
                addEditText("Açılacak Web Sitesi (URL)", "link_url", "https://www.google.com")
                addEditText("Buton Yazısı", "link_top", "TIKLA")
                addEditText("Alt Açıklama", "link_label", "Web'i Aç")
                addSwitch("Şeffaf Arka Plan", "link_transp", false)
                addEditText("Arka Plan Rengi (#RRGGBB)", "link_bg", "#4F46E5")
                addEditText("Yazı Rengi (#RRGGBB)", "link_text", "#FFFFFF")
            }
            "calendar" -> {
                titleView.text = "📅 Klasik Takvim Ayarları (2x2)"
                addSwitch("Şeffaf Arka Plan", "cal_transp", false)
                addEditText("Kart Arka Plan Rengi (#RRGGBB)", "cal_bg", "#FFFFFF")
                addEditText("Başlık & Bugün Rozet Rengi", "cal_head", "#E53935")
                addEditText("Gün Sayıları Rengi", "cal_text", "#1C1C1E")
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
