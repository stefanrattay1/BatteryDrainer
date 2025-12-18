package com.batterydrainer.benchmark.monitor

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.batterydrainer.benchmark.data.ThermalState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Thermal Protection System - Monitors device temperature and triggers safety cutoffs
 * 
 * This is CRITICAL for preventing device damage during stress tests.
 * Implements multiple temperature monitoring methods and auto-pause functionality.
 */
class ThermalProtection(private val context: Context) {
    
    // Temperature thresholds (Celsius)
    companion object {
        const val THRESHOLD_WARNING = 40f      // Start showing warnings
        const val THRESHOLD_THROTTLE = 43f     // Start reducing load
        const val THRESHOLD_PAUSE = 45f        // Pause test temporarily
        const val THRESHOLD_STOP = 48f         // Stop test completely
        const val THRESHOLD_EMERGENCY = 50f    // Emergency shutdown
        
        const val COOLDOWN_TARGET = 38f        // Resume when below this
        const val COOLDOWN_CHECK_INTERVAL = 5000L // Check every 5 seconds during cooldown
    }
    
    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _currentTemperature = MutableStateFlow(0f)
    val currentTemperature: StateFlow<Float> = _currentTemperature.asStateFlow()
    
    private val _thermalState = MutableStateFlow(ThermalState.NONE)
    val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()
    
    private val _isInCooldown = MutableStateFlow(false)
    val isInCooldown: StateFlow<Boolean> = _isInCooldown.asStateFlow()
    
    private val _shouldPauseTest = MutableStateFlow(false)
    val shouldPauseTest: StateFlow<Boolean> = _shouldPauseTest.asStateFlow()
    
    private val _shouldStopTest = MutableStateFlow(false)
    val shouldStopTest: StateFlow<Boolean> = _shouldStopTest.asStateFlow()
    
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Callbacks
    var onWarning: ((Float, ThermalState) -> Unit)? = null
    var onThrottle: ((Float) -> Unit)? = null
    var onPause: ((Float) -> Unit)? = null
    var onStop: ((Float) -> Unit)? = null
    var onCooldownComplete: (() -> Unit)? = null
    
    // Custom thresholds (can be modified by user in settings)
    var pauseThreshold = THRESHOLD_PAUSE
    var stopThreshold = THRESHOLD_STOP
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    /**
     * Enable or disable thermal protection
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            _shouldPauseTest.value = false
            _shouldStopTest.value = false
        }
    }
    
    /**
     * Start monitoring temperature
     */
    fun startMonitoring(intervalMs: Long = 2000) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                val temp = readMaxTemperature()
                _currentTemperature.value = temp
                
                if (_isEnabled.value) {
                    evaluateTemperature(temp)
                }
                
