package com.aivy.navigator.data.network

import com.aivy.navigator.data.model.GeocodeResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface NaverApiService {
    @GET("map-geocode/v2/geocode")
    suspend fun getGeocode(
        @Header("X-NCP-APIGW-API-KEY-ID") clientId: String,
        @Header("X-NCP-APIGW-API-KEY") clientSecret: String,
        @Query("query") query: String
    ): GeocodeResponse
}