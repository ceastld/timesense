package com.cea.timesense.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cea.timesense.TimeSenseStore
import com.cea.timesense.ui.theme.Amber
import com.cea.timesense.ui.theme.AmberDim
import com.cea.timesense.ui.theme.Charcoal
import com.cea.timesense.ui.theme.CharcoalRaised
import com.cea.timesense.ui.theme.Cream
import com.cea.timesense.ui.theme.Hairline
import com.cea.timesense.ui.theme.Muted
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun TimeSenseScreen(
    onToggle: (wantRunning: Boolean) -> Unit,
) {
    val running by TimeSenseStore.isRunning.collectAsState()
    val sync by TimeSenseStore.lastSync.collectAsState()
    var clock by remember { mutableStateOf(formatClock(TimeSenseStore.nowMillis())) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = TimeSenseStore.nowMillis()
            clock = formatClock(now)
            val toNext = 1000L - (now % 1000L)
            delay(toNext.coerceIn(16L, 1000L))
        }
    }

    val buttonColor by animateColorAsState(
        targetValue = if (running) CharcoalRaised else Amber,
        label = "playColor",
    )
    val buttonContent by animateColorAsState(
        targetValue = if (running) Cream else Charcoal,
        label = "playContent",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Charcoal)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Text(
                text = "时感",
                color = Amber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 10.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = clock,
                color = Cream,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                fontSize = 56.sp,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 60.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (running) "正在走时" else "静  止",
                color = if (running) Amber else Muted,
                fontSize = 13.sp,
                letterSpacing = 6.sp,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onToggle(!running) },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = buttonContent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .then(
                        if (running) {
                            Modifier.border(1.dp, Hairline, RoundedCornerShape(28.dp))
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    text = if (running) "停止" else "走时",
                    fontSize = 17.sp,
                    letterSpacing = 8.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(28.dp))
            SyncStatus(sync)
            Spacer(Modifier.height(28.dp))
            SoundHint()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SyncStatus(sync: TimeSenseStore.SyncInfo?) {
    val text = remember(sync) { formatSync(sync) }
    val color = when {
        sync == null -> Muted
        sync.success -> Muted
        else -> AmberDim
    }
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SoundHint() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HintCell("滴答", "每秒")
        HintCell("卡塔", "每分")
        HintCell("叮", "每时")
    }
}

@Composable
private fun HintCell(title: String, caption: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalRaised)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(title, color = Cream, fontSize = 14.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(2.dp))
        Text(caption, color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

private val CLOCK_FMT = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
private val DAY_FMT = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
private val HM_FMT = SimpleDateFormat("HH:mm", Locale.CHINA)

private fun formatClock(ms: Long): String = CLOCK_FMT.format(Date(ms))

private fun formatSync(sync: TimeSenseStore.SyncInfo?): String {
    if (sync == null) return "尚未校时  ·  使用系统时钟"
    val whenText = formatSyncWhen(sync.syncedAtSystemMs)
    val sign = if (sync.offsetMs >= 0) "+" else "−"
    val offset = "$sign${abs(sync.offsetMs)} ms"
    return if (sync.success) {
        "上次校时  $whenText  ·  偏移 $offset"
    } else {
        val extra = sync.error?.let { "（$it）" } ?: ""
        "校时未成功$extra  ·  约 15 分钟后重试\n仍使用${if (TimeSenseStore.offsetMs != 0L) "上次偏移 $offset" else "系统时钟"}"
    }
}

private fun formatSyncWhen(systemMs: Long): String {
    if (systemMs <= 0L) return "—"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = systemMs }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) HM_FMT.format(Date(systemMs)) else DAY_FMT.format(Date(systemMs))
}
