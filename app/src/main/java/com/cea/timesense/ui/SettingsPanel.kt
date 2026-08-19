package com.cea.timesense.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cea.timesense.TimeSenseStore
import com.cea.timesense.audio.Cue
import com.cea.timesense.audio.SoundBank
import com.cea.timesense.audio.SoundOption
import com.cea.timesense.ui.theme.Amber
import com.cea.timesense.ui.theme.Charcoal
import com.cea.timesense.ui.theme.CharcoalRaised
import com.cea.timesense.ui.theme.Cream
import com.cea.timesense.ui.theme.Hairline
import com.cea.timesense.ui.theme.Muted

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsPanel(
    onPreview: (soundId: String) -> Unit,
    onClose: () -> Unit,
    focusCue: Cue? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings by TimeSenseStore.settings.collectAsState()
    var pendingCue by remember { mutableStateOf<Cue?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val cue = pendingCue
        pendingCue = null
        if (uri == null || cue == null) return@rememberLauncherForActivityResult
        val result = SoundBank.import(context, cue, uri)
        result.fold(
            onSuccess = { imported ->
                TimeSenseStore.addCustomSound(imported)
                onPreview(imported.id)
                Toast.makeText(context, "已加入 ${imported.name}", Toast.LENGTH_SHORT).show()
            },
            onFailure = { err ->
                Toast.makeText(context, err.message ?: "无法导入音频", Toast.LENGTH_SHORT).show()
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CharcoalRaised)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("设置", color = Amber, fontSize = 14.sp, letterSpacing = 6.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "完成",
                color = Cream,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("忽略音频焦点", color = Cream, fontSize = 14.sp)
                Text(
                    text = if (settings.ignoreAudioFocus) {
                        "看视频或打电话时仍发声"
                    } else {
                        "其他应用占用音频时暂停发声"
                    },
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Switch(
                checked = settings.ignoreAudioFocus,
                onCheckedChange = { TimeSenseStore.setIgnoreAudioFocus(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Charcoal,
                    checkedTrackColor = Amber,
                    uncheckedThumbColor = Cream,
                    uncheckedTrackColor = Hairline,
                    uncheckedBorderColor = Muted,
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Cue.entries.forEach { cue ->
                val selected = settings.slot(cue).selectedId
                SoundGrid(
                    cue = cue,
                    options = settings.optionsFor(cue),
                    selectedId = selected,
                    focused = cue == focusCue,
                    onSelect = { option ->
                        TimeSenseStore.setSelectedSound(cue, option.id)
                        onPreview(option.id)
                    },
                    onDelete = { option ->
                        TimeSenseStore.removeCustomSound(context, option.id)
                    },
                    onAdd = {
                        pendingCue = cue
                        picker.launch(arrayOf("audio/*"))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundGrid(
    cue: Cue,
    options: List<SoundOption>,
    selectedId: String,
    focused: Boolean,
    onSelect: (SoundOption) -> Unit,
    onDelete: (SoundOption) -> Unit,
    onAdd: () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) requester.bringIntoView()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .clip(RoundedCornerShape(12.dp))
            .background(Charcoal)
            .then(
                if (focused) {
                    Modifier.border(1.dp, Amber, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text("${cue.titleZh}  ·  ${cue.periodZh}", color = Cream, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        options.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { option ->
                    SoundChip(
                        option = option,
                        selected = option.id == selectedId,
                        onSelect = { onSelect(option) },
                        onDelete = if (option.builtin) null else ({ onDelete(option) }),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = "+ 自定义",
            color = Amber,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onAdd)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SoundChip(
    option: SoundOption,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Amber.copy(alpha = 0.16f) else CharcoalRaised)
            .border(1.dp, if (selected) Amber else Hairline, RoundedCornerShape(8.dp))
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = option.label,
            color = if (selected) Amber else Cream,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onDelete != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "×",
                color = Muted,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClick = onDelete)
                    .padding(horizontal = 2.dp),
            )
        }
    }
}
