package com.senle.widgets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit

class AodMusicActivity : AppCompatActivity() {

    private lateinit var aodRoot: View
    private lateinit var aodAlbumArt: ImageView
    private lateinit var aodSongTitle: TextView
    private lateinit var aodArtistName: TextView
    private lateinit var aodSeekBar: SeekBar
    private lateinit var aodCurrentTime: TextView
    private lateinit var aodTotalTime: TextView
    private lateinit var aodBtnPrev: ImageButton
    private lateinit var aodBtnPlayPause: ImageButton
    private lateinit var aodBtnNext: ImageButton
    private lateinit var aodBtnDimmer: MaterialButton
    private lateinit var aodBtnSpotify: MaterialButton
    private lateinit var aodBtnClose: MaterialButton

    private var isAodDimmed = false
    private var isUserTrackingSeek = false
    private val handler = Handler(Looper.getMainLooper())

    private val mediaUpdateListener = {
        runOnUiThread {
            updateUi()
        }
    }

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (!isUserTrackingSeek && MediaHolder.isPlaying) {
                updateProgress()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private val idleDimmerRunnable = Runnable {
        if (isAodDimmed) {
            setBrightness(0.02f)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kilit ekranı üzerinde çalışma ve ekranı uyandırma izinleri
        setupLockScreenFlags()

        setContentView(R.layout.activity_aod_music)

        initViews()
        setupListeners()
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        MediaHolder.addListener(mediaUpdateListener)
        updateUi()
        handler.post(progressUpdater)
    }

    override fun onPause() {
        super.onPause()
        MediaHolder.removeListener(mediaUpdateListener)
        handler.removeCallbacks(progressUpdater)
        handler.removeCallbacks(idleDimmerRunnable)
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun initViews() {
        aodRoot = findViewById(R.id.aodRoot)
        aodAlbumArt = findViewById(R.id.aodAlbumArt)
        aodSongTitle = findViewById(R.id.aodSongTitle)
        aodArtistName = findViewById(R.id.aodArtistName)
        aodSeekBar = findViewById(R.id.aodSeekBar)
        aodCurrentTime = findViewById(R.id.aodCurrentTime)
        aodTotalTime = findViewById(R.id.aodTotalTime)
        aodBtnPrev = findViewById(R.id.aodBtnPrev)
        aodBtnPlayPause = findViewById(R.id.aodBtnPlayPause)
        aodBtnNext = findViewById(R.id.aodBtnNext)
        aodBtnDimmer = findViewById(R.id.aodBtnDimmer)
        aodBtnSpotify = findViewById(R.id.aodBtnSpotify)
        aodBtnClose = findViewById(R.id.aodBtnClose)

        // Şarkı adı kayan yazı (Marquee) başlat
        aodSongTitle.isSelected = true
    }

    private fun setupListeners() {
        aodBtnPrev.setOnClickListener {
            resetIdleDimmerTimer()
            MediaHolder.skipToPrevious(this)
            postDelayedUpdate()
        }

        aodBtnPlayPause.setOnClickListener {
            resetIdleDimmerTimer()
            MediaHolder.togglePlayPause(this)
            postDelayedUpdate()
        }

        aodBtnNext.setOnClickListener {
            resetIdleDimmerTimer()
            MediaHolder.skipToNext(this)
            postDelayedUpdate()
        }

        aodBtnSpotify.setOnClickListener {
            val pm = packageManager
            val intent = pm.getLaunchIntentForPackage("com.spotify.music")
                ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MUSIC)
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        aodBtnClose.setOnClickListener {
            finish()
        }

        // AOD Karartma Modu Butonu
        aodBtnDimmer.setOnClickListener {
            isAodDimmed = !isAodDimmed
            if (isAodDimmed) {
                setBrightness(0.02f)
                aodBtnDimmer.text = "🌙 AOD: Karartıldı"
                aodBtnDimmer.setTextColor(android.graphics.Color.parseColor("#38BDF8"))
            } else {
                setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                aodBtnDimmer.text = "☀️ Parlaklık: Normal"
                aodBtnDimmer.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                handler.removeCallbacks(idleDimmerRunnable)
            }
        }

        // Ekrana dokunulduğunda geçici olarak aydınlat, 8sn sonra tekrar karart
        aodRoot.setOnClickListener {
            resetIdleDimmerTimer()
        }

        // SeekBar etkileşimi
        aodSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && MediaHolder.durationMs > 0) {
                    val targetMs = (MediaHolder.durationMs * progress) / 100
                    aodCurrentTime.text = formatTime(targetMs)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingSeek = true
                resetIdleDimmerTimer()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingSeek = false
                seekBar?.let {
                    if (MediaHolder.durationMs > 0) {
                        val targetMs = (MediaHolder.durationMs * it.progress) / 100
                        MediaHolder.seekTo(targetMs)
                    }
                }
            }
        })
    }

    private fun resetIdleDimmerTimer() {
        if (isAodDimmed) {
            setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            handler.removeCallbacks(idleDimmerRunnable)
            handler.postDelayed(idleDimmerRunnable, 8000)
        }
    }

    private fun setBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value
        window.attributes = lp
    }

    private fun postDelayedUpdate() {
        handler.postDelayed({
            updateUi()
        }, 300)
    }

    private fun updateUi() {
        aodSongTitle.text = MediaHolder.songTitle
        aodArtistName.text = MediaHolder.artistName

        val art = MediaHolder.albumArt
        if (art != null) {
            aodAlbumArt.setImageBitmap(art)
        } else {
            aodAlbumArt.setImageResource(R.drawable.ic_music_note)
        }

        if (MediaHolder.isPlaying) {
            aodBtnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            aodBtnPlayPause.setImageResource(R.drawable.ic_play)
        }

        updateProgress()
    }

    private fun updateProgress() {
        val duration = MediaHolder.durationMs
        val current = MediaHolder.getCurrentPosition()

        if (duration > 0) {
            val progressPercent = ((current * 100) / duration).toInt().coerceIn(0, 100)
            aodSeekBar.progress = progressPercent
            aodCurrentTime.text = formatTime(current)
            aodTotalTime.text = formatTime(duration)
        } else {
            aodSeekBar.progress = 0
            aodCurrentTime.text = "0:00"
            aodTotalTime.text = "--:--"
        }
    }

    private fun formatTime(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
