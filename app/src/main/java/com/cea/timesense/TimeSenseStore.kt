package com.cea.timesense

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide clock offset and UI-facing run/sync state.
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

    private const val PREFS = "timesense"
    private const val KEY_OFFSET = "offset_ms"
    private const val KEY_SYNCED_AT = "synced_at"
    private const val KEY_RTT = "rtt_ms"
    private const val KEY_HOST = "host"
    private const val KEY_OK = "ok"

    @Volatile
    var offsetMs: Long = 0L
        private set

    private lateinit var prefs: SharedPreferences

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastSync = MutableStateFlow<SyncInfo?>(null)
    val lastSync: StateFlow<SyncInfo?> = _lastSync.asStateFlow()

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
    }

    fun nowMillis(): Long = System.currentTimeMillis() + offsetMs

    fun setRunning(running: Boolean) {
        _isRunning.value = running
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
}
