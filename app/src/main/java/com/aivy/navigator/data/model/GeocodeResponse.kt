package com.aivy.navigator.data.model

data class GeocodeResponse(
    val status: String,
    val addresses: List<Address>
)

data class Address(
    val roadAddress: String, // 도로명 주소
    val x: String,           // 경도 (Longitude)
    val y: String            // 위도 (Latitude)
)