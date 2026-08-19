package com.cea.timesense

import android.content.Context
import android.content.SharedPreferences
import com.cea.timesense.audio.BuiltinTones
import com.cea.timesense.audio.Cue
import com.cea.timesense.audio.SoundBank
import com.cea.timesense.audio.SoundOption
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

    data class CustomSound(
        val id: String,
        val cue: Cue,
        val name: String,
    )

    data class SoundSlot(
        val selectedId: String,
    ) {
        val isCustom: Boolean get() = SoundBank.isCustomId(selectedId)

        fun label(customs: List<CustomSound>): String {
            if (isCustom) {
                return customs.firstOrNull { it.id == selectedId }?.name ?: "自定义"
            }
            return BuiltinTones.byId(selectedId)?.label ?: "经典"
        }
    }

    data class Settings(
        val ignoreAudioFocus: Boolean,
        val sounds: Map<Cue, SoundSlot>,
        val customs: List<CustomSound>,
        val epoch: Int,
        val audioEpoch: Int,
    ) {
        fun slot(cue: Cue): SoundSlot = sounds[cue] ?: SoundSlot(cue.defaultSoundId)

        fun optionsFor(cue: Cue): List<SoundOption> {
            val builtin = BuiltinTones.forCue(cue)
            val extra = customs.filter { it.cue == cue }.map { custom ->
                SoundOption(
                    id = custom.id,
                    cue = cue,
                    label = custom.name,
                    builtin = false,
                    durationMs = 1_200L,
                )
            }
            return builtin + extra
        }
    }

    private const val PREFS = "timesense"
    private const val KEY_OFFSET = "offset_ms"
    private const val KEY_SYNCED_AT = "synced_at"
    private const val KEY_RTT = "rtt_ms"
    private const val KEY_HOST = "host"
    private const val KEY_OK = "ok"
    private const val KEY_IGNORE_FOCUS = "ignore_audio_focus"
    private const val KEY_CUSTOMS = "custom_sounds"

    @Volatile
    var offsetMs: Long = 0L
        private set

    private lateinit var prefs: SharedPreferences

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastSync = MutableStateFlow<SyncInfo?>(null)
    val lastSync: StateFlow<SyncInfo?> = _lastSync.asStateFlow()

    private val _settings = MutableStateFlow(defaultSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _audioHeldOut = MutableStateFlow(false)
    val audioHeldOut: StateFlow<Boolean> = _audioHeldOut.asStateFlow()

    private val _ticksHeld = MutableStateFlow(false)
    val ticksHeld: StateFlow<Boolean> = _ticksHeld.asStateFlow()

    @Volatile
    var ticksHeldNow: Boolean = false
        private set

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
        migrateLegacyCustoms(context)
    }

    fun nowMillis(): Long = System.currentTimeMillis() + offsetMs

    fun setRunning(running: Boolean) {
        _isRunning.value = running
        if (!running) {
            _audioHeldOut.value = false
            setTicksHeld(false)
        }
    }

    fun setAudioHeldOut(heldOut: Boolean) {
        _audioHeldOut.value = heldOut
    }

    fun setTicksHeld(held: Boolean) {
        ticksHeldNow = held
        _ticksHeld.value = held
    }

    fun setIgnoreAudioFocus(ignore: Boolean) {
        if (!this::prefs.isInitialized) return
        prefs.edit().putBoolean(KEY_IGNORE_FOCUS, ignore).apply()
        bumpSettings(reloadAudio = true)
    }

    fun setSelectedSound(cue: Cue, soundId: String) {
        if (!this::prefs.isInitialized) return
        prefs.edit().putString(cue.prefsKey, soundId).apply()
        bumpSettings(reloadAudio = false)
    }

    fun addCustomSound(imported: SoundBank.Imported) {
        if (!this::prefs.isInitialized) return
        val next = customsFromPrefs() + CustomSound(imported.id, imported.cue, imported.name)
        writeCustoms(next)
        prefs.edit().putString(imported.cue.prefsKey, imported.id).apply()
        bumpSettings(reloadAudio = true)
    }

    fun removeCustomSound(context: Context, id: String) {
        if (!this::prefs.isInitialized) return
        val remaining = customsFromPrefs().filterNot { it.id == id }
        writeCustoms(remaining)
        Cue.entries.forEach { cue ->
            if (prefs.getString(cue.prefsKey, cue.defaultSoundId) == id) {
                prefs.edit().putString(cue.prefsKey, cue.defaultSoundId).apply()
            }
        }
        SoundBank.delete(context, id)
        bumpSettings(reloadAudio = true)
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

    private fun bumpSettings(reloadAudio: Boolean) {
        val current = _settings.value
        _settings.value = readSettings(
            epoch = current.epoch + 1,
            audioEpoch = if (reloadAudio) current.audioEpoch + 1 else current.audioEpoch,
        )
    }

    private fun readSettings(epoch: Int = _settings.value.epoch, audioEpoch: Int = _settings.value.audioEpoch): Settings {
        if (!this::prefs.isInitialized) return defaultSettings(epoch, audioEpoch)
        val customs = customsFromPrefs()
        val sounds = Cue.entries.associateWith { cue ->
            val raw = prefs.getString(cue.prefsKey, null)
            val selected = when {
                raw.isNullOrBlank() -> cue.defaultSoundId
                SoundBank.isCustomId(raw) && customs.none { it.id == raw } -> cue.defaultSoundId
                !SoundBank.isCustomId(raw) && BuiltinTones.byId(raw) == null -> cue.defaultSoundId
                else -> raw
            }
            SoundSlot(selected)
        }
        return Settings(
            ignoreAudioFocus = prefs.getBoolean(KEY_IGNORE_FOCUS, true),
            sounds = sounds,
            customs = customs,
            epoch = epoch,
            audioEpoch = audioEpoch,
        )
    }

    private fun defaultSettings(epoch: Int = 0, audioEpoch: Int = 0): Settings = Settings(
        ignoreAudioFocus = true,
        sounds = Cue.entries.associateWith { SoundSlot(it.defaultSoundId) },
        customs = emptyList(),
        epoch = epoch,
        audioEpoch = audioEpoch,
    )

    private fun customsFromPrefs(): List<CustomSound> {
        if (!this::prefs.isInitialized) return emptyList()
        return prefs.getStringSet(KEY_CUSTOMS, emptySet())
            .orEmpty()
            .mapNotNull { decodeCustom(it) }
            .sortedBy { it.name }
    }

    private fun writeCustoms(list: List<CustomSound>) {
        val encoded = list.map { encodeCustom(it) }.toSet()
        prefs.edit().putStringSet(KEY_CUSTOMS, encoded).apply()
    }

    private fun encodeCustom(item: CustomSound): String {
        return "${item.cue.name}\t${item.id}\t${item.name.replace('\t', ' ')}"
    }

    private fun decodeCustom(raw: String): CustomSound? {
        val parts = raw.split('\t')
        if (parts.size < 3) return null
        val cue = Cue.entries.firstOrNull { it.name == parts[0] } ?: return null
        return CustomSound(id = parts[1], cue = cue, name = parts.drop(2).joinToString("\t"))
    }

    private fun migrateLegacyCustoms(context: Context) {
        Cue.entries.forEach { cue ->
            val legacyName = prefs.getString("sound_${cue.name.lowercase()}", null)
            val imported = SoundBank.migrateV1(context, cue) ?: return@forEach
            val named = imported.copy(name = legacyName?.takeIf { it.isNotBlank() } ?: imported.name)
            addCustomSound(named)
            prefs.edit().remove("sound_${cue.name.lowercase()}").apply()
        }
    }
}
