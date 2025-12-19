package com.batterydrainer.benchmark.stressors

import android.content.Context
import com.batterydrainer.benchmark.data.StressProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages all stressor modules and coordinates their operation
 */
class StressorManager(private val context: Context) {
    
    val cpuStressor = CpuStressor(context)
    val gpuStressor = GpuStressor(context)
    val networkStressor = NetworkStressor(context)
    val sensorStressor = SensorStressor(context)
    
    private val _activeProfile = MutableStateFlow<StressProfile?>(null)
    val activeProfile: StateFlow<StressProfile?> = _activeProfile.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _totalEstimatedPowerDraw = MutableStateFlow(0)
    val totalEstimatedPowerDraw: StateFlow<Int> = _totalEstimatedPowerDraw.asStateFlow()
    
    /**
     * Get list of all available stressors
     */
    fun getAllStressors(): List<Stressor> {
        return listOf(cpuStressor, gpuStressor, networkStressor, sensorStressor)
    }
    
    /**
     * Check availability of all stressors
     */
    fun checkAvailability(): List<StressorAvailability> {
        return getAllStressors().map { stressor ->
            StressorAvailability(
                stressorId = stressor.id,
                isAvailable = stressor.isAvailable(),
                reason = if (!stressor.isAvailable()) "Not available on this device" else null
            )
        }
    }
    
    /**
     * Start all stressors according to a profile
     */
    suspend fun startProfile(profile: StressProfile): Boolean {
        if (_isRunning.value) {
            stopAll()
        }
        
        _activeProfile.value = profile
        var startedAny = false
        
        // Start each stressor with its configured intensity
        if (profile.cpuLoad > 0) {
            cpuStressor.start(profile.cpuLoad)
            startedAny = startedAny || cpuStressor.isRunning.value
        }
        
        if (profile.gpuLoad > 0) {
            gpuStressor.start(profile.gpuLoad)
            startedAny = startedAny || gpuStressor.isRunning.value
        }
        
        if (profile.networkLoad > 0) {
            networkStressor.start(profile.networkLoad)
            startedAny = startedAny || networkStressor.isRunning.value
        }
        
        // Configure and start sensor stressor
        if (profile.gpsEnabled || profile.flashlightEnabled || profile.vibrateEnabled) {
            sensorStressor.configure(
                gps = profile.gpsEnabled,
                flashlight = profile.flashlightEnabled,
                vibration = profile.vibrateEnabled
            )
            sensorStressor.start(100) // Full intensity since we manually configured
            startedAny = startedAny || sensorStressor.isRunning.value
        }
        
        _isRunning.value = startedAny
        if (!startedAny) {
            _activeProfile.value = null
            _totalEstimatedPowerDraw.value = 0
            return false
        }

        updatePowerEstimate()
        return true
    }
    
    /**
     * Start individual stressors manually
     */
    suspend fun startCpu(intensity: Int) {
        cpuStressor.start(intensity)
        _isRunning.value = true
        updatePowerEstimate()
    }
    
    suspend fun startGpu(intensity: Int) {
        gpuStressor.start(intensity)
        _isRunning.value = true
        updatePowerEstimate()
    }
    
    suspend fun startNetwork(intensity: Int) {
        networkStressor.start(intensity)
        _isRunning.value = true
        updatePowerEstimate()
    }
    
    suspend fun startSensors(gps: Boolean = false, flashlight: Boolean = false, vibration: Boolean = false) {
        sensorStressor.configure(gps, flashlight, vibration)
        sensorStressor.start(100)
        _isRunning.value = true
        updatePowerEstimate()
    }
    
    /**
     * Stop all stressors
     */
    suspend fun stopAll() {
        cpuStressor.stop()
        gpuStressor.stop()
        networkStressor.stop()
        sensorStressor.stop()
        
        _activeProfile.value = null
        _isRunning.value = false
        _totalEstimatedPowerDraw.value = 0
    }
    
    /**
     * Stop a specific stressor
     */
    suspend fun stopStressor(stressorId: String) {
        when (stressorId) {
            "cpu" -> cpuStressor.stop()
            "gpu" -> gpuStressor.stop()
            "network" -> networkStressor.stop()
            "sensor" -> sensorStressor.stop()
        }
        
        // Check if any stressors are still running
        val anyRunning = getAllStressors().any { it.isRunning.value }
        _isRunning.value = anyRunning
        
        if (!anyRunning) {
            _activeProfile.value = null
        }
        
        updatePowerEstimate()
    }
    
    /**
     * Update intensity of a running stressor
     */
    suspend fun setStressorIntensity(stressorId: String, intensity: Int) {
        when (stressorId) {
            "cpu" -> cpuStressor.setIntensity(intensity)
            "gpu" -> gpuStressor.setIntensity(intensity)
            "network" -> networkStressor.setIntensity(intensity)
            "sensor" -> sensorStressor.setIntensity(intensity)
        }
        updatePowerEstimate()
    }
    
    private fun updatePowerEstimate() {
        _totalEstimatedPowerDraw.value = getAllStressors().sumOf { 
            if (it.isRunning.value) it.getEstimatedPowerDraw() else 0 
        }
    }
    
    /**
     * Get status of all stressors
     */
    fun getStatus(): Map<String, StressorStatus> {
        return getAllStressors().associate { stressor ->
            stressor.id to StressorStatus(
                id = stressor.id,
                name = stressor.name,
                isRunning = stressor.isRunning.value,
                currentLoad = stressor.currentLoad.value,
                estimatedPowerDraw = stressor.getEstimatedPowerDraw(),
                isAvailable = stressor.isAvailable()
            )
        }
    }
}

/**
 * Status of a single stressor
 */
data class StressorStatus(
    val id: String,
    val name: String,
    val isRunning: Boolean,
    val currentLoad: Int,
    val estimatedPowerDraw: Int,
    val isAvailable: Boolean
)
