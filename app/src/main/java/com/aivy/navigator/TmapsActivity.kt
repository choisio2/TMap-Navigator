package com.aivy.navigator

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import com.aivy.navigator.data.network.PoiItem
import com.aivy.navigator.data.network.RetrofitClient
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.skt.Tmap.TMapData
import com.skt.Tmap.TMapMarkerItem
import com.skt.Tmap.TMapPOILayer
import com.skt.Tmap.TMapPoint
import com.skt.Tmap.TMapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.isNullOrEmpty
import kotlin.collections.map
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.aivy.navigator.data.network.PedestrianRouteRequest
import com.aivy.navigator.data.network.RouteStep
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.skt.Tmap.TMapPolyLine
import com.aivy.navigator.data.network.*
import com.google.android.gms.location.Priority
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import java.util.Locale

class TmapsActivity : AppCompatActivity(),TextToSpeech.OnInitListener {

    private lateinit var tMapView: TMapView
    private lateinit var fabRoute: ExtendedFloatingActionButton
    private lateinit var etSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var cardRouteInfo: CardView
    private lateinit var tvRouteSummary: TextView
    private lateinit var btnStartNavigation: Button

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: TMapPoint? = null // 출발지 (내 위치)
    private var destinationPoint: TMapPoint? = null // 목적지

    // 🟢 내비게이션용 UI 변수
    private lateinit var navTopCard: CardView
    private lateinit var tvNavInstruction: TextView
    private lateinit var navBottomCard: CardView
    private lateinit var tvNavRemainInfo: TextView
    private lateinit var btnStopNav: Button

    // 실시간 추적 & TTS 변수
    private lateinit var tts: TextToSpeech
    private var isNavigating = false
    private lateinit var locationCallback: LocationCallback
    // 🚗 가상 주행을 위한 거
    private val allRoutePoints = mutableListOf<TMapPoint>()
    private var isMockMode = true

    // 경로 안내 리스트 저장용
    private val routeSteps = mutableListOf<RouteStep>()

    // 안내 제어 변수
    private var lastTTSTime = 0L                    // 마지막 TTS 시간
    private val TTS_COOLDOWN = 2000L                // TTS 최소 간격 (5초)
    private val announcedStepIndices = mutableSetOf<Int>()  // 이미 안내한 분기점

    // 속도 조절
    private val MOCK_SPEED_MS = 500L   // 가상주행 속도 (300ms = 빠름, 1000ms = 보통)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tmaps)

        // UI 초기화
        fabRoute = findViewById(R.id.fabRoute)
        etSearch = findViewById(R.id.etSearch)
        progressBar = findViewById(R.id.progressBar)
        cardRouteInfo = findViewById(R.id.cardRouteInfo)
        tvRouteSummary = findViewById(R.id.tvRouteSummary)
        btnStartNavigation = findViewById(R.id.btnStartNavigation)

        // 내비 UI 매핑
        navTopCard = findViewById(R.id.navTopCard)
        tvNavInstruction = findViewById(R.id.tvNavInstruction)
        navBottomCard = findViewById(R.id.navBottomCard)
        tvNavRemainInfo = findViewById(R.id.tvNavRemainInfo)
        btnStopNav = findViewById(R.id.btnStopNav)

        // TTS 초기화
        tts = TextToSpeech(this, this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupTMapView()
        setupSearch()
        setupRouteButton()

        // 실시간 위치 업데이트 콜백 설정
        setupLocationCallback()
    }

    companion object {
        private const val TAG = "TMAP_debug"
    }

