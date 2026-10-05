package com.senle.widgets

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private var activeCategory = "ios" // "ios" or "standard"
    private var activeWidget = "ios_calendar" // "ios_calendar", "ios_weather", "ios_battery", "ios_clock", "ios_notes", "music_41", "music_22", "clock", "weather", "link", "date"

    // Views
    private lateinit var btnCatIos: MaterialButton
    private lateinit var btnCatStandard: MaterialButton
    private lateinit var scrollIosTabs: View
    private lateinit var scrollStdTabs: View

    // iOS tab buttons
    private lateinit var btnIosTabCal: MaterialButton
    private lateinit var btnIosTabWeather: MaterialButton
    private lateinit var btnIosTabBattery: MaterialButton
    private lateinit var btnIosTabClock: MaterialButton
    private lateinit var btnIosTabNotes: MaterialButton
    private lateinit var btnIosTabMusic: MaterialButton
    private lateinit var btnIosTabMusic22: MaterialButton

    // Permission views
    private lateinit var cardMusicPermission: View
    private lateinit var btnGrantMusicPermission: MaterialButton

    // Standard tab buttons
    private lateinit var btnStdTabClock: MaterialButton
    private lateinit var btnStdTabWeather: MaterialButton
    private lateinit var btnStdTabLink: MaterialButton
    private lateinit var btnStdTabCal: MaterialButton

    // Live preview
    private lateinit var ivLivePreview: ImageView

    // Color pickers & Opacity
    private lateinit var btnPickBgColor: MaterialButton
    private lateinit var btnPickTextColor: MaterialButton
    private lateinit var btnPickAccentColor: MaterialButton
    private lateinit var tvOpacityValue: TextView
    private lateinit var sbOpacity: SeekBar

    // Extra panels
    private lateinit var panelCitySettings: LinearLayout
    private lateinit var panelClockExtra: LinearLayout
    private lateinit var panelLinkExtra: LinearLayout
    private lateinit var switchShowDate: SwitchMaterial
    private lateinit var switch24Hour: SwitchMaterial
    private lateinit var etLinkUrl: TextInputEditText
    private lateinit var etLinkTop: TextInputEditText

    private lateinit var panelMusicExtra: LinearLayout
    private lateinit var switchMusicHideIdle: SwitchMaterial
    private lateinit var btnMusicSizeNormal: MaterialButton
    private lateinit var btnMusicSizeLarge: MaterialButton
    private lateinit var btnMusicSizeHuge: MaterialButton

    // Main action buttons
    private lateinit var btnSaveAndApply: MaterialButton
    private lateinit var btnPinWidget: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupCategoryTabs()
        setupWidgetTabs()
        setupColorAndOpacityControls()
        setupExtraOptions()
        setupActionButtons()

        refreshCurrentTabUi()
        updateLivePreview()
    }

    override fun onResume() {
        super.onResume()
        checkMusicPermission()
        updateLivePreview()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun checkMusicPermission() {
        val hasPermission = isNotificationServiceEnabled()
        cardMusicPermission.visibility = if (!hasPermission) View.VISIBLE else View.GONE
    }

    private fun initViews() {
        btnCatIos = findViewById(R.id.btnCatIos)
        btnCatStandard = findViewById(R.id.btnCatStandard)
        scrollIosTabs = findViewById(R.id.scrollIosTabs)
        scrollStdTabs = findViewById(R.id.scrollStdTabs)

        btnIosTabCal = findViewById(R.id.btnIosTabCal)
        btnIosTabWeather = findViewById(R.id.btnIosTabWeather)
        btnIosTabBattery = findViewById(R.id.btnIosTabBattery)
        btnIosTabClock = findViewById(R.id.btnIosTabClock)
        btnIosTabNotes = findViewById(R.id.btnIosTabNotes)
        btnIosTabMusic = findViewById(R.id.btnIosTabMusic)
        btnIosTabMusic22 = findViewById(R.id.btnIosTabMusic22)

        cardMusicPermission = findViewById(R.id.cardMusicPermission)
        btnGrantMusicPermission = findViewById(R.id.btnGrantMusicPermission)

        btnGrantMusicPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.btnOpenLockSettings).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        findViewById<MaterialButton>(R.id.btnLockstarGuide).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Samsung Good Lock & LockStar Rehberi")
                .setMessage(
                    "Samsung One UI'da Senle Widgets'ı Kilit Ekranına ve Always On Display'e (AOD) eklemenin resmi yöntemi:\n\n" +
                    "1. Galaxy Store'u açın ve 'Good Lock' (veya Play Store'dan 'Fine Lock') uygulamasını yükleyin.\n" +
                    "2. Good Lock içinde 'LockStar' modülünü kurun ve açın.\n" +
                    "3. Kilit Ekranı veya Always On Display düzenleme modunu açıp ekrana dokunun.\n" +
                    "4. '+' (Widget Ekle) butonuna basıp 'Senle Widgets' -> 'Spotify' veya 'Takvim' seçin!\n" +
                    "5. İstediğiniz yere taşıyıp 'Kaydet' deyin. Artık kilit ekranınızda ve AOD'de görünecektir!"
                )
                .setPositiveButton("Tamam", null)
                .show()
        }

        val switchAodAutoLaunch = findViewById<SwitchMaterial>(R.id.switchAodAutoLaunch)
        val btnLaunchAodMusic = findViewById<MaterialButton>(R.id.btnLaunchAodMusic)

        switchAodAutoLaunch.isChecked = P.b(this, 0, "aod_auto_launch", false)
        switchAodAutoLaunch.setOnCheckedChangeListener { _, isChecked ->
            P.put(this, 0, "aod_auto_launch", isChecked)
            Toast.makeText(
                this,
                if (isChecked) "Otomatik Tam Ekran AOD Müzik Çalar Etkin" else "Otomatik AOD Devre Dışı",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnLaunchAodMusic.setOnClickListener {
            val intent = Intent(this, AodMusicActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        btnStdTabClock = findViewById(R.id.btnStdTabClock)
        btnStdTabWeather = findViewById(R.id.btnStdTabWeather)
        btnStdTabLink = findViewById(R.id.btnStdTabLink)
        btnStdTabCal = findViewById(R.id.btnStdTabCal)

        ivLivePreview = findViewById(R.id.ivLivePreview)

        btnPickBgColor = findViewById(R.id.btnPickBgColor)
        btnPickTextColor = findViewById(R.id.btnPickTextColor)
        btnPickAccentColor = findViewById(R.id.btnPickAccentColor)
        tvOpacityValue = findViewById(R.id.tvOpacityValue)
        sbOpacity = findViewById(R.id.sbOpacity)

        panelCitySettings = findViewById(R.id.panelCitySettings)
        panelClockExtra = findViewById(R.id.panelClockExtra)
        panelLinkExtra = findViewById(R.id.panelLinkExtra)
        switchShowDate = findViewById(R.id.switchShowDate)
        switch24Hour = findViewById(R.id.switch24Hour)
        etLinkUrl = findViewById(R.id.etLinkUrl)
        etLinkTop = findViewById(R.id.etLinkTop)

        panelMusicExtra = findViewById(R.id.panelMusicExtra)
        switchMusicHideIdle = findViewById(R.id.switchMusicHideIdle)
        btnMusicSizeNormal = findViewById(R.id.btnMusicSizeNormal)
        btnMusicSizeLarge = findViewById(R.id.btnMusicSizeLarge)
        btnMusicSizeHuge = findViewById(R.id.btnMusicSizeHuge)

        switchMusicHideIdle.isChecked = P.b(this, 0, "music_hide_idle", false)
        switchMusicHideIdle.setOnCheckedChangeListener { _, isChecked ->
            P.put(this, 0, "music_hide_idle", isChecked)
            U.updateAll(this)
            Toast.makeText(
                this,
                if (isChecked) "Müzik durunca widget kilit ekranında gizlenecek" else "Widget kilit ekranında her zaman görünecek",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnMusicSizeNormal.setOnClickListener {
            P.put(this, 0, "music_padding", 12)
            P.put(this, 0, "music_scale", 100)
            U.updateAll(this)
            updatePreview()
            Toast.makeText(this, "Boyut: Standart (2x2)", Toast.LENGTH_SHORT).show()
        }

        btnMusicSizeLarge.setOnClickListener {
            P.put(this, 0, "music_padding", 6)
            P.put(this, 0, "music_scale", 125)
            U.updateAll(this)
            updatePreview()
            Toast.makeText(this, "Boyut: Büyük (+%25)", Toast.LENGTH_SHORT).show()
        }

        btnMusicSizeHuge.setOnClickListener {
            P.put(this, 0, "music_padding", 2)
            P.put(this, 0, "music_scale", 150)
            U.updateAll(this)
            updatePreview()
            Toast.makeText(this, "Boyut: Devasa (+Kenarsız)", Toast.LENGTH_SHORT).show()
        }

        btnSaveAndApply = findViewById(R.id.btnSaveAndApply)
        btnPinWidget = findViewById(R.id.btnPinWidget)
    }

    private fun setupCategoryTabs() {
        btnCatIos.setOnClickListener {
            activeCategory = "ios"
            activeWidget = "ios_calendar"
            refreshCurrentTabUi()
            updateLivePreview()
        }
        btnCatStandard.setOnClickListener {
            activeCategory = "standard"
            activeWidget = "clock"
            refreshCurrentTabUi()
            updateLivePreview()
        }
    }

    private fun setupWidgetTabs() {
        val iosTabs = listOf(
            btnIosTabCal to "ios_calendar",
            btnIosTabWeather to "ios_weather",
            btnIosTabBattery to "ios_battery",
            btnIosTabClock to "ios_clock",
            btnIosTabNotes to "ios_notes",
            btnIosTabMusic to "music_41",
            btnIosTabMusic22 to "music_22"
        )
        for ((btn, type) in iosTabs) {
            btn.setOnClickListener {
                activeWidget = type
                refreshCurrentTabUi()
                updateLivePreview()
            }
        }

        val stdTabs = listOf(
            btnStdTabClock to "clock",
            btnStdTabWeather to "weather",
            btnStdTabLink to "link",
            btnStdTabCal to "date"
        )
        for ((btn, type) in stdTabs) {
            btn.setOnClickListener {
                activeWidget = type
                refreshCurrentTabUi()
                updateLivePreview()
            }
        }
    }

    private fun refreshCurrentTabUi() {
        // Kategori stilleri
        if (activeCategory == "ios") {
            btnCatIos.setBackgroundColor(Color.parseColor("#6366F1"))
            btnCatIos.setTextColor(Color.WHITE)
            btnCatStandard.setBackgroundColor(Color.TRANSPARENT)
            btnCatStandard.setTextColor(Color.parseColor("#F8FAFC"))
            scrollIosTabs.visibility = View.VISIBLE
            scrollStdTabs.visibility = View.GONE
        } else {
            btnCatStandard.setBackgroundColor(Color.parseColor("#6366F1"))
            btnCatStandard.setTextColor(Color.WHITE)
            btnCatIos.setBackgroundColor(Color.TRANSPARENT)
            btnCatIos.setTextColor(Color.parseColor("#F8FAFC"))
            scrollStdTabs.visibility = View.VISIBLE
            scrollIosTabs.visibility = View.GONE
        }

        // Widget sekmeleri stilleri
        val allTabs = listOf(
            btnIosTabCal to "ios_calendar",
            btnIosTabWeather to "ios_weather",
            btnIosTabBattery to "ios_battery",
            btnIosTabClock to "ios_clock",
            btnIosTabNotes to "ios_notes",
            btnIosTabMusic to "music_41",
            btnIosTabMusic22 to "music_22",
            btnStdTabClock to "clock",
            btnStdTabWeather to "weather",
            btnStdTabLink to "link",
            btnStdTabCal to "date"
        )
        for ((btn, type) in allTabs) {
            if (type == activeWidget) {
                btn.setBackgroundColor(Color.parseColor("#38BDF8"))
                btn.setTextColor(Color.parseColor("#0F172A"))
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT)
                btn.setTextColor(Color.parseColor("#F8FAFC"))
            }
        }

        // Ekstra panel görünürlükleri
        panelCitySettings.visibility = if (activeWidget.contains("weather")) View.VISIBLE else View.GONE
        panelClockExtra.visibility = if (activeWidget == "clock") View.VISIBLE else View.GONE
        panelLinkExtra.visibility = if (activeWidget == "link") View.VISIBLE else View.GONE
        panelMusicExtra.visibility = if (activeWidget.startsWith("music")) View.VISIBLE else View.GONE

        // Aktif widget için opacity yükle
        val currentOpacity = getOpacityForWidget(activeWidget)
        sbOpacity.progress = currentOpacity
        updateOpacityText(currentOpacity)
    }

    private fun getOpacityForWidget(type: String): Int {
        val key = when (type) {
            "ios_calendar" -> "cal_opacity"
            "ios_weather" -> "ios_w_opacity"
            "ios_battery" -> "ios_bat_opacity"
            "ios_clock" -> "ios_clk_opacity"
            "ios_notes" -> "ios_notes_opacity"
            "music_41", "music_22" -> "music_opacity"
            "clock" -> "clock_opacity"
            "weather" -> "weather_opacity"
            "link" -> "link_opacity"
            else -> "cal_opacity"
        }
        return P.i(this, 0, key, if (type.startsWith("music")) 90 else 100)
    }

    private fun setOpacityForWidget(type: String, value: Int) {
        val key = when (type) {
            "ios_calendar" -> "cal_opacity"
            "ios_weather" -> "ios_w_opacity"
            "ios_battery" -> "ios_bat_opacity"
            "ios_clock" -> "ios_clk_opacity"
            "ios_notes" -> "ios_notes_opacity"
            "music_41", "music_22" -> "music_opacity"
            "clock" -> "clock_opacity"
            "weather" -> "weather_opacity"
            "link" -> "link_opacity"
            else -> "cal_opacity"
        }
        P.put(this, 0, key, value)
    }

    private fun getBgColorForWidget(type: String): String {
        return when (type) {
            "ios_calendar" -> P.s(this, 0, "cal_bg", "#FFFFFF")
            "ios_weather" -> P.s(this, 0, "ios_w_bg", "#1E3A8A")
            "ios_battery" -> P.s(this, 0, "ios_bat_bg", "#1C1C1E")
            "ios_clock" -> P.s(this, 0, "ios_clk_bg", "#18181B")
            "ios_notes" -> P.s(this, 0, "ios_notes_bg", "#1C1C1E")
            "music_41", "music_22" -> P.s(this, 0, "music_bg", "#121212")
            "clock" -> P.s(this, 0, "clock_bg", "#1E1E2E")
            "weather" -> P.s(this, 0, "weather_bg", "#1565C0")
            "link" -> P.s(this, 0, "link_bg", "#4F46E5")
            else -> P.s(this, 0, "cal_bg", "#FFFFFF")
        }
    }

    private fun setBgColorForWidget(type: String, hex: String) {
        val key = when (type) {
            "ios_calendar" -> "cal_bg"
            "ios_weather" -> "ios_w_bg"
            "ios_battery" -> "ios_bat_bg"
            "ios_clock" -> "ios_clk_bg"
            "ios_notes" -> "ios_notes_bg"
            "music_41", "music_22" -> "music_bg"
            "clock" -> "clock_bg"
            "weather" -> "weather_bg"
            "link" -> "link_bg"
            else -> "cal_bg"
        }
        P.put(this, 0, key, hex)
    }

    private fun getTextColorForWidget(type: String): String {
        return when (type) {
            "ios_calendar" -> P.s(this, 0, "cal_text", "#1C1C1E")
            "ios_weather" -> P.s(this, 0, "ios_w_text", "#FFFFFF")
            "ios_battery" -> P.s(this, 0, "ios_bat_text", "#FFFFFF")
            "ios_clock" -> P.s(this, 0, "ios_clk_text", "#FFFFFF")
            "ios_notes" -> P.s(this, 0, "ios_notes_text", "#FFFFFF")
            "music_41", "music_22" -> P.s(this, 0, "music_text", "#FFFFFF")
            "clock" -> P.s(this, 0, "clock_text", "#FFFFFF")
            "weather" -> P.s(this, 0, "weather_text", "#FFFFFF")
            "link" -> P.s(this, 0, "link_text", "#FFFFFF")
            else -> P.s(this, 0, "cal_text", "#1C1C1E")
        }
    }

    private fun setTextColorForWidget(type: String, hex: String) {
        val key = when (type) {
            "ios_calendar" -> "cal_text"
            "ios_weather" -> "ios_w_text"
            "ios_battery" -> "ios_bat_text"
            "ios_clock" -> "ios_clk_text"
            "ios_notes" -> "ios_notes_text"
            "music_41", "music_22" -> "music_text"
            "clock" -> "clock_text"
            "weather" -> "weather_text"
            "link" -> "link_text"
            else -> "cal_text"
        }
        P.put(this, 0, key, hex)
    }

    private fun getAccentColorForWidget(type: String): String {
        return when (type) {
            "ios_calendar" -> P.s(this, 0, "cal_head", "#E53935")
            "ios_weather" -> "#38BDF8"
            "ios_battery" -> P.s(this, 0, "ios_bat_accent", "#34C759")
            "ios_clock" -> P.s(this, 0, "ios_clk_accent", "#FF9500")
            "ios_notes" -> P.s(this, 0, "ios_notes_accent", "#0A84FF")
            "music_41", "music_22" -> P.s(this, 0, "music_accent", "#1DB954")
            else -> "#EF4444"
        }
    }

    private fun setAccentColorForWidget(type: String, hex: String) {
        val key = when (type) {
            "ios_calendar" -> "cal_head"
            "ios_battery" -> "ios_bat_accent"
            "ios_clock" -> "ios_clk_accent"
            "ios_notes" -> "ios_notes_accent"
            "music_41", "music_22" -> "music_accent"
            else -> "cal_head"
        }
        P.put(this, 0, key, hex)
        if (type == "ios_calendar") {
            P.put(this, 0, "cal_circle", hex)
        }
    }

    private fun setupColorAndOpacityControls() {
        btnPickBgColor.setOnClickListener {
            val currentHex = getBgColorForWidget(activeWidget)
            showColorPickerDialog("Arka Plan Rengi Seç", currentHex) { newHex ->
                setBgColorForWidget(activeWidget, newHex)
                updateLivePreview()
            }
        }

        btnPickTextColor.setOnClickListener {
            val currentHex = getTextColorForWidget(activeWidget)
            showColorPickerDialog("Yazı / Metin Rengi Seç", currentHex) { newHex ->
                setTextColorForWidget(activeWidget, newHex)
                updateLivePreview()
            }
        }

        btnPickAccentColor.setOnClickListener {
            val currentHex = getAccentColorForWidget(activeWidget)
            showColorPickerDialog("Vurgu / Rozet / İkon Rengi Seç", currentHex) { newHex ->
                setAccentColorForWidget(activeWidget, newHex)
                updateLivePreview()
            }
        }

        // Hızlı tema renkleri
        val quickColors = mapOf(
            R.id.quickRed to "#FF3B30",
            R.id.quickBlue to "#007AFF",
            R.id.quickGreen to "#34C759",
            R.id.quickPurple to "#AF52DE",
            R.id.quickAmber to "#FF9500",
            R.id.quickDark to "#1C1C1E",
            R.id.quickWhite to "#FFFFFF"
        )
        for ((btnId, hex) in quickColors) {
            findViewById<MaterialButton>(btnId).setOnClickListener {
                setBgColorForWidget(activeWidget, hex)
                if (hex == "#FFFFFF") {
                    setTextColorForWidget(activeWidget, "#1C1C1E")
                } else if (hex == "#1C1C1E" || hex == "#FF3B30" || hex == "#007AFF" || hex == "#AF52DE") {
                    setTextColorForWidget(activeWidget, "#FFFFFF")
                }
                updateLivePreview()
            }
        }

        // Opaklık SeekBar'ı
        sbOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateOpacityText(progress)
                if (fromUser) {
                    setOpacityForWidget(activeWidget, progress)
                    updateLivePreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Hızlı Opaklık Butonları
        findViewById<MaterialButton>(R.id.btnOp0).setOnClickListener { setOpacity(0) }
        findViewById<MaterialButton>(R.id.btnOp30).setOnClickListener { setOpacity(30) }
        findViewById<MaterialButton>(R.id.btnOp70).setOnClickListener { setOpacity(70) }
        findViewById<MaterialButton>(R.id.btnOp100).setOnClickListener { setOpacity(100) }
    }

    private fun setOpacity(op: Int) {
        sbOpacity.progress = op
        updateOpacityText(op)
        setOpacityForWidget(activeWidget, op)
        updateLivePreview()
    }

    private fun updateOpacityText(op: Int) {
        tvOpacityValue.text = when (op) {
            0 -> "%0 (Tamamen Şeffaf)"
            in 1..39 -> "%$op (Buzlu Cam)"
            in 40..89 -> "%$op (Yarı Şeffaf)"
            else -> "%$op (Tam Opak)"
        }
    }

    private fun setupExtraOptions() {
        findViewById<MaterialButton>(R.id.btnCityIst).setOnClickListener { setCity("İSTANBUL", "41.0082", "28.9784") }
        findViewById<MaterialButton>(R.id.btnCityAnk).setOnClickListener { setCity("ANKARA", "39.9334", "32.8597") }
        findViewById<MaterialButton>(R.id.btnCityIzm).setOnClickListener { setCity("İZMİR", "38.4237", "27.1428") }
        findViewById<MaterialButton>(R.id.btnCityAnt).setOnClickListener { setCity("ANTALYA", "36.8969", "30.7133") }

        findViewById<MaterialButton>(R.id.btnRefreshWeather).setOnClickListener {
            Thread {
                U.fetchWeather(this)
                runOnUiThread {
                    Toast.makeText(this, "Hava durumu başarıyla yenilendi!", Toast.LENGTH_SHORT).show()
                    updateLivePreview()
                }
            }.start()
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { updateLivePreview() }
            override fun afterTextChanged(s: Editable?) {}
        }
        etLinkUrl.addTextChangedListener(watcher)
        etLinkTop.addTextChangedListener(watcher)
        switchShowDate.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        switch24Hour.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
    }

    private fun setCity(name: String, lat: String, lon: String) {
        P.put(this, 0, "city", name)
        P.put(this, 0, "lat", lat)
        P.put(this, 0, "lon", lon)
        Thread {
            U.fetchWeather(this)
            runOnUiThread {
                Toast.makeText(this, "$name hava durumu güncellendi!", Toast.LENGTH_SHORT).show()
                updateLivePreview()
            }
        }.start()
    }

    private fun setupActionButtons() {
        btnSaveAndApply.setOnClickListener {
            saveAllSettings()
            U.updateAll(this)
            Toast.makeText(this, "Tüm widget ayarları kaydedildi ve ekrandakiler anında güncellendi!", Toast.LENGTH_LONG).show()
        }

        btnPinWidget.setOnClickListener {
            showPinDialog()
        }
    }

    private fun saveAllSettings() {
        P.put(this, 0, "clock_showDate", switchShowDate.isChecked)
        P.put(this, 0, "clock_h24", switch24Hour.isChecked)
        P.put(this, 0, "link_url", etLinkUrl.text.toString().trim())
        P.put(this, 0, "link_top", etLinkTop.text.toString().trim())
    }

    private fun updateLivePreview() {
        saveAllSettings()

        val bmp = when (activeWidget) {
            "ios_calendar" -> U.generateIosCalendarBitmap(this, 0, 2, 2)
            "ios_weather" -> U.generateIosWeatherBitmap(this, 0, 2, 2)
            "ios_battery" -> U.generateIosBatteryBitmap(this, 0, 2, 2)
            "ios_clock" -> U.generateIosClockBitmap(this, 0, 2, 2)
            "ios_notes" -> U.generateIosNotesBitmap(this, 0, 2, 2)
            "music_41" -> U.generateMusicBitmap(this, 0, 4, 1)
            "music_22" -> U.generateMusicBitmap(this, 0, 2, 2)
            "date" -> U.generateIosCalendarBitmap(this, 0, 2, 2)
            "clock" -> U.generateIosClockBitmap(this, 0, 2, 2)
            "weather" -> U.generateIosWeatherBitmap(this, 0, 2, 2)
            "link" -> {
                // Link için şık buton önizlemesi
                val w = 520; val h = 260
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(b)
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val op = P.i(this, 0, "link_opacity", 100)
                val bgCol = col(P.s(this, 0, "link_bg", "#4F46E5"), Color.parseColor("#4F46E5"))
                val textCol = col(P.s(this, 0, "link_text", "#FFFFFF"), Color.WHITE)
                if (op > 0) {
                    p.color = applyOpacity(bgCol, op)
                    cv.drawRoundRect(android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), 46f, 46f, p)
                }
                p.color = textCol
                p.textAlign = android.graphics.Paint.Align.CENTER
                p.textSize = 62f
                p.isFakeBoldText = true
                cv.drawText(etLinkTop.text.toString().ifEmpty { "TIKLA" }, w / 2f, 130f, p)
                p.textSize = 24f
                p.isFakeBoldText = false
                p.color = Color.argb(200, Color.red(textCol), Color.green(textCol), Color.blue(textCol))
                cv.drawText(etLinkUrl.text.toString().ifEmpty { "https://www.google.com" }, w / 2f, 190f, p)
                b
            }
            else -> U.generateIosCalendarBitmap(this, 0, 2, 2)
        }

        ivLivePreview.setImageBitmap(bmp)
    }

    // Gelişmiş İnteraktif Renk Seçici Diyaloğu
    private fun showColorPickerDialog(title: String, initialHex: String, onColorSelected: (String) -> Unit) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_color_picker)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialog.findViewById<TextView>(R.id.dialogColorTitle)
        val viewPreview = dialog.findViewById<View>(R.id.viewColorPreview)
        val tvHex = dialog.findViewById<TextView>(R.id.tvHexPreview)
        val presetContainer = dialog.findViewById<LinearLayout>(R.id.presetColorsContainer)
        val sbR = dialog.findViewById<SeekBar>(R.id.sbRed)
        val sbG = dialog.findViewById<SeekBar>(R.id.sbGreen)
        val sbB = dialog.findViewById<SeekBar>(R.id.sbBlue)
        val tvR = dialog.findViewById<TextView>(R.id.tvLabelR)
        val tvG = dialog.findViewById<TextView>(R.id.tvLabelG)
        val tvB = dialog.findViewById<TextView>(R.id.tvLabelB)
        val etHex = dialog.findViewById<TextInputEditText>(R.id.etDialogHex)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancelColor)
        val btnApply = dialog.findViewById<MaterialButton>(R.id.btnApplyColor)

        tvTitle.text = title

        var currentColor = col(initialHex, Color.parseColor("#EF4444"))
        fun updateDialogViews(color: Int, fromText: Boolean = false) {
            currentColor = color
            viewPreview.setBackgroundColor(color)
            val hexStr = String.format("#%06X", (0xFFFFFF and color))
            tvHex.text = hexStr
            if (!fromText) {
                etHex.setText(hexStr)
            }
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            sbR.progress = r
            sbG.progress = g
            sbB.progress = b
            tvR.text = "Kırmızı (R): $r"
            tvG.text = "Yeşil (G): $g"
            tvB.text = "Mavi (B): $b"
        }

        updateDialogViews(currentColor)

        // 24 Hazır Renk Çipi
        val presetList = listOf(
            "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#007AFF", "#5856D6",
            "#AF52DE", "#FF2D55", "#A2845E", "#8E8E93", "#1C1C1E", "#FFFFFF",
            "#EF4444", "#F59E0B", "#10B981", "#06B6D4", "#3B82F6", "#6366F1",
            "#8B5CF6", "#EC4899", "#1E293B", "#0F172A", "#1E3A8A", "#064E3B"
        )
        for (hex in presetList) {
            val chip = MaterialButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(100, 100).apply { setMargins(6, 6, 6, 6) }
                cornerRadius = 50
                setBackgroundColor(col(hex, Color.RED))
                setOnClickListener { updateDialogViews(col(hex, Color.RED)) }
            }
            presetContainer.addView(chip)
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val r = sbR.progress
                    val g = sbG.progress
                    val b = sbB.progress
                    updateDialogViews(Color.rgb(r, g, b))
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        sbR.setOnSeekBarChangeListener(seekListener)
        sbG.setOnSeekBarChangeListener(seekListener)
        sbB.setOnSeekBarChangeListener(seekListener)

        etHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                val str = s?.toString()?.trim() ?: ""
                if (str.length >= 7) {
                    try {
                        val parsed = Color.parseColor(str)
                        updateDialogViews(parsed, fromText = true)
                    } catch (e: Exception) {}
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnApply.setOnClickListener {
            val finalHex = String.format("#%06X", (0xFFFFFF and currentColor))
            onColorSelected(finalHex)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showPinDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Ana ekrana uzun basıp Senle Widgets seçiniz.", Toast.LENGTH_LONG).show()
            return
        }

        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        if (!appWidgetManager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, "Başlatıcınız doğrudan widget eklemeyi desteklemiyor.", Toast.LENGTH_LONG).show()
            return
        }

        saveAllSettings()

        if (activeWidget == "music_41" || activeWidget == "music_22") {
            val sizes = arrayOf(
                "🎵 4x1 Banner (1x4 Yana Uzatılabilir)",
                "🎵 3x1 Banner (1x3 Yana Uzatılabilir)",
                "🔒 2x1 Kilit Ekranı & AOD Kompakt Hap (One UI Uyumlu)",
                "🎵 2x2 Kare Kart (AOD & Kilit Ekranı Uyumlu)",
                "🎵 3x3 Büyük Kare Kart (Genişletilmiş Kilit & Ana Ekran)",
                "🎵 4x4 Devasa Kare Kart (Tam Genişlik Kilit & Ana Ekran)",
                "🎵 4x2 Büyük Kart (Geniş Albüm Oynatıcı)"
            )
            val classes = arrayOf(
                MusicW41::class.java,
                MusicW31::class.java,
                MusicLockW21::class.java,
                MusicW22::class.java,
                MusicW33::class.java,
                MusicW44::class.java,
                MusicW42::class.java
            )
            AlertDialog.Builder(this)
                .setTitle("Spotify Widget Formatı Seçin")
                .setItems(sizes) { _, which ->
                    val provider = ComponentName(this, classes[which])
                    appWidgetManager.requestPinAppWidget(provider, null, null)
                    Toast.makeText(this, "${sizes[which].substringBefore('(').trim()} ana ekrana eklendi!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("İptal", null)
                .show()
            return
        }

        val providerClass = when (activeWidget) {
            "ios_calendar" -> IosCalendarW22::class.java
            "ios_weather" -> IosWeatherW22::class.java
            "ios_battery" -> IosBatteryW22::class.java
            "ios_clock" -> IosClockW22::class.java
            "ios_notes" -> IosNotesW22::class.java
            "clock" -> ClockW22::class.java
            "weather" -> WeatherW22::class.java
            "link" -> LinkW22::class.java
            "date" -> DateW22::class.java
            else -> IosCalendarW22::class.java
        }

        val provider = ComponentName(this, providerClass)
        appWidgetManager.requestPinAppWidget(provider, null, null)
        Toast.makeText(this, "Samsung One UI 8.5 ana ekranına eklendi!", Toast.LENGTH_SHORT).show()
    }
}
