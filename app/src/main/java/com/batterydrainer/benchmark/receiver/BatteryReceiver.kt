package com.batterydrainer.benchmark.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log

/**
 * Receives battery-related broadcasts
 */
class BatteryReceiver : BroadcastReceiver() {
    
    var onBatteryChanged: ((Int, Boolean, Float) -> Unit)? = null
    var onBatteryLow: (() -> Unit)? = null
    var onPowerConnected: (() -> Unit)? = null
    var onPowerDisconnected: (() -> Unit)? = null
    
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val percent = (level * 100) / scale
                
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                
                onBatteryChanged?.invoke(percent, isCharging, temp)
            }
            
            Intent.ACTION_BATTERY_LOW -> {
                Log.w("BatteryReceiver", "Battery low!")
                onBatteryLow?.invoke()
            }
            
            Intent.ACTION_POWER_CONNECTED -> {
                Log.i("BatteryReceiver", "Power connected")
                onPowerConnected?.invoke()
            }
            
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.i("BatteryReceiver", "Power disconnected")
                onPowerDisconnected?.invoke()
            }
        }
    }
}
