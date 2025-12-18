package com.batterydrainer.benchmark.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives thermal status broadcasts
 */
class ThermalReceiver : BroadcastReceiver() {
    
    var onThermalEvent: ((Int) -> Unit)? = null
    
    override fun onReceive(context: Context?, intent: Intent?) {
        val thermalStatus = intent?.getIntExtra("thermal_status", -1) ?: -1
        Log.w("ThermalReceiver", "Thermal event received: status=$thermalStatus")
        onThermalEvent?.invoke(thermalStatus)
    }
}
