package com.senle.widgets

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

object MediaHolder {
    var activeController: MediaController? = null
    var songTitle: String = "Spotify'da Şarkı Başlat"
    var artistName: String = "Çalan müzik yok • Dokun"
    var albumArt: Bitmap? = null
    var isPlaying: Boolean = false
    var durationMs: Long = 0L
    var positionMs: Long = 0L
    var playbackSpeed: Float = 1.0f
    var lastPositionUpdateTime: Long = 0L

    private var isDiskLoaded = false
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun getOrLoadAlbumArt(c: Context): Bitmap? {
        if (albumArt != null) return albumArt
        if (!isDiskLoaded) {
            isDiskLoaded = true
            try {
                val file = File(c.filesDir, "album_art_cache.png")
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        albumArt = bmp
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return albumArt
    }

    private fun saveAlbumArt(c: Context, bmp: Bitmap) {
        try {
            val file = File(c.filesDir, "album_art_cache.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyListeners() {
        Handler(Looper.getMainLooper()).post {
            for (listener in listeners) {
                try {
                    listener.invoke()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getCurrentPosition(): Long {
        if (!isPlaying || playbackSpeed <= 0f) return positionMs
        val elapsed = System.currentTimeMillis() - lastPositionUpdateTime
        val estimated = positionMs + (elapsed * playbackSpeed).toLong()
        return if (durationMs > 0) estimated.coerceAtMost(durationMs) else estimated
    }

    fun skipToPrevious(c: Context) {
        val controller = activeController
        if (controller != null) {
            controller.transportControls?.skipToPrevious()
        } else {
            dispatchKeyEvent(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    fun togglePlayPause(c: Context) {
        val controller = activeController
        if (controller != null) {
            if (isPlaying) {
                controller.transportControls?.pause()
            } else {
                controller.transportControls?.play()
            }
        } else {
            dispatchKeyEvent(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun skipToNext(c: Context) {
        val controller = activeController
        if (controller != null) {
            controller.transportControls?.skipToNext()
        } else {
            dispatchKeyEvent(c, KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun seekTo(posMs: Long) {
        activeController?.transportControls?.seekTo(posMs)
        positionMs = posMs
        lastPositionUpdateTime = System.currentTimeMillis()
        notifyListeners()
    }

    private fun dispatchKeyEvent(c: Context, keyCode: Int) {
        try {
            val audioManager = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateFromController(c: Context, controller: MediaController?) {
        activeController = controller
        if (controller == null) {
            isPlaying = false
            durationMs = 0L
            positionMs = 0L
            P.put(c, 0, "music_is_playing", false)
            notifyListeners()
            return
        }

        val metadata = controller.metadata
        val pbState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

        if (!title.isNullOrEmpty()) {
            songTitle = title
            artistName = artist ?: "Bilinmeyen Sanatçı"
        }

        val rawArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        if (rawArt != null) {
            // IPC Binder boyut sınırını aşmamak ve kilit ekranı/AOD'da kaybolmayı önlemek için max 240x240'a ölçekle
            val maxDim = maxOf(rawArt.width, rawArt.height)
            val scaledArt = if (maxDim > 240) {
                val scale = 240f / maxDim
                val tw = (rawArt.width * scale).toInt().coerceAtLeast(1)
                val th = (rawArt.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(rawArt, tw, th, true)
            } else {
                rawArt
            }
            albumArt = scaledArt
            Thread {
                saveAlbumArt(c, scaledArt)
            }.start()
        }

        isPlaying = pbState?.state == PlaybackState.STATE_PLAYING
        durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        positionMs = pbState?.position ?: 0L
        playbackSpeed = pbState?.playbackSpeed ?: 1.0f
        lastPositionUpdateTime = pbState?.lastPositionUpdateTime ?: System.currentTimeMillis()

        // Tercihlere de kaydet
        P.put(c, 0, "music_title", songTitle)
        P.put(c, 0, "music_artist", artistName)
        P.put(c, 0, "music_is_playing", isPlaying)

        notifyListeners()
    }
}

class MediaListenerService : NotificationListenerService() {

    private var currentController: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            MediaHolder.updateFromController(applicationContext, currentController)
            U.updateAll(applicationContext)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            MediaHolder.updateFromController(applicationContext, currentController)
            U.updateAll(applicationContext)
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        findAndAttachActiveMedia()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(this, MediaListenerService::class.java)
            mm?.addOnActiveSessionsChangedListener(sessionsListener, component)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        findAndAttachActiveMedia()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, MediaListenerService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            mm?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {}
        try {
            currentController?.unregisterCallback(callback)
        } catch (e: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        findAndAttachActiveMedia()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        findAndAttachActiveMedia()
    }

    private fun findAndAttachActiveMedia() {
        try {
            val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val component = ComponentName(this, MediaListenerService::class.java)
            val controllers = mm.getActiveSessions(component)

            // Spotify öncelikli kontrol
            var target = controllers.firstOrNull { it.packageName == "com.spotify.music" }
            if (target == null) {
                target = controllers.firstOrNull {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
                } ?: controllers.firstOrNull()
            }

            if (target != null) {
                if (target != currentController) {
                    currentController?.unregisterCallback(callback)
                    currentController = target
                    currentController?.registerCallback(callback)
                }
                MediaHolder.updateFromController(applicationContext, target)
            } else {
                currentController?.unregisterCallback(callback)
                currentController = null
                MediaHolder.updateFromController(applicationContext, null)
            }

            U.updateAll(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class MediaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext

        // Eğer activeController null ise hemen session'lardan bulmaya çalış
        if (MediaHolder.activeController == null) {
            try {
                val mm = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                val component = ComponentName(appContext, MediaListenerService::class.java)
                val controllers = mm?.getActiveSessions(component)
                val target = controllers?.firstOrNull { it.packageName == "com.spotify.music" }
                    ?: controllers?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    ?: controllers?.firstOrNull()
                if (target != null) {
                    MediaHolder.activeController = target
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        when (intent.action) {
            "com.senle.widgets.ACTION_MEDIA_PREV" -> {
                MediaHolder.skipToPrevious(appContext)
            }
            "com.senle.widgets.ACTION_MEDIA_PLAY_PAUSE" -> {
                MediaHolder.togglePlayPause(appContext)
            }
            "com.senle.widgets.ACTION_MEDIA_NEXT" -> {
                MediaHolder.skipToNext(appContext)
            }
        }

        // Hızlı güncelleme (200ms)
        Handler(Looper.getMainLooper()).postDelayed({
            val c = MediaHolder.activeController
            MediaHolder.updateFromController(appContext, c)
            U.updateAll(appContext)
        }, 200)

        // İkincil teyit güncellemesi (700ms - yavaş gecikmeler için)
        Handler(Looper.getMainLooper()).postDelayed({
            val c = MediaHolder.activeController
            MediaHolder.updateFromController(appContext, c)
            U.updateAll(appContext)
        }, 700)
    }
}
