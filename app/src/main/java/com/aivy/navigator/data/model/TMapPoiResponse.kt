package com.aivy.navigator.data.model

import com.google.gson.annotations.SerializedName

data class TMapPoiResponse(
    @SerializedName("searchPoiInfo")
    val searchPoiInfo: SearchPoiInfo
)

data class SearchPoiInfo(
    @SerializedName("pois")
    val pois: Pois
)

data class Pois(
    @SerializedName("poi")
    val poiList: List<PoiItem>
)

data class PoiItem(
    val name: String,
    val upperAddrName: String,
    val middleAddrName: String,
    val lowerAddrName: String,
    val noorLat: String, // 위도 (문자열로 옴)
    val noorLon: String  // 경도 (문자열로 옴)
) {
    // 전체 주소를 합쳐주는 편의 기능
    fun getFullAddress(): String {
        return "$upperAddrName $middleAddrName $lowerAddrName".trim()
    }
}