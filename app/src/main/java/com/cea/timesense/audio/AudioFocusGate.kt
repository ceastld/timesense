package com.cea.timesense.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When [ignore] is false, ticks pause while another app is playing media
 * or the device is in a call. We never steal audio focus.
 * When [ignore] is true, ticks keep going (USAGE_ALARM overlay).
 */
class AudioFocusGate(
    context: Context,
    private val onMutedChanged: (Boolean) -> Unit,
) {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val ignore = AtomicBoolean(true)
    private val muted = AtomicBoolean(false)
    private var registered = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            refresh(configs)
        }
    }

    fun shouldPlay(): Boolean {
        if (ignore.get()) return true
        refresh()
        return !muted.get()
    }

    fun setIgnore(value: Boolean) {
        ignore.set(value)
        if (value) {
            unregister()
            setMuted(false)
        } else {
            register()
            refresh()
        }
    }

    fun abandon() {
        unregister()
        setMuted(false)
    }

    private fun register() {
        if (registered) return
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        registered = true
    }

    private fun unregister() {
        if (!registered) return
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        registered = false
    }

    private fun refresh(configs: List<AudioPlaybackConfiguration> = audioManager.activePlaybackConfigurations) {
        if (ignore.get()) {
            setMuted(false)
            return
        }
        setMuted(isCallActive() || othersPlaying(configs))
    }

    private fun isCallActive(): Boolean {
        return when (audioManager.mode) {
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_RINGTONE,
            -> true
            else -> false
        }
    }

    private fun othersPlaying(configs: List<AudioPlaybackConfiguration>): Boolean {
        return configs.any { cfg -> blockingUsage(cfg.audioAttributes.usage) }
    }

    private fun setMuted(value: Boolean) {
        if (muted.getAndSet(value) != value) {
            onMutedChanged(value)
        }
    }

    companion object {
        fun mediaAttrs(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun overlayAttrs(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        private fun blockingUsage(usage: Int): Boolean {
            return usage == AudioAttributes.USAGE_MEDIA ||
                usage == AudioAttributes.USAGE_GAME ||
                usage == AudioAttributes.USAGE_VOICE_COMMUNICATION ||
                usage == AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING ||
                usage == AudioAttributes.USAGE_ASSISTANT
        }
    }
}
