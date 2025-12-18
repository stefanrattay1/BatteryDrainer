package com.batterydrainer.benchmark.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import com.batterydrainer.benchmark.data.BatteryReading
import com.batterydrainer.benchmark.data.ThermalState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Monitors battery state, power consumption, and device temperature
 * 
 * Uses BatteryManager for detailed battery stats and thermal APIs for temperature monitoring.
 */
class BatteryMonitor(private val context: Context) {
    
    private val _currentReading = MutableStateFlow<BatteryReading?>(null)
    val currentReading: StateFlow<BatteryReading?> = _currentReading.asStateFlow()
    
    private val _thermalState = MutableStateFlow(ThermalState.NONE)
    val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()
    
    private val _batteryTemperature = MutableStateFlow(0f)
    val batteryTemperature: StateFlow<Float> = _batteryTemperature.asStateFlow()
    
    private val _cpuTemperature = MutableStateFlow<Float?>(null)
    val cpuTemperature: StateFlow<Float?> = _cpuTemperature.asStateFlow()
    
    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()
    
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    private var batteryReceiver: BroadcastReceiver? = null
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Readings history for the current session
    private val _readingsHistory = mutableListOf<BatteryReading>()
    val readingsHistory: List<BatteryReading> get() = _readingsHistory.toList()
    
    // Callback for thermal warnings
    var onThermalWarning: ((ThermalState) -> Unit)? = null
    
    /**
     * Start monitoring battery and temperature
     * @param intervalMs How often to take readings (default 1 second)
     */
    fun startMonitoring(intervalMs: Long = 1000) {
        registerBatteryReceiver()
        startPeriodicMonitoring(intervalMs)
        setupThermalCallbacks()
    }
    
    /**
     * Stop all monitoring
     */
    fun stopMonitoring() {
        unregisterBatteryReceiver()
        monitorJob?.cancel()
        monitorJob = null
    }
    
    /**
     * Clear the readings history
     */
    fun clearHistory() {
        _readingsHistory.clear()
    }
    
    /**
     * Get the current battery reading synchronously
     */
    fun getCurrentReadingSync(): BatteryReading {
        return takeBatteryReading()
    }
    
    /**
     * Get battery capacity in mAh (if available)
     */
    fun getBatteryCapacity(): Int? {
        return try {
            val powerProfile = Class.forName("com.android.internal.os.PowerProfile")
                .getConstructor(Context::class.java)
                .newInstance(context)
            
            val capacity = Class.forName("com.android.internal.os.PowerProfile")
                .getMethod("getBatteryCapacity")
                .invoke(powerProfile) as Double
            
            capacity.toInt()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { handleBatteryIntent(it) }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        
        context.registerReceiver(batteryReceiver, filter)
    }
    
    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Already unregistered
            }
        }
        batteryReceiver = null
    }
    
    private fun handleBatteryIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                _batteryTemperature.value = temp
            }
            Intent.ACTION_POWER_CONNECTED -> _isCharging.value = true
            Intent.ACTION_POWER_DISCONNECTED -> _isCharging.value = false
        }
    }
    
    private fun startPeriodicMonitoring(intervalMs: Long) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                val reading = takeBatteryReading()
                _currentReading.value = reading
                _readingsHistory.add(reading)
                
                // Update CPU temperature
                _cpuTemperature.value = readCpuTemperature()
                
                // Check thermal state
                updateThermalState()
                
                delay(intervalMs)
            }
        }
    }
    
    private fun takeBatteryReading(): BatteryReading {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPercent = (level * 100) / scale
        
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f
        
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        
        // Get instantaneous current (requires API 21+)
        val current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        
        // Get energy counter (requires API 21+, not all devices support)
        val energy = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            .takeIf { it != Long.MIN_VALUE }
        
        return BatteryReading(
            timestamp = System.currentTimeMillis(),
            level = batteryPercent,
            voltage = voltage,
            current = current,
            temperature = temperature,
            isCharging = isCharging,
            energyCounter = energy
        )
    }
    
    /**
     * Read CPU temperature from thermal zone files
     */
    private fun readCpuTemperature(): Float? {
        val thermalPaths = listOf(
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/platform/omap/omap_temp_sensor.0/temperature",
            "/sys/kernel/debug/tegra_thermal/temp_tj"
        )
        
        for (path in thermalPaths) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val temp = file.readText().trim().toFloatOrNull()
                    if (temp != null) {
                        // Most systems report in milli-celsius
                        return if (temp > 1000) temp / 1000f else temp
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return null
    }
    
    private fun setupThermalCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.addThermalStatusListener { status ->
                val state = when (status) {
                    PowerManager.THERMAL_STATUS_NONE -> ThermalState.NONE
                    PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
                    PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
                    PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
                    PowerManager.THERMAL_STATUS_CRITICAL -> ThermalState.CRITICAL
                    PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalState.EMERGENCY
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.SHUTDOWN
                    else -> ThermalState.NONE
                }
                
                _thermalState.value = state
                
                if (state >= ThermalState.SEVERE) {
                    onThermalWarning?.invoke(state)
                }
            }
        }
    }
    
    private fun updateThermalState() {
        // If API level doesn't support thermal callbacks, estimate from battery temp
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val temp = _batteryTemperature.value
            _thermalState.value = when {
                temp < 35f -> ThermalState.NONE
                temp < 40f -> ThermalState.LIGHT
                temp < 43f -> ThermalState.MODERATE
                temp < 45f -> ThermalState.SEVERE
                temp < 50f -> ThermalState.CRITICAL
                else -> ThermalState.EMERGENCY
            }
            
            if (_thermalState.value >= ThermalState.SEVERE) {
                onThermalWarning?.invoke(_thermalState.value)
            }
        }
    }
    
    /**
     * Get battery health string
     */
    fun getBatteryHealth(): String {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        
        return when (batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
    }
}
