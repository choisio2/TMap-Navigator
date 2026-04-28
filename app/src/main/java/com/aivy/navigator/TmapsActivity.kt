package com.aivy.navigator

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace
import com.aivy.navigator.ui.theme.AivyTheme
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
        private const val TTS_COOLDOWN = 2000L
        private const val MOCK_SPEED_MS = 1000L
    }

    // 시스템 및 하드웨어 매니저
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var tts: TextToSpeech
    private lateinit var sensorManager: SensorManager
    private lateinit var cameraExecutor: ExecutorService

    // 지도 객체
    private lateinit var tMapView: TMapView

    // 위치 및 경로 데이터
    private var currentLocation: TMapPoint? = null
    private var destinationPoint: TMapPoint? = null
    private var waypointPoint: TMapPoint? = null
    private val allRoutePoints = mutableListOf<TMapPoint>()
    private val routeSteps = mutableListOf<RouteStep>()
    private val upcomingSteps = mutableListOf<RouteStep>()
    private val announcedStepIndices = mutableSetOf<Int>()

    // 방향 센서 상태
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var currentAzimuth = 0f
    private var lastMarkerAzimuth = 0f
    private var myLocationBaseBitmap: Bitmap? = null

    // 내비게이션 상태 플래그
    private var isTtsReady = false
    private var isRerouting = false
    private var lastTTSTime = 0L
    private var lastAnnouncedLoc: TMapPoint? = null
    private var isInitialDirectionAnnounced = false
    private var mockNavJob: Job? = null

    // 카메라 및 AI 상태
    private var imageCapture: ImageCapture? = null
    private var currentStepForAI: RouteStep? = null

    // UI 상태 변수 모음
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

    // 다이얼로그 상태
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

        // 앱 진입 즉시 방향 센서 활성화
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
            AivyTheme {
                NavigationScreen()
            }
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

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(this, "위치 권한이 거부되어 현재 위치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTMapView() {
        tMapView = TMapView(this).apply {
            setSKTMapApiKey(BuildConfig.TMAP_APP_KEY)
            setOnApiKeyListener(object : TMapView.OnApiKeyListenerCallback {
                override fun SKTMapApikeySucceed() { runOnUiThread { fetchCurrentLocation() } }
                override fun SKTMapApikeyFailed(msg: String?) { Log.e(TAG, "인증실패: $msg") }
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

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
            if (loc != null) {
                currentLocation = TMapPoint(loc.latitude, loc.longitude)
                updateMyLocationMarker()
                tMapView.setCenterPoint(loc.longitude, loc.latitude)
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

    private suspend fun getLandmarkForTurn(point: TMapPoint): String? = withContext(Dispatchers.IO) {
        try {
            val targetCategories = "편의점,커피전문점,패스트푸드,지하철역,은행,약국"
            val resp = RetrofitClient.tmapService.searchAroundPOI(
                appKey = BuildConfig.TMAP_APP_KEY,
                centerLat = point.latitude.toString(),
                centerLon = point.longitude.toString(),
                radius = "1",
                count = 5,
                categories = targetCategories
            )

            val poiList = resp.body()?.searchPoiInfo?.pois?.poiList
            if (poiList.isNullOrEmpty()) return@withContext null
            return@withContext poiList.firstOrNull()?.name
        } catch (e: Exception) {
            return@withContext null
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

        if (currentLocation != null && destinationPoint != null) {
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
    }

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

    private fun clearWaypoint() {
        waypointPoint = null
        uiStateWaypointName = ""
        tMapView.removeMarkerItem("waypoint")
        if (currentLocation != null && destinationPoint != null) {
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
    }

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
            announcedStepIndices.clear()

            uiStateNavInstruction = "새로운 경로로 안내를 계속합니다."
            speakTTS("새로운 경로를 찾았습니다. 안내를 계속합니다.")
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
            lineColor = AndroidColor.parseColor("#215CF3")
            lineWidth = 11f
            outLineColor = AndroidColor.parseColor("#1976D2")
            outLineWidth = 2f
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
        isInitialDirectionAnnounced = false
        lastAnnouncedLoc = currentLocation

        upcomingSteps.clear()
        upcomingSteps.addAll(routeSteps)

        speakTTS(if (uiStateIsMockMode) "가상 주행 안내를 시작합니다." else "안내를 시작합니다.")
        if (uiStateIsMockMode) startMockNavigation() else startRealNavigation()
    }

    private fun showStopDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("안내 종료")
            .setMessage("경로 안내를 종료하시겠습니까?")
            .setPositiveButton("예") { d, _ -> stopNavigation(); d.dismiss() }
            .setNegativeButton("아니오") { d, _ -> d.dismiss() }
            .show()
    }

    private fun stopNavigation() {
        uiStateIsNavigating = false
        isRerouting = false
        mockNavJob?.cancel()

        if (this::locationCallback.isInitialized) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }

        speakTTS("경로 안내를 종료합니다.")
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

    private fun startRealNavigation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(2000).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
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
            val distToTurn = calculateDistance(currentLoc, next.coordinate)

            if (distToTurn <= 30f && !announcedStepIndices.contains(next.pointIndex)) {
                announcedStepIndices.add(next.pointIndex)

                lifecycleScope.launch {
                    val landmark = getLandmarkForTurn(next.coordinate) ?: "특징적인 랜드마크 없음"
                    val rawInstruction = GeminiHelper.enhanceNavigationText(next.description, landmark, currentAzimuth)

                    val cleanInstruction = rawInstruction
                        .replace("\"", "")
                        .replace("**", "")
                        .lines()
                        .firstOrNull { it.isNotBlank() && !it.contains("AI") }
                        ?: rawInstruction.replace("\n", " ")

                    uiStateNavInstruction = cleanInstruction
                    speakTTSWithCooldown(cleanInstruction)
                    upcomingSteps.removeAt(0)
                    uiStateShowAiGuideButton = false
                }
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

    private fun takePhotoAndSendToGemini() {
        val step = currentStepForAI ?: return
        val capture = imageCapture ?: return

        uiStateAiStatusMessage = "사진 촬영 완료! AI가 분석 중입니다..."
        uiStateShowAiLoading = true

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    if (bitmap != null) processGeminiAnalysis(bitmap, step)
                }
                override fun onError(exc: ImageCaptureException) {
                    uiStateShowAiCamera = false
                    uiStateShowAiLoading = false
                    Toast.makeText(this@TmapsActivity, "촬영에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun processGeminiAnalysis(bitmap: Bitmap, step: RouteStep) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rawResponse = withTimeoutOrNull(5000L) {
                    GeminiHelper.analyzeImage(bitmap, step, currentAzimuth)?.trim()
                }

                val responseText = rawResponse
                    ?.replace("\"", "")
                    ?.replace("**", "")
                    ?.lines()
                    ?.firstOrNull { it.isNotBlank() && !it.contains("AI") }
                    ?.trim()

                withContext(Dispatchers.Main) {
                    uiStateShowAiLoading = false
                    uiStateShowAiCamera = false

                    if (responseText.isNullOrEmpty() || responseText == "0") {
                        val fallback = step.description
                        uiStateNavInstruction = fallback
                        speakTTS(fallback)
                        Toast.makeText(this@TmapsActivity, "AI 응답이 지연되어 기본 안내로 대체합니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        uiStateNavInstruction = responseText
                        speakTTS(responseText)
                    }
                }
            } catch (e: Exception) {
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

            if (!uiStateIsNavigating && !uiStateShowAiCamera) {
                Surface(
                    color = AivyColors.Surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 160.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AivySpace.Md, vertical = AivySpace.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = AivyColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(AivySpace.Xs))
                        Text("현재 위치", style = MaterialTheme.typography.bodySmall, color = AivyColors.Accent)
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = AivyColors.Surface,
                    shape = RoundedCornerShape(topStart = AivyRadius.Xl, topEnd = AivyRadius.Xl),
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AivySpace.Page, vertical = AivySpace.Lg),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(38.dp)
                                .height(4.dp)
                                .background(AivyColors.Border, RoundedCornerShape(2.dp)),
                        )
                        Spacer(modifier = Modifier.height(AivySpace.Md))

                        if (!uiStateIsRouteReady) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("어디로 갈까요?", style = MaterialTheme.typography.titleLarge, color = AivyColors.Primary)
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.Close, contentDescription = "닫기", tint = AivyColors.Text3)
                                }
                            }
                            Spacer(modifier = Modifier.height(AivySpace.Sm))
                            OutlinedTextField(
                                value = uiStateSearchQuery,
                                onValueChange = { uiStateSearchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("목적지를 검색하세요", color = AivyColors.Text4) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AivyColors.Text3) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            if (uiStateSearchQuery.isNotBlank()) searchPOI(uiStateSearchQuery)
                                        }
                                    ) {
                                        Icon(Icons.Outlined.Route, contentDescription = "검색", tint = AivyColors.Primary)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AivyColors.Accent,
                                    unfocusedBorderColor = AivyColors.Border,
                                    focusedContainerColor = AivyColors.Surface,
                                    unfocusedContainerColor = AivyColors.Surface,
                                ),
                                shape = RoundedCornerShape(AivyRadius.Md),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        if (uiStateSearchQuery.isNotBlank()) searchPOI(uiStateSearchQuery)
                                    }
                                ),
                                singleLine = true,
                            )
                            Spacer(modifier = Modifier.height(AivySpace.Md))
                            Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm)) {
                                listOf("근처 카페", "약국", "지하철역").forEach { label ->
                                    AssistChip(
                                        onClick = { uiStateSearchQuery = label },
                                        label = { Text(label) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = AivyColors.BackgroundAlt, labelColor = AivyColors.Text2),
                                        border = null,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(AivySpace.Sm))
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (uiStateSearchQuery.isNotBlank()) searchPOI(uiStateSearchQuery)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                enabled = uiStateSearchQuery.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Primary),
                                shape = RoundedCornerShape(AivyRadius.Lg),
                            ) {
                                Text("경로 검색", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(modifier = Modifier.height(AivySpace.Sm))
                            Text(
                                text = "또는 AIVY 기기에 목적지를 말하세요",
                                style = MaterialTheme.typography.bodySmall,
                                color = AivyColors.Text4,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = AivyColors.AccentLight, shape = RoundedCornerShape(AivyRadius.Md), modifier = Modifier.size(44.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Map, contentDescription = null, tint = AivyColors.Accent)
                                    }
                                }
                                Spacer(modifier = Modifier.width(AivySpace.Md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("최적 도보 경로", style = MaterialTheme.typography.bodySmall, color = AivyColors.Accent)
                                    Text(uiStateRouteSummary, style = MaterialTheme.typography.titleLarge, color = AivyColors.Text1)
                                }
                            }
                            Spacer(modifier = Modifier.height(AivySpace.Md))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AivyColors.BackgroundAlt, RoundedCornerShape(AivyRadius.Md))
                                    .padding(AivySpace.Md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (uiStateWaypointName.isEmpty()) "경유지 없음" else "경유지: $uiStateWaypointName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AivyColors.Text2,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    if (uiStateWaypointName.isEmpty()) uiStateShowWaypointInputDialog = true else clearWaypoint()
                                }) {
                                    Icon(Icons.Outlined.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(AivySpace.Xs))
                                    Text(if (uiStateWaypointName.isEmpty()) "추가" else "삭제")
                                }
                            }
                            Spacer(modifier = Modifier.height(AivySpace.Sm))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AivyColors.WarningLight, RoundedCornerShape(AivyRadius.Md))
                                    .padding(horizontal = AivySpace.Md, vertical = AivySpace.Sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("가상 주행 모드", style = MaterialTheme.typography.bodyMedium, color = AivyColors.Warning, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = uiStateIsMockMode,
                                    onCheckedChange = { uiStateIsMockMode = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = AivyColors.Accent, checkedTrackColor = AivyColors.AccentLight),
                                )
                            }
                            Spacer(modifier = Modifier.height(AivySpace.Md))
                            Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { cancelRoutePreview() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp),
                                    shape = RoundedCornerShape(AivyRadius.Lg),
                                ) {
                                    Text("취소", color = AivyColors.Text2)
                                }
                                Button(
                                    onClick = { startNavigation() },
                                    modifier = Modifier
                                        .weight(2f)
                                        .height(54.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Primary),
                                    shape = RoundedCornerShape(AivyRadius.Lg),
                                ) {
                                    Text("안내 시작", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }

            if (uiStateIsNavigating && !uiStateShowAiCamera) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AivySpace.Page, vertical = AivySpace.Lg)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(AivyRadius.Lg),
                    color = AivyColors.Primary,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .padding(AivySpace.Md)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(AivyRadius.Md),
                            modifier = Modifier.size(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Route, contentDescription = null, tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(AivySpace.Md))
                        Text(
                            text = uiStateNavInstruction,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f),
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
                        containerColor = AivyColors.Warning,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "AI 랜드마크 안내")
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AivySpace.Page)
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(AivyRadius.Xl),
                    color = AivyColors.Surface,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .padding(AivySpace.Md)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("남은 거리", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
                            Text(uiStateNavRemainDistance, style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1)
                        }
                        Button(
                            onClick = { showStopDialog() },
                            colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Danger),
                            shape = RoundedCornerShape(AivyRadius.Md)
                        ) { Text("안내 종료", fontWeight = FontWeight.Bold) }
                    }
                }
            }

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
                    contentColor = AivyColors.Accent,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "내 위치 찾기", modifier = Modifier.size(28.dp))
                }
            }

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

            if (uiStateShowSearchDialog) {
                AlertDialog(
                    onDismissRequest = { uiStateShowSearchDialog = false },
                    title = { Text("어느 곳으로 갈까요?", style = MaterialTheme.typography.titleMedium, color = AivyColors.Primary) },
                    text = {
                        LazyColumn {
                            items(uiStateSearchResults) { item ->
                                val rowInteractionSource = remember { MutableInteractionSource() }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = rowInteractionSource,
                                            indication = null,
                                        ) {
                                            uiStateShowSearchDialog = false
                                            if (isSearchingForWaypoint) {
                                                setWaypointMarker(item)
                                            } else {
                                                setDestinationMarker(item)
                                            }
                                        }
                                        .padding(vertical = 14.dp)
                                ) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(item.getFullAddress(), style = MaterialTheme.typography.bodySmall, color = AivyColors.Text3)
                                }
                                Divider(color = AivyColors.Border, thickness = 1.dp)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { uiStateShowSearchDialog = false }) {
                            Text("취소", color = AivyColors.Accent, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = AivyColors.Surface,
                    shape = RoundedCornerShape(AivyRadius.Lg),
                )
            }

            if (uiStateShowWaypointInputDialog) {
                AlertDialog(
                    onDismissRequest = { uiStateShowWaypointInputDialog = false },
                    title = { Text("경유지 추가", style = MaterialTheme.typography.titleMedium, color = AivyColors.Primary) },
                    text = {
                        OutlinedTextField(
                            value = uiStateWaypointQuery,
                            onValueChange = { uiStateWaypointQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("경유지를 입력하세요") },
                            shape = RoundedCornerShape(AivyRadius.Md),
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
                            colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Primary),
                            shape = RoundedCornerShape(AivyRadius.Md),
                        ) { Text("검색", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { uiStateShowWaypointInputDialog = false }) { Text("취소", color = AivyColors.Text3) }
                    },
                    containerColor = AivyColors.Surface,
                    shape = RoundedCornerShape(AivyRadius.Lg),
                )
            }
        }
    }
}
