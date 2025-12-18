package com.batterydrainer.benchmark.stressors

import kotlinx.coroutines.flow.StateFlow

/**
 * Base interface for all battery stressor modules
 */
interface Stressor {
    /** Unique identifier for this stressor */
    val id: String
    
    /** Human-readable name */
    val name: String
    
    /** Whether this stressor is currently running */
    val isRunning: StateFlow<Boolean>
    
    /** Current load level (0-100) */
    val currentLoad: StateFlow<Int>
    
    /**
     * Start the stressor with the given intensity
     * @param intensity Load level from 0-100
     */
    suspend fun start(intensity: Int)
    
    /**
     * Stop the stressor
     */
    suspend fun stop()
    
    /**
     * Update intensity while running
     * @param intensity New load level from 0-100
     */
    suspend fun setIntensity(intensity: Int)
    
    /**
     * Check if this stressor is available on the device
     */
    fun isAvailable(): Boolean
    
    /**
     * Get current power consumption estimate in mA
     */
    fun getEstimatedPowerDraw(): Int
}

/**
 * Result of a stressor availability check
 */
data class StressorAvailability(
    val stressorId: String,
    val isAvailable: Boolean,
    val reason: String? = null
)
