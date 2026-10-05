package com.senle.widgets

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
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

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

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

        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null) {
            albumArt = art
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

    override fun onListenerConnected() {
        super.onListenerConnected()
        findAndAttachActiveMedia()
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
        val controller = MediaHolder.activeController

        when (intent.action) {
            "com.senle.widgets.ACTION_MEDIA_PREV" -> {
                MediaHolder.skipToPrevious(context)
            }
            "com.senle.widgets.ACTION_MEDIA_PLAY_PAUSE" -> {
                MediaHolder.togglePlayPause(context)
            }
            "com.senle.widgets.ACTION_MEDIA_NEXT" -> {
                MediaHolder.skipToNext(context)
            }
        }

        // 350ms sonra güncelleme gönder
        Handler(Looper.getMainLooper()).postDelayed({
            MediaHolder.updateFromController(context, controller)
            U.updateAll(context)
        }, 350)
    }
}
