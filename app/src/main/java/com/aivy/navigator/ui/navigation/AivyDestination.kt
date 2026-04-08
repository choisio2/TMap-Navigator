package com.aivy.navigator.ui.navigation

sealed class AivyDestination(
    val route: String,
    val label: String,
    val showBottomNav: Boolean,
    val isPrimaryTab: Boolean,
) {
    data object Home : AivyDestination("home", "홈", showBottomNav = true, isPrimaryTab = true)
    data object Navigate : AivyDestination("navigate", "길안내", showBottomNav = true, isPrimaryTab = true)
    data object Translate : AivyDestination("translate", "번역", showBottomNav = true, isPrimaryTab = true)
    data object Memory : AivyDestination("memory", "기억", showBottomNav = true, isPrimaryTab = true)
    data object Settings : AivyDestination("settings", "설정", showBottomNav = true, isPrimaryTab = true)

    data object Pairing : AivyDestination("pairing", "페어링", showBottomNav = false, isPrimaryTab = false)
    data object Ocr : AivyDestination("ocr", "OCR", showBottomNav = false, isPrimaryTab = false)
    data object Gallery : AivyDestination("gallery", "갤러리", showBottomNav = false, isPrimaryTab = false)
    data object Meeting : AivyDestination("meeting", "미팅", showBottomNav = false, isPrimaryTab = false)
    data object Exercise : AivyDestination("exercise", "운동", showBottomNav = false, isPrimaryTab = false)
    data object Onboarding : AivyDestination("onboarding", "온보딩", showBottomNav = false, isPrimaryTab = false)

    companion object {
        val all = listOf(
            Home,
            Navigate,
            Translate,
            Memory,
            Settings,
            Pairing,
            Ocr,
            Gallery,
            Meeting,
            Exercise,
            Onboarding,
        )

        val primaryTabs = all.filter { it.isPrimaryTab }

        fun fromRoute(route: String?): AivyDestination {
            val normalized = route?.substringBefore("?")
            return all.firstOrNull { it.route == normalized } ?: Home
        }

        fun shouldShowBottomNav(route: String?): Boolean = fromRoute(route).showBottomNav
    }
}
