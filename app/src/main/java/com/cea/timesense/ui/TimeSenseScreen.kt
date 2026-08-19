package com.cea.timesense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cea.timesense.TimeSenseStore
import com.cea.timesense.audio.BuiltinTones
import com.cea.timesense.audio.Cue
import com.cea.timesense.audio.SoundEngine
import com.cea.timesense.ui.theme.Amber
import com.cea.timesense.ui.theme.AmberDim
import com.cea.timesense.ui.theme.Charcoal
import com.cea.timesense.ui.theme.CharcoalRaised
import com.cea.timesense.ui.theme.Cream
import com.cea.timesense.ui.theme.Hairline
import com.cea.timesense.ui.theme.Muted
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimeSenseScreen(
    onToggle: (wantRunning: Boolean) -> Unit,
) {
    val running by TimeSenseStore.isRunning.collectAsState()
    val sync by TimeSenseStore.lastSync.collectAsState()
    val settings by TimeSenseStore.settings.collectAsState()
    val heldOut by TimeSenseStore.audioHeldOut.collectAsState()
    var clock by remember { mutableStateOf(formatClock(TimeSenseStore.nowMillis())) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsFocusCue by remember { mutableStateOf<Cue?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewEngine = remember { SoundEngine(context.applicationContext, holdFocus = false) }
    var previewHoldJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(previewEngine) {
        onDispose {
            previewHoldJob?.cancel()
            TimeSenseStore.setTicksHeld(false)
            previewEngine.release()
        }
    }
    LaunchedEffect(settings.ignoreAudioFocus) {
        previewEngine.reloadFromStore()
    }
    LaunchedEffect(showSettings) {
        previewHoldJob?.cancel()
        TimeSenseStore.setTicksHeld(showSettings)
    }

    fun previewSound(soundId: String) {
        previewEngine.playExclusive(soundId, force = true)
        if (showSettings) return
        previewHoldJob?.cancel()
        previewHoldJob = scope.launch {
            TimeSenseStore.setTicksHeld(true)
            delay(BuiltinTones.durationMs(soundId) + 80L)
            TimeSenseStore.setTicksHeld(false)
        }
    }

    fun openSettings(cue: Cue? = null) {
        settingsFocusCue = cue
        showSettings = true
    }

    fun closeSettings() {
        showSettings = false
        settingsFocusCue = null
    }

    BackHandler(enabled = showSettings) {
        closeSettings()
    }

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
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "时感",
                    color = Amber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 10.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                Text(
                    text = if (showSettings) "返回" else "设置",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            if (showSettings) closeSettings() else openSettings()
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            if (showSettings) {
                Spacer(Modifier.height(20.dp))
                SettingsPanel(
                    onPreview = { soundId -> previewSound(soundId) },
                    onClose = { closeSettings() },
                    focusCue = settingsFocusCue,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.height(16.dp))
            } else {
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
                    text = when {
                        running && heldOut -> "走时静音"
                        running -> "正在走时"
                        else -> "静  止"
                    },
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
                SoundHint(
                    settings = settings,
                    onPreview = { cue -> previewSound(settings.slot(cue).selectedId) },
                    onOpenSettings = { cue -> openSettings(cue) },
                )
                Spacer(Modifier.height(24.dp))
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundHint(
    settings: TimeSenseStore.Settings,
    onPreview: (Cue) -> Unit,
    onOpenSettings: (Cue) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cue.entries.forEach { cue ->
                val slot = settings.slot(cue)
                HintCell(
                    title = cue.titleZh,
                    caption = slot.label(settings.customs),
                    onClick = { onPreview(cue) },
                    onLongClick = { onOpenSettings(cue) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "点按试听  ·  长按设置",
            color = Muted,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HintCell(
    title: String,
    caption: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "试听$title",
                onLongClickLabel = "打开${title}设置",
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(CharcoalRaised)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            color = Cream,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = caption,
            color = Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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
