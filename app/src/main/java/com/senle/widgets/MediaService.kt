package com.senle.widgets

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

object MediaHolder {
    var activeController: MediaController? = null
    var songTitle: String = "Spotify'da Şarkı Başlat"
    var artistName: String = "Çalan müzik yok • Dokun"
    var albumArt: Bitmap? = null
    var isPlaying: Boolean = false

    fun updateFromController(c: Context, controller: MediaController?) {
        activeController = controller
        if (controller == null) {
            isPlaying = false
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

        // Tercihlere de kaydet
        P.put(c, 0, "music_title", songTitle)
        P.put(c, 0, "music_artist", artistName)
        P.put(c, 0, "music_is_playing", isPlaying)
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
                if (controller != null) {
                    controller.transportControls?.skipToPrevious()
                } else {
                    dispatchKeyEvent(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
            }
            "com.senle.widgets.ACTION_MEDIA_PLAY_PAUSE" -> {
                if (controller != null) {
                    if (MediaHolder.isPlaying) {
                        controller.transportControls?.pause()
                    } else {
                        controller.transportControls?.play()
                    }
                } else {
                    dispatchKeyEvent(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
            }
            "com.senle.widgets.ACTION_MEDIA_NEXT" -> {
                if (controller != null) {
                    controller.transportControls?.skipToNext()
                } else {
                    dispatchKeyEvent(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            }
        }

        // 350ms sonra güncelleme gönder
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            MediaHolder.updateFromController(context, controller)
            U.updateAll(context)
        }, 350)
    }

    private fun dispatchKeyEvent(c: Context, keyCode: Int) {
        try {
            val audioManager = c.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
