package com.cea.timesense.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cea.timesense.MainActivity
import com.cea.timesense.R
import com.cea.timesense.TimeSenseStore
import com.cea.timesense.audio.Cue
import com.cea.timesense.audio.SoundEngine
import com.cea.timesense.time.ClockScheduler
import com.cea.timesense.time.TimeSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Foreground media-playback service that keeps 时感 ticking with the
 * screen off: a partial wake lock + second-aligned [ClockScheduler].
 */
class TickService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var soundEngine: SoundEngine? = null
    private var scheduler: ClockScheduler? = null
    private var syncThread: Thread? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var syncRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
        soundEngine = SoundEngine(this, holdFocus = true)
        scope.launch {
            TimeSenseStore.settings
                .map { it.audioEpoch }
                .distinctUntilChanged()
                .drop(1)
                .collect { soundEngine?.reloadFromStore() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        TimeSenseStore.setRunning(true)
        startScheduler()
        startSyncLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        syncRunning = false
        syncThread?.interrupt()
        syncThread = null
        scheduler?.stop()
        scheduler = null
        soundEngine?.release()
        soundEngine = null
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
        TimeSenseStore.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startScheduler() {
        if (scheduler != null) return
        scheduler = ClockScheduler { wallClockMs ->
            onAlignedSecond(wallClockMs)
        }.also { it.start() }
    }

    private fun onAlignedSecond(wallClockMs: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = wallClockMs }
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val cue = when {
            minute == 0 && second == 0 -> Cue.DING
            second == 0 -> Cue.KATA
            else -> Cue.TICK
        }
        if (!TimeSenseStore.ticksHeldNow) {
            soundEngine?.play(cue)
        }
        updateNotification(wallClockMs)
    }

    private fun startSyncLoop() {
        if (syncThread?.isAlive == true) return
        syncRunning = true
        syncThread = Thread({
            while (syncRunning) {
                val ok = try {
                    TimeSync.syncIfStale()
                } catch (e: Exception) {
                    TimeSenseStore.applyFailedSync(e.message ?: "sync")
                    false
                }
                val waitMs = if (ok) SYNC_OK_MS else SYNC_RETRY_MS
                val deadline = System.currentTimeMillis() + waitMs
                while (syncRunning && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(1_000L)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            }
        }, "timesense-ntp").also { it.start() }
    }

    private fun startInForeground() {
        val notification = buildNotification(TimeSenseStore.nowMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(wallClockMs: Long) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(wallClockMs))
    }

    private fun buildNotification(wallClockMs: Long): Notification {
        val clock = formatHms(wallClockMs)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TickService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(clock)
            .setSubText(clock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.cea.timesense.action.STOP"
        const val CHANNEL_ID = "timesense.tick"
        const val NOTIFICATION_ID = 17
        private const val WAKELOCK_TAG = "com.cea.timesense:tick"
        private val SYNC_OK_MS = TimeUnit.HOURS.toMillis(12)
        private val SYNC_RETRY_MS = TimeUnit.MINUTES.toMillis(15)
    }

    private fun formatHms(wallClockMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = wallClockMs }
        return String.format(
            Locale.CHINA,
            "%02d:%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND),
        )
    }
}
