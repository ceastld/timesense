package com.cea.timesense.audio

import com.cea.timesense.R

data class SoundOption(
    val id: String,
    val cue: Cue,
    val label: String,
    val builtin: Boolean,
    val resId: Int? = null,
)

object BuiltinTones {

    val all: List<SoundOption> = listOf(
        SoundOption("tick", Cue.TICK, "经典", true, R.raw.tick),
        SoundOption("tick_soft", Cue.TICK, "轻柔", true, R.raw.tick_soft),
        SoundOption("tick_crisp", Cue.TICK, "清脆", true, R.raw.tick_crisp),
        SoundOption("tick_wood", Cue.TICK, "木质", true, R.raw.tick_wood),
        SoundOption("kata", Cue.KATA, "经典", true, R.raw.kata),
        SoundOption("kata_deep", Cue.KATA, "低沉", true, R.raw.kata_deep),
        SoundOption("kata_knock", Cue.KATA, "叩击", true, R.raw.kata_knock),
        SoundOption("kata_glass", Cue.KATA, "玻璃", true, R.raw.kata_glass),
        SoundOption("ding", Cue.DING, "经典", true, R.raw.ding),
        SoundOption("ding_low", Cue.DING, "低铃", true, R.raw.ding_low),
        SoundOption("ding_chime", Cue.DING, "风铃", true, R.raw.ding_chime),
        SoundOption("ding_bright", Cue.DING, "高亮", true, R.raw.ding_bright),
    )

    fun forCue(cue: Cue): List<SoundOption> = all.filter { it.cue == cue }

    fun byId(id: String): SoundOption? = all.firstOrNull { it.id == id }

    fun defaultId(cue: Cue): String = cue.name.lowercase()
}
