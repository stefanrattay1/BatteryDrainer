package com.batterydrainer.benchmark.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for data models in BatteryModels.kt
 */
class BatteryModelsTest {

    // ===== BATTERY READING TESTS =====

    @Test
    fun `BatteryReading default timestamp is current time`() {
        val before = System.currentTimeMillis()
        val reading = BatteryReading(
            level = 50,
            voltage = 4000,
            current = -500000,
            temperature = 30f,
            isCharging = false
        )
        val after = System.currentTimeMillis()

        assertThat(reading.timestamp).isAtLeast(before)
        assertThat(reading.timestamp).isAtMost(after)
    }

    @Test
    fun `BatteryReading stores all values correctly`() {
        val reading = BatteryReading(
            timestamp = 1234567890L,
            level = 75,
            voltage = 4100,
            current = -750000,
            temperature = 35.5f,
            isCharging = true,
            energyCounter = 5000000000L
        )

        assertThat(reading.timestamp).isEqualTo(1234567890L)
        assertThat(reading.level).isEqualTo(75)
        assertThat(reading.voltage).isEqualTo(4100)
        assertThat(reading.current).isEqualTo(-750000)
        assertThat(reading.temperature).isEqualTo(35.5f)
        assertThat(reading.isCharging).isTrue()
        assertThat(reading.energyCounter).isEqualTo(5000000000L)
    }

    @Test
    fun `BatteryReading energyCounter defaults to null`() {
        val reading = BatteryReading(
            level = 50,
            voltage = 4000,
            current = -500000,
            temperature = 30f,
            isCharging = false
        )

        assertThat(reading.energyCounter).isNull()
    }

    @Test
    fun `BatteryReading level bounds are valid`() {
        // Test minimum
        val minReading = BatteryReading(
            level = 0,
            voltage = 3200,
            current = 0,
            temperature = 25f,
            isCharging = false
        )
        assertThat(minReading.level).isEqualTo(0)

        // Test maximum
        val maxReading = BatteryReading(
            level = 100,
            voltage = 4350,
            current = 0,
            temperature = 25f,
            isCharging = true
        )
        assertThat(maxReading.level).isEqualTo(100)
    }

    @Test
    fun `BatteryReading copy works correctly`() {
        val original = BatteryReading(
            timestamp = 1000L,
            level = 80,
            voltage = 4100,
            current = -500000,
            temperature = 30f,
            isCharging = false
        )

        val modified = original.copy(level = 75, isCharging = true)

        assertThat(modified.level).isEqualTo(75)
        assertThat(modified.isCharging).isTrue()
        assertThat(modified.timestamp).isEqualTo(original.timestamp)
        assertThat(modified.voltage).isEqualTo(original.voltage)
    }

    @Test
    fun `BatteryReading negative current indicates discharging`() {
        val discharging = BatteryReading(
            level = 50,
            voltage = 4000,
            current = -500000,  // Negative = discharging
            temperature = 30f,
            isCharging = false
        )

        assertThat(discharging.current).isLessThan(0)
    }

    @Test
    fun `BatteryReading positive current indicates charging`() {
        val charging = BatteryReading(
            level = 50,
            voltage = 4000,
            current = 1500000,  // Positive = charging
            temperature = 30f,
            isCharging = true
        )

        assertThat(charging.current).isGreaterThan(0)
    }

    // ===== THERMAL STATE TESTS =====

    @Test
    fun `ThermalState has correct number of states`() {
        assertThat(ThermalState.entries).hasSize(7)
    }

    @Test
    fun `ThermalState values are in increasing severity order`() {
        val states = ThermalState.entries
        for (i in 0 until states.size - 1) {
            assertThat(states[i].ordinal).isLessThan(states[i + 1].ordinal)
        }
    }

    @Test
    fun `ThermalState NONE is first state`() {
        assertThat(ThermalState.entries.first()).isEqualTo(ThermalState.NONE)
    }

    @Test
    fun `ThermalState SHUTDOWN is last state`() {
        assertThat(ThermalState.entries.last()).isEqualTo(ThermalState.SHUTDOWN)
    }

    // ===== DEVICE STATS TESTS =====

