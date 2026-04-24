package com.aivy.navigator.data.model

data class NaverGeocodeResponse(
    val status: String,
    val addresses: List<NaverGeocodeAddress>?
)

data class NaverGeocodeAddress(
    val roadAddress: String, // 도로명 주소
    val jibunAddress: String, // 지번 주소
    val x: String, // 경도 (lon)
    val y: String  // 위도 (lat)
)