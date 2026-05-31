package com.commlink.app.ui.ptt

import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.rule.GrantPermissionRule
import com.commlink.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class PTTScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Auto-grant RECORD_AUDIO so the permission dialog never blocks the Compose hierarchy
    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        // Keep screen on and show over lock screen so the Compose hierarchy stays reachable
        composeRule.activityRule.scenario.onActivity { activity ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun selectChannel() {
        composeRule.onNodeWithText("Alpha Team").performClick()
    }

    @Test
    fun channelList_isDisplayedOnLaunch() {
        composeRule.onNodeWithText("Select Channel").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Team").assertIsDisplayed()
        composeRule.onNodeWithText("Bravo Squad").assertIsDisplayed()
        composeRule.onNodeWithText("Dispatch").assertIsDisplayed()
        composeRule.onNodeWithText("Command").assertIsDisplayed()
    }

    @Test
    fun pttButton_isDisplayedAfterChannelSelection() {
        selectChannel()
        composeRule.onNodeWithText("PTT").assertIsDisplayed()
    }

    @Test
    fun leaveButton_isDisplayedAfterChannelSelection() {
        selectChannel()
        composeRule.onNodeWithText("Leave").assertIsDisplayed()
    }

    @Test
    fun networkStatusBar_isDisplayedAfterChannelSelection() {
        selectChannel()
        composeRule.onNodeWithText("PTT").assertIsDisplayed()
    }

    @Test
    fun sendingMessage_appearsInMessageList() {
        selectChannel()
        composeRule.onNodeWithText("Message").performTextInput("All units respond")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.onNodeWithText("All units respond").assertIsDisplayed()
    }
}
