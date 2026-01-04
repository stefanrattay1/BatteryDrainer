package com.batterydrainer.benchmark.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for StressProfile and related functionality
 */
class StressProfileTest {

    // ===== PRESET VALIDATION TESTS =====

    @Test
    fun `PRESETS contains expected number of profiles`() {
        // Should have 40+ profiles as documented
        assertThat(StressProfile.PRESETS.size).isAtLeast(30)
    }

    @Test
    fun `all PRESETS have unique IDs`() {
        val ids = StressProfile.PRESETS.map { it.id }
        val uniqueIds = ids.toSet()
        assertThat(uniqueIds.size).isEqualTo(ids.size)
    }

    @Test
    fun `all PRESETS have non-empty names`() {
        StressProfile.PRESETS.forEach { profile ->
            assertThat(profile.name).isNotEmpty()
        }
    }

    @Test
    fun `all PRESETS have non-empty descriptions`() {
        StressProfile.PRESETS.forEach { profile ->
            assertThat(profile.description).isNotEmpty()
        }
    }

    @Test
    fun `all load values are within valid range 0-100`() {
        StressProfile.PRESETS.forEach { profile ->
            assertThat(profile.cpuLoad).isIn(0..100)
            assertThat(profile.gpuLoad).isIn(0..100)
            assertThat(profile.networkLoad).isIn(0..100)
            assertThat(profile.screenBrightness).isIn(0..100)
        }
    }

    // ===== GETBYID TESTS =====

    @Test
    fun `getById returns correct profile for valid ID`() {
        val profile = StressProfile.getById("idle")
        assertThat(profile).isNotNull()
        assertThat(profile?.name).isEqualTo("Idle Baseline")
    }

    @Test
    fun `getById returns null for invalid ID`() {
        val profile = StressProfile.getById("nonexistent_profile_12345")
        assertThat(profile).isNull()
    }

    @Test
    fun `getById is case sensitive`() {
        val profile = StressProfile.getById("IDLE")
        assertThat(profile).isNull()
    }

    // ===== GETBYCATEGORY TESTS =====

    @Test
    fun `getByCategory returns profiles for BASELINE category`() {
        val profiles = StressProfile.getByCategory(ProfileCategory.BASELINE)
        assertThat(profiles).isNotEmpty()
        profiles.forEach {
            assertThat(it.category).isEqualTo(ProfileCategory.BASELINE)
        }
    }

    @Test
    fun `getByCategory returns profiles for all categories`() {
        ProfileCategory.entries.forEach { category ->
            val profiles = StressProfile.getByCategory(category)
            // Each category should have at least one profile
            assertThat(profiles).isNotEmpty()
        }
    }

    @Test
    fun `sum of all category profiles equals total PRESETS`() {
        val totalFromCategories = ProfileCategory.entries.sumOf { category ->
            StressProfile.getByCategory(category).size
        }
        assertThat(totalFromCategories).isEqualTo(StressProfile.PRESETS.size)
    }

    // ===== PREMIUM PROFILES TESTS =====

    @Test
    fun `getFreeProfiles excludes premium profiles`() {
        val freeProfiles = StressProfile.getFreeProfiles()
        freeProfiles.forEach {
            assertThat(it.isPremium).isFalse()
        }
    }

    @Test
    fun `getPremiumProfiles only returns premium profiles`() {
        val premiumProfiles = StressProfile.getPremiumProfiles()
        premiumProfiles.forEach {
            assertThat(it.isPremium).isTrue()
        }
    }

    @Test
    fun `free plus premium profiles equals total PRESETS`() {
        val freeCount = StressProfile.getFreeProfiles().size
        val premiumCount = StressProfile.getPremiumProfiles().size
        assertThat(freeCount + premiumCount).isEqualTo(StressProfile.PRESETS.size)
    }

    // ===== SPECIFIC PROFILE TESTS =====

    @Test
    fun `idle profile has zero load`() {
        val idle = StressProfile.getById("idle")!!
        assertThat(idle.cpuLoad).isEqualTo(0)
        assertThat(idle.gpuLoad).isEqualTo(0)
        assertThat(idle.networkLoad).isEqualTo(0)
        assertThat(idle.gpsEnabled).isFalse()
        assertThat(idle.flashlightEnabled).isFalse()
        assertThat(idle.vibrateEnabled).isFalse()
    }

