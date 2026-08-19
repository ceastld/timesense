package com.cea.timesense

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.cea.timesense.service.TickService
import com.cea.timesense.time.TimeSync
import com.cea.timesense.ui.TimeSenseScreen
import com.cea.timesense.ui.theme.TimeSenseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Foreground-service notification is the reason we ask; start either way
        // so Play is never blocked by a denied banner permission.
        startTicker()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        lifecycleScope.launch(Dispatchers.IO) {
            TimeSync.syncIfStale()
        }

        setContent {
            TimeSenseTheme {
                TimeSenseScreen(onToggle = { wantRunning ->
                    if (wantRunning) requestNotifyThenStart() else stopTicker()
                })
            }
        }
    }

    private fun requestNotifyThenStart() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startTicker()
    }

    private fun startTicker() {
        val intent = Intent(this, TickService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopTicker() {
        stopService(Intent(this, TickService::class.java))
    }
}
