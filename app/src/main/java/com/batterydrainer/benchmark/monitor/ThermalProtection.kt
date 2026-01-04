package com.batterydrainer.benchmark.monitor

import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.HardwarePropertiesManager
import android.os.PowerManager
import android.util.Log
import com.batterydrainer.benchmark.data.ThermalState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temperature source - indicates where the temperature reading came from
 */
enum class TemperatureSource(val displayName: String, val icon: String) {
    BATTERY("Battery Sensor", "🔋"),
    HARDWARE_PROPERTIES("CPU Sensor", "🖥️"),
    SYSTEM_THERMAL("System Thermal", "📊"),
    UNKNOWN("Unknown", "❓")
}

/**
 * Thermal Protection System - Monitors device temperature and triggers safety cutoffs
 *
 * This is CRITICAL for preventing device damage during stress tests.
 *
 * Temperature sources (in order of preference):
 * 1. HardwarePropertiesManager (Android 7+) - Direct CPU/GPU temps, but requires DEVICE_POWER permission
 * 2. PowerManager Thermal API (Android 10+) - System thermal state (not exact temp)
 * 3. BatteryManager - Battery temperature (always available, but lags behind CPU)
 *
 * Note: Reading /sys/class/thermal/ files is blocked by SELinux on Android 7+ for regular apps.
 */
class ThermalProtection(private val context: Context) {

    companion object {
        private const val TAG = "ThermalProtection"

        // Temperature thresholds (Celsius)
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

    private val _temperatureSource = MutableStateFlow(TemperatureSource.UNKNOWN)
    val temperatureSource: StateFlow<TemperatureSource> = _temperatureSource.asStateFlow()

    private val _thermalState = MutableStateFlow(ThermalState.NONE)
    val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()

    private val _isInCooldown = MutableStateFlow(false)
    val isInCooldown: StateFlow<Boolean> = _isInCooldown.asStateFlow()

    private val _shouldPauseTest = MutableStateFlow(false)
    val shouldPauseTest: StateFlow<Boolean> = _shouldPauseTest.asStateFlow()

    private val _shouldStopTest = MutableStateFlow(false)
    val shouldStopTest: StateFlow<Boolean> = _shouldStopTest.asStateFlow()

    // Tracks if we've logged the source detection
    private var hasLoggedSourceDetection = false

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

    private val hardwarePropertiesManager: HardwarePropertiesManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? HardwarePropertiesManager
            } catch (e: Exception) {
                Log.d(TAG, "HardwarePropertiesManager not available: ${e.message}")
                null
            }
        } else {
            null
        }
    }

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
        hasLoggedSourceDetection = false
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                val (temp, source) = readTemperature()
                _currentTemperature.value = temp
                _temperatureSource.value = source

                if (_isEnabled.value) {
                    evaluateTemperature(temp)
                }

                delay(if (_isInCooldown.value) COOLDOWN_CHECK_INTERVAL else intervalMs)
            }
        }

        // Also use system thermal callbacks if available (Android 10+)
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

    /**
     * Read temperature from the best available source.
     * Returns a pair of (temperature, source).
     */
    private fun readTemperature(): Pair<Float, TemperatureSource> {
        // Try HardwarePropertiesManager first (Android 7+)
        // This gives actual CPU/GPU temps but requires special permission
        val hwTemp = readHardwarePropertiesTemperature()
        if (hwTemp > 0f) {
            logSourceOnce("Using HardwarePropertiesManager for CPU temperature")
            return Pair(hwTemp, TemperatureSource.HARDWARE_PROPERTIES)
        }

        // Fall back to battery temperature (always available)
        val batteryTemp = readBatteryTemperature()
        if (batteryTemp > 0f) {
            logSourceOnce("Using battery temperature (BatteryManager) - this is a proxy, actual CPU temp may be higher")
            return Pair(batteryTemp, TemperatureSource.BATTERY)
        }

        logSourceOnce("WARNING: No temperature source available!")
        return Pair(0f, TemperatureSource.UNKNOWN)
    }

    /**
     * Try to read CPU temperature from HardwarePropertiesManager (Android 7+).
     * This API requires android.permission.DEVICE_POWER which is signature|privileged,
     * so it will only work on system apps or with special grants.
     */
    private fun readHardwarePropertiesTemperature(): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return 0f
        }

        return try {
            val hpm = hardwarePropertiesManager ?: return 0f

            @Suppress("DEPRECATION")
            val cpuTemps = hpm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                HardwarePropertiesManager.TEMPERATURE_CURRENT
            )

            // Get the maximum CPU core temperature
            val maxTemp = cpuTemps.filter { it > 0 && it < 150 }.maxOrNull()

            if (maxTemp != null && maxTemp > 0) {
                Log.d(TAG, "HardwarePropertiesManager CPU temps: ${cpuTemps.toList()}, using max: $maxTemp")
                maxTemp
            } else {
                0f
            }
        } catch (e: SecurityException) {
            // Expected - requires DEVICE_POWER permission (signature|privileged)
            Log.d(TAG, "HardwarePropertiesManager requires DEVICE_POWER permission")
            0f
        } catch (e: Exception) {
            Log.d(TAG, "HardwarePropertiesManager failed: ${e.message}")
            0f
        }
    }

    /**
     * Read battery temperature from BatteryManager.
     * This is always available on all Android devices.
     * Battery temp typically lags 5-15°C behind CPU temp during heavy load.
     */
    private fun readBatteryTemperature(): Float {
        return try {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempCelsius = tempTenths / 10f

            // Sanity check
            if (tempCelsius in 10f..70f) {
                tempCelsius
            } else {
                Log.w(TAG, "Battery temperature out of range: $tempCelsius°C")
                0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read battery temperature: ${e.message}")
            0f
        }
    }

    private fun logSourceOnce(message: String) {
        if (!hasLoggedSourceDetection) {
            Log.i(TAG, message)
            hasLoggedSourceDetection = true
        }
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
     * Setup PowerManager thermal status listener (Android 10+).
     * This provides system-level thermal state but not exact temperature.
     */
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

                    Log.d(TAG, "System thermal status changed: $systemState")

                    // Update source if system thermal kicks in
                    if (systemState > ThermalState.NONE) {
                        _temperatureSource.value = TemperatureSource.SYSTEM_THERMAL
                    }

                    // Use system thermal state if it's more severe than our calculated state
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
                Log.d(TAG, "PowerManager thermal status listener registered")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register thermal status listener: ${e.message}")
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

    /**
     * Get description of the current temperature source
     */
    fun getTemperatureSourceDescription(): String {
        val source = _temperatureSource.value
        return when (source) {
            TemperatureSource.BATTERY ->
                "${source.icon} Battery temp (actual CPU may be higher)"
            TemperatureSource.HARDWARE_PROPERTIES ->
                "${source.icon} CPU temperature"
            TemperatureSource.SYSTEM_THERMAL ->
                "${source.icon} System thermal status"
            TemperatureSource.UNKNOWN ->
                "${source.icon} Temperature unavailable"
        }
    }
}
