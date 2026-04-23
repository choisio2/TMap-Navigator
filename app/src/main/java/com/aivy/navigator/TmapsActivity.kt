package com.aivy.navigator

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aivy.navigator.data.model.PoiItem
import com.aivy.navigator.data.network.RetrofitClient
import com.skt.Tmap.TMapMarkerItem
import com.skt.Tmap.TMapPoint
import com.skt.Tmap.TMapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.core.app.ActivityCompat
import com.aivy.navigator.data.model.RouteStep
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.skt.Tmap.TMapPolyLine
import com.google.android.gms.location.Priority
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import java.util.Locale
import android.app.AlertDialog
import com.aivy.navigator.databinding.ActivityTmapsBinding
import com.aivy.navigator.data.model.RouteFeature
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.ArrayAdapter
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Typeface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import android.graphics.Matrix
import android.graphics.BitmapFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class TmapsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // ============================================================
    // 변수 선언
    // ============================================================

    private lateinit var binding: ActivityTmapsBinding
    private lateinit var tMapView: TMapView

    // -- 위치 --
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: TMapPoint? = null

    // -- 목적지 / 경유지 --
    private var destinationPoint: TMapPoint? = null
    private var destinationName: String = ""
    private var waypointPoint: TMapPoint? = null
    private var waypointName: String = ""

    // -- 경로 데이터 --
    private val allRoutePoints = mutableListOf<TMapPoint>()
    private val routeSteps = mutableListOf<RouteStep>()

    // -- 내비게이션 상태 --
    private var isNavigating = false
    private var isMockMode = true
    private var mockNavJob: Job? = null

    //  재탐색 중복 호출 방지 플래그
    private var isRerouting = false

    // -- TTS --
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    // -- 안내 제어 --
    private var lastTTSTime = 0L
    private val TTS_COOLDOWN = 2000L
    private val announcedStepIndices = mutableSetOf<Int>()
    private val MOCK_SPEED_MS = 1000L

    private val upcomingSteps = mutableListOf<RouteStep>()

    // -- Camera --
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var currentStepForAI: RouteStep? = null
    private var autoCaptureJob: Job? = null

    companion object { private const val TAG = "TMAP_debug" }

    // -- 방향(방위각) 센서 --
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var currentAzimuth = 0f // 현재 내가 바라보는 방위각 (0~360)
    private var lastMarkerAzimuth = 0f
    private var myLocationBaseBitmap: Bitmap? = null
    // 출발 시 방향 안내를 한 번만 하기 위한 플래그
    private var isInitialDirectionAnnounced = false

    // 음성 출력 빈도 증가
    private var lastAnnouncedLoc: TMapPoint? = null
    // ============================================================
    // 생명주기
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTmapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this, this)

        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancelRoutePreview.setOnClickListener { cancelRoutePreview() }

        binding.btnAddWaypoint.setOnClickListener { onAddWaypointClicked() }
        binding.btnRemoveWaypoint.setOnClickListener { removeWaypointAndReroute() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnManualCapture.setOnClickListener { takePhotoAndSendToGemini() }
        binding.btnTestGallery.setOnClickListener { openGalleryForAiTest() }
        binding.fabAiGuide.setOnClickListener { startAiCameraFlow() }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        myLocationBaseBitmap = getBitmapFromVectorDrawable(R.drawable.ic_my_location_dot_sensor)

        setupTMapView()
        setupSearch()
        setupRouteButton()
        setupLocationCallback()
    }

    override fun onDestroy() {
        if (isTtsReady) { tts.stop(); tts.shutdown() }
        if (isNavigating && this::locationCallback.isInitialized) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }
        sensorManager.unregisterListener(sensorEventListener)
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN; tts.setSpeechRate(0.85f);tts.setPitch(1.0f); isTtsReady = true
        }
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) gravity.indices.forEach { gravity[it] = event.values[it] }
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) geomagnetic.indices.forEach { geomagnetic[it] = event.values[it] }

            val R = FloatArray(9)
            val I = FloatArray(9)
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)
                currentAzimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360) % 360

                // 폰을 5도 이상 돌렸을 때만 화살표 화면 갱신
                if (Math.abs(currentAzimuth - lastMarkerAzimuth) > 5f) {
                    lastMarkerAzimuth = currentAzimuth
                    updateMyLocationMarker()
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // 두 좌표 간의 방위각(Bearing)을 구하는 유틸 함수
    private fun getBearingToNextPoint(p1: TMapPoint, p2: TMapPoint): Float {
        val l1 = android.location.Location("").apply { latitude = p1.latitude; longitude = p1.longitude }
        val l2 = android.location.Location("").apply { latitude = p2.latitude; longitude = p2.longitude }
        return (l1.bearingTo(l2) + 360) % 360
    }

    // 출발 초기 방향 안내 함수
    private fun checkInitialDirection() {
        if (allRoutePoints.size < 2 || isInitialDirectionAnnounced) return

        val targetBearing = getBearingToNextPoint(allRoutePoints[0], allRoutePoints[1])

        // 내 방향과 가야할 방향의 차이 계산 (-180 ~ 180도)
        var diff = targetBearing - currentAzimuth
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360

        val directionMessage = when {
            abs(diff) <= 30 -> "올바른 방향입니다. 그대로 직진하세요."
            diff > 30 && diff <= 120 -> "오른쪽 방향으로 돌아서 출발하세요."
            diff < -30 && diff >= -120 -> "왼쪽 방향으로 돌아서 출발하세요."
            else -> "현재 반대 방향을 보고 있습니다. 뒤로 돌아 출발하세요."
        }

        binding.tvNavInstruction.text = directionMessage
        speakTTS(directionMessage)
        isInitialDirectionAnnounced = true // 한 번만 안내하도록 플래그 잠금
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(this, "위치 권한이 거부되어 현재 위치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getBitmapFromVectorDrawable(drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, drawableId)!!
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // ============================================================
    // TMap 지도
    // ============================================================

    private fun setupTMapView() {
        tMapView = TMapView(this)
        tMapView.setSKTMapApiKey(BuildConfig.TMAP_APP_KEY)
        tMapView.setOnApiKeyListener(object : TMapView.OnApiKeyListenerCallback {
            override fun SKTMapApikeySucceed() { runOnUiThread { fetchCurrentLocation() } }
            override fun SKTMapApikeyFailed(msg: String?) { Log.e(TAG, "인증실패: $msg") }
        })
        binding.tmapLayout.addView(tMapView)
    }

    // ============================================================
    // 현재 위치
    // ============================================================

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLocation = TMapPoint(loc.latitude, loc.longitude)
                    updateMyLocationMarker()
                    tMapView.setCenterPoint(loc.longitude, loc.latitude)
                    tMapView.zoomLevel = 15
                }
            }
    }

    private fun updateMyLocationMarker() {
        currentLocation?.let {
            tMapView.removeMarkerItem("myLocation")
            val marker = TMapMarkerItem().apply {
                tMapPoint = it
                name = "내 위치"

                val base = myLocationBaseBitmap ?: return@apply

                // Matrix를 이용한 비트맵 회전
                val matrix = Matrix().apply { postRotate(currentAzimuth) }
                icon = Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)

                // 마커의 중심점을 이미지의 정중앙으로 설정
                setPosition(0.5f, 0.5f)
            }
            tMapView.addMarkerItem("myLocation", marker)
            tMapView.postInvalidate()
        }
    }

    // ============================================================
    // 목적지 검색
    // ============================================================

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            val q = binding.etSearch.text.toString().trim()
            if (q.isNotEmpty()) { hideKeyboard(); searchPOI(q) { setDestinationMarker(it) } }
            else Toast.makeText(this, "목적지를 입력하세요", Toast.LENGTH_SHORT).show()
        }
        binding.etSearch.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { binding.btnSearch.performClick(); true } else false
        }
    }

    private fun searchPOI(query: String, onSelected: (PoiItem) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.tmapService.searchPOI(BuildConfig.TMAP_APP_KEY, query)
                withContext(Dispatchers.Main) {
                    val list = resp.body()?.searchPoiInfo?.pois?.poiList
                    if (resp.isSuccessful && !list.isNullOrEmpty()) {
                        val adapter = object : ArrayAdapter<PoiItem>(this@TmapsActivity, android.R.layout.simple_list_item_2, android.R.id.text1, list) {
                            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                                val view = super.getView(position, convertView, parent)
                                val text1 = view.findViewById<TextView>(android.R.id.text1)
                                val text2 = view.findViewById<TextView>(android.R.id.text2)
                                val item = getItem(position)

                                text1.text = item?.name
                                text1.textSize = 16f
                                text1.setTypeface(null, Typeface.BOLD)
                                text1.setTextColor(Color.parseColor("#212121"))

                                text2.text = item?.getFullAddress()
                                text2.textSize = 13f
                                text2.setTextColor(Color.parseColor("#757575"))

                                view.setPadding(32, 24, 32, 24)
                                return view
                            }
                        }

                        MaterialAlertDialogBuilder(this@TmapsActivity)
                            .setTitle("어느 곳으로 갈까요?")
                            .setAdapter(adapter) { _, i -> onSelected(list[i]) }
                            .setNegativeButton("취소", null)
                            .show()
                    } else {
                        Toast.makeText(this@TmapsActivity, "검색 결과가 없습니다", Toast.LENGTH_SHORT).show()
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
        destinationName = item.name

        tMapView.removeAllTMapPolyLine()
        binding.cardRouteInfo.visibility = View.GONE
        tMapView.removeMarkerItem("destination")
        tMapView.removeMarkerItem("waypoint")

        val marker = TMapMarkerItem().apply {
            tMapPoint = destinationPoint
            name = item.name
            icon = getBitmapFromVectorDrawable(R.drawable.ic_location_on)
            setPosition(0.5f, 1.0f)
        }

        tMapView.addMarkerItem("destination", marker)
        tMapView.setCenterPoint(lon, lat)
        tMapView.zoomLevel = 15
        tMapView.postInvalidate()

        binding.etSearch.setText(item.name)
        binding.fabRoute.visibility = View.VISIBLE
        hideKeyboard()
    }

    // ============================================================
    // 경로 탐색 & 그리기
    // ============================================================

    private fun setupRouteButton() {
        binding.fabRoute.setOnClickListener {
            if (currentLocation == null || destinationPoint == null) {
                Toast.makeText(this, "출발지 또는 목적지를 알 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
        binding.btnStartNavigation.setOnClickListener { startNavigation() }
        binding.btnStopNav.setOnClickListener { showStopDialog() }
    }

    private fun cancelRoutePreview() {
        tMapView.removeAllTMapPolyLine()
        tMapView.removeMarkerItem("destination")
        tMapView.removeMarkerItem("waypoint")

        waypointPoint = null
        waypointName = ""
        binding.tvWaypointInfo.text = "경유지 없음"
        binding.btnAddWaypoint.visibility = View.VISIBLE
        binding.btnRemoveWaypoint.visibility = View.GONE

        binding.cardRouteInfo.visibility = View.GONE
        binding.searchCard.visibility = View.VISIBLE
        binding.fabRoute.visibility = View.GONE
        currentLocation?.let { tMapView.setCenterPoint(it.longitude, it.latitude); tMapView.zoomLevel = 15 }
        updateMyLocationMarker()
    }

    private fun findPedestrianRoute(start: TMapPoint, end: TMapPoint) {
        // 이미 내비게이션 중(재탐색)이면 프로그래스바를 띄우지 않음
        if (!isNavigating) {
            binding.progressBar.visibility = View.VISIBLE
            binding.fabRoute.visibility = View.GONE
        }

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
                    binding.progressBar.visibility = View.GONE
                    if (resp.isSuccessful && resp.body() != null) {
                        drawRouteAndSaveSteps(resp.body()!!.features)
                    } else {
                        Toast.makeText(this@TmapsActivity, "경로 탐색 실패", Toast.LENGTH_SHORT).show()
                        if (!isNavigating) binding.fabRoute.visibility = View.VISIBLE
                        isRerouting = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@TmapsActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                    if (!isNavigating) binding.fabRoute.visibility = View.VISIBLE
                    isRerouting = false
                }
            }
        }
    }

    private fun drawRouteAndSaveSteps(features: List<RouteFeature>) {
        routeSteps.clear(); allRoutePoints.clear()
        var totalTime = 0; var totalDistance = 0

        features.forEach { f ->
            val geo = f.geometry; val props = f.properties
            if (props.totalTime != null) totalTime = props.totalTime
            if (props.totalDistance != null) totalDistance = props.totalDistance

            when (geo.type) {
                "LineString" -> {
                    val arr = geo.coordinates.asJsonArray
                    for (i in 0 until arr.size()) {
                        val c = arr[i].asJsonArray
                        val pt = TMapPoint(c[1].asDouble, c[0].asDouble)
                        allRoutePoints.add(pt)
                    }
                }
                "Point" -> {
                    val c = geo.coordinates.asJsonArray
                    if (props.description != null && props.turnType != null && props.pointIndex != null) {
                        routeSteps.add(RouteStep(
                            props.pointIndex, TMapPoint(c[1].asDouble, c[0].asDouble),
                            props.description, props.turnType, props.totalDistance ?: 0
                        ))
                    }
                }
            }
        }

        // 폴리라인 업데이트 (기존 선 지우고 새로 그리기)
        updatePolyline()

        if (isNavigating) {
            // 재탐색 성공 시, 내비게이션 데이터 리셋 및 안내 재개
            upcomingSteps.clear()
            upcomingSteps.addAll(routeSteps)
            upcomingSteps.removeAll { s ->
                s.description.contains("이동") && !s.description.contains("회전")
                        && !s.description.contains("좌") && !s.description.contains("우")
                        && !s.description.contains("m")
            }
            announcedStepIndices.clear()

            binding.tvNavInstruction.text = "새로운 경로로 안내를 계속합니다."
            speakTTS("새로운 경로를 찾았습니다. 안내를 계속합니다.")
            isRerouting = false // 재탐색 플래그 해제

            // 가상 주행 모드였다면 모의 주행 재시작
            if (isMockMode) startMockNavigation()

        } else {
            // 최초 탐색 시 (미리보기 화면)
            binding.tvRouteSummary.text = String.format("도보 %d분 (%.1fkm)", totalTime / 60, totalDistance / 1000.0)
            binding.cardRouteInfo.visibility = View.VISIBLE
            binding.searchCard.visibility = View.GONE
        }
    }

    // 궤적 갱신 공통 함수 (지나간 길 지우기 + 재탐색 그리기 겸용)
    private fun updatePolyline() {
        tMapView.removeTMapPolyLine("pedestrian_route")
        if (allRoutePoints.isEmpty()) return

        val polyLine = TMapPolyLine().apply {
            lineColor = Color.parseColor("#215CF3")
            lineWidth = 11f
            outLineColor = Color.parseColor("#1976D2")
            outLineWidth = 2f
        }

        allRoutePoints.forEach { polyLine.addLinePoint(it) }
        tMapView.addTMapPolyLine("pedestrian_route", polyLine)
        tMapView.postInvalidate()
    }

    // ============================================================
    // 내비게이션 시작 / 종료
    // ============================================================

    private fun startNavigation() {
        isMockMode = binding.switchMockMode.isChecked
        isNavigating = true
        isRerouting = false

        isInitialDirectionAnnounced = false
        lastAnnouncedLoc = currentLocation

        upcomingSteps.clear()
        upcomingSteps.addAll(routeSteps)
        upcomingSteps.removeAll { s ->
            s.description.contains("이동") && !s.description.contains("회전")
                    && !s.description.contains("좌") && !s.description.contains("우")
                    && !s.description.contains("m")
        }

        binding.searchCard.visibility = View.GONE
        binding.cardRouteInfo.visibility = View.GONE
        binding.navTopCard.visibility = View.VISIBLE
        binding.navBottomCard.visibility = View.VISIBLE

        val modeText = if (isMockMode) "가상 주행으로" else "실제 주행으로"
        speakTTS("$modeText 경로 안내를 시작합니다.")

        if (isMockMode) startMockNavigation() else startRealNavigation()

        accelerometer?.let { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI) }
        isInitialDirectionAnnounced = false // 플래그 초기화

        // 실제 주행이든 가상 주행이든 시작 시 첫 방향 브리핑
        binding.tmapLayout.postDelayed({ checkInitialDirection() }, 1000)

        if (isMockMode) startMockNavigation() else startRealNavigation()
    }

    private fun showStopDialog() {
        AlertDialog.Builder(this)
            .setTitle("안내 종료").setMessage("경로 안내를 종료하시겠습니까?")
            .setPositiveButton("예") { d, _ -> stopNavigation(); d.dismiss() }
            .setNegativeButton("아니오") { d, _ -> d.dismiss() }
            .show()
    }

    private fun stopNavigation() {
        sensorManager.unregisterListener(sensorEventListener) // 센서 해제

        isNavigating = false
        isRerouting = false
        mockNavJob?.cancel()

        if (this::locationCallback.isInitialized) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }

        speakTTS("경로 안내를 종료합니다.")

        tMapView.removeAllTMapPolyLine()
        tMapView.removeMarkerItem("destination")
        tMapView.removeMarkerItem("waypoint")

        allRoutePoints.clear()
        routeSteps.clear()
        announcedStepIndices.clear()
        waypointPoint = null
        waypointName = ""

        binding.tvWaypointInfo.text = "경유지 없음"
        binding.btnAddWaypoint.visibility = View.VISIBLE
        binding.btnRemoveWaypoint.visibility = View.GONE
        binding.etSearch.text.clear()

        binding.navTopCard.visibility = View.GONE
        binding.navBottomCard.visibility = View.GONE
        binding.searchCard.visibility = View.VISIBLE
        binding.fabRoute.visibility = View.GONE
        currentLocation?.let { tMapView.setCenterPoint(it.longitude, it.latitude); tMapView.zoomLevel = 15 }
        updateMyLocationMarker()
    }

    // ============================================================
    // 가상 주행
    // ============================================================

    private fun startMockNavigation() {
        mockNavJob?.cancel()
        mockNavJob = lifecycleScope.launch(Dispatchers.Main) {
            // point 리스트 복사본 사용 (도중에 잘려나가도 에러 안 나게)
            val points = allRoutePoints.toList()
            for (pt in points) {
                if (!isNavigating) break
                currentLocation = pt
                updateMyLocationMarker()
                tMapView.setCenterPoint(pt.longitude, pt.latitude)

                checkNavigationProgress(pt)
                delay(MOCK_SPEED_MS)
            }
        }
    }

    // ============================================================
    // 실제 GPS 주행
    // ============================================================

    private fun startRealNavigation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(2000).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED)
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isNavigating) return
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
    // 경유지
    // ============================================================

    private fun onAddWaypointClicked() {
        val et = EditText(this).apply {
            hint = "경유지 검색"
            setPadding(60, 40, 60, 40)
        }
        AlertDialog.Builder(this)
            .setTitle("경유지 추가")
            .setView(et)
            .setPositiveButton("검색") { _, _ ->
                val q = et.text.toString().trim()
                if (q.isNotEmpty()) searchPOI(q) { addWaypointAndReroute(it) }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addWaypointAndReroute(item: PoiItem) {
        val lat = item.noorLat.toDoubleOrNull() ?: return
        val lon = item.noorLon.toDoubleOrNull() ?: return
        waypointPoint = TMapPoint(lat, lon)
        waypointName = item.name

        val marker = TMapMarkerItem().apply {
            tMapPoint = waypointPoint
            name = "경유지: ${item.name}"
            icon = getBitmapFromVectorDrawable(R.drawable.ic_location_on)
            setPosition(0.5f, 1.0f)
        }
        tMapView.addMarkerItem("waypoint", marker)

        binding.tvWaypointInfo.text = "경유지: ${item.name}"
        binding.btnAddWaypoint.visibility = View.GONE
        binding.btnRemoveWaypoint.visibility = View.VISIBLE

        speakTTS("${item.name}을 경유지로 추가했습니다.")

        if (currentLocation != null && destinationPoint != null) {
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
    }

    private fun removeWaypointAndReroute() {
        waypointPoint = null
        waypointName = ""
        tMapView.removeMarkerItem("waypoint")

        binding.tvWaypointInfo.text = "경유지 없음"
        binding.btnAddWaypoint.visibility = View.VISIBLE
        binding.btnRemoveWaypoint.visibility = View.GONE

        if (currentLocation != null && destinationPoint != null) {
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
    }

    // ============================================================
    // 랜드마크
    // ============================================================

    private suspend fun searchNearbyPOIForTurn(point: TMapPoint): String = withContext(Dispatchers.IO) {
        try {
            val resp = RetrofitClient.tmapService.searchAroundPOI(
                appKey = BuildConfig.TMAP_APP_KEY,
                version = 1,
                centerLat = point.latitude.toString(),
                centerLon = point.longitude.toString(),
                radius = "1",
                count = 5,
                categories = "편의점;커피전문점;은행;패스트푸드;지하철역"
            )

            val poiList = resp.body()?.searchPoiInfo?.pois?.poiList
            if (poiList.isNullOrEmpty()) return@withContext "특징적인 랜드마크 없음"

            poiList.filter { !it.name.contains("소화전") && !it.name.contains("공중전화") }
                .take(3)
                .joinToString(", ") { it.name }
        } catch (e: Exception) { "랜드마크 검색 실패" }
    }

    // ============================================================
    // TTS
    // ============================================================

    private fun speakTTS(text: String) {
        if (isTtsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    private fun speakTTSWithCooldown(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastTTSTime >= TTS_COOLDOWN) { lastTTSTime = now; speakTTS(text) }
    }

    // ============================================================
    // 유틸
    // ============================================================

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun calculateDistance(p1: TMapPoint, p2: TMapPoint): Float {
        val a = android.location.Location("").apply { latitude = p1.latitude; longitude = p1.longitude }
        val b = android.location.Location("").apply { latitude = p2.latitude; longitude = p2.longitude }
        return a.distanceTo(b)
    }

    // ============================================================
    // AI 카메라 & Gemini 연동 로직
    // ============================================================

    private fun startAiCameraFlow() {
        binding.fabAiGuide.visibility = View.GONE
        binding.cvAiCameraLayout.visibility = View.VISIBLE
        binding.ivSelectedImage.visibility = View.GONE
        binding.viewFinder.visibility = View.VISIBLE

        binding.tvAiCameraStatus.text = "전방의 랜드마크를 비춘 후 촬영 버튼을 눌러주세요."
        startCameraX()
        speakTTS("전방을 비춘 후 촬영 버튼을 눌러주세요.")
    }

    private fun startCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) { Log.e(TAG, "카메라 바인딩 실패", exc) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhotoAndSendToGemini() {
        val step = currentStepForAI ?: return
        val capture = imageCapture ?: return

        binding.tvAiCameraStatus.text = "사진 촬영 완료! AI가 분석 중입니다..."
        binding.progressBarAi.visibility = View.VISIBLE

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    if (bitmap != null) processGeminiAnalysis(bitmap, step)
                }
                override fun onError(exc: ImageCaptureException) {
                    binding.cvAiCameraLayout.visibility = View.GONE
                    Toast.makeText(this@TmapsActivity, "촬영 실패", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun processGeminiAnalysis(bitmap: Bitmap, step: RouteStep) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val responseText = GeminiHelper.analyzeImage(bitmap, step, currentAzimuth)?.trim()

                withContext(Dispatchers.Main) {
                    binding.progressBarAi.visibility = View.GONE
                    binding.cvAiCameraLayout.visibility = View.GONE

                    if (responseText == null || responseText == "0" || responseText.isEmpty()) {
                        val fallbackMessage = step.description
                        binding.tvNavInstruction.text = fallbackMessage
                        speakTTS(fallbackMessage)
                        Toast.makeText(this@TmapsActivity, "특징을 찾기 어려워 기본 안내를 제공합니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        binding.tvNavInstruction.text = "$responseText"
                        speakTTS(responseText)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarAi.visibility = View.GONE
                    binding.cvAiCameraLayout.visibility = View.GONE
                    val fallbackMessage = step.description
                    binding.tvNavInstruction.text = fallbackMessage
                    speakTTS(fallbackMessage)
                    Toast.makeText(this@TmapsActivity, "AI 연결 오류로 기본 안내를 제공합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val planeProxy = image.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val matrix = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
            postScale(640f / original.width, 480f / original.height)
        }
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    }

    private val galleryForAiLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                autoCaptureJob?.cancel()
                val inputStream = contentResolver.openInputStream(it)
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                val resized = Bitmap.createScaledBitmap(original, 640, 480, true)

                binding.viewFinder.visibility = View.GONE
                binding.ivSelectedImage.visibility = View.VISIBLE
                binding.ivSelectedImage.setImageBitmap(original)
                binding.tvAiCameraStatus.text = "갤러리 사진 로드 완료! AI 분석 중..."
                binding.progressBarAi.visibility = View.VISIBLE

                currentStepForAI?.let { step -> processGeminiAnalysis(resized, step) }
            } catch (e: Exception) {}
        }
    }

    private fun openGalleryForAiTest() { galleryForAiLauncher.launch("image/*") }

    // ============================================================
    // 실시간 내비게이션 통합 상태 체크 - 지나간 길 지우기 & 이탈 감지
    // ============================================================

    private fun checkNavigationProgress(currentLoc: TMapPoint) {
        if (isRerouting || allRoutePoints.isEmpty()) return

        // 지나간 경로 지우기 & 이탈 판단을 위해 가장 가까운 점 찾기
        var minDist = Float.MAX_VALUE
        var closestIdx = 0
        for (i in allRoutePoints.indices) {
            val dist = calculateDistance(currentLoc, allRoutePoints[i])
            if (dist < minDist) {
                minDist = dist
                closestIdx = i
            }
        }

        // 경로 이탈 감지 (30m 이상 벗어났고, 가상주행이 아닐 때)
        if (minDist > 30f && !isMockMode) {
            isRerouting = true
            speakTTS("경로를 벗어났습니다. 경로를 재탐색합니다.")
            binding.tvNavInstruction.text = "경로 재탐색 중..."

            if (destinationPoint != null) {
                findPedestrianRoute(currentLoc, destinationPoint!!)
            }
            return
        }

        // 지나간 경로 지우기 (뒤쪽 좌표 삭제 후 폴리라인 다시 그리기)
        if (closestIdx > 0) {
            allRoutePoints.subList(0, closestIdx).clear()
            updatePolyline()
        }

        // 이전 위치와의 거리를 계산해 총 이동 거리에 합산
        val distSinceLastAnnouncement = calculateDistance(lastAnnouncedLoc ?: currentLoc, currentLoc)
        if (distSinceLastAnnouncement >= 100f) {
            val nextDist = upcomingSteps.firstOrNull()?.let { calculateDistance(currentLoc, it.coordinate) } ?: 999f
            if (nextDist > 50f) { // 곧 회전할 지점이 아닐 때만
                speakTTSWithCooldown("경로를 따라 잘 이동하고 있습니다. 계속 직진하세요.")
                lastAnnouncedLoc = currentLoc // 안내를 했으므로 위치 갱신
            }
        }

        // 목적지 잔여 거리 계산 및 도착 체크
        destinationPoint?.let { dest ->
            val dist = calculateDistance(currentLoc, dest)
            binding.tvNavRemainInfo.text = String.format("목적지까지: %.0fm", dist)

            if (dist <= 15f) {
                speakTTSWithCooldown("목적지 부근에 도착했습니다. 안내를 종료합니다.")
                stopNavigation()
                return
            }
        }

        // 분기점 AI 안내 로직
        if (upcomingSteps.isNotEmpty()) {
            val next = upcomingSteps.first()
            val distToTurn = calculateDistance(currentLoc, next.coordinate)

            // ... (AI 버튼 등장 로직 생략) ...

            if (distToTurn <= 30f && !announcedStepIndices.contains(next.pointIndex)) {
                announcedStepIndices.add(next.pointIndex)
                val originalMessage = next.description

                lifecycleScope.launch {
                    val landmarks = searchNearbyPOIForTurn(next.coordinate)
                    val enhancedMessage = if (landmarks == "특징적인 랜드마크 없음" || landmarks == "랜드마크 검색 실패") {
                        "G: $originalMessage"
                    } else {
                        GeminiHelper.enhanceNavigationText(originalMessage, landmarks, currentAzimuth)
                    }

                    binding.tvNavInstruction.text = enhancedMessage
                    speakTTSWithCooldown(enhancedMessage)
                    lastAnnouncedLoc = currentLoc // ⭐ 추가: 분기점 안내를 했으므로 기준 위치 갱신
                }

                upcomingSteps.removeAt(0)
                binding.fabAiGuide.visibility = View.GONE
            }
        }
    }
}