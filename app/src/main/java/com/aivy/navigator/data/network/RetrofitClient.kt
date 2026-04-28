package com.aivy.navigator.data.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.jvm.java

object RetrofitClient {
    private const val TAG = "AIVY_DEBUG"
    // TMAP용 Base URL 추가
    private const val TMAP_BASE_URL = "https://apis.openapi.sk.com/"

    // 공용 로그 인터셉터
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, "Network Log: $message")
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()


    // TMAP 서비스 추가
    val tmapService: TMapApiService by lazy {
        Retrofit.Builder()
            .baseUrl(TMAP_BASE_URL) // TMAP 주소로 설정
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TMapApiService::class.java)
    }
}