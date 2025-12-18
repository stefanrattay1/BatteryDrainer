package com.batterydrainer.benchmark.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Utility class to check actual system status of various hardware features.
 * This shows the REAL state, not just what the app wants to do.
 */
class SystemStatusChecker(private val context: Context) {

    data class SystemStatus(
        val gpsSystemEnabled: Boolean,
        val gpsPermissionGranted: Boolean,
        val gpsHardwareAvailable: Boolean,
        val flashlightAvailable: Boolean,
        val vibratorAvailable: Boolean,
        val networkConnected: Boolean,
        val networkType: String,
        val wifiEnabled: Boolean,
        val locationMode: String
    )

    /**
     * Get comprehensive system status
     */
    fun getSystemStatus(): SystemStatus {
        return SystemStatus(
            gpsSystemEnabled = isGpsEnabled(),
            gpsPermissionGranted = hasLocationPermission(),
            gpsHardwareAvailable = hasGpsHardware(),
            flashlightAvailable = hasFlashlight(),
            vibratorAvailable = hasVibrator(),
            networkConnected = isNetworkConnected(),
            networkType = getNetworkType(),
            wifiEnabled = isWifiConnected(),
            locationMode = getLocationModeDescription()
        )
    }

    /**
     * Check if GPS is enabled in system settings
     */
    fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    /**
     * Check if any location provider is enabled
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        }
    }

    /**
     * Get human-readable location mode description
     */
    fun getLocationModeDescription(): String {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        
        return when {
            locationManager == null -> "Unknown"
            !isLocationEnabled() -> "Off"
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> "High accuracy"
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> "GPS only"
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> "Network only"
            else -> "Off"
        }
    }

    /**
     * Check if app has fine location permission
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if device has GPS hardware
     */
    fun hasGpsHardware(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
    }

    /**
     * Check if device has flashlight
     */
    fun hasFlashlight(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    /**
     * Check if device has vibrator
     */
    fun hasVibrator(): Boolean {
        val vibrator = getVibrator()
        return vibrator?.hasVibrator() == true
    }

    /**
     * Get the vibrator service
     */
    fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Check if network is connected
     */
    fun isNetworkConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager?.activeNetworkInfo
            return networkInfo?.isConnected == true
        }
    }

    /**
     * Check if WiFi is connected
     */
    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager?.activeNetworkInfo
            return networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
        }
    }

    /**
     * Get network type description
     */
    fun getNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager?.activeNetwork ?: return "Disconnected"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Disconnected"
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Other"
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager?.activeNetworkInfo
            return when (networkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> "WiFi"
                ConnectivityManager.TYPE_MOBILE -> "Cellular"
                ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                else -> if (networkInfo?.isConnected == true) "Other" else "Disconnected"
            }
        }
    }

    /**
     * Get camera manager for flashlight control
     */
    fun getCameraManager(): CameraManager? {
        return context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    /**
     * Get the camera ID that has a flashlight
     */
    fun getFlashlightCameraId(): String? {
        val cameraManager = getCameraManager() ?: return null
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if GPS will actually work (enabled AND has permission)
     */
    fun canUseGps(): Boolean {
        return hasGpsHardware() && isGpsEnabled() && hasLocationPermission()
    }

    /**
     * Get a summary of what features are available and ready to use
     */
    fun getAvailabilityReport(): Map<String, FeatureAvailability> {
        return mapOf(
            "GPS" to FeatureAvailability(
                available = hasGpsHardware(),
                ready = canUseGps(),
                reason = when {
                    !hasGpsHardware() -> "No GPS hardware"
                    !isGpsEnabled() -> "GPS is turned off in settings"
                    !hasLocationPermission() -> "Location permission not granted"
                    else -> "Ready"
                }
            ),
            "Flashlight" to FeatureAvailability(
                available = hasFlashlight(),
                ready = hasFlashlight(),
                reason = if (hasFlashlight()) "Ready" else "No flashlight hardware"
            ),
            "Vibration" to FeatureAvailability(
                available = hasVibrator(),
                ready = hasVibrator(),
                reason = if (hasVibrator()) "Ready" else "No vibrator hardware"
            ),
            "Network" to FeatureAvailability(
                available = true,
                ready = isNetworkConnected(),
                reason = if (isNetworkConnected()) "Connected (${getNetworkType()})" else "No network connection"
            )
        )
    }

    data class FeatureAvailability(
        val available: Boolean,
        val ready: Boolean,
        val reason: String
    )
}
