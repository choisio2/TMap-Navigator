package com.aivy.navigator

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Color as AndroidColor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aivy.navigator.data.model.PoiItem
import com.aivy.navigator.data.model.RouteFeature
import com.aivy.navigator.data.model.RouteStep
import com.aivy.navigator.data.network.RetrofitClient
import com.google.android.gms.location.*
import com.skt.Tmap.TMapMarkerItem
import com.skt.Tmap.TMapPoint
import com.skt.Tmap.TMapPolyLine
import com.skt.Tmap.TMapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class TmapsActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    // ============================================================
    // 상수 정의
    // ============================================================
    companion object {
        private const val TAG = "TMAP_debug"

        // TTS 연속 출력 방지용 쿨다운 (ms)
        private const val TTS_COOLDOWN = 500L
        // 가상 주행 시 포인트 간 이동 간격 (ms)
        private const val MOCK_SPEED_MS = 2000L

        // 분기점 3단계 안내 임계 거리 (m)
        private const val ANNOUNCE_DIST_FAR  = 50f
        private const val ANNOUNCE_DIST_MID  = 30f
        private const val ANNOUNCE_DIST_NEAR = 10f

        // 직진 구간 피드백 주기 (ms)
        private const val STRAIGHT_FEEDBACK_MS = 30_000L

        // 경로 이탈 판단 거리 (m) — GPS 오차 고려
        private const val REROUTE_THRESHOLD_M   = 25f
        // 연속 이 횟수 이상 이탈 시 재탐색
        private const val REROUTE_CONFIRM_COUNT = 3
        // 지나온 경로를 한 번에 최대 N포인트씩 제거
        private const val POLYLINE_TRIM_MAX = 3

        // 랜드마크 검색 최대 반경 (m) — 길 건너편 배제
        private const val LANDMARK_MAX_DIST_M = 15f

        // 초기 방향 안내: 진행 방향과 사용자 방위각 차이 임계값 (도)
        private const val DIRECTION_THRESHOLD_BACK  = 150f  // 뒤돌아서
        private const val DIRECTION_THRESHOLD_TURN  = 45f   // 좌/우로 돌아서
    }

    // ============================================================
    // 멤버 변수
    // ============================================================

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var tts: TextToSpeech
    private lateinit var sensorManager: SensorManager
    private lateinit var tMapView: TMapView

    // 현재 위치 / 목적지 / 경유지
    private var currentLocation: TMapPoint? = null
    private var destinationPoint: TMapPoint? = null
    private var waypointPoint: TMapPoint? = null

    // 경로 포인트 및 안내 스텝
    private val allRoutePoints = mutableListOf<TMapPoint>()
    private val routeSteps    = mutableListOf<RouteStep>()
    private val upcomingSteps = mutableListOf<RouteStep>()

    // 방향 센서
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity     = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var currentAzimuth    = 0f
    private var lastMarkerAzimuth = 0f
    private var myLocationBaseBitmap: Bitmap? = null

    // 상태 플래그
    private var isTtsReady   = false
    private var isRerouting  = false
    private var offRouteCount = 0   // 연속 이탈 횟수 카운터

    // TTS 쿨다운
    private var lastTTSTime = 0L

    // 가상 주행 / 직진 피드백 코루틴 핸들
    private var mockNavJob: Job? = null
    private var straightFeedbackJob: Job? = null

    // 분기점별 안내 단계 기록 (pointIndex → 완료된 단계 집합)
    private val announcedStages = mutableMapOf<Int, MutableSet<String>>()
    private var lastStraightFeedbackTime = 0L

    // ============================================================
    // Compose UI 상태 (by mutableStateOf)
    // ============================================================

    private var uiStateSearchQuery   by mutableStateOf("")
    private var uiStateIsRouteReady  by mutableStateOf(false)
    private var uiStateRouteSummary  by mutableStateOf("")
    private var uiStateWaypointName  by mutableStateOf("")

    private var uiStateIsNavigating      by mutableStateOf(false)
    private var uiStateIsMockMode        by mutableStateOf(true)
    private var uiStateNavInstruction    by mutableStateOf("경로를 탐색합니다.")
    private var uiStateNavRemainDistance by mutableStateOf("")

    // 목적지/경유지 검색 다이얼로그
    private var uiStateShowSearchDialog    by mutableStateOf(false)
    private var uiStateSearchResults       by mutableStateOf<List<PoiItem>>(emptyList())
    private var isSearchingForWaypoint     by mutableStateOf(false)
    private var uiStateShowWaypointInputDialog by mutableStateOf(false)
    private var uiStateWaypointQuery       by mutableStateOf("")

    // ============================================================
    // 생명주기
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this, this)

        sensorManager  = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer   = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        myLocationBaseBitmap = getBitmapFromVectorDrawable(R.drawable.ic_my_location_dot_sensor)

        setupLocationCallback()
        setupTMapView()

        accelerometer?.let { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let  { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI) }

        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent { NavigationScreen() }
    }

    override fun onDestroy() {
        if (isTtsReady) { tts.stop(); tts.shutdown() }
        if (uiStateIsNavigating && this::locationCallback.isInitialized) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }
        sensorManager.unregisterListener(sensorEventListener)
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN
            tts.setSpeechRate(0.85f)
            tts.setPitch(1.0f)
            isTtsReady = true
        }
    }

    // ============================================================
    // 권한 요청
    // ============================================================

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // TMap 초기화
    // ============================================================

    private fun setupTMapView() {
        tMapView = TMapView(this).apply {
            setSKTMapApiKey(BuildConfig.TMAP_APP_KEY)
            setOnApiKeyListener(object : TMapView.OnApiKeyListenerCallback {
                override fun SKTMapApikeySucceed() { runOnUiThread { fetchCurrentLocation() } }
                override fun SKTMapApikeyFailed(msg: String?) { Log.e(TAG, "인증 실패: $msg") }
            })
        }
    }

    // ============================================================
    // 위치 관련
    // ============================================================

    // 현재 위치를 조회하고 지도 중심을 이동
    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                currentLocation = TMapPoint(loc.latitude, loc.longitude)
                updateMyLocationMarker()
                tMapView.setCenterPoint(loc.longitude, loc.latitude)
                tMapView.zoomLevel = 15
            }
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLocation = TMapPoint(loc.latitude, loc.longitude)
                    updateMyLocationMarker()
                    tMapView.setCenterPoint(loc.longitude, loc.latitude)
                }
            }
    }

    // 실시간 위치 업데이트 콜백 설정
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!uiStateIsNavigating) return
                result.lastLocation?.let { loc ->
                    val newLoc = TMapPoint(loc.latitude, loc.longitude)
                    currentLocation = newLoc
                    updateMyLocationMarker()
                    tMapView.setCenterPoint(loc.longitude, loc.latitude)
                    checkNavigationProgress(newLoc)
                }
            }
        }
    }

    // ============================================================
    // 방향 센서
    // ============================================================

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER)
                gravity.indices.forEach { gravity[it] = event.values[it] }
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD)
                geomagnetic.indices.forEach { geomagnetic[it] = event.values[it] }

            val rMatrix = FloatArray(9)
            val iMatrix = FloatArray(9)
            if (SensorManager.getRotationMatrix(rMatrix, iMatrix, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rMatrix, orientation)
                currentAzimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360) % 360

                if (abs(currentAzimuth - lastMarkerAzimuth) > 5f) {
                    lastMarkerAzimuth = currentAzimuth
                    updateMyLocationMarker()
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // 방향 계산 결과를 반영하여 내 위치 마커를 갱신
    private fun updateMyLocationMarker() {
        currentLocation?.let {
            tMapView.removeMarkerItem("myLocation")
            val marker = TMapMarkerItem().apply {
                tMapPoint = it
                name = "내 위치"
                val base = myLocationBaseBitmap ?: return@apply
                val matrix = Matrix().apply { postRotate(currentAzimuth) }
                icon = Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)
                setPosition(0.5f, 0.5f)
            }
            tMapView.addMarkerItem("myLocation", marker)
            tMapView.postInvalidate()
        }
    }

    // ============================================================
    // 유틸
    // ============================================================

    private fun getBitmapFromVectorDrawable(drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, drawableId)!!
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // 두 지점 간의 실제 거리 계산 (m)
    private fun calculateDistance(p1: TMapPoint, p2: TMapPoint): Float {
        val a = android.location.Location("").apply { latitude = p1.latitude; longitude = p1.longitude }
        val b = android.location.Location("").apply { latitude = p2.latitude; longitude = p2.longitude }
        return a.distanceTo(b)
    }

    // 두 지점 사이의 방위각 계산 (0~360도, 북=0)
    private fun bearingBetween(from: TMapPoint, to: TMapPoint): Float {
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (bearing + 360) % 360
    }

    // ============================================================
    // POI 검색 (단일 함수로 통합)
    //
    // [역할 구분]
    //   isWaypoint = true  → 경유지 선택용 검색 (Compose 다이얼로그에 결과 표시)
    //   isWaypoint = false → 목적지 선택용 검색 (Compose 다이얼로그에 결과 표시)
    //
    // 내비게이션 중 자동으로 랜드마크를 탐색하는 getLandmarkForTurn()은
    // 사용자 입력 없이 내부에서만 호출되는 별도 함수이므로 분리 유지.
    // ============================================================

    private fun searchPOI(query: String, isWaypoint: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.tmapService.searchPOI(
                    appKey    = BuildConfig.TMAP_APP_KEY,
                    keyword   = query,
                    categories = "편의점,커피전문점,지하철역,공공기관,학교,공원,관광명소,병원"
                )
                withContext(Dispatchers.Main) {
                    val list = resp.body()?.searchPoiInfo?.pois?.poiList
                    if (resp.isSuccessful && !list.isNullOrEmpty()) {
                        isSearchingForWaypoint = isWaypoint
                        uiStateSearchResults   = list
                        uiStateShowSearchDialog = true
                    } else {
                        Toast.makeText(this@TmapsActivity, "검색 결과가 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TmapsActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ============================================================
    // 마커 및 경로 탐색
    // ============================================================

    // 목적지 마커를 지도에 표시하고 경로 탐색 시작
    private fun setDestinationMarker(item: PoiItem) {
        val lat = item.noorLat.toDoubleOrNull() ?: return
        val lon = item.noorLon.toDoubleOrNull() ?: return
        destinationPoint = TMapPoint(lat, lon)
        uiStateSearchQuery = item.name

        tMapView.removeAllTMapPolyLine()
        tMapView.removeMarkerItem("destination")

        val marker = TMapMarkerItem().apply {
            tMapPoint = destinationPoint
            name      = item.name
            icon      = getBitmapFromVectorDrawable(R.drawable.ic_location_on)
            setPosition(0.5f, 1.0f)
        }
        tMapView.addMarkerItem("destination", marker)
        tMapView.setCenterPoint(lon, lat)
        tMapView.zoomLevel = 15

        if (currentLocation != null) findPedestrianRoute(currentLocation!!, destinationPoint!!)
    }

    // 경유지 마커를 지도에 표시하고 경로 재탐색
    private fun setWaypointMarker(item: PoiItem) {
        val lat = item.noorLat.toDoubleOrNull() ?: return
        val lon = item.noorLon.toDoubleOrNull() ?: return
        waypointPoint    = TMapPoint(lat, lon)
        uiStateWaypointName = item.name

        tMapView.removeMarkerItem("waypoint")
        val marker = TMapMarkerItem().apply {
            tMapPoint = waypointPoint
            name      = "경유지: ${item.name}"
            icon      = getBitmapFromVectorDrawable(R.drawable.ic_location_on)
            setPosition(0.5f, 1.0f)
        }
        tMapView.addMarkerItem("waypoint", marker)
        speakTTS("${item.name}을 경유지로 설정했습니다.")

        if (currentLocation != null && destinationPoint != null)
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
    }

    // 경유지를 초기화하고 경로 재탐색
    private fun clearWaypoint() {
        waypointPoint    = null
        uiStateWaypointName = ""
        tMapView.removeMarkerItem("waypoint")
        if (currentLocation != null && destinationPoint != null)
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
    }

    // TMap 도보 경로 탐색 API 호출
    private fun findPedestrianRoute(start: TMapPoint, end: TMapPoint) {
        val passListStr = waypointPoint?.let { "${it.longitude},${it.latitude}" }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.tmapService.getPedestrianRoute(
                    appKey   = BuildConfig.TMAP_APP_KEY,
                    startX   = start.longitude.toString(),
                    startY   = start.latitude.toString(),
                    endX     = end.longitude.toString(),
                    endY     = end.latitude.toString(),
                    passList = passListStr
                )
                withContext(Dispatchers.Main) {
                    if (resp.isSuccessful && resp.body() != null) {
                        drawRouteAndSaveSteps(resp.body()!!.features)
                    } else {
                        Toast.makeText(this@TmapsActivity, "경로 탐색 실패", Toast.LENGTH_SHORT).show()
                        isRerouting = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TmapsActivity, "네트워크 연결 불안정", Toast.LENGTH_SHORT).show()
                    isRerouting = false
                }
            }
        }
    }

    // API 응답을 파싱하여 경로 포인트와 안내 스텝을 저장하고 지도에 렌더링
    private fun drawRouteAndSaveSteps(features: List<RouteFeature>) {
        routeSteps.clear()
        allRoutePoints.clear()
        var totalTime = 0
        var totalDistance = 0

        features.forEach { f ->
            val geo   = f.geometry
            val props = f.properties
            if (props.totalTime     != null) totalTime     = props.totalTime
            if (props.totalDistance != null) totalDistance = props.totalDistance

            when (geo.type) {
                "LineString" -> {
                    val arr = geo.coordinates.asJsonArray
                    for (i in 0 until arr.size()) {
                        val c = arr[i].asJsonArray
                        allRoutePoints.add(TMapPoint(c[1].asDouble, c[0].asDouble))
                    }
                }
                "Point" -> {
                    val c = geo.coordinates.asJsonArray
                    if (props.description != null && props.turnType != null && props.pointIndex != null) {
                        val desc = props.description
                        val isImportantTurn = desc.contains("회전") || desc.contains("출발") || desc.contains("도착")
                        val isInstruction   = desc.contains("좌")   || desc.contains("우")   || desc.contains("횡단보도")
                        if ((isImportantTurn || isInstruction) && desc.length > 2) {
                            routeSteps.add(
                                RouteStep(
                                    props.pointIndex,
                                    TMapPoint(c[1].asDouble, c[0].asDouble),
                                    desc,
                                    props.turnType,
                                    props.totalDistance ?: 0
                                )
                            )
                        }
                    }
                }
            }
        }

        updatePolyline()

        if (uiStateIsNavigating) {
            // 재탐색 완료 — 새 경로로 안내 재개
            upcomingSteps.clear()
            upcomingSteps.addAll(routeSteps)
            announcedStages.clear()
            uiStateNavInstruction = "새로운 경로로 안내를 계속합니다."
            speakTTS("새로운 경로를 찾았습니다. 안내를 계속합니다.")
            lastStraightFeedbackTime = System.currentTimeMillis()
            isRerouting = false
            if (uiStateIsMockMode) startMockNavigation()
        } else {
            uiStateRouteSummary = String.format("도보 %d분 (%.1fkm)", totalTime / 60, totalDistance / 1000.0)
            uiStateIsRouteReady = true
        }
    }

    // 경로 폴리라인을 지도에 그림
    private fun updatePolyline() {
        tMapView.removeTMapPolyLine("pedestrian_route")
        if (allRoutePoints.isEmpty()) return

        val polyLine = TMapPolyLine().apply {
            lineColor    = AndroidColor.parseColor("#215CF3")
            lineWidth    = 11f
            outLineColor = AndroidColor.parseColor("#1976D2")
            outLineWidth = 2f
        }
        allRoutePoints.forEach { polyLine.addLinePoint(it) }
        tMapView.addTMapPolyLine("pedestrian_route", polyLine)
        tMapView.postInvalidate()
    }

    // 경로 미리보기를 취소하고 초기 상태로 복귀
    private fun cancelRoutePreview() {
        tMapView.removeAllTMapPolyLine()
        tMapView.removeMarkerItem("destination")
        tMapView.removeMarkerItem("waypoint")
        uiStateIsRouteReady  = false
        uiStateSearchQuery   = ""
        uiStateWaypointName  = ""
        waypointPoint        = null
        currentLocation?.let { tMapView.setCenterPoint(it.longitude, it.latitude); tMapView.zoomLevel = 15 }
    }

    // ============================================================
    // 내비게이션 시작 / 종료
    // ============================================================

    private fun startNavigation() {
        uiStateIsNavigating = true
        isRerouting         = false
        offRouteCount       = 0

        upcomingSteps.clear()
        upcomingSteps.addAll(routeSteps)
        announcedStages.clear()
        lastStraightFeedbackTime = System.currentTimeMillis()

        speakTTS(if (uiStateIsMockMode) "가상 주행 안내를 시작합니다." else "안내를 시작합니다.")

        // 출발 직후 사용자가 바라보는 방향과 진행 방향을 비교하여 초기 방향 안내
        announceInitialDirection()

        if (uiStateIsMockMode) startMockNavigation() else startRealNavigation()
        startStraightFeedback()
    }

    // 안내 종료 확인 다이얼로그
    private fun showStopDialog() {
        AlertDialog.Builder(this)
            .setTitle("안내 종료")
            .setMessage("경로 안내를 종료하시겠습니까?")
            .setPositiveButton("예") { d, _ -> stopNavigation(); d.dismiss() }
            .setNegativeButton("아니오") { d, _ -> d.dismiss() }
            .show()
    }

    // 주행을 멈추고 모든 상태 초기화
    private fun stopNavigation() {
        uiStateIsNavigating = false
        isRerouting         = false
        offRouteCount       = 0
        mockNavJob?.cancel()
        straightFeedbackJob?.cancel()

        if (this::locationCallback.isInitialized) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }
        speakTTS("경로 안내를 종료합니다.")
        cancelRoutePreview()
    }

    // ============================================================
    // 초기 방향 안내
    // ============================================================

    // 출발 시 사용자 방위각과 첫 번째 진행 방향을 비교하여 회전 안내 제공
    private fun announceInitialDirection() {
        val start    = currentLocation ?: return
        val firstStep = upcomingSteps.firstOrNull() ?: return

        // 현재 위치 → 첫 번째 분기점 방향 (목표 방위각)
        val targetBearing = bearingBetween(start, firstStep.coordinate)

        // 사용자가 현재 바라보는 방향과의 차이 (-180~180 범위로 정규화)
        var diff = targetBearing - currentAzimuth
        if (diff > 180f)  diff -= 360f
        if (diff < -180f) diff += 360f

        val msg = when {
            // 뒤쪽 (150도 이상 차이) → 뒤돌아서
            abs(diff) >= DIRECTION_THRESHOLD_BACK -> "뒤돌아서 출발하세요."
            // 오른쪽 (45도 이상 오른쪽) → 오른쪽으로 돌아서
            diff >= DIRECTION_THRESHOLD_TURN      -> "오른쪽으로 돌아서 출발하세요."
            // 왼쪽 (45도 이상 왼쪽) → 왼쪽으로 돌아서
            diff <= -DIRECTION_THRESHOLD_TURN     -> "왼쪽으로 돌아서 출발하세요."
            // 거의 정면 → 안내 생략
            else -> null
        }

        if (msg != null) {
            Log.d(TAG, "[InitDir] targetBearing=$targetBearing currentAzimuth=$currentAzimuth diff=$diff → $msg")
            // 출발 TTS 이후 1.5초 뒤에 방향 안내 (겹침 방지)
            lifecycleScope.launch {
                delay(1500L)
                speakTTS(msg)
            }
        }
    }

    // ============================================================
    // 주행 모드
    // ============================================================

    // 가상 주행: allRoutePoints를 순서대로 이동하며 안내
    private fun startMockNavigation() {
        mockNavJob?.cancel()
        mockNavJob = lifecycleScope.launch(Dispatchers.Main) {
            val points = allRoutePoints.toList()
            for (pt in points) {
                if (!uiStateIsNavigating) break
                currentLocation = pt
                updateMyLocationMarker()
                tMapView.setCenterPoint(pt.longitude, pt.latitude)
                checkNavigationProgress(pt)
                delay(MOCK_SPEED_MS)
            }
        }
    }

    // 실제 주행: FusedLocationProvider로 실시간 위치 수신
    @SuppressLint("MissingPermission")
    private fun startRealNavigation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(2000).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED)
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    // ============================================================
    // 핵심 내비게이션 로직
    // ============================================================

    // 현재 위치를 기준으로 경로 이탈 확인, 경로 정리, 분기점 안내 수행
    private fun checkNavigationProgress(currentLoc: TMapPoint) {
        if (isRerouting || allRoutePoints.isEmpty()) return

        // 가장 가까운 경로 포인트 탐색
        var minDist    = Float.MAX_VALUE
        var closestIdx = 0
        for (i in allRoutePoints.indices) {
            val dist = calculateDistance(currentLoc, allRoutePoints[i])
            if (dist < minDist) { minDist = dist; closestIdx = i }
        }

        // 경로 이탈 판단: REROUTE_CONFIRM_COUNT 회 연속 이탈 시 재탐색
        if (minDist > REROUTE_THRESHOLD_M && !uiStateIsMockMode) {
            offRouteCount++
            Log.d(TAG, "[Reroute] 이탈 $offRouteCount/$REROUTE_CONFIRM_COUNT (dist=${minDist}m)")
            if (offRouteCount >= REROUTE_CONFIRM_COUNT) {
                offRouteCount = 0
                isRerouting   = true
                speakTTS("경로를 벗어났습니다. 경로를 재탐색합니다.")
                lastStraightFeedbackTime = System.currentTimeMillis()
                uiStateNavInstruction    = "경로 재탐색 중..."
                destinationPoint?.let { findPedestrianRoute(currentLoc, it) }
            }
            return
        } else {
            offRouteCount = 0
        }

        // 지나온 경로를 최대 POLYLINE_TRIM_MAX 포인트씩 점진적으로 제거
        if (closestIdx > 0) {
            val trimCount = minOf(closestIdx, POLYLINE_TRIM_MAX)
            allRoutePoints.subList(0, trimCount).clear()
            updatePolyline()
        }

        // 목적지 도착 확인
        destinationPoint?.let { dest ->
            val dist = calculateDistance(currentLoc, dest)
            uiStateNavRemainDistance = String.format("목적지까지: %.0fm", dist)
            if (dist <= 15f) {
                speakTTSWithCooldown("도착했습니다. 안내를 종료합니다.")
                stopNavigation()
                return
            }
        }

        // 다가오는 분기점 거리별 3단계 안내 (50m → 30m → 10m)
        if (upcomingSteps.isNotEmpty()) {
            val next        = upcomingSteps.first()
            val distToTurn  = calculateDistance(currentLoc, next.coordinate)
            val stages      = announcedStages.getOrPut(next.pointIndex) { mutableSetOf() }

            // 1단계: 50m — 분기점 예고
            if (distToTurn <= ANNOUNCE_DIST_FAR && "far" !in stages) {
                stages.add("far")
                lastStraightFeedbackTime = System.currentTimeMillis()
                val approxDist = (distToTurn / 10).toInt() * 10
                val msg = buildFarAnnounce(next.turnType, approxDist, next.description)
                uiStateNavInstruction = msg
                speakTTSWithCooldown(msg)
            }

            // 2단계: 30m — 횡단보도는 고정 안내, 일반 분기점은 주변 랜드마크 검색
            if (distToTurn <= ANNOUNCE_DIST_MID && "mid" !in stages) {
                stages.add("mid")
                lastStraightFeedbackTime = System.currentTimeMillis()

                if (next.turnType in 211..217) {
                    // 횡단보도: POI 검색 없이 상황별 고정 안내
                    // (건너편 POI를 참조하면 문맥이 어색해지기 때문)
                    val msg = buildMidAnnounceCrosswalk(next.description)
                    uiStateNavInstruction = msg
                    speakTTSWithCooldown(msg)
                } else {
                    // 일반 분기점: 15m 이내 랜드마크 검색 후 자연스러운 문장 조합
                    lifecycleScope.launch {
                        val landmark = getLandmarkForTurn(next.coordinate)
                        val msg = if (landmark != null) {
                            formatLandmarkInstruction(landmark, next.turnType, next.description)
                        } else {
                            buildMidAnnounce(next.turnType, next.description)
                        }
                        // [버그 수정] withContext(Main) 없이 speakTTS를 호출하면
                        // IO 스레드에서 TTS API를 건드리게 되어 무음 처리됨.
                        // 반드시 Main 스레드로 전환 후 TTS 호출.
                        withContext(Dispatchers.Main) {
                            uiStateNavInstruction = msg
                            speakTTSWithCooldown(msg)
                        }
                    }
                }
            }

            // 3단계: 10m — 진입 직전 안내 후 다음 스텝으로 전환
            if (distToTurn <= ANNOUNCE_DIST_NEAR && "near" !in stages) {
                stages.add("near")
                lastStraightFeedbackTime = System.currentTimeMillis()
                val msg = buildNearAnnounce(next.turnType, next.description)
                uiStateNavInstruction = msg
                speakTTSWithCooldown(msg)

                // 완료된 스텝의 stages 제거 후 다음 스텝으로 이동
                announcedStages.remove(next.pointIndex)
                upcomingSteps.removeAt(0)
            }
        }
    }

    // ============================================================
    // 랜드마크 검색
    //
    // searchPOI()와 역할 구분:
    //   searchPOI()           → 사용자가 검색창에 입력한 목적지/경유지를 찾는 UI용 함수
    //   getLandmarkForTurn()  → 내비 중 분기점 근처 랜드마크를 자동으로 찾는 내부 전용 함수
    //
    // TMap searchAroundPOI()와의 관계:
    //   TMap API 파라미터 radius "1" ≈ 33m 반경 검색.
    //   반환된 결과를 LANDMARK_MAX_DIST_M(15m) 기준으로 한 번 더 필터링하여
    //   길 건너편 POI가 포함되는 것을 방지.
    // ============================================================

    private suspend fun getLandmarkForTurn(point: TMapPoint): String? = withContext(Dispatchers.IO) {
        try {
            val resp = RetrofitClient.tmapService.searchAroundPOI(
                appKey    = BuildConfig.TMAP_APP_KEY,
                centerLat = point.latitude.toString(),
                centerLon = point.longitude.toString(),
                radius    = "1",
                count     = 10
            )

            val poiList = resp.body()?.searchPoiInfo?.pois?.poiList
                ?: return@withContext null

            val turnLoc = android.location.Location("").apply {
                latitude  = point.latitude
                longitude = point.longitude
            }

            // LANDMARK_MAX_DIST_M 이내 POI만 허용, 가장 가까운 것 선택
            val closest = poiList.mapNotNull { poi ->
                val lat = poi.noorLat.toDoubleOrNull() ?: return@mapNotNull null
                val lon = poi.noorLon.toDoubleOrNull() ?: return@mapNotNull null
                val poiLoc = android.location.Location("").apply { latitude = lat; longitude = lon }
                val distM  = turnLoc.distanceTo(poiLoc)
                if (distM > LANDMARK_MAX_DIST_M) null else Pair(poi.name, distM)
            }.minByOrNull { it.second }

            // "스타벅스 강남역점" → "스타벅스" (첫 단어만 사용해 자연스러운 발화 유도)
            closest?.first?.split(" ")?.firstOrNull()

        } catch (e: Exception) {
            Log.w(TAG, "[POI] 랜드마크 검색 실패: ${e.message}")
            null
        }
    }

    // ============================================================
    // TTS 문장 생성 헬퍼
    // ============================================================

    // 50m 예고 안내 문장
    private fun buildFarAnnounce(turnType: Int, approxDist: Int, desc: String): String {
        return when (turnType) {
            12          -> "약 ${approxDist}미터 앞에서 왼쪽으로 회전하세요."
            13          -> "약 ${approxDist}미터 앞에서 오른쪽으로 회전하세요."
            14          -> "약 ${approxDist}미터 앞에서 유턴하세요."
            in 211..217 -> "약 ${approxDist}미터 앞에 횡단보도가 있습니다."
            else        -> "약 ${approxDist}미터 앞, $desc"
        }
    }

    // 30m 기본 안내 문장 (랜드마크 없을 때)
    private fun buildMidAnnounce(turnType: Int, desc: String): String {
        return when (turnType) {
            12          -> "왼쪽으로 회전할 준비를 하세요."
            13          -> "오른쪽으로 회전할 준비를 하세요."
            14          -> "유턴할 준비를 하세요."
            in 211..217 -> "횡단보도가 가까워지고 있습니다."
            else        -> desc
        }
    }

    // 30m 횡단보도 전용 안내 문장
    // POI를 사용하지 않는 이유: 횡단보도 분기점 좌표는 건너편 도착 지점이므로
    // 주변 POI가 '건너편 건물'이 되어 문맥이 어색해짐.
    private fun buildMidAnnounceCrosswalk(desc: String): String {
        return when {
            desc.contains("신호등") -> "신호등이 있는 횡단보도입니다. 신호를 확인하고 건너세요."
            desc.contains("육교")   -> "육교를 이용해 건너세요."
            desc.contains("지하도") -> "지하도를 이용해 건너세요."
            else                    -> "횡단보도를 건너세요."
        }
    }

    // 30m 랜드마크 조합 안내 문장
    private fun formatLandmarkInstruction(poiName: String, turnType: Int, defaultDesc: String): String {
        return when (turnType) {
            12   -> "${poiName}을 끼고 왼쪽으로 회전하세요."
            13   -> "${poiName}을 끼고 오른쪽으로 회전하세요."
            14   -> "${poiName} 앞에서 유턴하세요."
            else -> defaultDesc
        }
    }

    // 10m 진입 직전 안내 문장
    private fun buildNearAnnounce(turnType: Int, desc: String): String {
        return when (turnType) {
            12          -> "지금 왼쪽으로 회전하세요."
            13          -> "지금 오른쪽으로 회전하세요."
            14          -> "지금 유턴하세요."
            in 211..217 -> "지금 횡단보도를 건너세요."
            else        -> "곧 $desc"
        }
    }

    // ============================================================
    // TTS 출력
    // ============================================================

    private fun speakTTS(text: String) {
        if (isTtsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    // 쿨다운 내 중복 발화 방지
    private fun speakTTSWithCooldown(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastTTSTime >= TTS_COOLDOWN) { lastTTSTime = now; speakTTS(text) }
    }

    // ============================================================
    // 직진 구간 피드백
    // ============================================================

    // 분기점 안내 없이 30초가 지나면 직진 안내를 제공하여 사용자 안심감 유도.
    // 단, 다음 분기점이 50m 이내이면 생략하여 분기점 안내와 겹치지 않도록 함.
    private fun startStraightFeedback() {
        straightFeedbackJob?.cancel()
        straightFeedbackJob = lifecycleScope.launch {
            while (uiStateIsNavigating) {
                delay(3_000L)
                if (!uiStateIsNavigating) break

                val now = System.currentTimeMillis()
                if (now - lastStraightFeedbackTime < STRAIGHT_FEEDBACK_MS) continue

                val currentLoc = currentLocation ?: continue
                val nextStep   = upcomingSteps.firstOrNull()

                // 분기점 임박 시 직진 피드백 생략 (분기점 안내 우선)
                if (nextStep != null && calculateDistance(currentLoc, nextStep.coordinate) <= ANNOUNCE_DIST_FAR) {
                    Log.d(TAG, "[StraightFeedback] 분기점 임박 — 생략")
                    continue
                }

                lastStraightFeedbackTime = now
                val message = if (upcomingSteps.isNotEmpty()) {
                    "계속 직진하세요."
                } else {
                    destinationPoint?.let { dest ->
                        val dist = calculateDistance(currentLoc, dest).toInt()
                        "목적지까지 약 ${dist}미터 남았습니다. 계속 직진하세요."
                    } ?: "잘 가고 있어요. 계속 직진하세요."
                }

                withContext(Dispatchers.Main) {
                    uiStateNavInstruction = message
                    speakTTS(message)
                    lastTTSTime = System.currentTimeMillis()
                }
            }
        }
    }

    // ============================================================
    // Compose UI
    // ============================================================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun NavigationScreen() {
        val focusManager = LocalFocusManager.current

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {

            // 지도 뷰
            AndroidView(factory = { tMapView }, modifier = Modifier.fillMaxSize())

            // ── 탐색 전 UI ──────────────────────────────────────────
            if (!uiStateIsNavigating) {

                // 목적지 검색 바
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(30.dp).align(Alignment.TopCenter),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    OutlinedTextField(
                        value         = uiStateSearchQuery,
                        onValueChange = { uiStateSearchQuery = it },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = { Text("어디로 갈까요?", color = Color.Gray) },
                        leadingIcon   = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.Close, contentDescription = "닫기")
                            }
                        },
                        trailingIcon  = {
                            IconButton(onClick = {
                                focusManager.clearFocus()
                                if (uiStateSearchQuery.isNotEmpty()) searchPOI(uiStateSearchQuery)
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "검색", tint = Color(0xFF102841))
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                if (uiStateSearchQuery.isNotEmpty()) searchPOI(uiStateSearchQuery)
                            }
                        ),
                        singleLine = true
                    )
                }

                // 경로 탐색 완료 — 출발 카드
                if (uiStateIsRouteReady) {
                    Card(
                        modifier  = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),
                        shape     = RoundedCornerShape(24.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("최적 도보 경로", fontSize = 14.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiStateRouteSummary, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)
                            Spacer(modifier = Modifier.height(16.dp))

                            // 경유지 설정
                            Row(
                                modifier             = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment    = Alignment.CenterVertically
                            ) {
                                Text(
                                    text     = if (uiStateWaypointName.isEmpty()) "경유지 없음" else "경유지: $uiStateWaypointName",
                                    fontSize = 14.sp,
                                    color    = Color.DarkGray
                                )
                                if (uiStateWaypointName.isEmpty()) {
                                    OutlinedButton(
                                        onClick = { uiStateShowWaypointInputDialog = true },
                                        shape   = RoundedCornerShape(8.dp)
                                    ) { Text("+ 경유지", color = Color(0xFF4CAF50)) }
                                } else {
                                    OutlinedButton(
                                        onClick = { clearWaypoint() },
                                        shape   = RoundedCornerShape(8.dp)
                                    ) { Text("경유지 삭제", color = Color(0xFFD32F2F)) }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 가상 주행 토글
                            Row(
                                modifier             = Modifier.fillMaxWidth()
                                    .background(Color(0xFFFCFAEC), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment    = Alignment.CenterVertically
                            ) {
                                Text("가상 주행 모드 (디버깅용)", fontSize = 14.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked         = uiStateIsMockMode,
                                    onCheckedChange = { uiStateIsMockMode = it },
                                    colors          = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF1976D2),
                                        checkedTrackColor = Color(0xFF9EC6EE)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 취소 / 안내 시작 버튼
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick  = { cancelRoutePreview() },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape    = RoundedCornerShape(12.dp)
                                ) { Text("취소", fontSize = 16.sp, color = Color.DarkGray) }

                                Button(
                                    onClick  = { startNavigation() },
                                    modifier = Modifier.weight(2f).height(52.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                    shape    = RoundedCornerShape(12.dp)
                                ) { Text("안내 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }

            // ── 주행 중 UI ──────────────────────────────────────────
            if (uiStateIsNavigating) {

                // 상단 안내 카드
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color(0xFF1976D2)),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier             = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment  = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text      = uiStateNavInstruction,
                            fontSize  = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color     = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // 하단 잔여 거리 + 종료 카드
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier              = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(uiStateNavRemainDistance, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Button(
                            onClick = { showStopDialog() },
                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape   = RoundedCornerShape(12.dp)
                        ) { Text("안내 종료", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            // ── 내 위치 찾기 버튼 ────────────────────────────────────
            FloatingActionButton(
                onClick         = { fetchCurrentLocation() },
                modifier        = Modifier.align(Alignment.BottomEnd).padding(
                    end    = 16.dp,
                    bottom = if (uiStateIsRouteReady) 320.dp else if (uiStateIsNavigating) 120.dp else 40.dp
                ),
                containerColor = Color.White,
                contentColor   = Color(0xFF1976D2),
                shape          = CircleShape,
                elevation      = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "내 위치 찾기", modifier = Modifier.size(28.dp))
            }

            // ── 목적지/경유지 검색 결과 다이얼로그 ──────────────────
            if (uiStateShowSearchDialog) {
                AlertDialog(
                    onDismissRequest = { uiStateShowSearchDialog = false },
                    title = { Text("어느 곳으로 갈까요?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black) },
                    text  = {
                        LazyColumn {
                            items(uiStateSearchResults) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            uiStateShowSearchDialog = false
                                            if (isSearchingForWaypoint) setWaypointMarker(item)
                                            else setDestinationMarker(item)
                                        }
                                        .padding(vertical = 14.dp)
                                ) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(item.getFullAddress(), fontSize = 13.sp, color = Color.Gray)
                                }
                                Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { uiStateShowSearchDialog = false }) {
                            Text("취소", color = Color(0xFF6200EE), fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = Color.White
                )
            }

            // ── 경유지 입력 다이얼로그 ──────────────────────────────
            if (uiStateShowWaypointInputDialog) {
                AlertDialog(
                    onDismissRequest = { uiStateShowWaypointInputDialog = false },
                    title = { Text("경유지 추가", fontWeight = FontWeight.Bold, color = Color.Black) },
                    text  = {
                        OutlinedTextField(
                            value         = uiStateWaypointQuery,
                            onValueChange = { uiStateWaypointQuery = it },
                            modifier      = Modifier.fillMaxWidth(),
                            placeholder   = { Text("경유지를 입력하세요") },
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (uiStateWaypointQuery.isNotEmpty()) {
                                        uiStateShowWaypointInputDialog = false
                                        searchPOI(uiStateWaypointQuery, true)
                                    }
                                }
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (uiStateWaypointQuery.isNotEmpty()) {
                                    uiStateShowWaypointInputDialog = false
                                    searchPOI(uiStateWaypointQuery, true)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) { Text("검색", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { uiStateShowWaypointInputDialog = false }) {
                            Text("취소", color = Color.Gray)
                        }
                    },
                    containerColor = Color.White
                )
            }
        }
    }
}