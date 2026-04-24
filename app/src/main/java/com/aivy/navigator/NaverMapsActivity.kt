package com.aivy.navigator

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.naver.maps.map.*
import com.naver.maps.map.util.FusedLocationSource
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.overlay.PathOverlay
import android.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.aivy.navigator.data.network.RetrofitClient
import com.aivy.navigator.data.model.RouteStep
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.naver.maps.map.overlay.Marker
import kotlinx.coroutines.Dispatchers
import com.aivy.navigator.data.model.NaverGeocodeAddress


class NaverMapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var naverMap: NaverMap
    private lateinit var locationSource: FusedLocationSource

    // === 센서 관련 ===
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    var currentAzimuth: Float = 0f // 현재 바라보는 절대 방향 (0=북, 90=동, 180=남, 270=서)
    private var lastUpdateAzimuth: Float = 0f

    // === 경로 찾기 관련 ===
    // 네이버 지도에 선을 그릴 객체
    private var pathOverlay: PathOverlay? = null

    // 안내 정보를 담을 리스트 (기존 RouteStep 재사용)
    private val routeSteps = mutableListOf<RouteStep>()

    // === 검색 및 목적지 관련 ===
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: ImageView
    private var destinationMarker: Marker? = null
    private var destinationLatLng: LatLng? = null

    // =============== onCreate ===============
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 네이버 지도 인증
        NaverMapSdk.getInstance(this).client = NaverMapSdk.NcpKeyClient("s23srtgglv")

        setContentView(R.layout.activity_naver_maps)

        // 디버깅용
        NaverMapSdk.getInstance(this).onAuthFailedListener =
            NaverMapSdk.OnAuthFailedListener { exception ->
                Log.e("NAVER_MAP_AUTH", "인증 실패: ${exception.message}")
                Toast.makeText(this, "네이버 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
            }

        // FusedLocationSource 초기화 (네이버 내장 위치 추적기)
        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)

        // XML의 MapView를 찾아오고, 생명주기(onCreate) 연결
        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)

        // 지도가 준비되면 onMapReady 함수를 부르도록 비동기 호출
        mapView.getMapAsync(this)

        // 커스텀 내 위치 버튼 리스너
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btn_location).setOnClickListener {
            if (this::naverMap.isInitialized) {
                // 내 위치로 카메라 이동 및 추적
                naverMap.locationTrackingMode = LocationTrackingMode.Follow
            }
        }

        // Sensor Manager 회전 벡터 센서 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationVectorSensor == null) {
            Toast.makeText(this, "이 기기에는 방향 센서가 없습니다.", Toast.LENGTH_SHORT).show()
        }

        // 검색창 UI 연결 (XML의 ID와 동일해야 합니다!)
        // XML에서 TextView로 되어있다면 EditText로 꼭 변경해주세요.
        etSearch = findViewById(R.id.etSearch)
        btnSearch = findViewById(R.id.btnSearch) // 돋보기 아이콘

        // 검색 버튼 클릭 시
        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                hideKeyboard()
                searchAddressWithNaver(query)
            }
        }

        // 키보드 엔터(검색) 눌렀을 때
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard()
                    searchAddressWithNaver(query)
                }
                true
            } else false
        }
    }

    // 센서 값이 바뀔 때마다 각도를 계산해주는 리스너 객체 생성
    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                val orientationValues = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationValues)

                // 라디안을 각도(Degree)로 변환
                var azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                azimuth = (azimuth + 360) % 360
                currentAzimuth = azimuth

                // 0.5도 이상 변했을 때만 부드럽게 업데이트
                if (Math.abs(currentAzimuth - lastUpdateAzimuth) > 0.5f) {
                    lastUpdateAzimuth = currentAzimuth

                    if (this@NaverMapsActivity::naverMap.isInitialized) {
                        val locationOverlay = naverMap.locationOverlay

                        locationOverlay.isVisible = true
                        locationOverlay.bearing = currentAzimuth
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // 정확도 변경 시 처리 (보통 비워둠)
        }
    }

    // 지도가 화면에 성공적으로 그려졌을 때 불리는 함수
    override fun onMapReady(map: NaverMap) {
        this.naverMap = map
        Log.d("NAVER_MAP_AUTH", "지도 준비 완료 (인증 성공)")

        // 위치 소스 연결 및 기본 UI 버튼 숨김 (우리가 만든 FAB를 쓸 거니까)
        naverMap.locationSource = locationSource
        naverMap.uiSettings.isLocationButtonEnabled = false

        // 권한이 있다면 바로 추적 모드 켜기
        naverMap.locationTrackingMode = LocationTrackingMode.Follow
    }

    // 네이버 FusedLocationSource를 쓰려면 이 함수를 반드시 오버라이드 해야 함
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated) { // 권한이 거부되었을 때
                naverMap.locationTrackingMode = LocationTrackingMode.None
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    // =================================================================
    // MapView를 사용할 때 반드시 추가해야 하는 생명주기(Lifecycle) 함수들
    // =================================================================
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        // 앱 켜지면 센서 가동 시작
        rotationVectorSensor?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()

        // 앱 내리면 센서 정지 (배터리 누수 방지)
        sensorManager.unregisterListener(sensorEventListener)
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }


    // 키보드 내리기
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    // 네이버 Geocoding API 통신
    private fun searchAddressWithNaver(query: String) {
        val clientId = "s23srtgglv"
        val clientSecret = "Wby93FLVv0oFRaJinfzxi1ESif27EWuVb9dmud9a"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.naverService.getGeocode(clientId, clientSecret, query)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val addresses = body.addresses

                        if (body.status == "OK" && !addresses.isNullOrEmpty()) {
                            showSearchResults(addresses)
                        } else {
                            Toast.makeText(this@NaverMapsActivity, "주소를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@NaverMapsActivity, "검색 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("NAVER_GEO", "주소 검색 오류", e)
                    Toast.makeText(this@NaverMapsActivity, "통신 오류", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 다이얼로그 띄우고 마커 찍기
    private fun showSearchResults(items: List<NaverGeocodeAddress>) {
        val names = items.map { it.roadAddress.ifEmpty { it.jibunAddress } }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("주소 검색 결과")
            .setItems(names) { _, index ->
                val selectedAddr = items[index]
                // 네이버 응답 x=경도(lon), y=위도(lat)
                val lat = selectedAddr.y.toDoubleOrNull() ?: return@setItems
                val lon = selectedAddr.x.toDoubleOrNull() ?: return@setItems

                destinationLatLng = LatLng(lat, lon)
                etSearch.setText(names[index])

                // 기존 마커 지우고 새로 찍기
                destinationMarker?.map = null
                destinationMarker = Marker().apply {
                    position = destinationLatLng!!
                    map = naverMap
                    iconTintColor = Color.RED // 목적지는 빨간색!
                }

                // 목적지로 카메라 이동
                val cameraUpdate = CameraUpdate.scrollTo(destinationLatLng!!)
                    .animate(CameraAnimation.Easing)
                naverMap.moveCamera(cameraUpdate)

                Toast.makeText(this, "목적지가 설정되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }
}