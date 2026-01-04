package com.batterydrainer.benchmark.monitor

import android.content.Context
import com.batterydrainer.benchmark.data.ThermalState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for ThermalProtection
 *
 * Note: These tests verify the logic and state management of ThermalProtection.
 * Actual temperature reading requires a real device because:
 * - HardwarePropertiesManager requires DEVICE_POWER permission (signature|privileged)
 * - BatteryManager reads from actual hardware
 * - PowerManager thermal callbacks are device-specific
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class ThermalProtectionTest {

    private lateinit var context: Context
    private lateinit var thermalProtection: ThermalProtection

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        thermalProtection = ThermalProtection(context)
    }

    @After
    fun tearDown() {
        thermalProtection.stopMonitoring()
    }

    // ===== THRESHOLD CONSTANTS TESTS =====

    @Test
    fun `threshold constants are in logical order`() {
        assertThat(ThermalProtection.THRESHOLD_WARNING)
            .isLessThan(ThermalProtection.THRESHOLD_THROTTLE)
        assertThat(ThermalProtection.THRESHOLD_THROTTLE)
            .isLessThan(ThermalProtection.THRESHOLD_PAUSE)
        assertThat(ThermalProtection.THRESHOLD_PAUSE)
            .isLessThan(ThermalProtection.THRESHOLD_STOP)
        assertThat(ThermalProtection.THRESHOLD_STOP)
            .isLessThan(ThermalProtection.THRESHOLD_EMERGENCY)
    }

    @Test
    fun `cooldown target is below warning threshold`() {
        assertThat(ThermalProtection.COOLDOWN_TARGET)
            .isLessThan(ThermalProtection.THRESHOLD_WARNING)
    }

    @Test
    fun `threshold values are reasonable for mobile devices`() {
        // Typical mobile device safe operating temps are 0-45C
        assertThat(ThermalProtection.THRESHOLD_WARNING).isAtLeast(35f)
        assertThat(ThermalProtection.THRESHOLD_EMERGENCY).isAtMost(55f)
    }

    // ===== ENABLE/DISABLE TESTS =====

    @Test
    fun `thermal protection is enabled by default`() {
        assertThat(thermalProtection.isEnabled.value).isTrue()
    }

    @Test
    fun `setEnabled changes state correctly`() {
        thermalProtection.setEnabled(false)
        assertThat(thermalProtection.isEnabled.value).isFalse()

        thermalProtection.setEnabled(true)
        assertThat(thermalProtection.isEnabled.value).isTrue()
    }

    @Test
    fun `disabling clears pause and stop flags`() {
        thermalProtection.setEnabled(true)
        thermalProtection.setEnabled(false)

        assertThat(thermalProtection.shouldPauseTest.value).isFalse()
        assertThat(thermalProtection.shouldStopTest.value).isFalse()
    }

    // ===== CUSTOM THRESHOLD TESTS =====

    @Test
    fun `custom thresholds can be set`() {
        thermalProtection.pauseThreshold = 42f
        thermalProtection.stopThreshold = 46f

        assertThat(thermalProtection.pauseThreshold).isEqualTo(42f)
        assertThat(thermalProtection.stopThreshold).isEqualTo(46f)
    }

    // ===== STATE FLOW TESTS =====

    @Test
    fun `initial thermal state is NONE`() {
        assertThat(thermalProtection.thermalState.value).isEqualTo(ThermalState.NONE)
    }

    @Test
    fun `initial temperature is zero`() {
        assertThat(thermalProtection.currentTemperature.value).isEqualTo(0f)
    }

    @Test
    fun `initial temperature source is UNKNOWN`() {
        assertThat(thermalProtection.temperatureSource.value).isEqualTo(TemperatureSource.UNKNOWN)
    }

    @Test
    fun `initial cooldown state is false`() {
        assertThat(thermalProtection.isInCooldown.value).isFalse()
    }

    @Test
    fun `initial pause flag is false`() {
        assertThat(thermalProtection.shouldPauseTest.value).isFalse()
    }

    @Test
    fun `initial stop flag is false`() {
        assertThat(thermalProtection.shouldStopTest.value).isFalse()
    }

    // ===== RESET TESTS =====

    @Test
    fun `reset clears all states`() {
        thermalProtection.reset()

        assertThat(thermalProtection.isInCooldown.value).isFalse()
        assertThat(thermalProtection.shouldPauseTest.value).isFalse()
        assertThat(thermalProtection.shouldStopTest.value).isFalse()
        assertThat(thermalProtection.thermalState.value).isEqualTo(ThermalState.NONE)
    }

    // ===== DESCRIPTION TESTS =====

    @Test
    fun `getThermalStateDescription returns non-empty for all states`() {
        val description = thermalProtection.getThermalStateDescription()
        assertThat(description).isNotEmpty()
        assertThat(description).contains("Normal")
    }

    @Test
    fun `getRecommendedAction returns non-empty for initial state`() {
        val action = thermalProtection.getRecommendedAction()
        assertThat(action).isNotEmpty()
    }

    @Test
    fun `getTemperatureSourceDescription returns non-empty`() {
        val description = thermalProtection.getTemperatureSourceDescription()
        assertThat(description).isNotEmpty()
    }

    @Test
    fun `getTemperatureSourceDescription contains icon for UNKNOWN source`() {
        // Initial source is UNKNOWN
        val description = thermalProtection.getTemperatureSourceDescription()
        assertThat(description).contains(TemperatureSource.UNKNOWN.icon)
    }

    // ===== MONITORING LIFECYCLE TESTS =====

    @Test
    fun `startMonitoring does not crash`() {
        thermalProtection.startMonitoring(1000)
        thermalProtection.stopMonitoring()
    }

    @Test
    fun `stopMonitoring resets cooldown state`() {
        thermalProtection.startMonitoring(1000)
        thermalProtection.stopMonitoring()

        assertThat(thermalProtection.isInCooldown.value).isFalse()
        assertThat(thermalProtection.shouldPauseTest.value).isFalse()
        assertThat(thermalProtection.shouldStopTest.value).isFalse()
    }

    @Test
    fun `multiple startMonitoring calls do not crash`() {
        thermalProtection.startMonitoring(1000)
        thermalProtection.startMonitoring(500)
        thermalProtection.stopMonitoring()
    }

    @Test
    fun `stopMonitoring when not started does not crash`() {
        thermalProtection.stopMonitoring()
    }

    // ===== CALLBACK TESTS =====

    @Test
    fun `callbacks can be set to null`() {
        thermalProtection.onWarning = null
        thermalProtection.onThrottle = null
        thermalProtection.onPause = null
        thermalProtection.onStop = null
        thermalProtection.onCooldownComplete = null
    }

    @Test
    fun `callbacks can be set to functions`() {
        var warningCalled = false
        var throttleCalled = false

        thermalProtection.onWarning = { _, _ -> warningCalled = true }
        thermalProtection.onThrottle = { throttleCalled = true }

        assertThat(thermalProtection.onWarning).isNotNull()
        assertThat(thermalProtection.onThrottle).isNotNull()
    }
}

