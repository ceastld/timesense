package com.cea.timesense.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.cea.timesense.R

/**
 * Low-latency playback for the three 时感 cues via [SoundPool]
 * on [AudioAttributes.USAGE_MEDIA] (STREAM_MUSIC). System media
 * volume is respected; we never duck or take audio focus.
 */
class SoundEngine(context: Context) {

    enum class Cue { TICK, KATA, DING }

    private val pool: SoundPool
    private val ids = HashMap<Cue, Int>(3)

    @Volatile
    private var released = false

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(attrs)
            .build()
        val app = context.applicationContext
        ids[Cue.TICK] = pool.load(app, R.raw.tick, 1)
        ids[Cue.KATA] = pool.load(app, R.raw.kata, 1)
        ids[Cue.DING] = pool.load(app, R.raw.ding, 1)
    }

    fun play(cue: Cue) {
        if (released) return
        val id = ids[cue] ?: return
        try {
            pool.play(id, 1f, 1f, /* priority */ 1, /* loop */ 0, /* rate */ 1f)
        } catch (_: RuntimeException) {
            // SoundPool already released on the service teardown path.
        }
    }

    fun release() {
        released = true
        try {
            pool.release()
        } catch (_: RuntimeException) {
        }
        ids.clear()
    }
}
