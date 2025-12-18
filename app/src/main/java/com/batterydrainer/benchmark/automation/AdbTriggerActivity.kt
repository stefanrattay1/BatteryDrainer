package com.batterydrainer.benchmark.automation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.batterydrainer.benchmark.data.StressProfile
import com.batterydrainer.benchmark.data.TestConfig
import com.batterydrainer.benchmark.service.DrainerService

/**
 * Activity to handle ADB Intent triggers for automation
 * 
 * This allows enterprise users to control the benchmark via USB commands:
 * 
 * Start a test:
 * adb shell am start -n com.batterydrainer.benchmark/.automation.AdbTriggerActivity \
 *     --es "profile" "commute" \
 *     --ei "duration" 60 \
 *     --ei "target_drop" 20
 * 
 * Stop a test:
 * adb shell am start -n com.batterydrainer.benchmark/.automation.AdbTriggerActivity \
 *     --es "action" "stop"
 * 
 * Get status:
 * adb shell am start -n com.batterydrainer.benchmark/.automation.AdbTriggerActivity \
 *     --es "action" "status"
 * 
 * List profiles:
 * adb shell am start -n com.batterydrainer.benchmark/.automation.AdbTriggerActivity \
 *     --es "action" "list_profiles"
 */
class AdbTriggerActivity : Activity() {
    
    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_TARGET_DROP = "target_drop"
        const val EXTRA_MAX_TEMP = "max_temp"
        
        // Actions
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_STATUS = "status"
        const val ACTION_LIST_PROFILES = "list_profiles"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
        finish()
    }
    
    private fun handleIntent(intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: ACTION_START
        
        when (action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STATUS -> handleStatus()
            ACTION_LIST_PROFILES -> handleListProfiles()
            else -> printError("Unknown action: $action")
        }
    }
    
    private fun handleStart(intent: Intent) {
        val profileId = intent.getStringExtra(EXTRA_PROFILE) ?: "idle"
        val duration = intent.getIntExtra(EXTRA_DURATION, 60)
        val targetDrop = intent.getIntExtra(EXTRA_TARGET_DROP, 10)
        val maxTemp = intent.getFloatExtra(EXTRA_MAX_TEMP, 45f)
        
        val profile = StressProfile.getById(profileId)
        if (profile == null) {
            printError("Unknown profile: $profileId")
            printOutput("Available profiles: ${StressProfile.PRESETS.map { it.id }.joinToString(", ")}")
            return
        }
        
        // Check if premium profile requires license (placeholder for monetization)
        if (profile.isPremium && !isPremiumUnlocked()) {
            printError("Profile '$profileId' requires Pro license")
            return
        }
        
        val config = TestConfig(
            targetBatteryDrop = targetDrop,
            maxDurationMinutes = duration,
            maxTemperatureCelsius = maxTemp,
            enableThermalProtection = true
        )
        
        // Start the service
        val serviceIntent = Intent(this, DrainerService::class.java).apply {
            this.action = DrainerService.ACTION_START_TEST
            putExtra(DrainerService.EXTRA_PROFILE_ID, profileId)
            putExtra(DrainerService.EXTRA_DURATION_MINUTES, duration)
            putExtra(DrainerService.EXTRA_TARGET_BATTERY_DROP, targetDrop)
        }
        
        startForegroundService(serviceIntent)
        
        printOutput("""
            ✅ Test Started
            Profile: ${profile.name} (${profile.id})
            Duration: ${duration} minutes
            Target Drop: ${targetDrop}%
            Max Temp: ${maxTemp}°C
            
            Stressors:
            - CPU: ${profile.cpuLoad}%
            - GPU: ${profile.gpuLoad}%
            - Network: ${profile.networkLoad}%
            - GPS: ${profile.gpsEnabled}
            - Flashlight: ${profile.flashlightEnabled}
            - Vibration: ${profile.vibrateEnabled}
        """.trimIndent())
    }
    
    private fun handleStop() {
        val serviceIntent = Intent(this, DrainerService::class.java).apply {
            action = DrainerService.ACTION_STOP_TEST
        }
        startService(serviceIntent)
        printOutput("✅ Test stopped")
    }
    
    private fun handlePause() {
        val serviceIntent = Intent(this, DrainerService::class.java).apply {
            action = DrainerService.ACTION_PAUSE_TEST
        }
        startService(serviceIntent)
        printOutput("✅ Test paused")
    }
    
    private fun handleResume() {
        val serviceIntent = Intent(this, DrainerService::class.java).apply {
            action = DrainerService.ACTION_RESUME_TEST
        }
        startService(serviceIntent)
        printOutput("✅ Test resumed")
    }
    
    private fun handleStatus() {
        val service = DrainerService.getInstance()
        
        if (service == null) {
            printOutput("""
                Status: Service not running
                Test: None
            """.trimIndent())
            return
        }
        
        val session = service.currentSession.value
        val reading = service.batteryMonitor.currentReading.value
        val stressorStatus = service.stressorManager.getStatus()
        
        printOutput("""
            Status: ${service.statusMessage.value}
            Running: ${service.isTestRunning.value}
            Paused: ${service.isPaused.value}
            Progress: ${(service.testProgress.value * 100).toInt()}%
            
            Session:
            - Profile: ${session?.profileName ?: "None"}
            - Start Battery: ${session?.startBatteryLevel ?: 0}%
            - Current Battery: ${reading?.level ?: 0}%
            
            Battery Stats:
            - Level: ${reading?.level ?: 0}%
            - Current: ${(reading?.current ?: 0) / 1000} mA
            - Voltage: ${reading?.voltage ?: 0} mV
            - Temperature: ${reading?.temperature ?: 0}°C
            - Charging: ${reading?.isCharging ?: false}
            
            Stressors:
            ${stressorStatus.values.joinToString("\n") { 
                "- ${it.name}: ${if (it.isRunning) "Running (${it.currentLoad}%)" else "Stopped"}"
            }}
        """.trimIndent())
    }
    
    private fun handleListProfiles() {
        printOutput("""
            Available Profiles:
            
            ${StressProfile.PRESETS.joinToString("\n\n") { profile ->
                """
                ${profile.icon} ${profile.name} (${profile.id})
                   ${profile.description}
                   CPU: ${profile.cpuLoad}% | GPU: ${profile.gpuLoad}% | Network: ${profile.networkLoad}%
                   GPS: ${profile.gpsEnabled} | Flash: ${profile.flashlightEnabled} | Vibrate: ${profile.vibrateEnabled}
                   ${if (profile.isPremium) "🔒 PRO ONLY" else ""}
                """.trimIndent()
            }}
        """.trimIndent())
    }
    
    private fun isPremiumUnlocked(): Boolean {
        // TODO: Implement license verification
        // For development, return true
        return true
    }
    
    private fun printOutput(message: String) {
        // Output to logcat for ADB shell capture
        println("BATTERY_DRAINER_OUTPUT:\n$message")
        android.util.Log.i("BatteryDrainer", message)
    }
    
    private fun printError(message: String) {
        println("BATTERY_DRAINER_ERROR: $message")
        android.util.Log.e("BatteryDrainer", message)
    }
}
