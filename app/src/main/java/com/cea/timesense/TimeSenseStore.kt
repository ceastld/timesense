package com.cea.timesense

import android.content.Context
import android.content.SharedPreferences
import com.cea.timesense.audio.Cue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide clock offset and UI-facing run/sync/settings state.
 *
 * [offsetMs] is added to [System.currentTimeMillis] so every consumer
 * (scheduler, UI, notification) sees the same NTP-corrected wall clock.
 * A failed sync never clears a previously good offset.
 */
object TimeSenseStore {

    data class SyncInfo(
        val success: Boolean,
        val syncedAtSystemMs: Long,
        val offsetMs: Long,
        val roundTripMs: Long,
        val host: String,
        val error: String? = null,
    )

    data class SoundSlot(
        val customName: String?,
    ) {
        val isCustom: Boolean get() = !customName.isNullOrBlank()
    }

    data class Settings(
        val ignoreAudioFocus: Boolean,
        val sounds: Map<Cue, SoundSlot>,
        val epoch: Int,
    ) {
        fun slot(cue: Cue): SoundSlot = sounds[cue] ?: SoundSlot(null)
    }

    private const val PREFS = "timesense"
    private const val KEY_OFFSET = "offset_ms"
    private const val KEY_SYNCED_AT = "synced_at"
    private const val KEY_RTT = "rtt_ms"
    private const val KEY_HOST = "host"
    private const val KEY_OK = "ok"
    private const val KEY_IGNORE_FOCUS = "ignore_audio_focus"

    @Volatile
    var offsetMs: Long = 0L
        private set

    private lateinit var prefs: SharedPreferences

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastSync = MutableStateFlow<SyncInfo?>(null)
    val lastSync: StateFlow<SyncInfo?> = _lastSync.asStateFlow()

    private val _audioHeldOut = MutableStateFlow(false)
    val audioHeldOut: StateFlow<Boolean> = _audioHeldOut.asStateFlow()

    private val _settings = MutableStateFlow(defaultSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (this::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_SYNCED_AT)) {
            offsetMs = prefs.getLong(KEY_OFFSET, 0L)
            _lastSync.value = SyncInfo(
                success = prefs.getBoolean(KEY_OK, false),
                syncedAtSystemMs = prefs.getLong(KEY_SYNCED_AT, 0L),
                offsetMs = offsetMs,
                roundTripMs = prefs.getLong(KEY_RTT, 0L),
                host = prefs.getString(KEY_HOST, "") ?: "",
            )
        }
        _settings.value = readSettings()
    }

    fun nowMillis(): Long = System.currentTimeMillis() + offsetMs

    fun setRunning(running: Boolean) {
        _isRunning.value = running
        if (!running) _audioHeldOut.value = false
    }

    fun setAudioHeldOut(heldOut: Boolean) {
        _audioHeldOut.value = heldOut
    }

    fun setIgnoreAudioFocus(ignore: Boolean) {
        if (!this::prefs.isInitialized) return
        prefs.edit().putBoolean(KEY_IGNORE_FOCUS, ignore).apply()
        bumpSettings()
    }

    fun setCustomSound(cue: Cue, displayName: String) {
        if (!this::prefs.isInitialized) return
        prefs.edit().putString(cue.prefsKey, displayName).apply()
        bumpSettings()
    }

    fun clearCustomSound(cue: Cue) {
        if (!this::prefs.isInitialized) return
        prefs.edit().remove(cue.prefsKey).apply()
        bumpSettings()
    }

    fun applySuccessfulSync(offset: Long, rtt: Long, host: String) {
        offsetMs = offset
        val info = SyncInfo(
            success = true,
            syncedAtSystemMs = System.currentTimeMillis(),
            offsetMs = offset,
            roundTripMs = rtt,
            host = host,
        )
        _lastSync.value = info
        persist(info)
    }

    fun applyFailedSync(error: String, host: String = "") {
        val previous = _lastSync.value
        val info = SyncInfo(
            success = false,
            syncedAtSystemMs = System.currentTimeMillis(),
            offsetMs = offsetMs,
            roundTripMs = previous?.roundTripMs ?: 0L,
            host = host.ifEmpty { previous?.host ?: "" },
            error = error,
        )
        _lastSync.value = info
        persist(info)
    }

    private fun persist(info: SyncInfo) {
        if (!this::prefs.isInitialized) return
        prefs.edit()
            .putLong(KEY_OFFSET, offsetMs)
            .putLong(KEY_SYNCED_AT, info.syncedAtSystemMs)
            .putLong(KEY_RTT, info.roundTripMs)
            .putString(KEY_HOST, info.host)
            .putBoolean(KEY_OK, info.success)
            .apply()
    }

    private fun bumpSettings() {
        _settings.value = readSettings(_settings.value.epoch + 1)
    }

    private fun readSettings(epoch: Int = _settings.value.epoch): Settings {
        if (!this::prefs.isInitialized) return defaultSettings(epoch)
        val sounds = Cue.entries.associateWith { cue ->
            SoundSlot(prefs.getString(cue.prefsKey, null)?.takeIf { it.isNotBlank() })
        }
        return Settings(
            ignoreAudioFocus = prefs.getBoolean(KEY_IGNORE_FOCUS, true),
            sounds = sounds,
            epoch = epoch,
        )
    }

    private fun defaultSettings(epoch: Int = 0): Settings = Settings(
        ignoreAudioFocus = true,
        sounds = Cue.entries.associateWith { SoundSlot(null) },
        epoch = epoch,
    )
}
