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
    private val ids = HashMap<Cue, Int>(3)
    private val loadedCount = AtomicInteger(0)
    private val pending = ConcurrentLinkedQueue<Cue>()
    private val expectedLoads = AtomicInteger(0)

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

    fun play(cue: Cue, force: Boolean = false) {
        if (released) return
        if (!force && holdFocus && focusGate?.shouldPlay() == false) return
        if (loadedCount.get() < expectedLoads.get()) {
            pending.add(cue)
            return
        }
        playNow(cue)
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
    }

    private fun rebuildSounds() {
        pending.clear()
        ids.clear()
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
        for (cue in Cue.entries) {
            val id = loadCue(cue) ?: continue
            ids[cue] = id
            loads += 1
        }
        expectedLoads.set(loads)
        if (loads == 0) {
            loadedCount.set(0)
        }
    }

    private fun loadCue(cue: Cue): Int? {
        val custom = SoundBank.fileFor(app, cue)
        val slot = TimeSenseStore.settings.value.slot(cue)
        if (slot.isCustom && custom.isFile && custom.length() > 0L) {
            val id = pool.load(custom.absolutePath, 1)
            if (id != 0) return id
        }
        return pool.load(app, cue.builtinRes, 1)
    }

    private fun buildPool(): SoundPool {
        val ignore = TimeSenseStore.settings.value.ignoreAudioFocus
        val attrs = if (ignore) AudioFocusGate.overlayAttrs() else AudioFocusGate.mediaAttrs()
        return SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(attrs)
            .build()
    }

    private fun flushPending() {
        if (loadedCount.get() < expectedLoads.get()) return
        while (true) {
            val cue = pending.poll() ?: break
            playNow(cue)
        }
    }

    private fun playNow(cue: Cue) {
        if (released) return
        val id = ids[cue] ?: return
        try {
            pool.play(id, 1f, 1f, /* priority */ 1, /* loop */ 0, /* rate */ 1f)
        } catch (_: RuntimeException) {
            // SoundPool already released on the service teardown path.
        }
    }
}
