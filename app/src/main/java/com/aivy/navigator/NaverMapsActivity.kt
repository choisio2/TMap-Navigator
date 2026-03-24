package com.aivy.navigator

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.naver.maps.map.*
import com.naver.maps.map.util.FusedLocationSource

class NaverMapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var naverMap: NaverMap
    private lateinit var locationSource: FusedLocationSource

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            naverMap.locationTrackingMode = LocationTrackingMode.Follow
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 인증 설정을 가장 먼저 수행
        NaverMapSdk.getInstance(this).client = NaverMapSdk.NaverCloudPlatformClient("s23srtgglv")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_naver_maps)

        // 디버깅용
        NaverMapSdk.getInstance(this).onAuthFailedListener = NaverMapSdk.OnAuthFailedListener { exception ->
            Log.e("NAVER_MAP_AUTH", "인증 실패 이유: ${exception.message}")
            // 예: "Invalid Client ID", "Unauthorized Package Name" 등
        }

        // 1. FusedLocationSource 초기화
        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)

        // 2. MapFragment 찾기 및 지도 비동기 호출
        val fm = supportFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map_fragment) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map_fragment, it).commit()
            }

        mapFragment.getMapAsync(this)

        // 3. 커스텀 내 위치 버튼 리스너
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btn_location).setOnClickListener {
            if (::naverMap.isInitialized) {
                naverMap.locationTrackingMode = LocationTrackingMode.Follow
            }
        }
    }

    override fun onMapReady(map: NaverMap) {
        this.naverMap = map
        Log.d("NAVER_MAP_AUTH", "지도 준비 완료 (인증 성공)")

        // 4. 지도에 위치 소스 연결 및 설정
        naverMap.locationSource = locationSource
        naverMap.uiSettings.isLocationButtonEnabled = false // 기본 버튼 숨김

        // 5. 권한 요청
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}