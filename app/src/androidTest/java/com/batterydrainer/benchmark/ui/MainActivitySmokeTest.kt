package com.batterydrainer.benchmark.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.batterydrainer.benchmark.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun opensSettingsAndReturns() {
        onView(withId(R.id.btnSettings)).perform(click())
        onView(withId(R.id.switchThermalProtection)).check(matches(isDisplayed()))
        // Back
        pressBackSafely()
        onView(withId(R.id.btnStartStop)).check(matches(isDisplayed()))
    }

    @Test
    fun opensReportsAndShowsEitherEmptyOrList() {
        onView(withId(R.id.btnReports)).perform(click())
        // Either empty view or list should be visible; accept either to avoid flakiness.
        val sawEmpty = tryCheckDisplayed(R.id.emptyView)
        val sawList = tryCheckDisplayed(R.id.reportsRecyclerView)
        assert(sawEmpty || sawList)
        pressBackSafely()
    }

    @Test
    fun profileSelectionUpdatesIndicator() {
        onView(withId(R.id.btnSelectProfile)).perform(click())
        onView(withText("GPS Only")).perform(click())
        onView(withId(R.id.gpsIndicator)).check(matches(isDisplayed()))
    }

    @Test
    fun startStopHandlesDialogOrRunningState() {
        onView(withId(R.id.btnStartStop)).perform(click())
        val dialogShown = tryCheckDisplayedText("⚠️ Test Cannot Start")
        if (dialogShown) {
            // Dismiss blocking dialog
            onView(withText("OK")).perform(click())
        } else {
            // If test actually started, stop it to reset UI
            onView(withId(R.id.btnStartStop)).perform(click())
        }
        onView(withId(R.id.btnStartStop)).check(matches(isDisplayed()))
    }

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
        false
    }

    private fun pressBackSafely() {
        try {
            androidx.test.espresso.Espresso.pressBack()
        } catch (_: NoMatchingViewException) {
            // ignore
        }
    }
}
