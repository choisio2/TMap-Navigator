package com.aivy.navigator.ui.model

import com.aivy.navigator.ui.navigation.AivyDestination

data class HomeUiState(
    val deviceName: String,
    val batteryPercent: Int,
    val connected: Boolean,
    val bannerText: String,
    val shortcuts: List<HomeShortcut>,
    val insights: List<HomeInsight>,
    val activities: List<HomeActivity>,
)

data class HomeShortcut(
    val title: String,
    val subtitle: String,
    val destination: AivyDestination,
)

data class HomeInsight(
    val title: String,
    val description: String,
    val metric: String? = null,
)

data class HomeActivity(
    val type: String,
    val time: String,
    val summary: String,
    val detail: String,
)

enum class NavigatePanelState {
    Search,
    RouteSelect,
    Navigating,
    Arrived,
}

data class NavigateRouteOption(
    val id: String,
    val label: String,
    val duration: String,
    val distance: String,
    val detail: String,
)

data class NavigateUiState(
    val query: String,
    val selectedRouteId: String?,
    val panelState: NavigatePanelState,
    val routes: List<NavigateRouteOption>,
    val remainingDistance: String,
    val remainingTime: String,
)

enum class TranslateUiMode {
    Idle,
    Listening,
    Speaking,
    Log,
    ShowCard,
}

data class TranslateMessage(
    val id: String,
    val speaker: String,
    val original: String,
    val translated: String,
    val timestamp: String,
)

data class TranslateUiState(
    val mode: TranslateUiMode,
    val currentOriginal: String,
    val currentTranslated: String,
    val messages: List<TranslateMessage>,
)

data class MemoryEntry(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val createdAt: String,
    val status: String,
)

data class MemoryUiState(
    val selectedCategory: String,
    val categories: List<String>,
    val entries: List<MemoryEntry>,
)

data class SettingsUiState(
    val autoConnect: Boolean,
    val cloudSync: Boolean,
    val safetyFilter: Boolean,
    val fallDetection: Boolean,
    val showAdvanced: Boolean,
    val ttsSpeed: Int,
    val appLanguage: String,
    val translationTarget: String,
)

object AivyMockData {
    fun homeState(): HomeUiState = HomeUiState(
        deviceName = "AIVY-Pro",
        batteryPercent = 82,
        connected = true,
        bannerText = "내일 10시 김 대리 미팅 — 미완료 To-do 1건",
        shortcuts = listOf(
            HomeShortcut("길안내", "목적지 검색", AivyDestination.Navigate),
            HomeShortcut("번역", "KO ↔ JA", AivyDestination.Translate),
            HomeShortcut("OCR", "문서 읽기", AivyDestination.Ocr),
            HomeShortcut("운동", "러닝", AivyDestination.Exercise),
            HomeShortcut("미팅", "요약 준비", AivyDestination.Meeting),
            HomeShortcut("갤러리", "추억 보기", AivyDestination.Gallery),
            HomeShortcut("기억", "로그 확인", AivyDestination.Memory),
            HomeShortcut("온보딩", "가이드", AivyDestination.Onboarding),
        ),
        insights = listOf(
            HomeInsight("이번 주 활동", "외출 빈도 14% 증가, 실내 활동 균형 유지", "+14%"),
            HomeInsight("번역 패턴", "일본어 대화 요청 7건, 길안내 연계 3건", "7회"),
            HomeInsight("건강 신호", "최근 3일 수면 시간이 평균보다 40분 부족", "-40분"),
        ),
        activities = listOf(
            HomeActivity("활동", "15:02", "약 복용 알림 확인", "오후 약 복용 리마인더를 완료했습니다"),
            HomeActivity("번역", "14:52", "카페 위치 문의 대화 저장", "KO ↔ JA 대화 4턴이 기억에 저장되었습니다"),
            HomeActivity("길안내", "13:30", "신촌역 도보 경로 안내 완료", "총 1.2km, 15분 이동"),
        ),
    )

    fun navigateState(): NavigateUiState = NavigateUiState(
        query = "",
        selectedRouteId = null,
        panelState = NavigatePanelState.Search,
        routes = listOf(
            NavigateRouteOption("walk-fast", "도보 빠른 경로", "15분", "1.2km", "큰길 중심"),
            NavigateRouteOption("walk-safe", "도보 안전 경로", "18분", "1.3km", "횡단보도 우선"),
            NavigateRouteOption("transit", "버스 환승 경로", "20분", "2.1km", "버스 2정거장"),
        ),
        remainingDistance = "1.1km",
        remainingTime = "14분",
    )

    fun translateState(): TranslateUiState = TranslateUiState(
        mode = TranslateUiMode.Idle,
        currentOriginal = "",
        currentTranslated = "",
        messages = listOf(
            TranslateMessage("1", "상대", "すみません、この近くに駅はありますか？", "실례합니다, 이 근처에 역이 있나요?", "09:21"),
            TranslateMessage("2", "나", "네, 200미터 직진 후 오른쪽입니다.", "はい、200メートル直進して右です。", "09:22"),
            TranslateMessage("3", "상대", "ありがとうございます。", "감사합니다.", "09:22"),
        ),
    )

    fun memoryState(): MemoryUiState = MemoryUiState(
        selectedCategory = "전체",
        categories = listOf("전체", "대화", "이동", "건강", "일정"),
        entries = listOf(
            MemoryEntry("m1", "대화", "도쿄역 길안내 대화", "일본어 안내 대화 4턴", "오늘 09:22", "저장됨"),
            MemoryEntry("m2", "이동", "신촌역 도보 이동", "총 1.2km, 15분", "오늘 08:40", "동기화됨"),
            MemoryEntry("m3", "건강", "약 복용 확인", "오전 복용 완료", "어제 21:10", "저장됨"),
            MemoryEntry("m4", "일정", "내일 미팅 준비", "미완료 할 일 1개", "어제 17:05", "검토 필요"),
        ),
    )

    fun settingsState(): SettingsUiState = SettingsUiState(
        autoConnect = true,
        cloudSync = true,
        safetyFilter = true,
        fallDetection = true,
        showAdvanced = false,
        ttsSpeed = 50,
        appLanguage = "한국어",
        translationTarget = "자동",
    )
}