    private fun setupToolbar() {
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }
    }

    private fun setupTMapView() {
        val tmapLayout = findViewById<FrameLayout>(R.id.tmapLayout)
        tMapView = TMapView(this)
        tMapView.setSKTMapApiKey(BuildConfig.TMAP_APP_KEY)

        tMapView.setOnApiKeyListener(object : TMapView.OnApiKeyListenerCallback {
            override fun SKTMapApikeySucceed() {
                Log.d(TAG, "TMap API 인증 성공")
                runOnUiThread {
                    fetchCurrentLocation() // 인증 성공 시 내 위치 먼저 가져오기
                }
            }

            override fun SKTMapApikeyFailed(errorMsg: String?) {
                Log.e(TAG, "인증 실패: $errorMsg")
            }
        })
        tmapLayout.addView(tMapView)
    }


    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        currentLocation = TMapPoint(location.latitude, location.longitude)

                        val startMarker = TMapMarkerItem().apply {
                            tMapPoint = currentLocation
                            name = "내 위치"
                        }
                        tMapView.addMarkerItem("startMarker", startMarker)
                        tMapView.setCenterPoint(currentLocation!!.longitude, currentLocation!!.latitude)
                        tMapView.zoomLevel = 15
                        tMapView.postInvalidate()
                    } else {
                        Toast.makeText(this, "현재 위치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun setupSearch() {
        val btnSearch = findViewById<ImageButton>(R.id.btnSearch)

        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                hideKeyboard()
                searchPOI(query)
            } else {
                Toast.makeText(this, "목적지를 입력하세요", Toast.LENGTH_SHORT).show()
            }
        }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard()
                    searchPOI(query)
                }
                true
            } else false
        }
    }

    // Retrofit을 사용한 API 직접 호출
    private fun searchPOI(query: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // API 호출
                val response = RetrofitClient.tmapService.searchPOI(
                    appKey = BuildConfig.TMAP_APP_KEY,
                    keyword = query
                )

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val poiList = response.body()?.searchPoiInfo?.pois?.poiList
                        if (poiList.isNullOrEmpty()) {
                            Toast.makeText(this@TmapsActivity, "검색 결과가 없습니다", Toast.LENGTH_SHORT).show()
                        } else {
                            showSearchResults(poiList)
                        }
                    } else {
                        Toast.makeText(this@TmapsActivity, "검색 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "POI 검색 오류", e)
                    Toast.makeText(this@TmapsActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSearchResults(items: List<PoiItem>) {
        // 결과 목록 문자열 배열 생성
        val names = items.map { "${it.name}\n${it.getFullAddress()}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("검색 결과")
            .setItems(names) { _, index ->
                setDestinationMarker(items[index])
            }
            .setNegativeButton("취소", null)
            .show()
    }


    private fun setDestinationMarker(item: PoiItem) {
        val lat = item.noorLat.toDoubleOrNull() ?: return
        val lon = item.noorLon.toDoubleOrNull() ?: return
        destinationPoint = TMapPoint(lat, lon)

        // 이전 정보들 지우기
        tMapView.removeAllTMapPolyLine() // 이전에 그린 파란색 경로 지우기
        cardRouteInfo.visibility = View.GONE
        tMapView.removeMarkerItem("destination")

        // 새 마커 설정
        val markerItem = TMapMarkerItem().apply {
            tMapPoint = destinationPoint
            name = item.name
        }

        tMapView.addMarkerItem("destination", markerItem)
        tMapView.setCenterPoint(lon, lat)
        tMapView.zoomLevel = 15
        tMapView.postInvalidate()

        etSearch.setText(item.name)
        fabRoute.visibility = View.VISIBLE // 경로 탐색 버튼 보이기
        hideKeyboard()
    }

    private fun setupRouteButton() {
        fabRoute.setOnClickListener {
            if (currentLocation == null || destinationPoint == null) {
                Toast.makeText(this, "출발지 또는 목적지를 알 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            findPedestrianRoute()
        }

        btnStartNavigation.setOnClickListener {
            startNavigation()
        }

        // 방금 만든 안내 종료 버튼
        btnStopNav.setOnClickListener {
            stopNavigation()
        }
    }

    // 보행자 경로 API 호출 및 파싱
    private fun findPedestrianRoute() {
        progressBar.visibility = View.VISIBLE
        fabRoute.visibility = View.GONE

        val request = PedestrianRouteRequest(
            startX = currentLocation!!.longitude.toString(),
            startY = currentLocation!!.latitude.toString(),
            endX = destinationPoint!!.longitude.toString(),
            endY = destinationPoint!!.latitude.toString()
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.tmapService.getPedestrianRoute(
                    appKey = BuildConfig.TMAP_APP_KEY,
                    request = request
                )

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        drawRouteAndSaveSteps(response.body()!!.features)
                    } else {
                        Toast.makeText(this@TmapsActivity, "경로 탐색 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Log.e(TAG, "경로 탐색 에러", e)
                    Toast.makeText(this@TmapsActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 경로 선 긋기 및 정보 저장
    private fun drawRouteAndSaveSteps(features: List<Feature>) {
        tMapView.removeAllTMapPolyLine()
        routeSteps.clear()

        val polyLine = TMapPolyLine().apply {
            lineColor = Color.BLUE
            lineWidth = 15f
        }

        var totalTime = 0
        var totalDistance = 0

        features.forEach { feature ->
            val geometry = feature.geometry
            val props = feature.properties

            // 총 시간/거리 추출 (첫 번째 Feature에 들어있음)
            if (props.totalTime != null && props.totalDistance != null) {
                totalTime = props.totalTime
                totalDistance = props.totalDistance
            }

            // LineString 처리 (지도에 파란 선 그리기)
            if (geometry.type == "LineString") {
                val coordsArray = geometry.coordinates.asJsonArray
                for (i in 0 until coordsArray.size()) {
                    val coord = coordsArray[i].asJsonArray
                    val lon = coord[0].asDouble
                    val lat = coord[1].asDouble
                   // polyLine.addLinePoint(TMapPoint(lat, lon))

                    // 🚗가상주행
                    val point = TMapPoint(lat, lon)
                    polyLine.addLinePoint(point)
                    allRoutePoints.add(point)

                }
            }
            // Point 처리 (안내 정보 저장)
            else if (geometry.type == "Point") {
                val coord = geometry.coordinates.asJsonArray
                val lon = coord[0].asDouble
                val lat = coord[1].asDouble

                if (props.description != null && props.turnType != null && props.pointIndex != null) {
                    routeSteps.add(
                        RouteStep(
                            pointIndex = props.pointIndex,
                            coordinate = TMapPoint(lat, lon),
                            description = props.description,
                            turnType = props.turnType,
                            distance = props.totalDistance ?: 0
                        )
                    )
                }
            }
        }

        // 지도에 선 추가
        tMapView.addTMapPolyLine("pedestrian_route", polyLine)
        tMapView.postInvalidate() // 필수!

        // 하단 카드 업데이트
        val minutes = totalTime / 60
        val km = totalDistance / 1000.0
        tvRouteSummary.text = String.format("도보 %d분 (%.1fkm)", minutes, km)
        cardRouteInfo.visibility = View.VISIBLE
    }


    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    // TTS 설정 (OnInitListener 구현)
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN
            tts.setSpeechRate(0.85f)
            tts.setPitch(1.0f)
        } else {
            Log.e(TAG, "TTS 초기화 실패")
        }
    }
    // 액티비티가 꺼질 때 메모리 정리
    override fun onDestroy() {
        if (this::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (isNavigating) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        super.onDestroy()
    }

    // "안내 시작" 버튼을 눌렀을 때
    private fun startNavigation() {
        isNavigating = true

        // UI 변경 (검색창 숨기고 내비 화면 켜기)
        findViewById<CardView>(R.id.searchCard).visibility = View.GONE
        cardRouteInfo.visibility = View.GONE
        navTopCard.visibility = View.VISIBLE
        navBottomCard.visibility = View.VISIBLE

        speakTTS("경로 안내를 시작합니다.")

        // 🚗 가상주행
        if (isMockMode) {
            // 가상 주행만 실행, GPS 콜백은 시작하지 않음
            startMockNavigation()
        } else {
            // 실제 주행: GPS 콜백만 실행
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(2000)
                .build()

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            }
        }
    }

    // 🚗 가상 주행
    // 파란 선을 따라 이동하며 실시간 안내하는 시뮬레이터
    private fun startMockNavigation() {
        lifecycleScope.launch(Dispatchers.Main) {
            val upcomingSteps = routeSteps.toMutableList()

            upcomingSteps.removeAll { step ->
                val isJustMove = step.description.contains("이동") && !step.description.contains("회전") &&
                        !step.description.contains("좌") && !step.description.contains("우")

                // 미터(m) 표시가 없는 진짜 쓰레기 데이터만 날림
                isJustMove && !step.description.contains("m")
            }

            for (i in allRoutePoints.indices) {
                if (!isNavigating) break
                val point = allRoutePoints[i]
                currentLocation = point

                // 1. 내 위치 마커 업데이트
                val myMarker = TMapMarkerItem().apply {
                    tMapPoint = currentLocation
                    name = "내 위치"
                }
                tMapView.removeMarkerItem("startMarker")
                tMapView.addMarkerItem("startMarker", myMarker)
                tMapView.setCenterPoint(point.longitude, point.latitude)
                tMapView.postInvalidate()

                // 2. 목적지까지 남은 거리
                if (destinationPoint != null) {
                    val remainDist = calculateDistance(currentLocation!!, destinationPoint!!)
                    tvNavRemainInfo.text = String.format("목적지까지: %.0fm", remainDist)

                    if (remainDist <= 15f) {
                        speakTTSWithCooldown("목적지 부근에 도착했습니다. 안내를 종료합니다.")
                        stopNavigation()
                        break
                    }
                }

                // 3. 분기점 접근 감지 (50m 전부터 준비, 30m에서 안내)
                if (upcomingSteps.isNotEmpty()) {
                    val nextStep = upcomingSteps.first()
                    val distToNext = calculateDistance(currentLocation!!, nextStep.coordinate)

                    // 30m 이내 + 아직 안내 안 한 분기점 + 쿨다운 지남
                    if (distToNext <= 30f && !announcedStepIndices.contains(nextStep.pointIndex)) {
                        announcedStepIndices.add(nextStep.pointIndex)

                        // ⭐ 랜드마크 기반 안내 멘트 생성
                        val landmark = searchNearbyPOI(nextStep.coordinate)
                        val smartMessage = buildSmartMessage(nextStep, landmark)

                        tvNavInstruction.text = smartMessage
                        speakTTSWithCooldown(smartMessage)

                        upcomingSteps.removeAt(0)
                    }
                }

                delay(MOCK_SPEED_MS)  // ← 여기서 속도 조절!
            }
        }
    }

    // TMap POI 주변 검색 (반경 100m 내 편의점/건물 등)
    // 눈에 잘 띄는 랜드마크(편의점, 카페, 패스트푸드 등)만 찾는 함수
    private suspend fun searchNearbyPOI(point: TMapPoint): String? {
        return withContext(Dispatchers.IO) {
            try {
                // TMAP 업종 코드 카테고리 (가장 눈에 잘 띄는 것들 위주로 세팅)
                val searchCategory = "편의점;커피전문점;패스트푸드;은행"

                val response = RetrofitClient.tmapService.searchAroundPOI(
                    appKey = BuildConfig.TMAP_APP_KEY,
                    centerLat = point.latitude.toString(),
                    centerLon = point.longitude.toString(),
                    radius = "1",
                    count = 5, // 5개 정도 가져와서 제일 이상한 건 거르기 위함
                    categories = searchCategory
                )

                if (response.isSuccessful && response.body() != null) {
                    val poiList = response.body()?.searchPoiInfo?.pois?.poiList
                    if (!poiList.isNullOrEmpty()) {

                        // 가져온 5개 중에서 가장 이름이 깔끔한(쓸데없는 수식어가 적은) 녀석을 고르는 로직
                        for (poi in poiList) {
                            val name = poi.name

                            // 이름에 지점명이나 이상한 기호가 너무 길게 붙은 건 패스하고,
                            // "GS25", "스타벅스", "국민은행" 같이 딱 떨어지거나 끝이 "점"으로 끝나는 깔끔한 랜드마크 채택
                            if (!name.contains("소화전") && !name.contains("공중전화") && !name.contains("정류소")) {
                                return@withContext name // 가장 조건이 좋은 첫 번째 녀석을 리턴!
                            }
                        }
                    }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "POI 주변 검색 실패", e)
                null
            }
        }
    }


    // 랜드마크 + 방향을 조합한 자연어 안내 생성
    private fun buildSmartMessage(step: RouteStep, landmark: String?): String {
        val direction = when (step.turnType) {
            11 -> "직진"   // ⭐ 11번이 일반 직진입니다!
            12 -> "좌회전"
            13 -> "우회전"
            14 -> "유턴"
            16 -> "8시 방향"
            17 -> "10시 방향"
            18 -> "2시 방향"
            19 -> "4시 방향"
            211, 212, 213, 214, 215, 216, 217 -> "횡단보도"
            233 -> "직진"
            else -> step.description
        }

        // 직진인 경우
        if (direction == "직진") {
            // TMAP 원본: "백범로를 따라 240m 이동" -> 변경: "백범로를 따라 240미터 직진하세요"
            return step.description
                .replace("이동", "직진하세요")
                .replace("m", "미터") // TTS가 '엠'이라고 안 읽고 '미터'라고 예쁘게 읽도록!
        }

        // 그 외 코너인 경우 (랜드마크 로직)
        return if (landmark != null) {
            when {
                direction == "좌회전" -> "전방 ${landmark}을 끼고 왼쪽으로 도세요"
                direction == "우회전" -> "전방 ${landmark}을 끼고 오른쪽으로 도세요"
                direction == "횡단보도" -> "${landmark} 근처 횡단보도를 건너주세요"
                direction == "유턴" -> "${landmark} 앞에서 유턴하세요"
                else -> "${landmark} 방향으로 가세요"
            }
        } else {
            "약 30미터 앞에서 ${direction}하세요"
        }
    }

    // 쿨다운이 적용된 TTS (너무 빠르게 연속 안내 방지)
    private fun speakTTSWithCooldown(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastTTSTime >= TTS_COOLDOWN) {
            lastTTSTime = now
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_${now}")
            Log.d(TAG, "TTS 안내: $text")
        } else {
            Log.d(TAG, "TTS 쿨다운 중 (스킵): $text")
        }
    }


    // "안내 종료" 버튼을 눌렀을 때
    private fun stopNavigation() {
        isNavigating = false
        fusedLocationClient.removeLocationUpdates(locationCallback)

        speakTTS("경로 안내를 종료합니다.")

        // UI 원상복구
        findViewById<CardView>(R.id.searchCard).visibility = View.VISIBLE
        navTopCard.visibility = View.GONE
        navBottomCard.visibility = View.GONE

        // 경로 초기화 (선택사항)
        tMapView.removeAllTMapPolyLine()
        tMapView.removeMarkerItem("destination")
    }

    // 3초마다 GPS를 수신하는 콜백 함수
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (!isNavigating) return

                    val currentLat = location.latitude
                    val currentLon = location.longitude
                    currentLocation = TMapPoint(currentLat, currentLon)

                    // 내 위치 마커 이동
                    val myMarker = TMapMarkerItem().apply {
                        tMapPoint = currentLocation
                        name = "내 위치"
                    }
                    tMapView.removeMarkerItem("startMarker")
                    tMapView.addMarkerItem("startMarker", myMarker)

                    // 지도의 중심을 현재 내 위치로 이동 (Follow 모드)
                    tMapView.setCenterPoint(currentLon, currentLat)
                    tMapView.postInvalidate()

                    // TODO: 다음 단계에서 (currentLat, currentLon)과 routeSteps의 거리를 계산할 예정!
                }
            }
        }
    }

    private fun speakTTS(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    // 실제 거리를 계산하는 함수
    private fun calculateDistance(p1: TMapPoint, p2: TMapPoint): Float {
        val loc1 = android.location.Location("").apply {
            latitude = p1.latitude
            longitude = p1.longitude
        }
        val loc2 = android.location.Location("").apply {
            latitude = p2.latitude
            longitude = p2.longitude
        }
        return loc1.distanceTo(loc2) // 단위: 미터(m)
    }


}





