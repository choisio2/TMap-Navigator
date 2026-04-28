package com.aivy.navigator.data.network

import com.aivy.navigator.data.model.TMapPoiResponse
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import com.aivy.navigator.data.model.TmapRouteResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded

interface TMapApiService {

    // POI(장소) 통합 검색
    @GET("tmap/pois?version=1")
    suspend fun searchPOI(
        @Header("appKey") appKey: String,
        @Query("searchKeyword") keyword: String,
        @Query("resCoordType") resCoordType: String = "WGS84GEO",
        @Query("reqCoordType") reqCoordType: String = "WGS84GEO",
        @Query("count") count: Int = 10,
        @Query("reqCategories") categories: String? = null // 카테고리 필터
    ): Response<TMapPoiResponse>

    @FormUrlEncoded
    @POST("tmap/routes/pedestrian?version=1")
    suspend fun getPedestrianRoute(
        @Header("appKey") appKey: String,
        @Field("startX") startX: String,
        @Field("startY") startY: String,
        @Field("endX") endX: String,
        @Field("endY") endY: String,
        @Field("passList") passList: String? = null,
        @Field("reqCoordType") reqCoordType: String = "WGS84GEO",
        @Field("resCoordType") resCoordType: String = "WGS84GEO",
        @Field("startName") startName: String = "출발지",
        @Field("endName") endName: String = "목적지"
    ): Response<TmapRouteResponse>


    // 주변 POI 검색 API
    @GET("tmap/pois/search/around")
    suspend fun searchAroundPOI(
        @Header("appKey") appKey: String,
        @Query("version") version: Int = 1,
        @Query("centerLat") centerLat: String,
        @Query("centerLon") centerLon: String,
        @Query("reqCoordType") reqCoordType: String = "WGS84GEO",
        @Query("resCoordType") resCoordType: String = "WGS84GEO",
        @Query("radius") radius: String = "1",
        @Query("count") count: Int = 5,
        @Query("categories") categories: String? = null
    ): retrofit2.Response<TMapPoiResponse>

    @POST("https://api.openai.com/v1/chat/completions")
    suspend fun generateNaturalGuide(
        @Header("Authorization") bearerToken: String,
        @Body body: JsonObject
    ): Response<JsonObject>
}