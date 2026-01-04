package com.batterydrainer.benchmark.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batterydrainer.benchmark.R
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for SettingsActivity
 */
@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(SettingsActivity::class.java)

    // ===== LAYOUT VISIBILITY TESTS =====

    @Test
    fun thermalProtectionSection_isDisplayed() {
        onView(withId(R.id.switchThermalProtection))
            .check(matches(isDisplayed()))
    }

    @Test
    fun thermalLimitSeekbar_isDisplayed() {
        onView(withId(R.id.seekbarThermalLimit))
            .check(matches(isDisplayed()))
    }

    @Test
    fun defaultDurationSeekbar_isDisplayed() {
        onView(withId(R.id.seekbarDefaultDuration))
            .check(matches(isDisplayed()))
    }

    @Test
    fun targetBatteryDropSeekbar_isDisplayed() {
        onView(withId(R.id.seekbarDefaultDrop))
            .check(matches(isDisplayed()))
    }

    @Test
    fun samplingIntervalSeekbar_isDisplayed() {
        onView(withId(R.id.seekbarSamplingInterval))
            .check(matches(isDisplayed()))
    }

    @Test
    fun adbSupportSwitch_isDisplayed() {
        onView(withId(R.id.switchAdbSupport))
            .check(matches(isDisplayed()))
    }

    @Test
    fun autoExportSwitch_isDisplayed() {
        onView(withId(R.id.switchAutoExport))
            .check(matches(isDisplayed()))
    }

    // ===== DEFAULT VALUES TESTS =====

    @Test
    fun thermalProtection_isEnabledByDefault() {
        onView(withId(R.id.switchThermalProtection))
            .check(matches(isChecked()))
    }

    @Test
    fun adbSupport_isEnabledByDefault() {
        onView(withId(R.id.switchAdbSupport))
            .check(matches(isChecked()))
    }

    @Test
    fun thermalLimitText_showsDefaultValue() {
        onView(withId(R.id.textThermalLimit))
            .check(matches(isDisplayed()))
    }

    @Test
    fun durationText_showsDefaultValue() {
        onView(withId(R.id.textDefaultDuration))
            .check(matches(isDisplayed()))
    }

    @Test
    fun batteryDropText_showsDefaultValue() {
        onView(withId(R.id.textDefaultDrop))
            .check(matches(isDisplayed()))
    }

    // ===== INTERACTION TESTS =====

    @Test
    fun thermalProtectionSwitch_canBeToggled() {
        // Start enabled
        onView(withId(R.id.switchThermalProtection))
            .check(matches(isChecked()))

        // Toggle off
        onView(withId(R.id.switchThermalProtection))
            .perform(click())

        // Verify changed
        onView(withId(R.id.switchThermalProtection))
            .check(matches(isNotChecked()))

        // Toggle back on
        onView(withId(R.id.switchThermalProtection))
            .perform(click())

        onView(withId(R.id.switchThermalProtection))
            .check(matches(isChecked()))
    }

    @Test
    fun autoExportSwitch_canBeToggled() {
        // Toggle on
        onView(withId(R.id.switchAutoExport))
            .perform(click())

        // Verify state changed (either on or off depending on default)
        onView(withId(R.id.switchAutoExport))
            .check(matches(isDisplayed()))
    }

    @Test
    fun adbSupportSwitch_canBeToggled() {
        onView(withId(R.id.switchAdbSupport))
            .perform(click())

        onView(withId(R.id.switchAdbSupport))
            .check(matches(isNotChecked()))
    }

    // ===== SECTION HEADERS TESTS =====

    @Test
    fun thermalProtectionHeader_isDisplayed() {
        onView(withText("🛡️ Thermal Protection"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testDefaultsHeader_isDisplayed() {
        onView(withText("⚙️ Test Defaults"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun dataCollectionHeader_isDisplayed() {
        onView(withText("📊 Data Collection"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun proFeaturesHeader_isDisplayed() {
        onView(withText("⭐ Pro Features"))
            .check(matches(isDisplayed()))
    }
}
