package com.aivy.navigator.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StateReducersTest {
    @Test
    fun reduceNavigateState_movesToExpectedStates() {
        assertEquals(
            NavigatePanelState.RouteSelect,
            reduceNavigateState(NavigatePanelState.Search, NavigateAction.SearchSubmitted),
        )
        assertEquals(
            NavigatePanelState.Navigating,
            reduceNavigateState(NavigatePanelState.RouteSelect, NavigateAction.StartNavigation),
        )
        assertEquals(
            NavigatePanelState.Arrived,
            reduceNavigateState(NavigatePanelState.Navigating, NavigateAction.MarkArrived),
        )
        assertEquals(
            NavigatePanelState.Search,
            reduceNavigateState(NavigatePanelState.Arrived, NavigateAction.Reset),
        )
    }

    @Test
    fun reduceTranslateState_movesToExpectedModes() {
        assertEquals(
            TranslateUiMode.Listening,
            reduceTranslateState(TranslateUiMode.Idle, TranslateAction.StartListening),
        )
        assertEquals(
            TranslateUiMode.Log,
            reduceTranslateState(TranslateUiMode.Listening, TranslateAction.OpenLog),
        )
        assertEquals(
            TranslateUiMode.Idle,
            reduceTranslateState(TranslateUiMode.ShowCard, TranslateAction.Reset),
        )
    }

    @Test
    fun reduceSettingsState_clampsSpeed() {
        val base = AivyMockData.settingsState()
        val reduced = reduceSettingsState(base, SettingsAction.SetTtsSpeed(150))

        assertEquals(100, reduced.ttsSpeed)
    }
}
