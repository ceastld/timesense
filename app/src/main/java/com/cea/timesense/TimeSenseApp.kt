package com.cea.timesense

import android.app.Application

class TimeSenseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TimeSenseStore.init(this)
    }
}
