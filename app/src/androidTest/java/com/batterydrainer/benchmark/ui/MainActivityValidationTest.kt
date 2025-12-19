package com.batterydrainer.benchmark.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batterydrainer.benchmark.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityValidationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun startTest_whenGpsIsUnavailable_showsWarningDialog() {
        // 1. Click the button to select a profile
        onView(withId(R.id.btnSelectProfile)).perform(click())

        // 2. In the dialog, click on a profile that requires GPS.
        //    "The Commute" is a good candidate.
        onView(withText("🚗 The Commute")).perform(click())

        // 3. Click the main start button
        onView(withId(R.id.btnStartStop)).perform(click())

        // 4. Verify that the warning dialog is displayed by checking for its title.
        onView(withText("⚠️ Test Cannot Start")).check(matches(isDisplayed()))
    }
}
