package com.cea.timesense.audio

enum class Cue {
    TICK,
    KATA,
    DING,
    ;

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
        get() = "selected_${name.lowercase()}"

    val defaultSoundId: String
        get() = name.lowercase()
}
