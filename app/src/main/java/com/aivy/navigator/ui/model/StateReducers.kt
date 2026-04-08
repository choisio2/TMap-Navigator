package com.aivy.navigator.ui.model

sealed interface NavigateAction {
    data object SearchSubmitted : NavigateAction
    data object StartNavigation : NavigateAction
    data object MarkArrived : NavigateAction
    data object Reset : NavigateAction
}

fun reduceNavigateState(
    current: NavigatePanelState,
    action: NavigateAction,
): NavigatePanelState = when (action) {
    NavigateAction.SearchSubmitted -> NavigatePanelState.RouteSelect
    NavigateAction.StartNavigation -> NavigatePanelState.Navigating
    NavigateAction.MarkArrived -> NavigatePanelState.Arrived
    NavigateAction.Reset -> NavigatePanelState.Search
}

sealed interface TranslateAction {
    data object StartListening : TranslateAction
    data object StartSpeaking : TranslateAction
    data object OpenLog : TranslateAction
    data object ShowCard : TranslateAction
    data object Reset : TranslateAction
}

fun reduceTranslateState(
    current: TranslateUiMode,
    action: TranslateAction,
): TranslateUiMode = when (action) {
    TranslateAction.StartListening -> TranslateUiMode.Listening
    TranslateAction.StartSpeaking -> TranslateUiMode.Speaking
    TranslateAction.OpenLog -> TranslateUiMode.Log
    TranslateAction.ShowCard -> TranslateUiMode.ShowCard
    TranslateAction.Reset -> TranslateUiMode.Idle
}

sealed interface SettingsAction {
    data class ToggleAdvanced(val enabled: Boolean) : SettingsAction
    data class SetTtsSpeed(val speed: Int) : SettingsAction
}

fun reduceSettingsState(
    current: SettingsUiState,
    action: SettingsAction,
): SettingsUiState = when (action) {
    is SettingsAction.ToggleAdvanced -> current.copy(showAdvanced = action.enabled)
    is SettingsAction.SetTtsSpeed -> current.copy(ttsSpeed = action.speed.coerceIn(0, 100))
}
