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
import android.provider.Settings
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
        val targetLoad = intensity.coerceIn(0, 100)
        _currentLoad.value = targetLoad

        // Determine which sensors to enable based on intensity (if not manually configured)
        configureFromIntensity(targetLoad)

        android.util.Log.i("SensorStressor", "Starting with: GPS=$enableGps, Flash=$enableFlashlight, Vib=$enableVibration")

        var startedAny = false
        if (enableFlashlight) {
            val flashResult = startFlashlight()
            android.util.Log.i("SensorStressor", "Flashlight start result: $flashResult")
            startedAny = flashResult || startedAny
        }
        if (enableVibration) {
            val vibResult = startVibration()
            android.util.Log.i("SensorStressor", "Vibration start result: $vibResult")
            startedAny = vibResult || startedAny
        }
        if (enableGps) {
            val gpsResult = startGps()
            android.util.Log.i("SensorStressor", "GPS start result: $gpsResult")
            startedAny = gpsResult || startedAny
        }

        _isRunning.value = startedAny
        if (!startedAny) {
            shouldRun.set(false)
            _currentLoad.value = 0
            android.util.Log.w("SensorStressor", "No sensors could be started")
        } else {
            android.util.Log.i("SensorStressor", "Sensor stressor running successfully")
        }
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

        // Reset configuration flags so next start can reconfigure
        enableGps = false
        enableFlashlight = false
        enableVibration = false
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
    
    /**
     * Check if GPS can actually be used (hardware + system setting + permission)
     */
    fun canUseGps(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val masterEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            val mode = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
        return hasGps() &&
            masterEnabled &&
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true &&
            hasLocationPermission()
    }
    
    /**
     * Get detailed availability status for each sensor
     */
    fun getSensorAvailability(): Map<String, SensorAvailabilityInfo> {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val gpsSystemEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        
        return mapOf(
            "GPS" to SensorAvailabilityInfo(
                hasHardware = hasGps(),
                isSystemEnabled = gpsSystemEnabled,
                hasPermission = hasLocationPermission(),
                isReady = canUseGps(),
                statusMessage = when {
                    !hasGps() -> "No GPS hardware"
                    !gpsSystemEnabled -> "GPS is OFF in settings"
                    !hasLocationPermission() -> "Permission required"
                    else -> "Ready"
                }
            ),
            "Flashlight" to SensorAvailabilityInfo(
                hasHardware = hasFlashlight(),
                isSystemEnabled = true,
                hasPermission = true,
                isReady = hasFlashlight(),
                statusMessage = if (hasFlashlight()) "Ready" else "No flashlight"
            ),
            "Vibration" to SensorAvailabilityInfo(
                hasHardware = hasVibrator(),
                isSystemEnabled = true,
                hasPermission = true,
                isReady = hasVibrator(),
                statusMessage = if (hasVibrator()) "Ready" else "No vibrator"
            )
        )
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

    private fun startFlashlight(): Boolean {
        if (!hasFlashlight()) {
            android.util.Log.w("SensorStressor", "Flashlight hardware not available")
            return false
        }

        return try {
            cameraManager?.let { manager ->
                // Find a camera with flash capability
                cameraId = manager.cameraIdList.firstOrNull { id ->
                    try {
                        val characteristics = manager.getCameraCharacteristics(id)
                        characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } catch (e: Exception) {
                        false
                    }
                }

                if (cameraId == null) {
                    android.util.Log.w("SensorStressor", "No camera with flash found")
                    return false
                }

                android.util.Log.i("SensorStressor", "Enabling flashlight on camera: $cameraId")
                manager.setTorchMode(cameraId!!, true)
                flashlightEnabled.value = true
                true
            } ?: false
        } catch (e: CameraAccessException) {
            android.util.Log.e("SensorStressor", "Camera access error: ${e.message}")
            flashlightEnabled.value = false
            false
        } catch (e: Exception) {
            android.util.Log.e("SensorStressor", "Flashlight error: ${e.message}")
            flashlightEnabled.value = false
            false
        }
    }

    private fun stopFlashlight() {
        try {
            cameraId?.let { id ->
                cameraManager?.setTorchMode(id, false)
            }
        } catch (e: CameraAccessException) {
            android.util.Log.e("SensorStressor", "Error turning off flashlight: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("SensorStressor", "Error stopping flashlight: ${e.message}")
        }
        cameraId = null
        flashlightEnabled.value = false
    }
    
    // ========== VIBRATION ==========

    private fun hasVibrator(): Boolean {
        return vibrator?.hasVibrator() == true
    }

    private fun startVibration(): Boolean {
        if (!hasVibrator()) {
            android.util.Log.w("SensorStressor", "Vibrator hardware not available")
            return false
        }

        vibrationEnabled.value = true
        android.util.Log.i("SensorStressor", "Starting vibration loop")

        vibrationJob = scope.launch {
            while (shouldRun.get()) {
                vibrateOnce()
                delay(600) // 400ms vibration + 200ms pause = 600ms cycle
            }
        }
        return true
    }

    private fun vibrateOnce() {
        try {
            vibrator?.let { vib ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Use max amplitude for more power consumption
                    vib.vibrate(VibrationEffect.createOneShot(400, 255))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(400)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SensorStressor", "Vibration error: ${e.message}")
        }
    }

    private fun stopVibration() {
        vibrationJob?.cancel()
        vibrationJob = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            android.util.Log.e("SensorStressor", "Error stopping vibration: ${e.message}")
        }
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

    /**
     * Check if GPS is actually enabled in system settings
     */
    private fun isGpsSystemEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    // Native GPS listener for forcing actual GPS hardware usage
    private var nativeLocationListener: LocationListener? = null

    @SuppressLint("MissingPermission")
    private fun startGps(): Boolean {
        // Check all requirements: hardware, system setting, and permission
        if (!hasGps()) {
            gpsEnabled.value = false
            android.util.Log.w("SensorStressor", "GPS hardware not available")
            return false
        }

        if (!isGpsSystemEnabled()) {
            gpsEnabled.value = false
            android.util.Log.w("SensorStressor", "GPS is turned off in system settings")
            return false
        }

        if (!hasLocationPermission()) {
            gpsEnabled.value = false
            android.util.Log.w("SensorStressor", "Location permission not granted")
            return false
        }

        try {
            // Use BOTH native LocationManager AND FusedLocationProvider to ensure GPS is truly active
            // Native LocationManager forces actual GPS hardware usage
            val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            nativeLocationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lastLocation.value = location
                    android.util.Log.d("SensorStressor", "GPS location: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
                }

                @Deprecated("Deprecated in API level 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            // Request GPS updates directly from hardware - this forces actual GPS chip usage
            // minTimeMs=0 and minDistanceM=0 means maximum frequency updates
            locManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,        // minTimeMs - request updates as fast as possible
                0f,        // minDistanceM - request updates for any movement
                nativeLocationListener!!,
                Looper.getMainLooper()
            )

            // Also use FusedLocationClient with high accuracy for additional drain
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                500 // 500ms interval
            ).apply {
                setMinUpdateIntervalMillis(100)
                setMaxUpdateDelayMillis(500)
                setWaitForAccurateLocation(true)
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        lastLocation.value = loc
                    }
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            gpsEnabled.value = true
            android.util.Log.i("SensorStressor", "GPS started successfully (native + fused)")
            return true
        } catch (e: Exception) {
            gpsEnabled.value = false
            android.util.Log.e("SensorStressor", "Failed to start GPS: ${e.message}")
            return false
        }
    }

    private fun stopGps() {
        // Stop native location listener
        nativeLocationListener?.let { listener ->
            try {
                locationManager?.removeUpdates(listener)
            } catch (e: Exception) {
                android.util.Log.e("SensorStressor", "Error stopping native GPS: ${e.message}")
            }
        }
        nativeLocationListener = null

        // Stop fused location client
        locationCallback?.let { callback ->
            fusedLocationClient?.removeLocationUpdates(callback)
        }
        fusedLocationClient = null
        locationCallback = null
        gpsEnabled.value = false
    }
}

/**
 * Detailed availability info for a sensor
 */
data class SensorAvailabilityInfo(
    val hasHardware: Boolean,
    val isSystemEnabled: Boolean,
    val hasPermission: Boolean,
    val isReady: Boolean,
    val statusMessage: String
)
