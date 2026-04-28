package com.aivy.navigator

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class TmapsActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TMAP_debug"
        private const val TTS_COOLDOWN = 500L
        private const val MOCK_SPEED_MS = 2000L
        private const val ANNOUNCE_DIST_FAR = 50f
        private const val ANNOUNCE_DIST_MID = 30f
        private const val ANNOUNCE_DIST_NEAR = 10f
        private const val STRAIGHT_FEEDBACK_MS = 30_000L
        private const val POI_RADIUS_M = 100f
        private const val POI_FRONT_ANGLE = 90f
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var tts: TextToSpeech
    private lateinit var sensorManager: SensorManager
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tMapView: TMapView

    private var currentLocation: TMapPoint? = null
    private var destinationPoint: TMapPoint? = null
    private var waypointPoint: TMapPoint? = null
    private val allRoutePoints = mutableListOf<TMapPoint>()
    private val routeSteps = mutableListOf<RouteStep>()
    private val upcomingSteps = mutableListOf<RouteStep>()

    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var currentAzimuth = 0f
    private var lastMarkerAzimuth = 0f
    private var myLocationBaseBitmap: Bitmap? = null

    private var isTtsReady = false
    private var isRerouting = false
    private var lastTTSTime = 0L
    private var lastAnnouncedLoc: TMapPoint? = null
    private var isInitialDirectionAnnounced = false
    private var mockNavJob: Job? = null

    private val announcedStages = mutableMapOf<Int, MutableSet<String>>()
    private var lastStraightFeedbackTime = 0L
    private var straightFeedbackJob: Job? = null

    private var imageCapture: ImageCapture? = null
    private var currentStepForAI: RouteStep? = null

    private var uiStateSearchQuery by mutableStateOf("")
    private var uiStateIsRouteReady by mutableStateOf(false)
    private var uiStateRouteSummary by mutableStateOf("")
    private var uiStateWaypointName by mutableStateOf("")

    private var uiStateIsNavigating by mutableStateOf(false)
    private var uiStateIsMockMode by mutableStateOf(true)
    private var uiStateNavInstruction by mutableStateOf("경로를 탐색합니다.")
    private var uiStateNavRemainDistance by mutableStateOf("")

    private var uiStateShowAiCamera by mutableStateOf(false)
    private var uiStateAiStatusMessage by mutableStateOf("")
    private var uiStateShowAiLoading by mutableStateOf(false)
    private var uiStateShowAiGuideButton by mutableStateOf(false)

    private var uiStateShowSearchDialog by mutableStateOf(false)
    private var uiStateSearchResults by mutableStateOf<List<PoiItem>>(emptyList())
    private var isSearchingForWaypoint by mutableStateOf(false)
    private var uiStateShowWaypointInputDialog by mutableStateOf(false)
    private var uiStateWaypointQuery by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        myLocationBaseBitmap = getBitmapFromVectorDrawable(R.drawable.ic_my_location_dot_sensor)

        setupLocationCallback()
        setupTMapView()

        accelerometer?.let { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI) }

        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
            )
        )

        setContent {
            NavigationScreen()
        }
    }

    override fun onDestroy() {
        if (isTtsReady) {
            tts.stop()
            tts.shutdown()
        }
        if (uiStateIsNavigating && this::locationCallback.isInitialized) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }
        sensorManager.unregisterListener(sensorEventListener)
        cameraExecutor.shutdown()
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

    // 위치 및 카메라 권한 요청
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(this, "위치 권한이 거부되어 현재 위치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // TMap 초기화 및 설정
    private fun setupTMapView() {
        tMapView = TMapView(this).apply {
            setSKTMapApiKey(BuildConfig.TMAP_APP_KEY)
            setOnApiKeyListener(object : TMapView.OnApiKeyListenerCallback {
                override fun SKTMapApikeySucceed() { runOnUiThread { fetchCurrentLocation() } }
                override fun SKTMapApikeyFailed(msg: String?) { Log.e(TAG, "인증실패: $msg") }
            })
        }
    }

    // 현재 위치 조회 (캐시 및 실시간 업데이트)
    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                currentLocation = TMapPoint(loc.latitude, loc.longitude)
                updateMyLocationMarker()
                tMapView.setCenterPoint(loc.longitude, loc.latitude)
                tMapView.zoomLevel = 15
            }
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
            if (loc != null) {
                currentLocation = TMapPoint(loc.latitude, loc.longitude)
                updateMyLocationMarker()
                tMapView.setCenterPoint(loc.longitude, loc.latitude)
            }
        }
    }

    // 방향 센서 이벤트 처리
    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) gravity.indices.forEach { gravity[it] = event.values[it] }
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) geomagnetic.indices.forEach { geomagnetic[it] = event.values[it] }

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

    // 사용자 위치 마커 갱신
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

    private fun getBitmapFromVectorDrawable(drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, drawableId)!!
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // POI 장소 검색 API 호출
    private fun searchPOI(query: String, isWaypoint: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.tmapService.searchPOI(
                    appKey = BuildConfig.TMAP_APP_KEY,
                    keyword = query,
                    categories = "편의점,커피전문점,지하철역,공공기관,학교,공원,관광명소,병원"
                )
                withContext(Dispatchers.Main) {
                    val list = resp.body()?.searchPoiInfo?.pois?.poiList
                    if (resp.isSuccessful && !list.isNullOrEmpty()) {
                        isSearchingForWaypoint = isWaypoint
                        uiStateSearchResults = list
                        uiStateShowSearchDialog = true
                    } else {
                        Toast.makeText(this@TmapsActivity, "검색 결과가 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@TmapsActivity, "네트워크 오류", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // 특정 포인트 주변 랜드마크 검색
    private suspend fun getLandmarkForTurn(point: TMapPoint): String? = withContext(Dispatchers.IO) {
        try {
            val targetCategories = "편의점,커피전문점,패스트푸드,지하철역,은행,약국,관광명소"
            val resp = RetrofitClient.tmapService.searchAroundPOI(
                appKey = BuildConfig.TMAP_APP_KEY,
                centerLat = point.latitude.toString(),
                centerLon = point.longitude.toString(),
                radius = "1",
                count = 10,
                categories = targetCategories
            )

            val poiList = resp.body()?.searchPoiInfo?.pois?.poiList ?: return@withContext null
            val refLoc = android.location.Location("").apply {
                latitude = point.latitude
                longitude = point.longitude
            }

            val closestLandmark = poiList.mapNotNull { poi ->
                val poiLat = poi.noorLat.toDoubleOrNull() ?: return@mapNotNull null
                val poiLon = poi.noorLon.toDoubleOrNull() ?: return@mapNotNull null

                val poiLoc = android.location.Location("").apply {
                    latitude = poiLat
                    longitude = poiLon
                }
                val distM = refLoc.distanceTo(poiLoc)
                if (distM > POI_RADIUS_M) null else Pair(poi.name, distM)
            }.minByOrNull { it.second }

            return@withContext closestLandmark?.first
        } catch (e: Exception) {
            return@withContext null
        }
    }

    // 목적지 마커 설정 및 경로 탐색 시작
    private fun setDestinationMarker(item: PoiItem) {
        val lat = item.noorLat.toDoubleOrNull() ?: return
        val lon = item.noorLon.toDoubleOrNull() ?: return
        destinationPoint = TMapPoint(lat, lon)
        uiStateSearchQuery = item.name

        tMapView.removeAllTMapPolyLine()
        tMapView.removeMarkerItem("destination")

        val marker = TMapMarkerItem().apply {
            tMapPoint = destinationPoint
            name = item.name
            icon = getBitmapFromVectorDrawable(R.drawable.ic_location_on)
            setPosition(0.5f, 1.0f)
        }

        tMapView.addMarkerItem("destination", marker)
        tMapView.setCenterPoint(lon, lat)
        tMapView.zoomLevel = 15

        if (currentLocation != null && destinationPoint != null) {
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
    }

    // 경유지 마커 설정 및 경로 재탐색
    private fun setWaypointMarker(item: PoiItem) {
        val lat = item.noorLat.toDoubleOrNull() ?: return
        val lon = item.noorLon.toDoubleOrNull() ?: return
        waypointPoint = TMapPoint(lat, lon)
        uiStateWaypointName = item.name

        tMapView.removeMarkerItem("waypoint")

        val marker = TMapMarkerItem().apply {
            tMapPoint = waypointPoint
            name = "경유지: ${item.name}"
            icon = getBitmapFromVectorDrawable(R.drawable.ic_location_on)
            setPosition(0.5f, 1.0f)
        }
        tMapView.addMarkerItem("waypoint", marker)
        speakTTS("${item.name}을 경유지로 설정했습니다.")

        if (currentLocation != null && destinationPoint != null) {
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
    }

    // 경유지 초기화
    private fun clearWaypoint() {
        waypointPoint = null
        uiStateWaypointName = ""
        tMapView.removeMarkerItem("waypoint")
        if (currentLocation != null && destinationPoint != null) {
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
    }

    // 도보 경로 탐색 API 호출
    private fun findPedestrianRoute(start: TMapPoint, end: TMapPoint) {
        val passListStr = waypointPoint?.let { "${it.longitude},${it.latitude}" }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.tmapService.getPedestrianRoute(
                    appKey = BuildConfig.TMAP_APP_KEY,
                    startX = start.longitude.toString(),
                    startY = start.latitude.toString(),
                    endX = end.longitude.toString(),
                    endY = end.latitude.toString(),
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

    // 경로 라인 그리기 및 분기점 데이터 파싱
    private fun drawRouteAndSaveSteps(features: List<RouteFeature>) {
        routeSteps.clear()
        allRoutePoints.clear()
        var totalTime = 0
        var totalDistance = 0

        features.forEach { f ->
            val geo = f.geometry
            val props = f.properties
            if (props.totalTime != null) totalTime = props.totalTime
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
                        val isInstruction = desc.contains("좌") || desc.contains("우") || desc.contains("횡단보도")

                        if ((isImportantTurn || isInstruction) && desc.length > 2) {
                            routeSteps.add(RouteStep(props.pointIndex, TMapPoint(c[1].asDouble, c[0].asDouble), desc, props.turnType, props.totalDistance ?: 0))
                        }
                    }
                }
            }
        }

        updatePolyline()

        if (uiStateIsNavigating) {
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

    // 지도상에 경로 라인 렌더링
    private fun updatePolyline() {
        tMapView.removeTMapPolyLine("pedestrian_route")
        if (allRoutePoints.isEmpty()) return

        val polyLine = TMapPolyLine().apply {
            lineColor = AndroidColor.parseColor("#215CF3")
            lineWidth = 11f
            outLineColor = AndroidColor.parseColor("#1976D2")
            outLineWidth = 2f
        }

        allRoutePoints.forEach { polyLine.addLinePoint(it) }
        tMapView.addTMapPolyLine("pedestrian_route", polyLine)
        tMapView.postInvalidate()
    }

    // 내비게이션 진입 전 탐색 취소
    private fun cancelRoutePreview() {
        tMapView.removeAllTMapPolyLine()
        tMapView.removeMarkerItem("destination")
        tMapView.removeMarkerItem("waypoint")
        uiStateIsRouteReady = false
        uiStateSearchQuery = ""
        uiStateWaypointName = ""
        waypointPoint = null
        currentLocation?.let { tMapView.setCenterPoint(it.longitude, it.latitude); tMapView.zoomLevel = 15 }
    }

    // 내비게이션 주행 시작 처리
    private fun startNavigation() {
        uiStateIsNavigating = true
        isRerouting = false
        isInitialDirectionAnnounced = false
        lastAnnouncedLoc = currentLocation

        upcomingSteps.clear()
        upcomingSteps.addAll(routeSteps)
        announcedStages.clear()
        lastStraightFeedbackTime = System.currentTimeMillis()

        speakTTS(if (uiStateIsMockMode) "가상 주행 안내를 시작합니다." else "안내를 시작합니다.")
        if (uiStateIsMockMode) startMockNavigation() else startRealNavigation()
        startStraightFeedback()
    }

    // 주행 종료 다이얼로그 표시
    private fun showStopDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("안내 종료")
            .setMessage("경로 안내를 종료하시겠습니까?")
            .setPositiveButton("예") { d, _ -> stopNavigation(); d.dismiss() }
            .setNegativeButton("아니오") { d, _ -> d.dismiss() }
            .show()
    }

    // 주행 종료 및 상태 초기화
    private fun stopNavigation() {
        uiStateIsNavigating = false
        isRerouting = false
        mockNavJob?.cancel()
        straightFeedbackJob?.cancel()

        if (this::locationCallback.isInitialized) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }

        speakTTS("경로 안내를 종료합니다.")
        cancelRoutePreview()
    }

    // 모의 주행 로직 (디버깅용)
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

    // 실시간 위치 추적 (실제 주행)
    @SuppressLint("MissingPermission")
    private fun startRealNavigation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(2000).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    // 위치 업데이트 콜백 처리
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

    // 진행률 파악 및 분기점별 안내(TTS) 로직 수행
    private fun checkNavigationProgress(currentLoc: TMapPoint) {
        if (isRerouting || allRoutePoints.isEmpty()) return

        var minDist = Float.MAX_VALUE
        var closestIdx = 0
        for (i in allRoutePoints.indices) {
            val dist = calculateDistance(currentLoc, allRoutePoints[i])
            if (dist < minDist) {
                minDist = dist
                closestIdx = i
            }
        }

        if (minDist > 30f && !uiStateIsMockMode) {
            isRerouting = true
            speakTTS("경로를 벗어났습니다. 경로를 재탐색합니다.")
            lastStraightFeedbackTime = System.currentTimeMillis()
            uiStateNavInstruction = "경로 재탐색 중..."
            destinationPoint?.let { findPedestrianRoute(currentLoc, it) }
            return
        }

        if (closestIdx > 0) {
            allRoutePoints.subList(0, closestIdx).clear()
            updatePolyline()
        }

        destinationPoint?.let { dest ->
            val dist = calculateDistance(currentLoc, dest)
            uiStateNavRemainDistance = String.format("목적지까지: %.0fm", dist)
            if (dist <= 15f) {
                speakTTSWithCooldown("도착했습니다. 안내를 종료합니다.")
                stopNavigation()
                return
            }
        }

        if (upcomingSteps.isNotEmpty()) {
            val next = upcomingSteps.first()
            currentStepForAI = next // AI 카메라 분석 시 참조될 대상 설정

            val distToTurn = calculateDistance(currentLoc, next.coordinate)
            val stages = announcedStages.getOrPut(next.pointIndex) { mutableSetOf() }

            uiStateShowAiGuideButton = distToTurn <= ANNOUNCE_DIST_FAR

            val dirWord = when (next.turnType) {
                12 -> "왼쪽으로 회전"
                13 -> "오른쪽으로 회전"
                14 -> "유턴"
                211, 212, 213, 214, 215, 216, 217 -> "횡단보도 건너기"
                else -> next.description
            }

            // 1단계 예고 안내 (50m 이내)
            if (distToTurn <= ANNOUNCE_DIST_FAR && "far" !in stages) {
                stages.add("far")
                lastStraightFeedbackTime = System.currentTimeMillis()

                val approxDist = (distToTurn / 10).toInt() * 10
                val preview = "약 ${approxDist}미터 앞, $dirWord 입니다."
                uiStateNavInstruction = preview
                speakTTSWithCooldown(preview)
            }

            // 2단계 랜드마크 연동 안내 (30m 이내)
            if (distToTurn <= ANNOUNCE_DIST_MID && "mid" !in stages) {
                stages.add("mid")
                lastStraightFeedbackTime = System.currentTimeMillis()

                lifecycleScope.launch {
                    Log.d(TAG, "=========================================")
                    Log.d(TAG, "[Gemini-Text] 랜드마크 안내 텍스트 생성 요청 시작")
                    val startTime = System.currentTimeMillis()

                    val landmark = getLandmarkForTurn(next.coordinate)
                    Log.d(TAG, "[Gemini-Text] 검색된 랜드마크: $landmark")

                    if (!landmark.isNullOrEmpty()) {
                        // 10초 타임아웃 추가
                        val rawInstruction = withTimeoutOrNull(10000L) {
                            GeminiHelper.enhanceNavigationText(next.description, landmark, currentAzimuth)
                        }

                        val endTime = System.currentTimeMillis()
                        Log.d(TAG, "[Gemini-Text] 요청 소요 시간: ${endTime - startTime}ms")
                        Log.d(TAG, "[Gemini-Text] 원본 응답: \n$rawInstruction")

                        if (rawInstruction == null) {
                            Log.w(TAG, "[Gemini-Text] 경고: rawInstruction이 null입니다. (타임아웃 또는 네트워크 오류)")
                        }

                        val cleanInstruction = rawInstruction
                            ?.replace("\"", "")
                            ?.replace("**", "")
                            ?.lines()
                            ?.firstOrNull { it.isNotBlank() && !it.contains("AI") }
                            ?: rawInstruction?.replace("\n", " ") ?: next.description

                        Log.d(TAG, "[Gemini-Text] 파싱된 최종 텍스트: $cleanInstruction")
                        Log.d(TAG, "=========================================")

                        uiStateNavInstruction = cleanInstruction
                        speakTTSWithCooldown(cleanInstruction)
                    } else {
                        Log.d(TAG, "[Gemini-Text] 랜드마크가 없어 기본 안내 대기 (10m 직전 안내 수행 예정)")
                        Log.d(TAG, "=========================================")
                    }
                }
            }

            // 3단계 진입 직전 안내 (10m 이내)
            if (distToTurn <= ANNOUNCE_DIST_NEAR && "near" !in stages) {
                stages.add("near")
                lastStraightFeedbackTime = System.currentTimeMillis()

                val instruction = "곧 $dirWord 하세요."
                uiStateNavInstruction = instruction
                speakTTSWithCooldown(instruction)

                upcomingSteps.removeAt(0)
                uiStateShowAiGuideButton = false
            }
        }
    }

    private fun calculateDistance(p1: TMapPoint, p2: TMapPoint): Float {
        val a = android.location.Location("").apply { latitude = p1.latitude; longitude = p1.longitude }
        val b = android.location.Location("").apply { latitude = p2.latitude; longitude = p2.longitude }
        return a.distanceTo(b)
    }

    private fun speakTTS(text: String) {
        if (isTtsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    private fun speakTTSWithCooldown(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastTTSTime >= TTS_COOLDOWN) { lastTTSTime = now; speakTTS(text) }
    }

    // 장기간 경로 변동 없을 시 직진 피드백 제공
    private fun startStraightFeedback() {
        straightFeedbackJob?.cancel()
        straightFeedbackJob = lifecycleScope.launch {
            while (uiStateIsNavigating) {
                delay(3_000L)
                if (!uiStateIsNavigating) break

                val now = System.currentTimeMillis()
                if (now - lastStraightFeedbackTime >= STRAIGHT_FEEDBACK_MS) {
                    lastStraightFeedbackTime = now

                    val currentLoc = currentLocation ?: continue
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
    }

    // AI 카메라 촬영 후 Gemini에 분석 요청
    private fun takePhotoAndSendToGemini() {
        // UI 버튼 노출 시 currentStepForAI가 이미 할당된 상태여야 함
        val step = currentStepForAI ?: upcomingSteps.firstOrNull() ?: return
        val capture = imageCapture ?: return

        uiStateAiStatusMessage = "사진 촬영 완료! AI가 분석 중입니다..."
        uiStateShowAiLoading = true

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    if (bitmap != null) {
                        processGeminiAnalysis(bitmap, step)
                    } else {
                        uiStateShowAiCamera = false
                        uiStateShowAiLoading = false
                        Toast.makeText(this@TmapsActivity, "이미지 변환에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onError(exc: ImageCaptureException) {
                    uiStateShowAiCamera = false
                    uiStateShowAiLoading = false
                    Toast.makeText(this@TmapsActivity, "촬영에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    // Gemini 응답 기반 안내 파싱 처리 (디버깅 로그 추가)
    private fun processGeminiAnalysis(bitmap: Bitmap, step: RouteStep) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "[Gemini] 이미지 분석 요청 시작 (방위각: $currentAzimuth)")
                val startTime = System.currentTimeMillis()

                // 10초 타임아웃 제한
                val rawResponse = withTimeoutOrNull(10000L) {
                    val result = GeminiHelper.analyzeImage(bitmap, step, currentAzimuth)
                    Log.d(TAG, "[Gemini] 원본 응답 도착: \n$result") // 가장 중요한 원본 텍스트 확인!
                    result?.trim()
                }

                val endTime = System.currentTimeMillis()
                Log.d(TAG, "[Gemini] 요청 소요 시간: ${endTime - startTime}ms")

                if (rawResponse == null) {
                    Log.w(TAG, "[Gemini] ⚠️ rawResponse가 null입니다. (10초 타임아웃 발생 또는 API 내부 오류)")
                }

                // 파싱 로직 수행
                val responseText = rawResponse
                    ?.replace("\"", "")
                    ?.replace("**", "")
                    ?.lines()
                    ?.firstOrNull { it.isNotBlank() && !it.contains("AI") }
                    ?.trim()

                Log.d(TAG, "[Gemini] 파싱된 최종 텍스트: $responseText")
                Log.d(TAG, "=========================================")

                withContext(Dispatchers.Main) {
                    uiStateShowAiLoading = false
                    uiStateShowAiCamera = false

                    if (responseText.isNullOrEmpty() || responseText == "0") {
                        val fallback = step.description
                        uiStateNavInstruction = fallback
                        speakTTS(fallback)
                        Toast.makeText(this@TmapsActivity, "AI 응답 지연으로 기본 안내로 대체합니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        uiStateNavInstruction = responseText
                        speakTTS(responseText)
                    }
                }
            } catch (e: Exception) {
                // 앱이 터지지 않게 막아주지만, 무슨 에러인지 로그로 확인
                Log.e(TAG, "[Gemini] 🚨 예외 발생 (Exception): ${e.message}", e)

                withContext(Dispatchers.Main) {
                    uiStateShowAiLoading = false
                    uiStateShowAiCamera = false
                    val fallback = step.description
                    uiStateNavInstruction = fallback
                    speakTTS(fallback)
                }
            }
        }
    }

    // ImageProxy를 Bitmap으로 변환 처리 (회전 대응)
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val matrix = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
            postScale(640f / original.width, 480f / original.height)
        }
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    }

    // ============================================================
    // Compose 화면 UI 레이아웃
    // ============================================================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun NavigationScreen() {
        val focusManager = LocalFocusManager.current
        val lifecycleOwner = LocalLifecycleOwner.current

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
            AndroidView(
                factory = { tMapView },
                modifier = Modifier.fillMaxSize()
            )

            // 내비게이션 시작 이전 레이아웃
            if (!uiStateIsNavigating && !uiStateShowAiCamera) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(30.dp).align(Alignment.TopCenter),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    OutlinedTextField(
                        value = uiStateSearchQuery,
                        onValueChange = { uiStateSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("어디로 갈까요?", color = Color.Gray) },
                        leadingIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.Close, contentDescription = "닫기")
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (uiStateSearchQuery.isNotEmpty()) searchPOI(uiStateSearchQuery)
                                }
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "검색", tint = Color(0xFF102841))
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
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

                if (uiStateIsRouteReady) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("최적 도보 경로", fontSize = 14.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiStateRouteSummary, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (uiStateWaypointName.isEmpty()) "경유지 없음" else "경유지: $uiStateWaypointName",
                                    fontSize = 14.sp,
                                    color = Color.DarkGray
                                )
                                if (uiStateWaypointName.isEmpty()) {
                                    OutlinedButton(
                                        onClick = { uiStateShowWaypointInputDialog = true },
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("+ 경유지", color = Color(0xFF4CAF50)) }
                                } else {
                                    OutlinedButton(
                                        onClick = { clearWaypoint() },
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("경유지 삭제", color = Color(0xFFD32F2F)) }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFFFCFAEC), RoundedCornerShape(8.dp)).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("가상 주행 모드 (디버깅용)", fontSize = 14.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = uiStateIsMockMode,
                                    onCheckedChange = { uiStateIsMockMode = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1976D2), checkedTrackColor = Color(0xFF9EC6EE))
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { cancelRoutePreview() },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("취소", fontSize = 16.sp, color = Color.DarkGray) }

                                Button(
                                    onClick = { startNavigation() },
                                    modifier = Modifier.weight(2f).height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("안내 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }

            // 내비게이션 주행 레이아웃
            if (uiStateIsNavigating && !uiStateShowAiCamera) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1976D2)),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiStateNavInstruction,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                if (uiStateShowAiGuideButton) {
                    FloatingActionButton(
                        onClick = {
                            uiStateShowAiCamera = true
                            uiStateAiStatusMessage = "전방의 랜드마크를 비춘 후 촬영 버튼을 눌러주세요."
                            speakTTS("전방을 비춘 후 촬영 버튼을 눌러주세요.")
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp),
                        containerColor = Color(0xFFFF9800),
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "AI 랜드마크 안내")
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(uiStateNavRemainDistance, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Button(
                            onClick = { showStopDialog() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("안내 종료", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            // 내 위치 찾기 플로팅 버튼
            if (!uiStateShowAiCamera) {
                FloatingActionButton(
                    onClick = { fetchCurrentLocation() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = if (uiStateIsRouteReady) 320.dp else if (uiStateIsNavigating) 120.dp else 40.dp
                        ),
                    containerColor = Color.White,
                    contentColor = Color(0xFF1976D2),
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "내 위치 찾기", modifier = Modifier.size(28.dp))
                }
            }

            // AI 카메라 뷰 오버레이
            if (uiStateShowAiCamera) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { context ->
                            val previewView = PreviewView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }

                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                                } catch (exc: Exception) {
                                    Log.e(TAG, "카메라 연결 실패", exc)
                                }
                            }, ContextCompat.getMainExecutor(context))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiStateAiStatusMessage,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(12.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (uiStateShowAiLoading) {
                            CircularProgressIndicator(color = Color.White)
                        } else {
                            FloatingActionButton(
                                onClick = { takePhotoAndSendToGemini() },
                                modifier = Modifier.size(72.dp),
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "랜드마크 촬영", modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }
            }

            // 목적지 검색 결과 다이얼로그
            if (uiStateShowSearchDialog) {
                AlertDialog(
                    onDismissRequest = { uiStateShowSearchDialog = false },
                    title = { Text("어느 곳으로 갈까요?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black) },
                    text = {
                        LazyColumn {
                            items(uiStateSearchResults) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            uiStateShowSearchDialog = false
                                            if (isSearchingForWaypoint) {
                                                setWaypointMarker(item)
                                            } else {
                                                setDestinationMarker(item)
                                            }
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

            // 경유지 검색 다이얼로그
            if (uiStateShowWaypointInputDialog) {
                AlertDialog(
                    onDismissRequest = { uiStateShowWaypointInputDialog = false },
                    title = { Text("경유지 추가", fontWeight = FontWeight.Bold, color = Color.Black) },
                    text = {
                        OutlinedTextField(
                            value = uiStateWaypointQuery,
                            onValueChange = { uiStateWaypointQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("경유지를 입력하세요") },
                            singleLine = true,
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
                        TextButton(onClick = { uiStateShowWaypointInputDialog = false }) { Text("취소", color = Color.Gray) }
                    },
                    containerColor = Color.White
                )
            }
        }
    }
}