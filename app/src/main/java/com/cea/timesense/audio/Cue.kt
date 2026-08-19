package com.cea.timesense.audio

import com.cea.timesense.R

enum class Cue {
    TICK,
    KATA,
    DING,
    ;

    val builtinRes: Int
        get() = when (this) {
            TICK -> R.raw.tick
            KATA -> R.raw.kata
            DING -> R.raw.ding
        }

    val titleZh: String
        get() = when (this) {
            TICK -> "滴答"
            KATA -> "卡塔"
            DING -> "叮"
        }

    val periodZh: String
        get() = when (this) {
            TICK -> "每秒"
            KATA -> "每分"
            DING -> "每时"
        }

    val prefsKey: String
        get() = "sound_${name.lowercase()}"
}
