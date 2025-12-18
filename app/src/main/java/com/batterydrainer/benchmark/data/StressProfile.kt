package com.batterydrainer.benchmark.data

import com.google.gson.annotations.SerializedName

/**
 * Stress Profile - combines multiple stressors with intensity levels
 */
data class StressProfile(
    val id: String,
    val name: String,
    val description: String,
    val icon: String = "🔋",
    val cpuLoad: Int = 0,           // 0-100%
    val gpuLoad: Int = 0,           // 0-100%
    val networkLoad: Int = 0,       // 0-100%
    val gpsEnabled: Boolean = false,
    val flashlightEnabled: Boolean = false,
    val vibrateEnabled: Boolean = false,
    val screenBrightness: Int = 100, // 0-100%
    val audioEnabled: Boolean = false,
    val isPremium: Boolean = false
) {
    companion object {
        /**
         * Pre-built profiles for common scenarios
         */
        val PRESETS = listOf(
            // Basic Profiles
            StressProfile(
                id = "idle",
                name = "Idle Baseline",
                description = "Measures natural battery drain with no load",
                icon = "😴",
                cpuLoad = 0,
                gpuLoad = 0
            ),
            StressProfile(
                id = "flashlight_only",
                name = "Flashlight Test",
                description = "Tests flashlight battery impact",
                icon = "🔦",
                flashlightEnabled = true
            ),
            StressProfile(
                id = "vibrate_test",
                name = "Vibration Test",
                description = "Tests vibration motor impact",
                icon = "📳",
                vibrateEnabled = true
            ),
            
            // CPU Profiles
            StressProfile(
                id = "cpu_light",
                name = "CPU Light",
                description = "Single-core light workload",
                icon = "🖥️",
                cpuLoad = 25
            ),
            StressProfile(
                id = "cpu_medium",
                name = "CPU Medium",
                description = "Multi-core moderate workload",
                icon = "💻",
                cpuLoad = 50
            ),
            StressProfile(
                id = "cpu_meltdown",
                name = "CPU Meltdown",
                description = "All cores at maximum - CAUTION: High heat!",
                icon = "🔥",
                cpuLoad = 100
            ),
            
            // GPU Profiles
            StressProfile(
                id = "gpu_light",
                name = "GPU Light",
                description = "Simple rendering workload",
                icon = "🎨",
                gpuLoad = 25
            ),
            StressProfile(
                id = "gpu_heavy",
                name = "GPU Heavy",
                description = "Complex 3D rendering",
                icon = "🎮",
                gpuLoad = 100
            ),
            
            // Real-World Simulation Profiles
            StressProfile(
                id = "commute",
                name = "The Commute",
                description = "GPS navigation + music streaming simulation",
                icon = "🚗",
                cpuLoad = 30,
                networkLoad = 50,
                gpsEnabled = true,
                screenBrightness = 100,
                audioEnabled = true
            ),
            StressProfile(
                id = "gamer",
                name = "The Gamer",
                description = "Heavy gaming session simulation",
                icon = "🎮",
                cpuLoad = 100,
                gpuLoad = 100,
                audioEnabled = true,
                screenBrightness = 100
            ),
            StressProfile(
                id = "social_scroll",
                name = "Social Scroll",
                description = "Social media browsing with video autoplay",
                icon = "📱",
                cpuLoad = 40,
                gpuLoad = 30,
                networkLoad = 70,
                screenBrightness = 80
            ),
            StressProfile(
                id = "video_call",
                name = "Video Call",
                description = "Video conferencing simulation",
                icon = "📹",
                cpuLoad = 60,
                networkLoad = 80,
                audioEnabled = true,
                screenBrightness = 70
            ),
            StressProfile(
                id = "zombie",
                name = "The Zombie",
                description = "Poor signal + background sync stress",
                icon = "🧟",
                cpuLoad = 20,
                networkLoad = 100,
                gpsEnabled = true,
                isPremium = true
            ),
            StressProfile(
                id = "photographer",
                name = "The Photographer",
                description = "Camera + GPS + uploads",
                icon = "📸",
                cpuLoad = 50,
                gpuLoad = 40,
                networkLoad = 60,
                gpsEnabled = true,
                flashlightEnabled = false,
                isPremium = true
            ),
            
            // Maximum Stress
            StressProfile(
                id = "everything",
                name = "EVERYTHING",
                description = "All stressors at maximum - FOR TESTING ONLY",
                icon = "💀",
                cpuLoad = 100,
                gpuLoad = 100,
                networkLoad = 100,
                gpsEnabled = true,
                flashlightEnabled = true,
                vibrateEnabled = true,
                screenBrightness = 100,
                audioEnabled = true,
                isPremium = true
            )
        )
        
        fun getById(id: String): StressProfile? = PRESETS.find { it.id == id }
    }
}

/**
 * Custom profile created by user
 */
data class CustomProfile(
    val profile: StressProfile,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)
