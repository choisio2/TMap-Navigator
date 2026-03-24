package com.aivy.navigator.data.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.skt.Tmap.TMapPoint

// API 요청용 Body
data class PedestrianRouteRequest(
    val startX: String,
    val startY: String,
    val endX: String,
    val endY: String,
    val startName: String = "출발지",
    val endName: String = "목적지",
    val reqCoordType: String = "WGS84GEO",
    val resCoordType: String = "WGS84GEO"
)

// API 응답용 데이터
data class PedestrianRouteResponse(
    val features: List<Feature>
)

data class Feature(
    val type: String,
    val geometry: Geometry,
    val properties: Properties
)

data class Geometry(
    val type: String,
    val coordinates: JsonElement
)

data class Properties(
    val totalDistance: Int?, // 총 거리 (m)
    val totalTime: Int?,     // 총 소요 시간 (초)
    val description: String?, // 안내 문구
    val turnType: Int?,      // 회전 타입
    val pointIndex: Int?     // 포인트 인덱스
)

// 앱 내부에서 사용할 경로 안내 데이터 클래스
data class RouteStep(
    val pointIndex: Int,
    val coordinate: TMapPoint,
    val description: String,
    val turnType: Int,
    val distance: Int
)

// TMap 주변 POI 검색 응답용 DTO
data class TMapAroundPoiResponse(
    val searchPoiInfo: SearchPoiInfo
)