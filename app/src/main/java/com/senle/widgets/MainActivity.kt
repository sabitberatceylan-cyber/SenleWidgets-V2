package com.senle.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private var currentTab = "clock" // "clock", "weather", "link", "calendar"

    // Preview views
    private lateinit var previewContainerCard: MaterialCardView
    private lateinit var previewClock: LinearLayout
    private lateinit var previewClockDateTop: TextView
    private lateinit var previewClockTime: TextView
    private lateinit var previewClockDateBottom: TextView

    private lateinit var previewWeather: LinearLayout
    private lateinit var previewWeatherCity: TextView
    private lateinit var previewWeatherTemp: TextView
    private lateinit var previewWeatherDesc: TextView

    private lateinit var previewLink: LinearLayout
    private lateinit var previewLinkTop: TextView
    private lateinit var previewLinkLabel: TextView

    private lateinit var previewCalendar: ImageView

    // Tabs
    private lateinit var btnTabClock: MaterialButton
    private lateinit var btnTabWeather: MaterialButton
    private lateinit var btnTabLink: MaterialButton
    private lateinit var btnTabCalendar: MaterialButton

    // Color inputs
    private lateinit var etBgColor: TextInputEditText
    private lateinit var etTextColor: TextInputEditText
    private lateinit var switchTransparent: SwitchMaterial

    // Settings Panels
    private lateinit var panelClockSettings: LinearLayout
    private lateinit var panelWeatherSettings: LinearLayout
    private lateinit var panelLinkSettings: LinearLayout
    private lateinit var panelCalendarSettings: LinearLayout

    // Clock inputs
    private lateinit var switchShowDate: SwitchMaterial
    private lateinit var rgDatePos: RadioGroup
    private lateinit var rbDateTop: RadioButton
    private lateinit var rbDateBottom: RadioButton
    private lateinit var switch24Hour: SwitchMaterial

    // Weather inputs
    private lateinit var etCityName: TextInputEditText
    private lateinit var btnRefreshWeather: MaterialButton

    // Link inputs
    private lateinit var etLinkUrl: TextInputEditText
    private lateinit var etLinkTop: TextInputEditText
    private lateinit var etLinkLabel: TextInputEditText

    // Calendar inputs
    private lateinit var etCalAccent: TextInputEditText

    // Actions
    private lateinit var btnSaveAndApply: MaterialButton
    private lateinit var btnPinWidget: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTabs()
        setupColorPresets()
        setupListeners()
        loadPreferencesForTab(currentTab)
        updateLivePreview()
    }

    private fun initViews() {
        previewContainerCard = findViewById(R.id.previewContainerCard)
        previewClock = findViewById(R.id.previewClock)
        previewClockDateTop = findViewById(R.id.previewClockDateTop)
        previewClockTime = findViewById(R.id.previewClockTime)
        previewClockDateBottom = findViewById(R.id.previewClockDateBottom)

        previewWeather = findViewById(R.id.previewWeather)
        previewWeatherCity = findViewById(R.id.previewWeatherCity)
        previewWeatherTemp = findViewById(R.id.previewWeatherTemp)
        previewWeatherDesc = findViewById(R.id.previewWeatherDesc)

        previewLink = findViewById(R.id.previewLink)
        previewLinkTop = findViewById(R.id.previewLinkTop)
        previewLinkLabel = findViewById(R.id.previewLinkLabel)

        previewCalendar = findViewById(R.id.previewCalendar)

        btnTabClock = findViewById(R.id.btnTabClock)
        btnTabWeather = findViewById(R.id.btnTabWeather)
        btnTabLink = findViewById(R.id.btnTabLink)
        btnTabCalendar = findViewById(R.id.btnTabCalendar)

        etBgColor = findViewById(R.id.etBgColor)
        etTextColor = findViewById(R.id.etTextColor)
        switchTransparent = findViewById(R.id.switchTransparent)

        panelClockSettings = findViewById(R.id.panelClockSettings)
        panelWeatherSettings = findViewById(R.id.panelWeatherSettings)
        panelLinkSettings = findViewById(R.id.panelLinkSettings)
        panelCalendarSettings = findViewById(R.id.panelCalendarSettings)

        switchShowDate = findViewById(R.id.switchShowDate)
        rgDatePos = findViewById(R.id.rgDatePos)
        rbDateTop = findViewById(R.id.rbDateTop)
        rbDateBottom = findViewById(R.id.rbDateBottom)
        switch24Hour = findViewById(R.id.switch24Hour)

        etCityName = findViewById(R.id.etCityName)
        btnRefreshWeather = findViewById(R.id.btnRefreshWeather)

        etLinkUrl = findViewById(R.id.etLinkUrl)
        etLinkTop = findViewById(R.id.etLinkTop)
        etLinkLabel = findViewById(R.id.etLinkLabel)

        etCalAccent = findViewById(R.id.etCalAccent)

        btnSaveAndApply = findViewById(R.id.btnSaveAndApply)
        btnPinWidget = findViewById(R.id.btnPinWidget)
    }

    private fun setupTabs() {
        val tabs = listOf(
            btnTabClock to "clock",
            btnTabWeather to "weather",
            btnTabLink to "link",
            btnTabCalendar to "calendar"
        )

        for ((btn, tab) in tabs) {
            btn.setOnClickListener {
                if (currentTab != tab) {
                    currentTab = tab
                    updateTabStyles()
                    loadPreferencesForTab(tab)
                    updateLivePreview()
                }
            }
        }
    }

    private fun updateTabStyles() {
        val tabMap = mapOf(
            btnTabClock to "clock",
            btnTabWeather to "weather",
            btnTabLink to "link",
            btnTabCalendar to "calendar"
        )
        for ((btn, tab) in tabMap) {
            if (tab == currentTab) {
                btn.setBackgroundColor(Color.parseColor("#6366F1"))
                btn.setTextColor(Color.WHITE)
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT)
                btn.setTextColor(Color.parseColor("#F8FAFC"))
            }
        }

        panelClockSettings.visibility = if (currentTab == "clock") View.VISIBLE else View.GONE
        panelWeatherSettings.visibility = if (currentTab == "weather") View.VISIBLE else View.GONE
        panelLinkSettings.visibility = if (currentTab == "link") View.VISIBLE else View.GONE
        panelCalendarSettings.visibility = if (currentTab == "calendar") View.VISIBLE else View.GONE

        previewClock.visibility = if (currentTab == "clock") View.VISIBLE else View.GONE
        previewWeather.visibility = if (currentTab == "weather") View.VISIBLE else View.GONE
        previewLink.visibility = if (currentTab == "link") View.VISIBLE else View.GONE
        previewCalendar.visibility = if (currentTab == "calendar") View.VISIBLE else View.GONE
    }

    private fun setupColorPresets() {
        val presets = mapOf(
            R.id.colorRed to "#EF4444",
            R.id.colorBlue to "#1565C0",
            R.id.colorGreen to "#10B981",
            R.id.colorPurple to "#7C3AED",
            R.id.colorAmber to "#F59E0B",
            R.id.colorDark to "#1E1E2E",
            R.id.colorWhite to "#FFFFFF"
        )
        for ((btnId, hex) in presets) {
            findViewById<MaterialButton>(btnId).setOnClickListener {
                etBgColor.setText(hex)
                if (hex == "#FFFFFF") {
                    etTextColor.setText("#1C1C1E")
                } else if (hex == "#1E1E2E" || hex == "#1565C0" || hex == "#7C3AED") {
                    etTextColor.setText("#FFFFFF")
                }
                switchTransparent.isChecked = false
                updateLivePreview()
            }
        }

        findViewById<MaterialButton>(R.id.btnCityIst).setOnClickListener { setCity("İSTANBUL", "41.0082", "28.9784") }
        findViewById<MaterialButton>(R.id.btnCityAnk).setOnClickListener { setCity("ANKARA", "39.9334", "32.8597") }
        findViewById<MaterialButton>(R.id.btnCityIzm).setOnClickListener { setCity("İZMİR", "38.4237", "27.1428") }
        findViewById<MaterialButton>(R.id.btnCityAnt).setOnClickListener { setCity("ANTALYA", "36.8969", "30.7133") }
    }

    private fun setCity(name: String, lat: String, lon: String) {
        etCityName.setText(name)
        P.put(this, 0, "city", name)
        P.put(this, 0, "lat", lat)
        P.put(this, 0, "lon", lon)
        refreshWeatherAsync()
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                updateLivePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etBgColor.addTextChangedListener(watcher)
        etTextColor.addTextChangedListener(watcher)
        etLinkTop.addTextChangedListener(watcher)
        etLinkLabel.addTextChangedListener(watcher)
        etCityName.addTextChangedListener(watcher)
        etCalAccent.addTextChangedListener(watcher)

        switchTransparent.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        switchShowDate.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        rgDatePos.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        switch24Hour.setOnCheckedChangeListener { _, _ -> updateLivePreview() }

        btnRefreshWeather.setOnClickListener {
            refreshWeatherAsync()
        }

        btnSaveAndApply.setOnClickListener {
            saveCurrentTabSettings()
            U.updateAll(this)
            Toast.makeText(this, "Tüm widget ayarları kaydedildi ve güncellendi!", Toast.LENGTH_SHORT).show()
        }

        btnPinWidget.setOnClickListener {
            showPinWidgetDialog()
        }
    }

    private fun loadPreferencesForTab(tab: String) {
        when (tab) {
            "clock" -> {
                etBgColor.setText(P.s(this, 0, "clock_bg", "#1E1E2E"))
                etTextColor.setText(P.s(this, 0, "clock_text", "#FFFFFF"))
                switchTransparent.isChecked = P.b(this, 0, "clock_transp", false)
                switchShowDate.isChecked = P.b(this, 0, "clock_showDate", true)
                val pos = P.s(this, 0, "clock_pos", "bottom")
                if (pos == "top") rbDateTop.isChecked = true else rbDateBottom.isChecked = true
                switch24Hour.isChecked = P.b(this, 0, "clock_h24", true)
            }
            "weather" -> {
                etBgColor.setText(P.s(this, 0, "weather_bg", "#1565C0"))
                etTextColor.setText(P.s(this, 0, "weather_text", "#FFFFFF"))
                switchTransparent.isChecked = P.b(this, 0, "weather_transp", false)
                etCityName.setText(P.s(this, 0, "city", "İSTANBUL"))
            }
            "link" -> {
                etBgColor.setText(P.s(this, 0, "link_bg", "#4F46E5"))
                etTextColor.setText(P.s(this, 0, "link_text", "#FFFFFF"))
                switchTransparent.isChecked = P.b(this, 0, "link_transp", false)
                etLinkUrl.setText(P.s(this, 0, "link_url", "https://www.google.com"))
                etLinkTop.setText(P.s(this, 0, "link_top", "TIKLA"))
                etLinkLabel.setText(P.s(this, 0, "link_label", "Web'i Aç"))
            }
            "calendar" -> {
                etBgColor.setText(P.s(this, 0, "cal_bg", "#FFFFFF"))
                etTextColor.setText(P.s(this, 0, "cal_text", "#1C1C1E"))
                switchTransparent.isChecked = P.b(this, 0, "cal_transp", false)
                etCalAccent.setText(P.s(this, 0, "cal_head", "#E53935"))
            }
        }
    }

    private fun saveCurrentTabSettings() {
        val bg = etBgColor.text.toString().trim()
        val text = etTextColor.text.toString().trim()
        val isTransp = switchTransparent.isChecked

        when (currentTab) {
            "clock" -> {
                P.put(this, 0, "clock_bg", bg)
                P.put(this, 0, "clock_text", text)
                P.put(this, 0, "clock_transp", isTransp)
                P.put(this, 0, "clock_showDate", switchShowDate.isChecked)
                P.put(this, 0, "clock_pos", if (rbDateTop.isChecked) "top" else "bottom")
                P.put(this, 0, "clock_h24", switch24Hour.isChecked)
            }
            "weather" -> {
                P.put(this, 0, "weather_bg", bg)
                P.put(this, 0, "weather_text", text)
                P.put(this, 0, "weather_transp", isTransp)
                P.put(this, 0, "city", etCityName.text.toString().trim())
            }
            "link" -> {
                P.put(this, 0, "link_bg", bg)
                P.put(this, 0, "link_text", text)
                P.put(this, 0, "link_transp", isTransp)
                P.put(this, 0, "link_url", etLinkUrl.text.toString().trim())
                P.put(this, 0, "link_top", etLinkTop.text.toString().trim())
                P.put(this, 0, "link_label", etLinkLabel.text.toString().trim())
            }
            "calendar" -> {
                val accent = etCalAccent.text.toString().trim()
                P.put(this, 0, "cal_bg", bg)
                P.put(this, 0, "cal_text", text)
                P.put(this, 0, "cal_transp", isTransp)
                P.put(this, 0, "cal_head", accent)
                P.put(this, 0, "cal_circle", accent)
            }
        }
    }

    private fun updateLivePreview() {
        val isTransp = switchTransparent.isChecked
        val bgColor = col(etBgColor.text.toString(), Color.parseColor("#1E293B"))
        val textColor = col(etTextColor.text.toString(), Color.WHITE)

        if (isTransp) {
            previewContainerCard.setCardBackgroundColor(Color.parseColor("#20FFFFFF"))
            previewContainerCard.strokeColor = Color.parseColor("#40FFFFFF")
        } else {
            previewContainerCard.setCardBackgroundColor(bgColor)
            previewContainerCard.strokeColor = Color.parseColor("#334155")
        }

        when (currentTab) {
            "clock" -> {
                val showDate = switchShowDate.isChecked
                val isTop = rbDateTop.isChecked
                previewClockDateTop.visibility = if (showDate && isTop) View.VISIBLE else View.GONE
                previewClockDateBottom.visibility = if (showDate && !isTop) View.VISIBLE else View.GONE
                previewClockTime.text = if (switch24Hour.isChecked) "21:45" else "9:45 PM"
                previewClockTime.setTextColor(textColor)
                previewClockDateTop.setTextColor(textColor)
                previewClockDateBottom.setTextColor(textColor)
            }
            "weather" -> {
                previewWeatherCity.text = etCityName.text.toString().ifEmpty { "İSTANBUL" }.uppercase()
                previewWeatherCity.setTextColor(textColor)
                previewWeatherTemp.setTextColor(textColor)
                previewWeatherDesc.setTextColor(textColor)
            }
            "link" -> {
                previewLinkTop.text = etLinkTop.text.toString().ifEmpty { "TIKLA" }
                previewLinkLabel.text = etLinkLabel.text.toString().ifEmpty { "Web'i Aç" }
                previewLinkTop.setTextColor(textColor)
                previewLinkLabel.setTextColor(textColor)
            }
            "calendar" -> {
                saveCurrentTabSettings()
                val bmp = U.generateCalendarBitmap(this, 0)
                previewCalendar.setImageBitmap(bmp)
            }
        }
    }

    private fun refreshWeatherAsync() {
        Thread {
            U.fetchWeather(this)
            runOnUiThread {
                previewWeatherTemp.text = P.s(this, 0, "w_temp", "⛅ 21°C")
                previewWeatherDesc.text = P.s(this, 0, "w_desc", "Açık")
                previewWeatherCity.text = P.s(this, 0, "city", "İSTANBUL").uppercase()
                Toast.makeText(this, "Hava durumu başarıyla güncellendi!", Toast.LENGTH_SHORT).show()
                U.updateAll(this)
            }
        }.start()
    }

    private fun showPinWidgetDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Ana ekrana uzun basıp Senle Widgets seçiniz.", Toast.LENGTH_LONG).show()
            return
        }

        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        if (!appWidgetManager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, "Başlatıcınız doğrudan widget eklemeyi desteklemiyor.", Toast.LENGTH_LONG).show()
            return
        }

        saveCurrentTabSettings()

        val sizes = when (currentTab) {
            "calendar" -> arrayOf("2x2 (Kare)")
            else -> arrayOf("2x1 (Yatay)", "2x2 (Kare)", "3x1 (Yatay)", "3x2 (Yatay)")
        }

        AlertDialog.Builder(this)
            .setTitle("Boyut Seç ve Ana Ekrana Ekle")
            .setItems(sizes) { _, which ->
                val cls = when (currentTab) {
                    "clock" -> when (which) {
                        0 -> ClockW21::class.java
                        1 -> ClockW22::class.java
                        2 -> ClockW31::class.java
                        else -> ClockW32::class.java
                    }
                    "weather" -> when (which) {
                        0 -> WeatherW21::class.java
                        1 -> WeatherW22::class.java
                        2 -> WeatherW31::class.java
                        else -> WeatherW32::class.java
                    }
                    "link" -> when (which) {
                        0 -> LinkW21::class.java
                        1 -> LinkW22::class.java
                        2 -> LinkW31::class.java
                        else -> LinkW32::class.java
                    }
                    else -> DateW22::class.java
                }

                val provider = ComponentName(this, cls)
                appWidgetManager.requestPinAppWidget(provider, null, null)
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}
