package com.cea.timesense.audio

import android.content.Context
import android.media.SoundPool
import com.cea.timesense.TimeSenseStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Low-latency playback for the three 时感 cues via [SoundPool].
 *
 * [holdFocus] is true in [com.cea.timesense.service.TickService]: we then
 * honor the ignore-audio-focus setting. Preview playback never takes focus.
 *
 * Each cue keeps at most one live stream so a long custom tick cannot
 * stack on itself and steal slots from the next second.
 */
class SoundEngine(
    context: Context,
    private val holdFocus: Boolean,
) {

    private val app = context.applicationContext
    private val focusGate = if (holdFocus) {
        AudioFocusGate(app) { heldOut -> TimeSenseStore.setAudioHeldOut(heldOut) }
    } else {
        null
    }

    private var pool: SoundPool
    private val ids = HashMap<String, Int>(16)
    private val poolToSound = HashMap<Int, String>(16)
    private val ready = ConcurrentHashMap.newKeySet<String>()
    private val pendingId = AtomicReference<String?>(null)
    private val lastStreamByCue = IntArray(Cue.entries.size)
    private val lock = Any()

    @Volatile
    private var released = false

    init {
        pool = buildPool()
        reloadFromStore()
        if (holdFocus) {
            focusGate?.setIgnore(TimeSenseStore.settings.value.ignoreAudioFocus)
        }
    }

    fun reloadFromStore() {
        if (released) return
        val ignore = TimeSenseStore.settings.value.ignoreAudioFocus
        focusGate?.setIgnore(ignore)
        rebuildSounds()
    }

    fun ensureLoaded(soundId: String) {
        synchronized(lock) {
            if (released || ids.containsKey(soundId)) return
            val file = SoundBank.fileFor(app, soundId)
            if (!file.isFile || file.length() <= 0L) return
            val poolId = pool.load(file.absolutePath, 1)
            if (poolId == 0) return
            ids[soundId] = poolId
            poolToSound[poolId] = soundId
        }
    }

    fun play(cue: Cue, force: Boolean = false) {
        val id = TimeSenseStore.settings.value.slot(cue).selectedId
        if (cue != Cue.TICK) {
            stopCue(Cue.TICK)
        }
        playId(id, force)
    }

    fun playExclusive(soundId: String, force: Boolean = true) {
        if (released) return
        ensureLoaded(soundId)
        stopLast()
        playId(soundId, force)
    }

    fun playId(soundId: String, force: Boolean = false) {
        if (released) return
        if (!force && holdFocus && focusGate?.shouldPlay() == false) return
        synchronized(lock) {
            if (released) return
            val playable = if (isReady(soundId)) soundId else fallbackIfReady(soundId)
            if (playable == null) {
                pendingId.set(soundId)
                return
            }
            playNowLocked(playable)
        }
    }

    fun release() {
        synchronized(lock) {
            released = true
            focusGate?.abandon()
            TimeSenseStore.setAudioHeldOut(false)
            try {
                pool.release()
            } catch (_: RuntimeException) {
            }
            ids.clear()
            poolToSound.clear()
            ready.clear()
            pendingId.set(null)
            lastStreamByCue.fill(0)
        }
    }

    private fun rebuildSounds() {
        synchronized(lock) {
            if (released) return
            pendingId.set(null)
            ids.clear()
            poolToSound.clear()
            ready.clear()
            lastStreamByCue.fill(0)
            try {
                pool.release()
            } catch (_: RuntimeException) {
            }
            pool = buildPool()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                val soundId = synchronized(lock) { poolToSound[sampleId] } ?: return@setOnLoadCompleteListener
                if (status == 0) {
                    ready.add(soundId)
                    val waiting = pendingId.getAndSet(null)
                    if (waiting != null) {
                        playId(waiting, force = true)
                    }
                }
            }
            for (tone in BuiltinTones.all) {
                val resId = tone.resId ?: continue
                val poolId = pool.load(app, resId, 1)
                if (poolId != 0) {
                    ids[tone.id] = poolId
                    poolToSound[poolId] = tone.id
                }
            }
            for (custom in TimeSenseStore.settings.value.customs) {
                val file = SoundBank.fileFor(app, custom.id)
                if (!file.isFile || file.length() <= 0L) continue
                val poolId = pool.load(file.absolutePath, 1)
                if (poolId != 0) {
                    ids[custom.id] = poolId
                    poolToSound[poolId] = custom.id
                }
            }
        }
    }

    private fun buildPool(): SoundPool {
        val ignore = TimeSenseStore.settings.value.ignoreAudioFocus
        val attrs = if (ignore) AudioFocusGate.overlayAttrs() else AudioFocusGate.mediaAttrs()
        return SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()
    }

    private fun isReady(soundId: String): Boolean = ready.contains(soundId)

    private fun fallbackIfReady(soundId: String): String? {
        val fallback = fallbackId(soundId)
        return fallback.takeIf { isReady(it) }
    }

    private fun stopLast() {
        synchronized(lock) {
            if (released) return
            Cue.entries.forEach { stopCueLocked(it) }
        }
    }

    private fun stopCue(cue: Cue) {
        synchronized(lock) {
            if (released) return
            stopCueLocked(cue)
        }
    }

    private fun stopCueLocked(cue: Cue) {
        val streamId = lastStreamByCue[cue.ordinal]
        if (streamId == 0) return
        lastStreamByCue[cue.ordinal] = 0
        try {
            pool.stop(streamId)
        } catch (_: RuntimeException) {
        }
    }

    private fun playNowLocked(soundId: String) {
        if (released) return
        val poolId = ids[soundId] ?: ids[fallbackId(soundId)] ?: return
        val cue = cueFor(soundId)
        stopCueLocked(cue)
        try {
            val streamId = pool.play(
                poolId,
                1f,
                1f,
                /* priority */ priorityOf(cue),
                /* loop */ 0,
                /* rate */ 1f,
            )
            lastStreamByCue[cue.ordinal] = streamId
        } catch (_: RuntimeException) {
            // SoundPool already released on the service teardown path.
        }
    }

    private fun cueFor(soundId: String): Cue {
        return BuiltinTones.byId(soundId)?.cue
            ?: TimeSenseStore.settings.value.customs.firstOrNull { it.id == soundId }?.cue
            ?: Cue.TICK
    }

    private fun fallbackId(soundId: String): String {
        return cueFor(soundId).defaultSoundId
    }

    private fun priorityOf(cue: Cue): Int {
        return when (cue) {
            Cue.DING -> 3
            Cue.KATA -> 2
            Cue.TICK -> 1
        }
    }
}
