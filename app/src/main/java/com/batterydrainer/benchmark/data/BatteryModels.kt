package com.batterydrainer.benchmark.data

/**
 * Represents a battery reading at a specific point in time
 */
data class BatteryReading(
    val timestamp: Long = System.currentTimeMillis(),
    val level: Int,                    // 0-100%
    val voltage: Int,                  // millivolts
    val current: Int,                  // microamps (negative = discharging)
    val temperature: Float,            // celsius
    val isCharging: Boolean,
    val energyCounter: Long? = null    // nanowatt-hours (API 21+)
)

/**
 * Represents thermal state of the device
 */
enum class ThermalState {
    NONE,       // No thermal issues
    LIGHT,      // Light throttling may occur
    MODERATE,   // Moderate throttling
    SEVERE,     // Severe throttling - should pause
    CRITICAL,   // Critical - must stop immediately
    EMERGENCY,  // Emergency shutdown imminent
    SHUTDOWN    // Device shutting down
}

/**
 * Real-time device stats
 */
data class DeviceStats(
    val batteryReading: BatteryReading,
    val thermalState: ThermalState,
    val cpuTemperature: Float?,        // celsius
    val gpuTemperature: Float?,        // celsius
    val cpuUsagePercent: Float,
    val memoryUsageMb: Long,
    val networkBytesPerSecond: Long
)

/**
 * Test session data
 */
data class TestSession(
    val id: String,
    val profileId: String,
    val profileName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val startBatteryLevel: Int,
    val endBatteryLevel: Int? = null,
    val readings: MutableList<BatteryReading> = mutableListOf(),
    val stressors: List<String>,
    val wasAborted: Boolean = false,
    val abortReason: String? = null
)

/**
 * Test configuration
 */
data class TestConfig(
    val targetBatteryDrop: Int = 10,       // Stop after X% battery drop
    val maxDurationMinutes: Int = 60,       // Maximum test duration
    val maxTemperatureCelsius: Float = 45f, // Thermal cutoff
    val samplingIntervalMs: Long = 1000,    // Battery reading interval
    val enableThermalProtection: Boolean = true
)
