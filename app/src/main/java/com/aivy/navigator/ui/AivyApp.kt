package com.aivy.navigator.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aivy.navigator.ui.components.AivyBottomNav
import com.aivy.navigator.ui.navigation.AivyDestination
import com.aivy.navigator.ui.navigation.MapBridgeAction
import com.aivy.navigator.ui.screens.HomeScreen
import com.aivy.navigator.ui.screens.MemoryScreen
import com.aivy.navigator.ui.screens.NavigateScreen
import com.aivy.navigator.ui.screens.PlaceholderScreen
import com.aivy.navigator.ui.screens.SettingsScreen
import com.aivy.navigator.ui.screens.TranslateScreen
import com.aivy.navigator.ui.screens.ExerciseScreen

@Composable
fun AivyApp(
    mapBridgeAction: MapBridgeAction,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (AivyDestination.shouldShowBottomNav(currentRoute)) {
                AivyBottomNav(
                    currentRoute = currentRoute ?: AivyDestination.Home.route,
                    onTabSelected = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AivyDestination.Home.route,
            route = "root",
            modifier = androidx.compose.ui.Modifier.padding(padding),
        ) {
            composable(AivyDestination.Home.route) {
                HomeScreen(
                    onNavigate = { destination -> navController.navigate(destination.route) },
                )
            }

            composable(AivyDestination.Navigate.route) {
                NavigateScreen(mapBridgeAction = mapBridgeAction)
            }

            composable(AivyDestination.Translate.route) {
                TranslateScreen()
            }

            composable(AivyDestination.Memory.route) {
                MemoryScreen()
            }

            composable(AivyDestination.Settings.route) {
                SettingsScreen(onNavigate = { destination -> navController.navigate(destination.route) })
            }

            composable(AivyDestination.Pairing.route) {
                PlaceholderScreen(
                    title = "페어링",
                    description = "기기 연결 설정은 2차 구현에서 실제 로직과 연결됩니다.",
                    ctaLabel = "설정으로 돌아가기",
                    onClickCta = { navController.popBackStack() },
                )
            }

            composable(AivyDestination.Ocr.route) {
                PlaceholderScreen(
                    title = "OCR",
                    description = "문서 스캔 UI가 AIVY 디자인으로 준비되었습니다. 다음 단계에서 카메라 흐름을 연결합니다.",
                    ctaLabel = "홈으로",
                    onClickCta = { navController.navigate(AivyDestination.Home.route) },
                )
            }

            composable(AivyDestination.Gallery.route) {
                PlaceholderScreen(
                    title = "갤러리",
                    description = "기억 카드 갤러리 화면은 다음 단계에서 실제 데이터 연동 예정입니다.",
                    ctaLabel = "기억 탭으로",
                    onClickCta = { navController.navigate(AivyDestination.Memory.route) },
                )
            }

            composable(AivyDestination.Meeting.route) {
                PlaceholderScreen(
                    title = "미팅",
                    description = "회의 요약 화면이 같은 토큰/레이아웃 규칙으로 연결됩니다.",
                    ctaLabel = "홈으로",
                    onClickCta = { navController.navigate(AivyDestination.Home.route) },
                )
            }

            composable(AivyDestination.Exercise.route) {
                ExerciseScreen(
                    onBack = { navController.popBackStack() },
                    onHome = {
                        navController.navigate(AivyDestination.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(AivyDestination.Onboarding.route) {
                PlaceholderScreen(
                    title = "온보딩",
                    description = "초기 사용자 가이드 플로우를 Android 전용 인터랙션으로 이어서 구현합니다.",
                    ctaLabel = "시작하기",
                    onClickCta = { navController.navigate(AivyDestination.Home.route) },
                )
            }
        }
    }
}
