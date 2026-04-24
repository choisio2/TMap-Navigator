package com.aivy.navigator.data.model

import com.google.gson.JsonElement

// TMap 보행자 경로 API 최상위 응답 객체
data class TmapRouteResponse(
    val type: String,
    val features: List<RouteFeature>
)

data class RouteFeature(
    val type: String,
    val geometry: RouteGeometry,
    val properties: RouteProperties
)

data class RouteGeometry(
    val type: String,
    // 점(Point)과 선(LineString)의 배열 구조가 달라서 JsonElement로 유연하게 받습니다.
    val coordinates: JsonElement
)

data class RouteProperties(
    val index: Int?,
    val pointIndex: Int?,
    val lineIndex: Int?,
    val name: String?,
    val description: String?,
    val distance: Int?,
    val time: Int?,
    val turnType: Int?,
    val totalDistance: Int?,
    val totalTime: Int?
)