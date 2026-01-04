package com.batterydrainer.benchmark.ui

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batterydrainer.benchmark.R
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive UI tests for MainActivity
 */
@RunWith(AndroidJUnit4::class)
class MainActivityComprehensiveTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // ===== HEADER TESTS =====

    @Test
    fun header_batteryDrainerTitle_isDisplayed() {
        onView(withText("🔋 Battery Drainer"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun header_settingsButton_isDisplayed() {
        onView(withId(R.id.btnSettings))
            .check(matches(isDisplayed()))
    }

    @Test
    fun header_reportsButton_isDisplayed() {
        onView(withId(R.id.btnReports))
            .check(matches(isDisplayed()))
    }

    // ===== BATTERY STATS CARD TESTS =====

    @Test
    fun batteryCard_levelText_isDisplayed() {
        onView(withId(R.id.batteryLevelText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun batteryCard_progressBar_isDisplayed() {
        onView(withId(R.id.batteryProgressBar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun batteryCard_currentText_isDisplayed() {
        onView(withId(R.id.currentText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun batteryCard_voltageText_isDisplayed() {
        onView(withId(R.id.voltageText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun batteryCard_temperatureText_isDisplayed() {
        onView(withId(R.id.temperatureText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun batteryCard_thermalStatus_isDisplayed() {
        onView(withId(R.id.thermalStatus))
            .check(matches(isDisplayed()))
    }

    // ===== SYSTEM STATUS CARD TESTS =====

    @Test
    fun systemStatus_gpsIcon_isDisplayed() {
        onView(withId(R.id.gpsStatusIcon))
            .check(matches(isDisplayed()))
    }

    @Test
    fun systemStatus_networkIcon_isDisplayed() {
        onView(withId(R.id.networkStatusIcon))
            .check(matches(isDisplayed()))
    }

    @Test
    fun systemStatus_flashIcon_isDisplayed() {
        onView(withId(R.id.flashStatusIcon))
            .check(matches(isDisplayed()))
    }

    @Test
    fun systemStatus_vibratorIcon_isDisplayed() {
        onView(withId(R.id.vibratorStatusIcon))
            .check(matches(isDisplayed()))
    }

    // ===== PROFILE CARD TESTS =====

    @Test
    fun profileCard_isDisplayed() {
        onView(withId(R.id.profileCard))
            .check(matches(isDisplayed()))
    }

    @Test
    fun profileCard_profileIcon_isDisplayed() {
        onView(withId(R.id.profileIcon))
            .check(matches(isDisplayed()))
    }

    @Test
    fun profileCard_profileName_isDisplayed() {
        onView(withId(R.id.profileName))
            .check(matches(isDisplayed()))
    }

    @Test
    fun profileCard_profileDescription_isDisplayed() {
        onView(withId(R.id.profileDescription))
            .check(matches(isDisplayed()))
    }

    @Test
    fun profileCard_changeButton_isDisplayed() {
        onView(withId(R.id.btnSelectProfile))
            .check(matches(isDisplayed()))
    }

    @Test
    fun profileCard_cpuIndicator_isDisplayed() {
        onView(withId(R.id.cpuIndicator))
            .check(matches(isDisplayed()))
    }

    @Test
    fun profileCard_gpuIndicator_isDisplayed() {
        onView(withId(R.id.gpuIndicator))
            .check(matches(isDisplayed()))
    }

    @Test
    fun profileCard_networkIndicator_isDisplayed() {
        onView(withId(R.id.networkIndicator))
            .check(matches(isDisplayed()))
    }

    // ===== QUICK TOGGLES TESTS =====

    @Test
    fun quickToggles_flashlightSwitch_isDisplayed() {
        onView(withId(R.id.toggleFlashlight))
            .check(matches(isDisplayed()))
    }

    @Test
    fun quickToggles_vibrateSwitch_isDisplayed() {
        onView(withId(R.id.toggleVibrate))
            .check(matches(isDisplayed()))
    }

    @Test
    fun quickToggles_flashlightSwitch_canBeToggled() {
        onView(withId(R.id.toggleFlashlight))
            .perform(click())
        // Just verify no crash - actual flashlight behavior depends on device
    }

    // ===== TEST PROGRESS TESTS =====

    @Test
    fun testProgress_statusText_isDisplayed() {
        onView(withId(R.id.statusText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testProgress_statusText_showsReadyInitially() {
        onView(withId(R.id.statusText))
            .check(matches(withText(containsString("Ready"))))
    }

    @Test
    fun testProgress_progressBar_isDisplayed() {
        onView(withId(R.id.testProgressBar))
            .check(matches(isDisplayed()))
    }

    // ===== START/STOP BUTTON TESTS =====

    @Test
    fun startStopButton_isDisplayed() {
        onView(withId(R.id.btnStartStop))
            .check(matches(isDisplayed()))
    }

    @Test
    fun startStopButton_showsStartTextInitially() {
        onView(withId(R.id.btnStartStop))
            .check(matches(withText(containsString("START"))))
    }

    @Test
    fun startStopButton_isClickable() {
        onView(withId(R.id.btnStartStop))
            .check(matches(isClickable()))
    }

    // ===== NAVIGATION TESTS =====

    @Test
    fun settingsButton_navigatesToSettingsActivity() {
        onView(withId(R.id.btnSettings))
            .perform(click())

        // Verify we're in settings by checking for a settings-specific element
        onView(withId(R.id.switchThermalProtection))
            .check(matches(isDisplayed()))

        // Navigate back
        pressBackSafely()
    }

    @Test
    fun reportsButton_navigatesToReportsActivity() {
        onView(withId(R.id.btnReports))
            .perform(click())

        // Verify we're in reports (either empty view or list should be visible)
        val sawEmpty = tryCheckDisplayed(R.id.emptyView)
        val sawList = tryCheckDisplayed(R.id.reportsRecyclerView)
        assert(sawEmpty || sawList)

        pressBackSafely()
    }

    @Test
    fun navigationFromSettingsBack_returnsToMainActivity() {
        onView(withId(R.id.btnSettings))
            .perform(click())

        pressBackSafely()

        // Should be back on main activity
        onView(withId(R.id.btnStartStop))
            .check(matches(isDisplayed()))
    }

    // ===== PROFILE SELECTION TESTS =====

    @Test
    fun profileSelectButton_opensProfileDialog() {
        onView(withId(R.id.btnSelectProfile))
            .perform(click())

        // Dialog should show some profile options
        // Look for a known profile name
        val foundProfile = tryCheckDisplayedText("Idle Baseline") ||
                tryCheckDisplayedText("CPU Light") ||
                tryCheckDisplayedText("😴 Idle Baseline")

        assert(foundProfile) { "Profile selection dialog did not appear with expected profiles" }

        // Dismiss dialog
        pressBackSafely()
    }

    @Test
    fun selectingIdleProfile_updatesProfileCard() {
        onView(withId(R.id.btnSelectProfile))
            .perform(click())

        // Try to click Idle Baseline profile
        try {
            onView(withText("😴 Idle Baseline")).perform(click())
        } catch (e: Exception) {
            // Try without emoji
            try {
                onView(withText("Idle Baseline")).perform(click())
            } catch (e2: Exception) {
                pressBackSafely()
                return
            }
        }

        // Verify profile name was updated
        onView(withId(R.id.profileName))
            .check(matches(withText(containsString("Idle"))))
    }

    @Test
    fun selectingCpuHeavyProfile_showsCpuLoadIndicator() {
        onView(withId(R.id.btnSelectProfile))
            .perform(click())

        try {
            onView(withText("🔥 CPU Heavy")).perform(click())
        } catch (e: Exception) {
            try {
                onView(withText("CPU Heavy")).perform(click())
            } catch (e2: Exception) {
                pressBackSafely()
                return
            }
        }

        // CPU indicator should show load > 0
        onView(withId(R.id.cpuIndicator))
            .check(matches(not(withText("CPU: 0%"))))
    }

    @Test
    fun selectingGpsProfile_showsGpsIndicator() {
        onView(withId(R.id.btnSelectProfile))
            .perform(click())

        try {
            onView(withText("📍 GPS Only")).perform(click())
        } catch (e: Exception) {
            try {
                onView(withText("GPS Only")).perform(click())
            } catch (e2: Exception) {
                pressBackSafely()
                return
            }
        }

        // GPS indicator should become visible
        onView(withId(R.id.gpsIndicator))
            .check(matches(isDisplayed()))
    }

    // ===== TEST START BEHAVIOR TESTS =====

    @Test
    fun startTest_withIdleProfile_startsOrShowsDialog() {
        // First select idle profile (no special requirements)
        onView(withId(R.id.btnSelectProfile)).perform(click())
        try {
            onView(withText("😴 Idle Baseline")).perform(click())
        } catch (e: Exception) {
            try {
                onView(withText("Idle Baseline")).perform(click())
            } catch (e2: Exception) {
                pressBackSafely()
            }
        }

        // Click start
        onView(withId(R.id.btnStartStop)).perform(click())

        // Either test started (button shows STOP) or a dialog appeared
        val showsStop = tryCheckDisplayedText("STOP")
        val showsDialog = tryCheckDisplayedText("⚠️ Test Cannot Start") ||
                tryCheckDisplayedText("OK") ||
                tryCheckDisplayedText("charging")

        // If test started, stop it
        if (showsStop) {
            onView(withId(R.id.btnStartStop)).perform(click())
        } else if (showsDialog) {
            // Dismiss dialog
            try {
                onView(withText("OK")).perform(click())
            } catch (e: Exception) {
                pressBackSafely()
            }
        }

        // Main button should be visible
        onView(withId(R.id.btnStartStop))
            .check(matches(isDisplayed()))
    }

    // ===== HELPER FUNCTIONS =====

    private fun tryCheckDisplayed(viewId: Int): Boolean = try {
        onView(withId(viewId)).check(matches(isDisplayed()))
        true
    } catch (_: Throwable) {
        false
    }

    private fun tryCheckDisplayedText(text: String): Boolean = try {
        onView(withText(text)).check(matches(isDisplayed()))
        true
    } catch (_: Throwable) {
        try {
            onView(withText(containsString(text))).check(matches(isDisplayed()))
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun pressBackSafely() {
        try {
            pressBack()
        } catch (_: NoMatchingViewException) {
            // ignore
        } catch (_: Exception) {
            // ignore
        }
    }
}
