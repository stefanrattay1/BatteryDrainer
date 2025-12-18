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
    val isPremium: Boolean = false,
    val category: ProfileCategory = ProfileCategory.BASELINE
) {
    companion object {
        /**
         * Pre-built profiles for common scenarios
         */
        val PRESETS = listOf(
            // ===== BASELINE PROFILES =====
            StressProfile(
                id = "idle",
                name = "Idle Baseline",
                description = "Measures natural battery drain with no load - use as reference",
                icon = "😴",
                cpuLoad = 0,
                gpuLoad = 0,
                category = ProfileCategory.BASELINE
            ),
            StressProfile(
                id = "screen_only",
                name = "Screen On Only",
                description = "Screen at max brightness, no processing - isolates display drain",
                icon = "📺",
                screenBrightness = 100,
                category = ProfileCategory.BASELINE
            ),
            
            // ===== COMPONENT ISOLATION TESTS =====
            StressProfile(
                id = "flashlight_only",
                name = "Flashlight Test",
                description = "LED flashlight only - tests LED/torch drain",
                icon = "🔦",
                flashlightEnabled = true,
                category = ProfileCategory.COMPONENT
            ),
            StressProfile(
                id = "vibrate_test",
                name = "Vibration Test",
                description = "Continuous vibration motor - tests haptic drain",
                icon = "📳",
                vibrateEnabled = true,
                category = ProfileCategory.COMPONENT
            ),
            StressProfile(
                id = "gps_only",
                name = "GPS Only",
                description = "Continuous GPS polling - tests location drain",
                icon = "📍",
                gpsEnabled = true,
                category = ProfileCategory.COMPONENT
            ),
            StressProfile(
                id = "network_only",
                name = "Network Only",
                description = "Continuous downloads - tests modem/WiFi drain",
                icon = "📶",
                networkLoad = 100,
                category = ProfileCategory.COMPONENT
            ),
            
            // ===== CPU PROFILES =====
            StressProfile(
                id = "cpu_light",
                name = "CPU Light",
                description = "25% CPU - simulates light background work",
                icon = "🖥️",
                cpuLoad = 25,
                category = ProfileCategory.CPU
            ),
            StressProfile(
                id = "cpu_medium",
                name = "CPU Medium",
                description = "50% CPU - simulates moderate app usage",
                icon = "💻",
                cpuLoad = 50,
                category = ProfileCategory.CPU
            ),
            StressProfile(
                id = "cpu_heavy",
                name = "CPU Heavy",
                description = "75% CPU - simulates intensive processing",
                icon = "🔥",
                cpuLoad = 75,
                category = ProfileCategory.CPU
            ),
            StressProfile(
                id = "cpu_meltdown",
                name = "CPU Meltdown",
                description = "100% all cores - maximum thermal stress! CAUTION",
                icon = "☢️",
                cpuLoad = 100,
                category = ProfileCategory.CPU
            ),
            
            // ===== GPU PROFILES =====
            StressProfile(
                id = "gpu_light",
                name = "GPU Light",
                description = "25% GPU - simple 2D rendering load",
                icon = "🎨",
                gpuLoad = 25,
                category = ProfileCategory.GPU
            ),
            StressProfile(
                id = "gpu_medium",
                name = "GPU Medium",
                description = "50% GPU - moderate 3D rendering",
                icon = "🖼️",
                gpuLoad = 50,
                category = ProfileCategory.GPU
            ),
            StressProfile(
                id = "gpu_heavy",
                name = "GPU Heavy",
                description = "100% GPU - complex shader-heavy rendering",
                icon = "🎮",
                gpuLoad = 100,
                category = ProfileCategory.GPU
            ),
            
            // ===== REAL-WORLD SIMULATION PROFILES =====
            StressProfile(
                id = "messaging",
                name = "Messaging App",
                description = "Light CPU + Network - WhatsApp/Telegram usage",
                icon = "💬",
                cpuLoad = 15,
                networkLoad = 30,
                screenBrightness = 60,
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "email_sync",
                name = "Email Sync",
                description = "Periodic network + light CPU - background email",
                icon = "📧",
                cpuLoad = 20,
                networkLoad = 50,
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "music_streaming",
                name = "Music Streaming",
                description = "Audio + Network - Spotify/YouTube Music",
                icon = "🎵",
                cpuLoad = 15,
                networkLoad = 40,
                audioEnabled = true,
                screenBrightness = 0, // Screen off while listening
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "podcast",
                name = "Podcast Player",
                description = "Audio playback with screen off",
                icon = "🎙️",
                cpuLoad = 10,
                audioEnabled = true,
                screenBrightness = 0,
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "social_scroll",
                name = "Social Media Scroll",
                description = "CPU + GPU + Network - Instagram/TikTok scrolling",
                icon = "📱",
                cpuLoad = 40,
                gpuLoad = 35,
                networkLoad = 70,
                screenBrightness = 80,
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "video_streaming",
                name = "Video Streaming",
                description = "Netflix/YouTube video playback",
                icon = "📺",
                cpuLoad = 30,
                gpuLoad = 40,
                networkLoad = 80,
                audioEnabled = true,
                screenBrightness = 70,
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "web_browsing",
                name = "Web Browsing",
                description = "Chrome/Firefox general browsing",
                icon = "🌐",
                cpuLoad = 35,
                gpuLoad = 20,
                networkLoad = 50,
                screenBrightness = 70,
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "commute",
                name = "The Commute",
                description = "GPS navigation + music - Google Maps + Spotify",
                icon = "🚗",
                cpuLoad = 35,
                gpuLoad = 25,
                networkLoad = 50,
                gpsEnabled = true,
                audioEnabled = true,
                screenBrightness = 100,
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "rideshare_driver",
                name = "Rideshare Driver",
                description = "Uber/Lyft driver mode - GPS + Network + Screen always on",
                icon = "🚕",
                cpuLoad = 40,
                networkLoad = 60,
                gpsEnabled = true,
                screenBrightness = 100,
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "fitness_tracking",
                name = "Fitness Tracking",
                description = "Running app with GPS + Music",
                icon = "🏃",
                cpuLoad = 25,
                networkLoad = 20,
                gpsEnabled = true,
                audioEnabled = true,
                screenBrightness = 50,
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "video_call",
                name = "Video Call",
                description = "Zoom/Teams/FaceTime video conferencing",
                icon = "📹",
                cpuLoad = 60,
                gpuLoad = 30,
                networkLoad = 80,
                audioEnabled = true,
                screenBrightness = 70,
                category = ProfileCategory.REALWORLD
            ),
            StressProfile(
                id = "voice_call",
                name = "Voice Call",
                description = "Phone/VoIP call - network + audio",
                icon = "📞",
                cpuLoad = 20,
                networkLoad = 40,
                audioEnabled = true,
                screenBrightness = 0,
                category = ProfileCategory.REALWORLD
            ),
            
            // ===== GAMING PROFILES =====
            StressProfile(
                id = "casual_game",
                name = "Casual Game",
                description = "Candy Crush / Puzzle games - light GPU",
                icon = "🧩",
                cpuLoad = 30,
                gpuLoad = 40,
                audioEnabled = true,
                screenBrightness = 80,
                category = ProfileCategory.GAMING
            ),
            StressProfile(
                id = "mid_game",
                name = "Mid-Range Game",
                description = "Clash Royale / Among Us level games",
                icon = "⚔️",
                cpuLoad = 50,
                gpuLoad = 60,
                networkLoad = 30,
                audioEnabled = true,
                screenBrightness = 90,
                category = ProfileCategory.GAMING
            ),
            StressProfile(
                id = "gamer",
                name = "Heavy Gaming",
                description = "PUBG Mobile / Genshin Impact - max stress",
                icon = "🎮",
                cpuLoad = 100,
                gpuLoad = 100,
                networkLoad = 50,
                audioEnabled = true,
                screenBrightness = 100,
                category = ProfileCategory.GAMING
            ),
            StressProfile(
                id = "vr_ar",
                name = "VR/AR Session",
                description = "Pokemon GO / AR apps - GPU + GPS heavy",
                icon = "🥽",
                cpuLoad = 70,
                gpuLoad = 90,
                gpsEnabled = true,
                screenBrightness = 100,
                category = ProfileCategory.GAMING,
                isPremium = true
            ),
            
            // ===== PRODUCTIVITY PROFILES =====
            StressProfile(
                id = "document_editing",
                name = "Document Editing",
                description = "Google Docs / Office - light CPU + network",
                icon = "📝",
                cpuLoad = 25,
                networkLoad = 30,
                screenBrightness = 70,
                category = ProfileCategory.PRODUCTIVITY
            ),
            StressProfile(
                id = "photo_editing",
                name = "Photo Editing",
                description = "Lightroom / Snapseed - CPU + GPU heavy",
                icon = "🖼️",
                cpuLoad = 60,
                gpuLoad = 70,
                screenBrightness = 100,
                category = ProfileCategory.PRODUCTIVITY
            ),
            StressProfile(
                id = "video_editing",
                name = "Video Editing",
                description = "CapCut / KineMaster - very heavy load",
                icon = "🎬",
                cpuLoad = 80,
                gpuLoad = 80,
                screenBrightness = 100,
                category = ProfileCategory.PRODUCTIVITY,
                isPremium = true
            ),
            
            // ===== WORST-CASE SCENARIOS =====
            StressProfile(
                id = "zombie",
                name = "The Zombie",
                description = "Poor signal + sync - tests modem power hunting",
                icon = "🧟",
                cpuLoad = 25,
                networkLoad = 100,
                gpsEnabled = true,
                category = ProfileCategory.WORSTCASE,
                isPremium = true
            ),
            StressProfile(
                id = "photographer",
                name = "The Photographer",
                description = "GPS tagging + heavy processing + uploads",
                icon = "📸",
                cpuLoad = 60,
                gpuLoad = 50,
                networkLoad = 70,
                gpsEnabled = true,
                screenBrightness = 100,
                category = ProfileCategory.WORSTCASE,
                isPremium = true
            ),
            StressProfile(
                id = "live_streamer",
                name = "Live Streamer",
                description = "Recording + encoding + uploading simultaneously",
                icon = "📡",
                cpuLoad = 90,
                gpuLoad = 70,
                networkLoad = 100,
                audioEnabled = true,
                screenBrightness = 100,
                category = ProfileCategory.WORSTCASE,
                isPremium = true
            ),
            StressProfile(
                id = "everything",
                name = "EVERYTHING",
                description = "All stressors maxed - THERMAL WARNING!",
                icon = "💀",
                cpuLoad = 100,
                gpuLoad = 100,
                networkLoad = 100,
                gpsEnabled = true,
                flashlightEnabled = true,
                vibrateEnabled = true,
                audioEnabled = true,
                screenBrightness = 100,
                category = ProfileCategory.WORSTCASE,
                isPremium = true
            )
        )
        
        fun getById(id: String): StressProfile? = PRESETS.find { it.id == id }
        
        fun getByCategory(category: ProfileCategory): List<StressProfile> = 
            PRESETS.filter { it.category == category }
        
        fun getFreeProfiles(): List<StressProfile> = 
            PRESETS.filter { !it.isPremium }
        
        fun getPremiumProfiles(): List<StressProfile> = 
            PRESETS.filter { it.isPremium }
    }
}

/**
 * Profile categories for organization
 */
enum class ProfileCategory(val displayName: String, val icon: String) {
    BASELINE("Baseline", "📊"),
    COMPONENT("Component Tests", "🔧"),
    CPU("CPU Tests", "🖥️"),
    GPU("GPU Tests", "🎮"),
    REALWORLD("Real-World", "🌍"),
    GAMING("Gaming", "🎯"),
    PRODUCTIVITY("Productivity", "💼"),
    WORSTCASE("Worst Case", "⚠️")
}

/**
 * Custom profile created by user
 */
data class CustomProfile(
    val profile: StressProfile,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)
