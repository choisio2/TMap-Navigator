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

    companion object {
        private const val TAG = "TMAP_debug"

        // 안내 우선순위 정의 (숫자가 클수록 높은 우선순위)
        private const val PRI_URGENT = 5   // 출발, 도착, 경로이탈
        private const val PRI_NEAR = 4     // 10m 안내
        private const val PRI_MID = 3      // 30m 랜드마크 안내
        private const val PRI_FAR = 2      // 50m 예고
        private const val PRI_FEEDBACK = 1 // 직진 피드백

        private const val MOCK_SPEED_MS = 2000L
        private const val STRAIGHT_FEEDBACK_MS = 30_000L

        private const val ANNOUNCE_DIST_FAR  = 50f
        private const val ANNOUNCE_DIST_MID  = 30f
        private const val ANNOUNCE_DIST_NEAR = 10f

        private const val REROUTE_THRESHOLD_M   = 25f
        private const val REROUTE_CONFIRM_COUNT = 3
        private const val POLYLINE_TRIM_MAX = 3
        private const val LANDMARK_MAX_DIST_M = 15f

        private const val DIRECTION_THRESHOLD_BACK  = 150f
        private const val DIRECTION_THRESHOLD_TURN  = 45f
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var tts: TextToSpeech
    private lateinit var sensorManager: SensorManager
    private lateinit var tMapView: TMapView

    private var currentLocation: TMapPoint? = null
    private var destinationPoint: TMapPoint? = null
    private var waypointPoint: TMapPoint? = null

    private val allRoutePoints = mutableListOf<TMapPoint>()
    private val routeSteps    = mutableListOf<RouteStep>()
    private val upcomingSteps = mutableListOf<RouteStep>()

    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity     = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var currentAzimuth    = 0f
    private var lastMarkerAzimuth = 0f
    private var myLocationBaseBitmap: Bitmap? = null

    private var isTtsReady   = false
    private var isRerouting  = false
    private var offRouteCount = 0
    private var isInitialDirectionAnnounced = false

    private var mockNavJob: Job? = null
    private var straightFeedbackJob: Job? = null

    private val announcedStages = mutableMapOf<Int, MutableSet<String>>()
    private var lastStraightFeedbackTime = 0L

    // 우선순위 큐 대체 변수
    private var currentPriority = 0
    private var priorityReleaseTime = 0L

    private var uiStateSearchQuery   by mutableStateOf("")
    private var uiStateIsRouteReady  by mutableStateOf(false)
    private var uiStateRouteSummary  by mutableStateOf("")
    private var uiStateWaypointName  by mutableStateOf("")

    private var uiStateIsNavigating      by mutableStateOf(false)
    private var uiStateIsMockMode        by mutableStateOf(true)
    private var uiStateNavInstruction    by mutableStateOf("경로를 탐색합니다.")
    private var uiStateNavRemainDistance by mutableStateOf("")

    private var uiStateShowSearchDialog    by mutableStateOf(false)
    private var uiStateSearchResults       by mutableStateOf<List<PoiItem>>(emptyList())
    private var isSearchingForWaypoint     by mutableStateOf(false)
    private var uiStateShowWaypointInputDialog by mutableStateOf(false)
    private var uiStateWaypointQuery       by mutableStateOf("")

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
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
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

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTMapView() {
        tMapView = TMapView(this).apply {
            setSKTMapApiKey(BuildConfig.TMAP_APP_KEY)
            setOnApiKeyListener(object : TMapView.OnApiKeyListenerCallback {
                override fun SKTMapApikeySucceed() { runOnUiThread { fetchCurrentLocation() } }
                override fun SKTMapApikeyFailed(msg: String?) { Log.e(TAG, "인증 실패: $msg") }
            })
        }
    }

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
    }

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

    private fun calculateDistance(p1: TMapPoint, p2: TMapPoint): Float {
        val a = android.location.Location("").apply { latitude = p1.latitude; longitude = p1.longitude }
        val b = android.location.Location("").apply { latitude = p2.latitude; longitude = p2.longitude }
        return a.distanceTo(b)
    }

    private fun bearingBetween(from: TMapPoint, to: TMapPoint): Float {
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (bearing + 360) % 360
    }

    // 텍스트 출력과 보이스 출력을 동기화하고 우선순위 제어하기 위한 함수 
    private fun announceInstruction(msg: String, priority: Int): Boolean {
        val now = System.currentTimeMillis()

        // 현재 더 높은 우선순위 안내가 진행 중이고 4초가 안 지났다면 무시
        if (now < priorityReleaseTime && priority < currentPriority) {
            return false
        }

        uiStateNavInstruction = msg
        if (isTtsReady) {
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "tts_$now")
        }

        // 현재 우선순위 등록 및 4초간 하위 순위 침범 차단
        currentPriority = priority
        priorityReleaseTime = now + 4000L
        return true
    }

    private fun searchPOI(query: String, isWaypoint: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.tmapService.searchPOI(
                    appKey = BuildConfig.TMAP_APP_KEY,
                    keyword = query,
                    categories = "은행,편의점,미용실,커피,음식,카페,전문음식점,디저트,패스트푸드,병원,약국,내과,소아과,한의원,영화관,문화시설"
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
                withContext(Dispatchers.Main) { Toast.makeText(this@TmapsActivity, "네트워크 오류", Toast.LENGTH_SHORT).show() }
            }
        }
    }

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

        if (currentLocation != null) findPedestrianRoute(currentLocation!!, destinationPoint!!)
    }

    private fun setWaypointMarker(item: PoiItem) {
        val lat = item.noorLat.toDoubleOrNull() ?: return
        val lon = item.noorLon.toDoubleOrNull() ?: return
        waypointPoint = TMapPoint(lat, lon)
        uiStateWaypointName = item.name

        tMapView.removeMarkerItem("waypoint")
        val marker = TMapMarkerItem().apply {
            tMapPoint = waypointPoint; name = "경유지: ${item.name}"
            icon = getBitmapFromVectorDrawable(R.drawable.ic_location_on); setPosition(0.5f, 1.0f)
        }
        tMapView.addMarkerItem("waypoint", marker)
        announceInstruction("${item.name}을 경유지로 설정했습니다.", PRI_URGENT)

        if (currentLocation != null && destinationPoint != null) findPedestrianRoute(currentLocation!!, destinationPoint!!)
    }

    private fun clearWaypoint() {
        waypointPoint = null
        uiStateWaypointName = ""
        tMapView.removeMarkerItem("waypoint")
        if (currentLocation != null && destinationPoint != null) findPedestrianRoute(currentLocation!!, destinationPoint!!)
    }

    private fun findPedestrianRoute(start: TMapPoint, end: TMapPoint) {
        val passListStr = waypointPoint?.let { "${it.longitude},${it.latitude}" }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.tmapService.getPedestrianRoute(
                    appKey = BuildConfig.TMAP_APP_KEY,
                    startX = start.longitude.toString(), startY = start.latitude.toString(),
                    endX = end.longitude.toString(), endY = end.latitude.toString(),
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
                withContext(Dispatchers.Main) { Toast.makeText(this@TmapsActivity, "네트워크 연결 불안정", Toast.LENGTH_SHORT).show(); isRerouting = false }
            }
        }
    }

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
            announceInstruction("새로운 경로를 찾았습니다. 안내를 계속합니다.", PRI_URGENT)
            lastStraightFeedbackTime = System.currentTimeMillis()
            isRerouting = false
            if (uiStateIsMockMode) startMockNavigation()
        } else {
            uiStateRouteSummary = String.format("도보 %d분 (%.1fkm)", totalTime / 60, totalDistance / 1000.0)
            uiStateIsRouteReady = true
        }
    }

    private fun updatePolyline() {
        tMapView.removeTMapPolyLine("pedestrian_route")
        if (allRoutePoints.isEmpty()) return
        val polyLine = TMapPolyLine().apply {
            lineColor = AndroidColor.parseColor("#215CF3"); lineWidth = 11f
            outLineColor = AndroidColor.parseColor("#1976D2"); outLineWidth = 2f
        }
        allRoutePoints.forEach { polyLine.addLinePoint(it) }
        tMapView.addTMapPolyLine("pedestrian_route", polyLine)
        tMapView.postInvalidate()
    }

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

    private fun startNavigation() {
        uiStateIsNavigating = true
        isRerouting = false
        offRouteCount = 0
        isInitialDirectionAnnounced = false

        upcomingSteps.clear()
        upcomingSteps.addAll(routeSteps)
        announcedStages.clear()
        lastStraightFeedbackTime = System.currentTimeMillis()

        announceInstruction(if (uiStateIsMockMode) "가상 주행 안내를 시작합니다." else "안내를 시작합니다.", PRI_URGENT)

        if (uiStateIsMockMode) startMockNavigation() else startRealNavigation()
        startStraightFeedback()
    }

    private fun showStopDialog() {
        AlertDialog.Builder(this)
            .setTitle("안내 종료").setMessage("경로 안내를 종료하시겠습니까?")
            .setPositiveButton("예") { d, _ -> stopNavigation(); d.dismiss() }
            .setNegativeButton("아니오") { d, _ -> d.dismiss() }.show()
    }

    private fun stopNavigation() {
        uiStateIsNavigating = false
        isRerouting = false
        offRouteCount = 0
        mockNavJob?.cancel()
        straightFeedbackJob?.cancel()

        if (this::locationCallback.isInitialized) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }
        announceInstruction("경로 안내를 종료합니다.", PRI_URGENT)
        cancelRoutePreview()
    }

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

    @SuppressLint("MissingPermission")
    private fun startRealNavigation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).setMinUpdateIntervalMillis(2000).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private fun checkNavigationProgress(currentLoc: TMapPoint) {
        if (isRerouting || allRoutePoints.isEmpty()) return

        // 1순위: 출발 시 최초 방향 판단 로직
        if (!isInitialDirectionAnnounced) {
            val start = currentLocation ?: return
            val firstStep = upcomingSteps.firstOrNull() ?: return
            val targetBearing = bearingBetween(start, firstStep.coordinate)
            var diff = targetBearing - currentAzimuth
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f

            val msg = when {
                abs(diff) >= DIRECTION_THRESHOLD_BACK -> "뒤돌아서 출발하세요."
                diff >= DIRECTION_THRESHOLD_TURN -> "오른쪽으로 돌아서 출발하세요."
                diff <= -DIRECTION_THRESHOLD_TURN -> "왼쪽으로 돌아서 출발하세요."
                else -> "경로를 따라 출발하세요."
            }

            announceInstruction(msg, PRI_URGENT)
            isInitialDirectionAnnounced = true
        }

        var minDist = Float.MAX_VALUE
        var closestIdx = 0
        for (i in allRoutePoints.indices) {
            val dist = calculateDistance(currentLoc, allRoutePoints[i])
            if (dist < minDist) { minDist = dist; closestIdx = i }
        }

        // 경로 이탈 처리
        if (minDist > REROUTE_THRESHOLD_M && !uiStateIsMockMode) {
            offRouteCount++
            if (offRouteCount >= REROUTE_CONFIRM_COUNT) {
                offRouteCount = 0; isRerouting = true
                announceInstruction("경로를 벗어났습니다. 경로를 재탐색합니다.", PRI_URGENT)
                lastStraightFeedbackTime = System.currentTimeMillis()
                destinationPoint?.let { findPedestrianRoute(currentLoc, it) }
            }
            return
        } else {
            offRouteCount = 0
        }

        if (closestIdx > 0) {
            val trimCount = minOf(closestIdx, POLYLINE_TRIM_MAX)
            allRoutePoints.subList(0, trimCount).clear()
            updatePolyline()
        }

        // 목적지 도착
        destinationPoint?.let { dest ->
            val dist = calculateDistance(currentLoc, dest)
            uiStateNavRemainDistance = String.format("목적지까지: %.0fm", dist)
            if (dist <= 15f) {
                announceInstruction("도착했습니다. 안내를 종료합니다.", PRI_URGENT)
                stopNavigation()
                return
            }
        }

        // 3단계 안내 로직
        if (upcomingSteps.isNotEmpty()) {
            val next = upcomingSteps.first()
            val distToTurn = calculateDistance(currentLoc, next.coordinate)
            val stages = announcedStages.getOrPut(next.pointIndex) { mutableSetOf() }

            if (distToTurn <= ANNOUNCE_DIST_FAR && "far" !in stages) {
                val approxDist = (distToTurn / 10).toInt() * 10
                val msg = buildFarAnnounce(next.turnType, approxDist, next.description)
                // 우선순위가 낮아 실행 거부될 경우 stages에 추가하지 않고 다음 틱에 재시도
                if (announceInstruction(msg, PRI_FAR)) {
                    stages.add("far")
                    lastStraightFeedbackTime = System.currentTimeMillis()
                }
            }

            // 중복 실행 방지를 위해 mid_processing 상태 추가
            if (distToTurn <= ANNOUNCE_DIST_MID && "mid" !in stages && "mid_processing" !in stages) {
                stages.add("mid_processing")
                if (next.turnType in 211..217) {
                    val msg = buildMidAnnounceCrosswalk(next.description)
                    if (announceInstruction(msg, PRI_MID)) {
                        stages.add("mid")
                        stages.remove("mid_processing")
                        lastStraightFeedbackTime = System.currentTimeMillis()
                    } else {
                        stages.remove("mid_processing")
                    }
                } else {
                    lifecycleScope.launch {
                        val landmark = getLandmarkForTurn(next.coordinate)
                        val msg = if (landmark != null) {
                            formatLandmarkInstruction(landmark, next.turnType, next.description)
                        } else {
                            buildMidAnnounce(next.turnType, next.description)
                        }
                        withContext(Dispatchers.Main) {
                            if (announceInstruction(msg, PRI_MID)) {
                                stages.add("mid")
                                stages.remove("mid_processing")
                                lastStraightFeedbackTime = System.currentTimeMillis()
                            } else {
                                stages.remove("mid_processing")
                            }
                        }
                    }
                }
            }

            if (distToTurn <= ANNOUNCE_DIST_NEAR && "near" !in stages) {
                val msg = buildNearAnnounce(next.turnType, next.description)
                if (announceInstruction(msg, PRI_NEAR)) {
                    stages.add("near")
                    lastStraightFeedbackTime = System.currentTimeMillis()

                    // 리스트 비우기
                    announcedStages.remove(next.pointIndex)
                    upcomingSteps.removeAt(0)
                }
            }
        }
    }

    private suspend fun getLandmarkForTurn(point: TMapPoint): String? = withContext(Dispatchers.IO) {
        try {
            val resp = RetrofitClient.tmapService.searchAroundPOI(
                appKey = BuildConfig.TMAP_APP_KEY,
                centerLat = point.latitude.toString(), centerLon = point.longitude.toString(),
                radius = "1", count = 10,
                categories = "은행,편의점,미용실,커피,음식,카페,전문음식점,디저트,패스트푸드,병원,약국,내과,소아과,한의원,영화관,문화시설"
            )

            val poiList = resp.body()?.searchPoiInfo?.pois?.poiList ?: return@withContext null
            val turnLoc = android.location.Location("").apply { latitude = point.latitude; longitude = point.longitude }

            val closest = poiList.mapNotNull { poi ->
                val lat = poi.noorLat.toDoubleOrNull() ?: return@mapNotNull null
                val lon = poi.noorLon.toDoubleOrNull() ?: return@mapNotNull null
                val poiLoc = android.location.Location("").apply { latitude = lat; longitude = lon }
                val distM = turnLoc.distanceTo(poiLoc)
                if (distM > LANDMARK_MAX_DIST_M) null else Pair(poi.name, distM)
            }.minByOrNull { it.second }

            closest?.first?.split(" ")?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun buildFarAnnounce(turnType: Int, approxDist: Int, desc: String): String {
        return when (turnType) {
            12 -> "약 ${approxDist}미터 앞에서 왼쪽으로 회전하세요."
            13 -> "약 ${approxDist}미터 앞에서 오른쪽으로 회전하세요."
            14 -> "약 ${approxDist}미터 앞에서 유턴하세요."
            in 211..217 -> "약 ${approxDist}미터 앞에 횡단보도가 있습니다."
            else -> "약 ${approxDist}미터 앞, $desc"
        }
    }

    private fun buildMidAnnounce(turnType: Int, desc: String): String {
        return when (turnType) {
            12 -> "왼쪽으로 회전할 준비를 하세요."
            13 -> "오른쪽으로 회전할 준비를 하세요."
            14 -> "유턴할 준비를 하세요."
            in 211..217 -> "횡단보도가 가까워지고 있습니다."
            else -> desc
        }
    }

    private fun buildMidAnnounceCrosswalk(desc: String): String {
        return when {
            desc.contains("신호등") -> "신호등이 있는 횡단보도입니다. 신호를 확인하고 건너세요."
            desc.contains("육교") -> "육교를 이용해 건너세요."
            desc.contains("지하도") -> "지하도를 이용해 건너세요."
            else -> "횡단보도를 건너세요."
        }
    }

    private fun formatLandmarkInstruction(poiName: String, turnType: Int, defaultDesc: String): String {
        return when (turnType) {
            12 -> "${poiName}을 끼고 왼쪽으로 회전하세요."
            13 -> "${poiName}을 끼고 오른쪽으로 회전하세요."
            14 -> "${poiName} 앞에서 유턴하세요."
            else -> defaultDesc
        }
    }

    private fun buildNearAnnounce(turnType: Int, desc: String): String {
        return when (turnType) {
            12 -> "지금 왼쪽으로 회전하세요."
            13 -> "지금 오른쪽으로 회전하세요."
            14 -> "지금 유턴하세요."
            in 211..217 -> "지금 횡단보도를 건너세요."
            else -> "곧 $desc"
        }
    }

    private fun startStraightFeedback() {
        straightFeedbackJob?.cancel()
        straightFeedbackJob = lifecycleScope.launch {
            while (uiStateIsNavigating) {
                delay(3_000L)
                if (!uiStateIsNavigating) break

                val now = System.currentTimeMillis()
                if (now - lastStraightFeedbackTime < STRAIGHT_FEEDBACK_MS) continue

                val currentLoc = currentLocation ?: continue
                val nextStep = upcomingSteps.firstOrNull()

                if (nextStep != null && calculateDistance(currentLoc, nextStep.coordinate) <= ANNOUNCE_DIST_FAR) continue

                val message = if (upcomingSteps.isNotEmpty()) {
                    "계속 직진하세요."
                } else {
                    destinationPoint?.let { dest ->
                        val dist = calculateDistance(currentLoc, dest).toInt()
                        "목적지까지 약 ${dist}미터 남았습니다. 계속 직진하세요."
                    } ?: "잘 가고 있어요. 계속 직진하세요."
                }

                withContext(Dispatchers.Main) {
                    if (announceInstruction(message, PRI_FEEDBACK)) {
                        lastStraightFeedbackTime = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    // Compose 화면 UI 레이아웃
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun NavigationScreen() {
        val focusManager = LocalFocusManager.current

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
            AndroidView(factory = { tMapView }, modifier = Modifier.fillMaxSize())

            if (!uiStateIsNavigating) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(30.dp).align(Alignment.TopCenter),
                    shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    OutlinedTextField(
                        value = uiStateSearchQuery, onValueChange = { uiStateSearchQuery = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("어디로 갈까요?", color = Color.Gray) }, leadingIcon = { IconButton(onClick = { finish() }) { Icon(Icons.Default.Close, "닫기") } },
                        trailingIcon = { IconButton(onClick = { focusManager.clearFocus(); if (uiStateSearchQuery.isNotEmpty()) searchPOI(uiStateSearchQuery) }) { Icon(Icons.Default.Search, "검색", tint = Color(0xFF102841)) } },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); if (uiStateSearchQuery.isNotEmpty()) searchPOI(uiStateSearchQuery) }),
                        singleLine = true
                    )
                }

                if (uiStateIsRouteReady) {
                    Card(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),
                        shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("최적 도보 경로", fontSize = 14.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiStateRouteSummary, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(if (uiStateWaypointName.isEmpty()) "경유지 없음" else "경유지: $uiStateWaypointName", fontSize = 14.sp, color = Color.DarkGray)
                                if (uiStateWaypointName.isEmpty()) OutlinedButton(onClick = { uiStateShowWaypointInputDialog = true }, shape = RoundedCornerShape(8.dp)) { Text("+ 경유지", color = Color(0xFF4CAF50)) }
                                else OutlinedButton(onClick = { clearWaypoint() }, shape = RoundedCornerShape(8.dp)) { Text("경유지 삭제", color = Color(0xFFD32F2F)) }
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFFCFAEC), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("가상 주행 모드 (디버깅용)", fontSize = 14.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.SemiBold)
                                Switch(checked = uiStateIsMockMode, onCheckedChange = { uiStateIsMockMode = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1976D2), checkedTrackColor = Color(0xFF9EC6EE)))
                            }
                            Spacer(modifier = Modifier.height(20.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { cancelRoutePreview() }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp)) { Text("취소", fontSize = 16.sp, color = Color.DarkGray) }
                                Button(onClick = { startNavigation() }, modifier = Modifier.weight(2f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)), shape = RoundedCornerShape(12.dp)) { Text("안내 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }

            if (uiStateIsNavigating) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
                    shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1976D2)), elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiStateNavInstruction, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(uiStateNavRemainDistance, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Button(onClick = { showStopDialog() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), shape = RoundedCornerShape(12.dp)) { Text("안내 종료", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            FloatingActionButton(
                onClick = { fetchCurrentLocation() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (uiStateIsRouteReady) 320.dp else if (uiStateIsNavigating) 120.dp else 40.dp),
                containerColor = Color.White, contentColor = Color(0xFF1976D2), shape = CircleShape, elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) { Icon(Icons.Default.LocationOn, "내 위치 찾기", modifier = Modifier.size(28.dp)) }

            if (uiStateShowSearchDialog) {
                AlertDialog(
                    onDismissRequest = { uiStateShowSearchDialog = false },
                    title = { Text("어느 곳으로 갈까요?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black) },
                    text = {
                        LazyColumn {
                            items(uiStateSearchResults) { item ->
                                Column(
                                    modifier = Modifier.fillMaxWidth().clickable { uiStateShowSearchDialog = false; if (isSearchingForWaypoint) setWaypointMarker(item) else setDestinationMarker(item) }.padding(vertical = 14.dp)
                                ) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(item.getFullAddress(), fontSize = 13.sp, color = Color.Gray)
                                }
                                Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { uiStateShowSearchDialog = false }) { Text("취소", color = Color(0xFF6200EE), fontWeight = FontWeight.Bold) } },
                    containerColor = Color.White
                )
            }

            if (uiStateShowWaypointInputDialog) {
                AlertDialog(
                    onDismissRequest = { uiStateShowWaypointInputDialog = false },
                    title = { Text("경유지 추가", fontWeight = FontWeight.Bold, color = Color.Black) },
                    text = {
                        OutlinedTextField(
                            value = uiStateWaypointQuery, onValueChange = { uiStateWaypointQuery = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("경유지를 입력하세요") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (uiStateWaypointQuery.isNotEmpty()) { uiStateShowWaypointInputDialog = false; searchPOI(uiStateWaypointQuery, true) } })
                        )
                    },
                    confirmButton = { Button(onClick = { if (uiStateWaypointQuery.isNotEmpty()) { uiStateShowWaypointInputDialog = false; searchPOI(uiStateWaypointQuery, true) } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))) { Text("검색", fontWeight = FontWeight.Bold) } },
                    dismissButton = { TextButton(onClick = { uiStateShowWaypointInputDialog = false }) { Text("취소", color = Color.Gray) } },
                    containerColor = Color.White
                )
            }
        }
    }
}