package com.aivy.navigator.data.model

data class NaverDirectionResponse(
    val code: Int,
    val message: String?,
    val route: RouteData?
)

data class RouteData(
    val trafast: List<DirectionPath>? // 실시간 빠른길 옵션
)

data class DirectionPath(
    val summary: DirectionSummary,
    val path: List<List<Double>>, // [[경도, 위도], [경도, 위도]...] 형태의 배열
    val guide: List<DirectionGuide>? // TTS 안내를 위한 분기점(Turn) 정보
)

data class DirectionSummary(
    val distance: Int, // 전체 거리(m)
    val duration: Int  // 소요 시간(밀리초)
)

data class DirectionGuide(
    val pointIndex: Int, // path 배열 중 몇 번째 좌표에서 안내할지
    val type: Int,       // 회전 타입 (좌회전, 우회전 등)
    val instructions: String, // 안내 멘트 (예: "우회전입니다")
    val distance: Int
)