/**
 * Tests for ThermalState enum
 */
class ThermalStateTest {

    @Test
    fun `ThermalState has expected values`() {
        val states = ThermalState.entries
        assertThat(states).hasSize(7)
    }

    @Test
    fun `ThermalState ordering is correct for comparison`() {
        assertThat(ThermalState.NONE.ordinal).isLessThan(ThermalState.LIGHT.ordinal)
        assertThat(ThermalState.LIGHT.ordinal).isLessThan(ThermalState.MODERATE.ordinal)
        assertThat(ThermalState.MODERATE.ordinal).isLessThan(ThermalState.SEVERE.ordinal)
        assertThat(ThermalState.SEVERE.ordinal).isLessThan(ThermalState.CRITICAL.ordinal)
        assertThat(ThermalState.CRITICAL.ordinal).isLessThan(ThermalState.EMERGENCY.ordinal)
        assertThat(ThermalState.EMERGENCY.ordinal).isLessThan(ThermalState.SHUTDOWN.ordinal)
    }

    @Test
    fun `ThermalState comparison works as expected`() {
        assertThat(ThermalState.SEVERE > ThermalState.MODERATE).isTrue()
        assertThat(ThermalState.NONE < ThermalState.LIGHT).isTrue()
        assertThat(ThermalState.CRITICAL >= ThermalState.CRITICAL).isTrue()
    }
}

/**
 * Tests for TemperatureSource enum
 */
class TemperatureSourceTest {

    @Test
    fun `TemperatureSource has expected values`() {
        val sources = TemperatureSource.entries
        assertThat(sources).hasSize(4)
        assertThat(sources).contains(TemperatureSource.BATTERY)
        assertThat(sources).contains(TemperatureSource.HARDWARE_PROPERTIES)
        assertThat(sources).contains(TemperatureSource.SYSTEM_THERMAL)
        assertThat(sources).contains(TemperatureSource.UNKNOWN)
    }

    @Test
    fun `TemperatureSource displayNames are not empty`() {
        TemperatureSource.entries.forEach { source ->
            assertThat(source.displayName).isNotEmpty()
        }
    }

    @Test
    fun `TemperatureSource icons are not empty`() {
        TemperatureSource.entries.forEach { source ->
            assertThat(source.icon).isNotEmpty()
        }
    }

    @Test
    fun `BATTERY source has expected properties`() {
        assertThat(TemperatureSource.BATTERY.displayName).isEqualTo("Battery Sensor")
        assertThat(TemperatureSource.BATTERY.icon).isEqualTo("🔋")
    }

    @Test
    fun `HARDWARE_PROPERTIES source has expected properties`() {
        assertThat(TemperatureSource.HARDWARE_PROPERTIES.displayName).isEqualTo("CPU Sensor")
        assertThat(TemperatureSource.HARDWARE_PROPERTIES.icon).isEqualTo("🖥️")
    }

    @Test
    fun `SYSTEM_THERMAL source has expected properties`() {
        assertThat(TemperatureSource.SYSTEM_THERMAL.displayName).isEqualTo("System Thermal")
        assertThat(TemperatureSource.SYSTEM_THERMAL.icon).isEqualTo("📊")
    }

    @Test
    fun `UNKNOWN source has expected properties`() {
        assertThat(TemperatureSource.UNKNOWN.displayName).isEqualTo("Unknown")
        assertThat(TemperatureSource.UNKNOWN.icon).isEqualTo("❓")
    }
}
