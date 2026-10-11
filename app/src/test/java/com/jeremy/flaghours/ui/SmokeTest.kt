package com.jeremy.flaghours.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.jeremy.flaghours.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Clicks through every main screen on a fresh install to catch crashes and blank screens. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    private fun finishSetup() {
        waitFor("Step 1 of 7")
        rule.onNodeWithText("Flag rate per hour").performTextInput("32.50")
        rule.onNodeWithText("Next").performClick()
        waitFor("Step 2 of 7")
        // Walk the remaining steps with Next so every step renders once.
        for (i in 2..7) {
            waitFor("Step $i of 7")
            rule.onNodeWithText(if (i == 7) "Finish" else "Next").performClick()
        }
        waitFor("Est. gross")
    }

    @Test fun setupDashboardClockAndTabs() {
        finishSetup()
        rule.onNodeWithText("Clock in").performClick()
        waitFor("Clock out")
        rule.onNodeWithText("Clock out").performClick()
        waitFor("Clock in")

        rule.onNodeWithText("Jobs").performClick()
        waitFor("No jobs yet")
        rule.onNodeWithText("Time").performClick()
        waitFor("Shifts")
        rule.onNodeWithText("Paycheck").performClick()
        waitFor("From your pay stub")
        rule.onNode(hasText("Flag + hourly hours paid") and hasSetTextAction()).performTextInput("40")
        rule.onNodeWithText("Check").performClick()
        waitFor("+40.0 h")
        waitFor("No recorded jobs explain the gap")
        rule.onNodeWithText("Dashboard").performClick()
        waitFor("Est. gross")
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Edit pay plan")
        rule.onNodeWithText("Edit pay plan").performClick()
        waitFor("Review changes")
    }

    @Test fun createRoAndMarkPaid() {
        finishSetup()
        rule.onNodeWithText("Jobs").performClick()
        waitFor("New RO")
        rule.onNodeWithText("New RO", useUnmergedTree = true).performClick()
        waitFor("RO number")
        rule.onNode(hasText("RO number") and hasSetTextAction()).performTextInput("88123")
        rule.onNode(hasText("Make") and hasSetTextAction()).performTextInput("Ford")
        rule.onNode(hasText("Job description") and hasSetTextAction()).performScrollTo().performTextInput("Front brakes")
        rule.onNode(hasText("Flagged hours") and hasSetTextAction()).performScrollTo().performTextInput("1.6")
        rule.onNodeWithText("Save RO").performScrollTo().performClick()
        waitFor("RO 88123")
        waitFor("My total on this RO: 1.6 h")
        rule.onAllNodesWithText("Paid").onFirst().performClick()
        rule.waitForIdle()

        // Open the line editor and close it again.
        rule.onNodeWithText("Front brakes").performClick()
        waitFor("Edit line")
        rule.onAllNodesWithContentDescription("Back").onLast().performClick()
        waitFor("My total on this RO")
        rule.waitForIdle()
    }

    @Test fun cachedReferenceAutoMatchesScannedStyleLine() {
        finishSetup()
        val app = rule.activity.application as com.jeremy.flaghours.FlagHoursApp
        kotlinx.coroutines.runBlocking {
            val key = "ford|f-150|2017"
            app.database.olp().replaceVehicle(
                com.jeremy.flaghours.data.db.OlpVehicle(key, "ford", "f-150", 2017, 3, 0),
                listOf(
                    com.jeremy.flaghours.data.db.LaborRowEntity(key, "", "radiator", "Radiator", "Cooling", 210, 180, 260, "high"),
                    com.jeremy.flaghours.data.db.LaborRowEntity(key, "", "radiator-cap", "Radiator Cap", "Cooling", 10, 10, 20, "estimated"),
                    com.jeremy.flaghours.data.db.LaborRowEntity(key, "", "brake-pads-front", "Brake Pads - Front", "Brakes", 100, 80, 130, "high"),
                ),
            )
            app.database.olp().upsertAlias(com.jeremy.flaghours.data.db.OlpVehicleAlias("2017|ford|f 150", key, ""))
        }
        rule.onNodeWithText("Jobs").performClick()
        waitFor("New RO")
        rule.onNodeWithText("New RO", useUnmergedTree = true).performClick()
        waitFor("RO number")
        rule.onNode(hasText("RO number") and hasSetTextAction()).performTextInput("5001")
        rule.onNode(hasText("Year") and hasSetTextAction()).performTextInput("2017")
        rule.onNode(hasText("Make") and hasSetTextAction()).performTextInput("Ford")
        rule.onNode(hasText("Model") and hasSetTextAction()).performScrollTo().performTextInput("F-150")
        rule.onNode(hasText("Job description") and hasSetTextAction()).performScrollTo().performTextInput("radiator replacement")
        rule.onNode(hasText("Flagged hours") and hasSetTextAction()).performScrollTo().performTextInput("2.0")
        rule.onNodeWithText("Save RO").performScrollTo().performClick()
        waitFor("RO 5001")
        waitFor("Radiator · 1.8–2.6 h")
        waitFor("In range")
    }
}