    @Test
    fun `everything profile has all stressors enabled`() {
        val everything = StressProfile.getById("everything")!!
        assertThat(everything.cpuLoad).isEqualTo(100)
        assertThat(everything.gpuLoad).isEqualTo(100)
        assertThat(everything.networkLoad).isEqualTo(100)
        assertThat(everything.gpsEnabled).isTrue()
        assertThat(everything.flashlightEnabled).isTrue()
        assertThat(everything.vibrateEnabled).isTrue()
        assertThat(everything.audioEnabled).isTrue()
        assertThat(everything.screenBrightness).isEqualTo(100)
        assertThat(everything.isPremium).isTrue()
    }

    @Test
    fun `cpu_meltdown has 100 percent CPU load`() {
        val meltdown = StressProfile.getById("cpu_meltdown")!!
        assertThat(meltdown.cpuLoad).isEqualTo(100)
        assertThat(meltdown.category).isEqualTo(ProfileCategory.CPU)
    }

    @Test
    fun `gps_only profile has GPS enabled and minimal other load`() {
        val gpsOnly = StressProfile.getById("gps_only")!!
        assertThat(gpsOnly.gpsEnabled).isTrue()
        assertThat(gpsOnly.cpuLoad).isEqualTo(0)
        assertThat(gpsOnly.gpuLoad).isEqualTo(0)
        assertThat(gpsOnly.category).isEqualTo(ProfileCategory.COMPONENT)
    }

    @Test
    fun `flashlight_only profile has flashlight enabled`() {
        val flashlight = StressProfile.getById("flashlight_only")!!
        assertThat(flashlight.flashlightEnabled).isTrue()
    }

    // ===== DATA CLASS TESTS =====

    @Test
    fun `StressProfile default values are sensible`() {
        val profile = StressProfile(
            id = "test",
            name = "Test Profile",
            description = "A test profile"
        )

        assertThat(profile.cpuLoad).isEqualTo(0)
        assertThat(profile.gpuLoad).isEqualTo(0)
        assertThat(profile.networkLoad).isEqualTo(0)
        assertThat(profile.gpsEnabled).isFalse()
        assertThat(profile.flashlightEnabled).isFalse()
        assertThat(profile.vibrateEnabled).isFalse()
        assertThat(profile.screenBrightness).isEqualTo(100)
        assertThat(profile.audioEnabled).isFalse()
        assertThat(profile.isPremium).isFalse()
        assertThat(profile.category).isEqualTo(ProfileCategory.BASELINE)
    }

    @Test
    fun `StressProfile copy works correctly`() {
        val original = StressProfile.getById("idle")!!
        val modified = original.copy(cpuLoad = 50, name = "Modified Idle")

        assertThat(modified.id).isEqualTo(original.id)
        assertThat(modified.cpuLoad).isEqualTo(50)
        assertThat(modified.name).isEqualTo("Modified Idle")
        assertThat(original.cpuLoad).isEqualTo(0) // Original unchanged
    }

    // ===== PROFILE CATEGORY TESTS =====

    @Test
    fun `ProfileCategory has expected entries`() {
        val categories = ProfileCategory.entries
        assertThat(categories).contains(ProfileCategory.BASELINE)
        assertThat(categories).contains(ProfileCategory.COMPONENT)
        assertThat(categories).contains(ProfileCategory.CPU)
        assertThat(categories).contains(ProfileCategory.GPU)
        assertThat(categories).contains(ProfileCategory.REALWORLD)
        assertThat(categories).contains(ProfileCategory.GAMING)
        assertThat(categories).contains(ProfileCategory.PRODUCTIVITY)
        assertThat(categories).contains(ProfileCategory.WORSTCASE)
    }

    @Test
    fun `ProfileCategory displayNames are not empty`() {
        ProfileCategory.entries.forEach { category ->
            assertThat(category.displayName).isNotEmpty()
        }
    }

    @Test
    fun `ProfileCategory icons are not empty`() {
        ProfileCategory.entries.forEach { category ->
            assertThat(category.icon).isNotEmpty()
        }
    }
}
