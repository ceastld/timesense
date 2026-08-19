package com.cea.timesense.time

import android.os.Process
import android.os.SystemClock
import com.cea.timesense.TimeSenseStore

/**
 * Fires [onSecond] as close as possible to each NTP-corrected wall-clock
 * second boundary. Sleeps against [SystemClock.elapsedRealtime] so a
 * sudden system-clock step mid-sleep cannot stretch or shrink the wait;
 * the *target* is always recomputed from [TimeSenseStore.nowMillis].
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
            try {
                Thread.sleep(remain)
            } catch (_: InterruptedException) {
                return running && elapsedDeadline - SystemClock.elapsedRealtime() <= 0L
            }
        }
        return false
    }
}
