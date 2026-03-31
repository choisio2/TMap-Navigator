package com.aivy.navigator.data.network

import com.aivy.navigator.data.model.GeocodeResponse
import com.aivy.navigator.data.model.NaverDirectionResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.Response

interface NaverApiService {
    @GET("map-geocode/v2/geocode")
    suspend fun getGeocode(
        @Header("X-NCP-APIGW-API-KEY-ID") clientId: String,
        @Header("X-NCP-APIGW-API-KEY") clientSecret: String,
        @Query("query") query: String
    ): GeocodeResponse

    // Directions 5 (자동차 길찾기) API 추가
    @GET("map-direction/v1/driving")
    suspend fun getDrivingRoute(
        @Header("X-NCP-APIGW-API-KEY-ID") clientId: String,
        @Header("X-NCP-APIGW-API-KEY") clientSecret: String,
        @Query("start") start: String, // "경도,위도" (lon,lat)
        @Query("goal") goal: String,   // "경도,위도" (lon,lat)
        @Query("option") option: String = "trafast"
    ): Response<NaverDirectionResponse>
}