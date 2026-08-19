package com.cea.timesense.audio

import android.content.Context
import android.media.SoundPool
import com.cea.timesense.TimeSenseStore
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Low-latency playback for the three 时感 cues via [SoundPool].
 *
 * [holdFocus] is true in [com.cea.timesense.service.TickService]: we then
 * honor the ignore-audio-focus setting. Preview playback never takes focus.
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
    private val loadedCount = AtomicInteger(0)
    private val pending = ConcurrentLinkedQueue<String>()
    private val expectedLoads = AtomicInteger(0)

    @Volatile
    private var lastStreamId: Int = 0

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
        if (released || ids.containsKey(soundId)) return
        val file = SoundBank.fileFor(app, soundId)
        if (!file.isFile || file.length() <= 0L) return
        val poolId = pool.load(file.absolutePath, 1)
        if (poolId == 0) return
        ids[soundId] = poolId
        expectedLoads.incrementAndGet()
    }

    fun play(cue: Cue, force: Boolean = false) {
        val id = TimeSenseStore.settings.value.slot(cue).selectedId
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
        if (loadedCount.get() < expectedLoads.get()) {
            pending.add(soundId)
            return
        }
        playNow(soundId)
    }

    fun release() {
        released = true
        focusGate?.abandon()
        TimeSenseStore.setAudioHeldOut(false)
        try {
            pool.release()
        } catch (_: RuntimeException) {
        }
        ids.clear()
        pending.clear()
        lastStreamId = 0
    }

    private fun rebuildSounds() {
        pending.clear()
        ids.clear()
        lastStreamId = 0
        loadedCount.set(0)
        try {
            pool.release()
        } catch (_: RuntimeException) {
        }
        pool = buildPool()
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                loadedCount.incrementAndGet()
                flushPending()
            } else {
                expectedLoads.updateAndGet { n -> (n - 1).coerceAtLeast(0) }
                flushPending()
            }
        }
        var loads = 0
        for (tone in BuiltinTones.all) {
            val resId = tone.resId ?: continue
            val poolId = pool.load(app, resId, 1)
            if (poolId != 0) {
                ids[tone.id] = poolId
                loads += 1
            }
        }
        for (custom in TimeSenseStore.settings.value.customs) {
            val file = SoundBank.fileFor(app, custom.id)
            if (!file.isFile || file.length() <= 0L) continue
            val poolId = pool.load(file.absolutePath, 1)
            if (poolId != 0) {
                ids[custom.id] = poolId
                loads += 1
            }
        }
        expectedLoads.set(loads)
        if (loads == 0) {
            loadedCount.set(0)
        }
    }

    private fun buildPool(): SoundPool {
        val ignore = TimeSenseStore.settings.value.ignoreAudioFocus
        val attrs = if (ignore) AudioFocusGate.overlayAttrs() else AudioFocusGate.mediaAttrs()
        return SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
    }

    private fun flushPending() {
        if (loadedCount.get() < expectedLoads.get()) return
        while (true) {
            val id = pending.poll() ?: break
            playNow(id)
        }
    }

    private fun stopLast() {
        val streamId = lastStreamId
        if (streamId == 0) return
        lastStreamId = 0
        try {
            pool.stop(streamId)
        } catch (_: RuntimeException) {
        }
    }

    private fun playNow(soundId: String) {
        if (released) return
        val poolId = ids[soundId] ?: ids[fallbackId(soundId)] ?: return
        try {
            lastStreamId = pool.play(poolId, 1f, 1f, /* priority */ 1, /* loop */ 0, /* rate */ 1f)
        } catch (_: RuntimeException) {
            // SoundPool already released on the service teardown path.
        }
    }

    private fun fallbackId(soundId: String): String {
        val cue = BuiltinTones.byId(soundId)?.cue
            ?: TimeSenseStore.settings.value.customs.firstOrNull { it.id == soundId }?.cue
        return cue?.defaultSoundId ?: "tick"
    }
}
