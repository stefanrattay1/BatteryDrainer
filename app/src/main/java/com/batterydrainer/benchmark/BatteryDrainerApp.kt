package com.batterydrainer.benchmark

import android.app.Application

class BatteryDrainerApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: BatteryDrainerApp
            private set
    }
}
