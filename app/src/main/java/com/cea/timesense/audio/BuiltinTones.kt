package com.cea.timesense.audio

import com.cea.timesense.R

data class SoundOption(
    val id: String,
    val cue: Cue,
    val label: String,
    val builtin: Boolean,
    val resId: Int? = null,
    val durationMs: Long = 1_000L,
)

object BuiltinTones {

    val all: List<SoundOption> = listOf(
        SoundOption("tick", Cue.TICK, "经典", true, R.raw.tick, 55),
        SoundOption("tick_soft", Cue.TICK, "轻柔", true, R.raw.tick_soft, 70),
        SoundOption("tick_crisp", Cue.TICK, "清脆", true, R.raw.tick_crisp, 38),
        SoundOption("tick_wood", Cue.TICK, "木质", true, R.raw.tick_wood, 62),
        SoundOption("kata", Cue.KATA, "经典", true, R.raw.kata, 115),
        SoundOption("kata_deep", Cue.KATA, "低沉", true, R.raw.kata_deep, 150),
        SoundOption("kata_knock", Cue.KATA, "叩击", true, R.raw.kata_knock, 100),
        SoundOption("kata_glass", Cue.KATA, "玻璃", true, R.raw.kata_glass, 95),
        SoundOption("ding", Cue.DING, "经典", true, R.raw.ding, 780),
        SoundOption("ding_low", Cue.DING, "低铃", true, R.raw.ding_low, 900),
        SoundOption("ding_chime", Cue.DING, "风铃", true, R.raw.ding_chime, 950),
        SoundOption("ding_bright", Cue.DING, "高亮", true, R.raw.ding_bright, 520),
    )

    fun forCue(cue: Cue): List<SoundOption> = all.filter { it.cue == cue }

    fun byId(id: String): SoundOption? = all.firstOrNull { it.id == id }

    fun durationMs(id: String): Long = byId(id)?.durationMs ?: 1_200L
}
