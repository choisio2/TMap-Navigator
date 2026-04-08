package com.aivy.navigator.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aivy.navigator.ui.theme.AivyTheme
import org.junit.Rule
import org.junit.Test

class ExerciseScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sessionSettingsButton_opensSettingsSheet() {
        composeRule.setContent {
            AivyTheme {
                ExerciseScreen(
                    onBack = {},
                    onHome = {},
                )
            }
        }

        composeRule.onNodeWithText("세션 설정").performClick()
        composeRule.onNodeWithText("러닝 세션 설정").assertIsDisplayed()
    }

    @Test
    fun racePrepCard_opensRacePrepSheet() {
        composeRule.setContent {
            AivyTheme {
                ExerciseScreen(
                    onBack = {},
                    onHome = {},
                )
            }
        }

        composeRule.onNodeWithText("레이스 준비").performClick()
        composeRule.onNodeWithText("레이스 준비 상세").assertIsDisplayed()
    }
}