    @Test
    fun `DeviceStats stores all values correctly`() {
        val reading = BatteryReading(
            level = 50,
            voltage = 4000,
            current = -500000,
            temperature = 30f,
            isCharging = false
        )

        val stats = DeviceStats(
            batteryReading = reading,
            thermalState = ThermalState.MODERATE,
            cpuTemperature = 45f,
            gpuTemperature = 40f,
            cpuUsagePercent = 75.5f,
            memoryUsageMb = 2048L,
            networkBytesPerSecond = 1024000L
        )

        assertThat(stats.batteryReading).isEqualTo(reading)
        assertThat(stats.thermalState).isEqualTo(ThermalState.MODERATE)
        assertThat(stats.cpuTemperature).isEqualTo(45f)
        assertThat(stats.gpuTemperature).isEqualTo(40f)
        assertThat(stats.cpuUsagePercent).isEqualTo(75.5f)
        assertThat(stats.memoryUsageMb).isEqualTo(2048L)
        assertThat(stats.networkBytesPerSecond).isEqualTo(1024000L)
    }

    @Test
    fun `DeviceStats allows null temperatures`() {
        val reading = BatteryReading(
            level = 50,
            voltage = 4000,
            current = -500000,
            temperature = 30f,
            isCharging = false
        )

        val stats = DeviceStats(
            batteryReading = reading,
            thermalState = ThermalState.NONE,
            cpuTemperature = null,
            gpuTemperature = null,
            cpuUsagePercent = 0f,
            memoryUsageMb = 0L,
            networkBytesPerSecond = 0L
        )

        assertThat(stats.cpuTemperature).isNull()
        assertThat(stats.gpuTemperature).isNull()
    }

    // ===== TEST SESSION TESTS =====

    @Test
    fun `TestSession stores all values correctly`() {
        val session = TestSession(
            id = "session-123",
            profileId = "cpu_heavy",
            profileName = "CPU Heavy",
            startTime = 1000L,
            endTime = 2000L,
            startBatteryLevel = 100,
            endBatteryLevel = 90,
            stressors = listOf("CPU", "GPU"),
            wasAborted = false,
            abortReason = null
        )

        assertThat(session.id).isEqualTo("session-123")
        assertThat(session.profileId).isEqualTo("cpu_heavy")
        assertThat(session.profileName).isEqualTo("CPU Heavy")
        assertThat(session.startTime).isEqualTo(1000L)
        assertThat(session.endTime).isEqualTo(2000L)
        assertThat(session.startBatteryLevel).isEqualTo(100)
        assertThat(session.endBatteryLevel).isEqualTo(90)
        assertThat(session.stressors).containsExactly("CPU", "GPU")
        assertThat(session.wasAborted).isFalse()
        assertThat(session.abortReason).isNull()
    }

    @Test
    fun `TestSession readings list is mutable`() {
        val session = TestSession(
            id = "session-123",
            profileId = "test",
            profileName = "Test",
            startTime = 1000L,
            startBatteryLevel = 100,
            stressors = listOf("CPU")
        )

        val reading = BatteryReading(
            level = 99,
            voltage = 4100,
            current = -500000,
            temperature = 30f,
            isCharging = false
        )

        session.readings.add(reading)
        assertThat(session.readings).hasSize(1)
        assertThat(session.readings.first()).isEqualTo(reading)
    }

    @Test
    fun `TestSession endTime defaults to null`() {
        val session = TestSession(
            id = "session-123",
            profileId = "test",
            profileName = "Test",
            startTime = 1000L,
            startBatteryLevel = 100,
            stressors = listOf("CPU")
        )

        assertThat(session.endTime).isNull()
    }

    @Test
    fun `TestSession endBatteryLevel defaults to null`() {
        val session = TestSession(
            id = "session-123",
            profileId = "test",
            profileName = "Test",
            startTime = 1000L,
            startBatteryLevel = 100,
            stressors = listOf("CPU")
        )

        assertThat(session.endBatteryLevel).isNull()
    }

    @Test
    fun `TestSession wasAborted defaults to false`() {
        val session = TestSession(
            id = "session-123",
            profileId = "test",
            profileName = "Test",
            startTime = 1000L,
            startBatteryLevel = 100,
            stressors = listOf("CPU")
        )

        assertThat(session.wasAborted).isFalse()
    }

    @Test
    fun `TestSession handles aborted state`() {
        val session = TestSession(
            id = "session-123",
            profileId = "test",
            profileName = "Test",
            startTime = 1000L,
            startBatteryLevel = 100,
            stressors = listOf("CPU"),
            wasAborted = true,
            abortReason = "Thermal protection triggered"
        )

        assertThat(session.wasAborted).isTrue()
        assertThat(session.abortReason).isEqualTo("Thermal protection triggered")
    }

