package com.cea.timesense.time

import android.os.SystemClock
import com.cea.timesense.TimeSenseStore
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs

/**
 * Minimal SNTP client (RFC 4330). Queries public NTP hosts over UDP/123,
 * computes the local clock offset, and stores it on [TimeSenseStore].
 *
 * On failure the previous offset is kept so the ticker never jumps back
 * to an uncorrected clock mid-run.
 */
object TimeSync {

    private const val NTP_PORT = 123
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_MODE_CLIENT = 3
    private const val NTP_VERSION = 3
    private const val OFFSET_1900_TO_1970_SECONDS = 2_208_988_800L
    private const val TIMEOUT_MS = 4_000

    private val HOSTS = listOf(
        "time.google.com",
        "pool.ntp.org",
        "time.cloudflare.com",
    )

    /**
     * Blocking network call. Run off the main thread.
     * @return true if a usable offset was applied.
     */
    fun syncIfStale(maxAgeMs: Long = 5 * 60 * 1000L): Boolean {
        val last = TimeSenseStore.lastSync.value
        if (last?.success == true &&
            System.currentTimeMillis() - last.syncedAtSystemMs < maxAgeMs
        ) {
            return true
        }
        return sync()
    }

    /**
     * Blocking network call. Run off the main thread.
     * @return true if a usable offset was applied.
     */
    fun sync(): Boolean {
        var lastError = "unreachable"
        var lastHost = ""
        for (host in HOSTS) {
            lastHost = host
            try {
                val sample = query(host)
                TimeSenseStore.applySuccessfulSync(
                    offset = sample.offsetMs,
                    rtt = sample.roundTripMs,
                    host = host,
                )
                return true
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        TimeSenseStore.applyFailedSync(lastError, lastHost)
        return false
    }

    private data class Sample(val offsetMs: Long, val roundTripMs: Long)

    private fun query(host: String): Sample {
        val address = InetAddress.getByName(host)
        DatagramSocket().use { socket ->
            socket.soTimeout = TIMEOUT_MS

            val request = ByteArray(NTP_PACKET_SIZE)
            request[0] = ((NTP_VERSION shl 3) or NTP_MODE_CLIENT).toByte()

            val requestTimeMs = System.currentTimeMillis()
            val requestElapsed = SystemClock.elapsedRealtime()
            writeNtpTimestamp(request, 40, requestTimeMs)

            socket.send(DatagramPacket(request, request.size, address, NTP_PORT))

            val response = ByteArray(NTP_PACKET_SIZE)
            socket.receive(DatagramPacket(response, response.size))
            val responseElapsed = SystemClock.elapsedRealtime()
            val responseTimeMs = requestTimeMs + (responseElapsed - requestElapsed)

            val originate = readNtpTimestamp(response, 24)
            val receive = readNtpTimestamp(response, 32)
            val transmit = readNtpTimestamp(response, 40)

            if (transmit == 0L) {
                throw IllegalStateException("empty NTP transmit timestamp")
            }

            // Standard SNTP offset / delay. Prefer the originate we actually sent
            // if the server echoed it; otherwise fall back to our local request time.
            val t1 = if (originate != 0L) originate else requestTimeMs
            val t2 = receive
            val t3 = transmit
            val t4 = responseTimeMs

            val offset = ((t2 - t1) + (t3 - t4)) / 2L
            val delay = (t4 - t1) - (t3 - t2)

            if (delay < 0 || delay > 5_000) {
                throw IllegalStateException("implausible NTP delay ${delay}ms")
            }
            if (abs(offset) > 24L * 60 * 60 * 1000) {
                throw IllegalStateException("implausible NTP offset ${offset}ms")
            }
            return Sample(offsetMs = offset, roundTripMs = delay.coerceAtLeast(0))
        }
    }

    private fun readNtpTimestamp(buffer: ByteArray, offset: Int): Long {
        val seconds = readU32(buffer, offset)
        val fraction = readU32(buffer, offset + 4)
        if (seconds == 0L && fraction == 0L) return 0L
        val unixSeconds = seconds - OFFSET_1900_TO_1970_SECONDS
        val millis = (fraction * 1000L) ushr 32
        return unixSeconds * 1000L + millis
    }

    private fun writeNtpTimestamp(buffer: ByteArray, offset: Int, unixMs: Long) {
        val seconds = unixMs / 1000L + OFFSET_1900_TO_1970_SECONDS
        val millis = unixMs % 1000L
        val fraction = (millis shl 32) / 1000L
        writeU32(buffer, offset, seconds)
        writeU32(buffer, offset + 4, fraction)
    }

    private fun readU32(buffer: ByteArray, offset: Int): Long {
        return ((buffer[offset].toLong() and 0xffL) shl 24) or
            ((buffer[offset + 1].toLong() and 0xffL) shl 16) or
            ((buffer[offset + 2].toLong() and 0xffL) shl 8) or
            (buffer[offset + 3].toLong() and 0xffL)
    }

    private fun writeU32(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = (value ushr 24).toByte()
        buffer[offset + 1] = (value ushr 16).toByte()
        buffer[offset + 2] = (value ushr 8).toByte()
        buffer[offset + 3] = value.toByte()
    }
}