                delay(if (_isInCooldown.value) COOLDOWN_CHECK_INTERVAL else intervalMs)
            }
        }
        
        // Also use system thermal callbacks if available
        setupSystemThermalCallbacks()
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _isInCooldown.value = false
        _shouldPauseTest.value = false
        _shouldStopTest.value = false
    }
    
    /**
     * Reset thermal state (call when test is stopped)
     */
    fun reset() {
        _isInCooldown.value = false
        _shouldPauseTest.value = false
        _shouldStopTest.value = false
        _thermalState.value = ThermalState.NONE
    }
    
    private fun evaluateTemperature(temp: Float) {
        val previousState = _thermalState.value
        
        // Determine current thermal state
        val newState = when {
            temp >= THRESHOLD_EMERGENCY -> ThermalState.EMERGENCY
            temp >= stopThreshold -> ThermalState.CRITICAL
            temp >= pauseThreshold -> ThermalState.SEVERE
            temp >= THRESHOLD_THROTTLE -> ThermalState.MODERATE
            temp >= THRESHOLD_WARNING -> ThermalState.LIGHT
            else -> ThermalState.NONE
        }
        
        _thermalState.value = newState
        
        // Handle state changes
        when {
            temp >= THRESHOLD_EMERGENCY -> {
                _shouldStopTest.value = true
                _shouldPauseTest.value = true
                onStop?.invoke(temp)
            }
            
            temp >= stopThreshold -> {
                _shouldStopTest.value = true
                _shouldPauseTest.value = true
                if (previousState < ThermalState.CRITICAL) {
                    onStop?.invoke(temp)
                }
            }
            
            temp >= pauseThreshold -> {
                _shouldPauseTest.value = true
                _isInCooldown.value = true
                if (previousState < ThermalState.SEVERE) {
                    onPause?.invoke(temp)
                }
            }
            
            temp >= THRESHOLD_THROTTLE -> {
                if (previousState < ThermalState.MODERATE) {
                    onThrottle?.invoke(temp)
                }
            }
            
            temp >= THRESHOLD_WARNING -> {
                if (previousState < ThermalState.LIGHT) {
                    onWarning?.invoke(temp, newState)
                }
            }
        }
        
        // Check for cooldown completion
        if (_isInCooldown.value && temp < COOLDOWN_TARGET) {
            _isInCooldown.value = false
            _shouldPauseTest.value = false
            onCooldownComplete?.invoke()
        }
    }
    
    /**
     * Read the maximum temperature from all available thermal zones
     */
    private fun readMaxTemperature(): Float {
        val temperatures = mutableListOf<Float>()
        
        // Read battery temperature from intent
        val batteryTemp = readBatteryTemperature()
        if (batteryTemp > 0) temperatures.add(batteryTemp)
        
        // Read from thermal zones
        for (i in 0..15) {
            val paths = listOf(
                "/sys/devices/virtual/thermal/thermal_zone$i/temp",
                "/sys/class/thermal/thermal_zone$i/temp"
            )
            
            for (path in paths) {
                try {
                    val file = File(path)
                    if (file.exists() && file.canRead()) {
                        val temp = file.readText().trim().toFloatOrNull()
                        if (temp != null && temp > 0) {
                            // Convert from milli-celsius if needed
                            val normalizedTemp = if (temp > 1000) temp / 1000f else temp
                            if (normalizedTemp in 15f..100f) { // Sanity check
                                temperatures.add(normalizedTemp)
                            }
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        }
        
        // Read from CPU temp paths
        val cpuTempPaths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/devices/platform/omap/omap_temp_sensor.0/temperature"
        )
        
        for (path in cpuTempPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val temp = file.readText().trim().toFloatOrNull()
                    if (temp != null && temp in 15f..100f) {
                        temperatures.add(temp)
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return temperatures.maxOrNull() ?: batteryTemp
    }
    
    private fun readBatteryTemperature(): Float {
        return try {
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val temp = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            temp / 10f
        } catch (e: Exception) {
            0f
        }
    }
    
    private fun setupSystemThermalCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                powerManager.addThermalStatusListener { status ->
                    val systemState = when (status) {
                        PowerManager.THERMAL_STATUS_NONE -> ThermalState.NONE
                        PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
                        PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
                        PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
                        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalState.CRITICAL
                        PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalState.EMERGENCY
                        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.SHUTDOWN
                        else -> ThermalState.NONE
                    }
                    
                    // Use system thermal state if it's more severe
                    if (systemState > _thermalState.value) {
                        _thermalState.value = systemState
                        
                        when (systemState) {
                            ThermalState.SEVERE -> {
                                _shouldPauseTest.value = true
                                _isInCooldown.value = true
                                onPause?.invoke(_currentTemperature.value)
                            }
                            ThermalState.CRITICAL, ThermalState.EMERGENCY, ThermalState.SHUTDOWN -> {
                                _shouldStopTest.value = true
                                _shouldPauseTest.value = true
                                onStop?.invoke(_currentTemperature.value)
                            }
                            else -> {}
                        }
                    }
                }
            } catch (e: Exception) {
                // Thermal status listener not supported
            }
        }
    }
    
    /**
     * Get a user-friendly description of the current thermal state
     */
    fun getThermalStateDescription(): String {
        return when (_thermalState.value) {
            ThermalState.NONE -> "Normal - Device is cool"
            ThermalState.LIGHT -> "Warm - Slightly elevated temperature"
            ThermalState.MODERATE -> "Hot - Consider reducing load"
            ThermalState.SEVERE -> "Very Hot - Test paused for cooling"
            ThermalState.CRITICAL -> "Critical - Test stopped for safety"
            ThermalState.EMERGENCY -> "Emergency - Device overheating!"
            ThermalState.SHUTDOWN -> "Shutdown imminent!"
        }
    }
    
    /**
     * Get recommended action based on thermal state
     */
    fun getRecommendedAction(): String {
        return when (_thermalState.value) {
            ThermalState.NONE -> "All systems normal"
            ThermalState.LIGHT -> "Continue monitoring"
            ThermalState.MODERATE -> "Consider reducing stress levels"
            ThermalState.SEVERE -> "Waiting for device to cool down..."
            ThermalState.CRITICAL -> "Remove device from hot environment"
            ThermalState.EMERGENCY -> "Stop all activity immediately!"
            ThermalState.SHUTDOWN -> "Device protection activated"
        }
    }
}
