package com.cea.timesense.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun SettingsPanel(
    onPreview: (soundId: String) -> Unit,
    onClose: () -> Unit,
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
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .verticalScroll(rememberScrollState()),
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
        Spacer(Modifier.height(18.dp))
        Text("音频焦点", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("忽略音频焦点", color = Cream, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (settings.ignoreAudioFocus) {
                        "看视频或打电话时仍发声"
                    } else {
                        "其他应用占用音频时暂停发声"
                    },
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
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
        Spacer(Modifier.height(22.dp))
        Text("音效  ·  内置列表，自定义追加在后", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Cue.entries.forEach { cue ->
            val selected = settings.slot(cue).selectedId
            SoundList(
                cue = cue,
                options = settings.optionsFor(cue),
                selectedId = selected,
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
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SoundList(
    cue: Cue,
    options: List<SoundOption>,
    selectedId: String,
    onSelect: (SoundOption) -> Unit,
    onDelete: (SoundOption) -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Charcoal)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text("${cue.titleZh}  ·  ${cue.periodZh}", color = Cream, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        options.forEach { option ->
            val selected = option.id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioMark(selected)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = option.label,
                    color = if (selected) Amber else Cream,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (!option.builtin) {
                    Text(
                        text = "删除",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button) { onDelete(option) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "+ 添加自定义音频",
            color = Amber,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onAdd)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun RadioMark(selected: Boolean) {
    Spacer(
        modifier = Modifier
            .width(10.dp)
            .height(10.dp)
            .clip(CircleShape)
            .background(if (selected) Amber else Hairline)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, Muted, CircleShape)
                },
            ),
    )
}
