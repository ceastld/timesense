package com.cea.timesense.time

import android.os.Process
import android.os.SystemClock
import com.cea.timesense.TimeSenseStore
import com.cea.timesense.audio.Cue
import java.util.TimeZone

/**
 * Fires [onSecond] as close as possible to each NTP-corrected wall-clock
 * second boundary. Sleeps against [SystemClock.elapsedRealtime] so a
 * sudden system-clock step mid-sleep cannot stretch or shrink the wait;
 * the *target* is always recomputed from [TimeSenseStore.nowMillis].
 *
 * The last few milliseconds spin instead of [Thread.sleep]: Android's
 * scheduler often overshoots a single long sleep by 10–40 ms, which
 * makes a metronome sound uneven.
 */
class ClockScheduler(
    private val onSecond: (wallClockMs: Long) -> Unit,
) {
    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            loop()
        }, "timesense-clock").apply {
            isDaemon = false
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        while (running) {
            val now = TimeSenseStore.nowMillis()
            val nextBoundary = (now / 1000L + 1L) * 1000L
            val delayMs = (nextBoundary - now).coerceAtLeast(1L)
            val wakeElapsed = SystemClock.elapsedRealtime() + delayMs
            if (!sleepUntil(wakeElapsed)) return
            if (!running) return
            onSecond(TimeSenseStore.nowMillis())
        }
    }

    private fun sleepUntil(elapsedDeadline: Long): Boolean {
        while (running) {
            val remain = elapsedDeadline - SystemClock.elapsedRealtime()
            if (remain <= 0L) return true
            if (remain > SPIN_MS) {
                try {
                    Thread.sleep(remain - SPIN_MS)
                } catch (_: InterruptedException) {
                    if (!running) return false
                }
            } else {
                while (running && elapsedDeadline - SystemClock.elapsedRealtime() > 0L) {
                    // Tight wait so onset stays on the second boundary.
                }
                return running
            }
        }
        return false
    }

    companion object {
        private const val SPIN_MS = 4L

        fun cueAt(wallClockMs: Long): Cue {
            val localMs = wallClockMs + TimeZone.getDefault().getOffset(wallClockMs)
            val totalSec = Math.floorDiv(localMs, 1000L)
            val second = Math.floorMod(totalSec, 60L).toInt()
            if (second != 0) return Cue.TICK
            val minute = Math.floorMod(Math.floorDiv(totalSec, 60L), 60L).toInt()
            return if (minute == 0) Cue.DING else Cue.KATA
        }
    }
}