    // ===== TEST CONFIG TESTS =====

    @Test
    fun `TestConfig has sensible defaults`() {
        val config = TestConfig()

        assertThat(config.targetBatteryDrop).isEqualTo(10)
        assertThat(config.maxDurationMinutes).isEqualTo(60)
        assertThat(config.maxTemperatureCelsius).isEqualTo(45f)
        assertThat(config.samplingIntervalMs).isEqualTo(1000L)
        assertThat(config.enableThermalProtection).isTrue()
    }

    @Test
    fun `TestConfig stores custom values correctly`() {
        val config = TestConfig(
            targetBatteryDrop = 20,
            maxDurationMinutes = 120,
            maxTemperatureCelsius = 50f,
            samplingIntervalMs = 2000L,
            enableThermalProtection = false
        )

        assertThat(config.targetBatteryDrop).isEqualTo(20)
        assertThat(config.maxDurationMinutes).isEqualTo(120)
        assertThat(config.maxTemperatureCelsius).isEqualTo(50f)
        assertThat(config.samplingIntervalMs).isEqualTo(2000L)
        assertThat(config.enableThermalProtection).isFalse()
    }

    @Test
    fun `TestConfig copy allows partial modification`() {
        val original = TestConfig()
        val modified = original.copy(targetBatteryDrop = 5)

        assertThat(modified.targetBatteryDrop).isEqualTo(5)
        assertThat(modified.maxDurationMinutes).isEqualTo(original.maxDurationMinutes)
        assertThat(modified.enableThermalProtection).isEqualTo(original.enableThermalProtection)
    }

    // ===== DATA CLASS EQUALITY TESTS =====

    @Test
    fun `BatteryReading equality works correctly`() {
        val reading1 = BatteryReading(
            timestamp = 1000L,
            level = 50,
            voltage = 4000,
            current = -500000,
            temperature = 30f,
            isCharging = false
        )

        val reading2 = BatteryReading(
            timestamp = 1000L,
            level = 50,
            voltage = 4000,
            current = -500000,
            temperature = 30f,
            isCharging = false
        )

        val reading3 = reading1.copy(level = 49)

        assertThat(reading1).isEqualTo(reading2)
        assertThat(reading1).isNotEqualTo(reading3)
    }

    @Test
    fun `TestConfig equality works correctly`() {
        val config1 = TestConfig()
        val config2 = TestConfig()
        val config3 = TestConfig(targetBatteryDrop = 15)

        assertThat(config1).isEqualTo(config2)
        assertThat(config1).isNotEqualTo(config3)
    }
}

/**
 * Tests for CustomProfile data class
 */
class CustomProfileTest {

    @Test
    fun `CustomProfile stores profile correctly`() {
        val profile = StressProfile(
            id = "custom-1",
            name = "My Custom Profile",
            description = "A custom test profile",
            cpuLoad = 50,
            gpuLoad = 30
        )

        val customProfile = CustomProfile(profile = profile)

        assertThat(customProfile.profile).isEqualTo(profile)
        assertThat(customProfile.profile.cpuLoad).isEqualTo(50)
    }

    @Test
    fun `CustomProfile createdAt defaults to current time`() {
        val before = System.currentTimeMillis()
        val customProfile = CustomProfile(
            profile = StressProfile(
                id = "test",
                name = "Test",
                description = "Test"
            )
        )
        val after = System.currentTimeMillis()

        assertThat(customProfile.createdAt).isAtLeast(before)
        assertThat(customProfile.createdAt).isAtMost(after)
    }

    @Test
    fun `CustomProfile lastUsedAt defaults to null`() {
        val customProfile = CustomProfile(
            profile = StressProfile(
                id = "test",
                name = "Test",
                description = "Test"
            )
        )

        assertThat(customProfile.lastUsedAt).isNull()
    }

    @Test
    fun `CustomProfile lastUsedAt can be set`() {
        val customProfile = CustomProfile(
            profile = StressProfile(
                id = "test",
                name = "Test",
                description = "Test"
            ),
            lastUsedAt = 1234567890L
        )

        assertThat(customProfile.lastUsedAt).isEqualTo(1234567890L)
    }
}
