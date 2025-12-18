package com.batterydrainer.benchmark.stressors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sensor Stressor - Activates power-hungry sensors
 * 
 * Controls:
 * - GPS (high accuracy, frequent updates)
 * - Flashlight/Torch
 * - Vibration motor
 * 
 * These are some of the highest power consumers on a mobile device.
 */
class SensorStressor(private val context: Context) : Stressor {
    
    override val id = "sensor"
    override val name = "Sensor Burner"
    
    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _currentLoad = MutableStateFlow(0)
    override val currentLoad: StateFlow<Int> = _currentLoad.asStateFlow()
    
    // Individual sensor states
    val gpsEnabled = MutableStateFlow(false)
    val flashlightEnabled = MutableStateFlow(false)
    val vibrationEnabled = MutableStateFlow(false)
    val lastLocation = MutableStateFlow<Location?>(null)
    
    private val shouldRun = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // System services
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var fusedLocationClient: FusedLocationProviderClient? = null
    
    private var cameraId: String? = null
    private var vibrationJob: Job? = null
    private var locationCallback: LocationCallback? = null
    
    // Configuration
    private var enableGps = false
    private var enableFlashlight = false
    private var enableVibration = false
    
    override suspend fun start(intensity: Int) {
        if (_isRunning.value) return
        
        shouldRun.set(true)
        _isRunning.value = true
        _currentLoad.value = intensity.coerceIn(0, 100)
        
        // Determine which sensors to enable based on intensity
        configureFromIntensity(intensity)
        
        if (enableFlashlight) startFlashlight()
        if (enableVibration) startVibration()
        if (enableGps) startGps()
    }
    
    override suspend fun stop() {
        shouldRun.set(false)
        _isRunning.value = false
        _currentLoad.value = 0
        
        stopFlashlight()
        stopVibration()
        stopGps()
        
        gpsEnabled.value = false
        flashlightEnabled.value = false
        vibrationEnabled.value = false
    }
    
    override suspend fun setIntensity(intensity: Int) {
        val newIntensity = intensity.coerceIn(0, 100)
        _currentLoad.value = newIntensity
        
        if (_isRunning.value) {
            // Reconfigure based on new intensity
            stop()
            start(newIntensity)
        }
    }
    
    override fun isAvailable(): Boolean {
        return hasFlashlight() || hasVibrator() || hasGps()
    }
    
    override fun getEstimatedPowerDraw(): Int {
        var power = 0
        if (gpsEnabled.value) power += 150      // GPS: ~150mA
        if (flashlightEnabled.value) power += 200 // Flash: ~200mA
        if (vibrationEnabled.value) power += 100  // Vibrate: ~100mA
        return power
    }
    
    /**
     * Configure specific sensors manually
     */
    fun configure(gps: Boolean = false, flashlight: Boolean = false, vibration: Boolean = false) {
        enableGps = gps
        enableFlashlight = flashlight
        enableVibration = vibration
    }
    
    private fun configureFromIntensity(intensity: Int) {
        // If manually configured, use those settings
        if (enableGps || enableFlashlight || enableVibration) return
        
        // Otherwise, auto-configure based on intensity
        when {
            intensity <= 25 -> {
                enableFlashlight = true
                enableVibration = false
                enableGps = false
            }
            intensity <= 50 -> {
                enableFlashlight = true
                enableVibration = true
                enableGps = false
            }
            intensity <= 75 -> {
                enableFlashlight = true
                enableVibration = true
                enableGps = true
            }
            else -> {
                enableFlashlight = true
                enableVibration = true
                enableGps = true
            }
        }
    }
    
    // ========== FLASHLIGHT ==========
    
    private fun hasFlashlight(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }
    
    private fun startFlashlight() {
        if (!hasFlashlight()) return
        
        try {
            cameraManager?.let { manager ->
                cameraId = manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                
                cameraId?.let { id ->
                    manager.setTorchMode(id, true)
                    flashlightEnabled.value = true
                }
            }
        } catch (e: CameraAccessException) {
            flashlightEnabled.value = false
        }
    }
    
    private fun stopFlashlight() {
        try {
            cameraId?.let { id ->
                cameraManager?.setTorchMode(id, false)
            }
        } catch (e: CameraAccessException) {
            // Ignore
        }
        flashlightEnabled.value = false
    }
    
    // ========== VIBRATION ==========
    
    private fun hasVibrator(): Boolean {
        return vibrator?.hasVibrator() == true
    }
    
    private fun startVibration() {
        if (!hasVibrator()) return
        
        vibrationEnabled.value = true
        vibrationJob = scope.launch {
            while (shouldRun.get()) {
                vibrateOnce()
                delay(500) // Vibrate pattern: 500ms on, 500ms off
            }
        }
    }
    
    private fun vibrateOnce() {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(400)
            }
        }
    }
    
    private fun stopVibration() {
        vibrationJob?.cancel()
        vibrator?.cancel()
        vibrationEnabled.value = false
    }
    
    // ========== GPS ==========
    
    private fun hasGps(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (!hasGps() || !hasLocationPermission()) {
            gpsEnabled.value = false
            return
        }
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        // Request high-accuracy, frequent updates
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            100 // 100ms interval - very frequent!
        ).apply {
            setMinUpdateIntervalMillis(50)
            setMaxUpdateDelayMillis(100)
            setWaitForAccurateLocation(true)
        }.build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                lastLocation.value = result.lastLocation
            }
        }
        
        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            gpsEnabled.value = true
        } catch (e: Exception) {
            gpsEnabled.value = false
        }
    }
    
    private fun stopGps() {
        locationCallback?.let { callback ->
            fusedLocationClient?.removeLocationUpdates(callback)
        }
        fusedLocationClient = null
        locationCallback = null
        gpsEnabled.value = false
    }
}
