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
import com.skt.Tmap.TMapCircle
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

class TmapsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // ============================================================
    //  1. 변수 선언
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

    // ============================================================
    //  2. 생명주기
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

        // 카메라 사진 분석
        binding.btnManualCapture.setOnClickListener { takePhotoAndSendToGemini() }
        binding.btnTestGallery.setOnClickListener { openGalleryForAiTest() }
        binding.fabAiGuide.setOnClickListener { startAiCameraFlow() }

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
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN; tts.setSpeechRate(0.85f);tts.setPitch(1.0f); isTtsReady = true
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation() // 위치 가져오기 
        } else {
            Toast.makeText(this, "위치 권한이 거부되어 현재 위치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 내 위치 마커 수정
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
    //  3. TMap 지도
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
    //  4. 현재 위치
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
                // 커스텀 동그라미 마커 적용
                icon = getBitmapFromVectorDrawable(R.drawable.ic_my_location_dot)
                setPosition(0.5f, 0.5f)
            }

            tMapView.addMarkerItem("myLocation", marker)
            tMapView.postInvalidate()
        }
    }
    

    // ============================================================
    //  5. 목적지 검색
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
                        // 목적지 리스트 UI 꾸미기
                        val adapter = object : ArrayAdapter<PoiItem>(this@TmapsActivity, android.R.layout.simple_list_item_2, android.R.id.text1, list) {
                            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                                val view = super.getView(position, convertView, parent)
                                val text1 = view.findViewById<TextView>(android.R.id.text1)
                                val text2 = view.findViewById<TextView>(android.R.id.text2)
                                val item = getItem(position)

                                // 장소 이름 (크게, 굵게)
                                text1.text = item?.name
                                text1.textSize = 16f
                                text1.setTypeface(null, Typeface.BOLD)
                                text1.setTextColor(Color.parseColor("#212121"))

                                // 장소 주소 (작게, 회색)
                                text2.text = item?.getFullAddress()
                                text2.textSize = 13f
                                text2.setTextColor(Color.parseColor("#757575"))

                                view.setPadding(32, 24, 32, 24)
                                return view
                            }
                        }

                        // Material 스타일의 다이얼로그 띄우기
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
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TmapsActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
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
    //  6. 경로 탐색 & 그리기
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

        // 경유지 UI 초기화
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
        binding.progressBar.visibility = View.VISIBLE
        binding.fabRoute.visibility = View.GONE

        // 경유지(waypointPoint)가 설정되어 있다면 passList 문자열 생성
        val passListStr = waypointPoint?.let {
            "${it.longitude},${it.latitude}"
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.tmapService.getPedestrianRoute(
                    appKey = BuildConfig.TMAP_APP_KEY,
                    startX = start.longitude.toString(),
                    startY = start.latitude.toString(),
                    endX = end.longitude.toString(),
                    endY = end.latitude.toString(),
                    passList = passListStr // null이면 Retrofit이 전송 제외함
                )

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (resp.isSuccessful && resp.body() != null) {
                        drawRouteAndSaveSteps(resp.body()!!.features)
                    } else {
                        Toast.makeText(this@TmapsActivity, "경로 탐색 실패", Toast.LENGTH_SHORT).show()
                        binding.fabRoute.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@TmapsActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                    binding.fabRoute.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun drawRouteAndSaveSteps(features: List<RouteFeature>) {
        tMapView.removeAllTMapPolyLine()
        routeSteps.clear(); allRoutePoints.clear()

        val polyLine = TMapPolyLine().apply {
            lineColor = Color.parseColor("#215CF3")
            lineWidth = 11f
            outLineColor = Color.parseColor("#1976D2")
            outLineWidth = 2f
        }
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
                        polyLine.addLinePoint(pt); allRoutePoints.add(pt)
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

        tMapView.addTMapPolyLine("pedestrian_route", polyLine)
        tMapView.postInvalidate()
        binding.tvRouteSummary.text = String.format("도보 %d분 (%.1fkm)", totalTime / 60, totalDistance / 1000.0)
        binding.cardRouteInfo.visibility = View.VISIBLE
        binding.searchCard.visibility = View.GONE
    }


    // ============================================================
    //  7. 내비게이션 시작 / 종료
    // ============================================================

    private fun startNavigation() {
        isMockMode = binding.switchMockMode.isChecked
        isNavigating = true
        upcomingSteps.clear()
        upcomingSteps.addAll(routeSteps)
        upcomingSteps.removeAll { s ->
            s.description.contains("이동") && !s.description.contains("회전")
                    && !s.description.contains("좌") && !s.description.contains("우")
                    && !s.description.contains("m")
        }

        binding.searchCard.visibility = View.GONE

        binding.searchCard.visibility = View.GONE
        binding.cardRouteInfo.visibility = View.GONE
        binding.navTopCard.visibility = View.VISIBLE
        binding.navBottomCard.visibility = View.VISIBLE

        val modeText = if (isMockMode) "가상 주행으로" else "실제 주행으로"
        speakTTS("$modeText 경로 안내를 시작합니다.")

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
        // 가상주행 루프 탈출
        isNavigating = false

        // GPS 콜백 해제
        if (this::locationCallback.isInitialized) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }

        // TTS
        speakTTS("경로 안내를 종료합니다.")

        // 지도 정리
        tMapView.removeAllTMapPolyLine()
        tMapView.removeMarkerItem("destination")
        tMapView.removeMarkerItem("waypoint")

        // 데이터 초기화 (isWaypointMode 삭제됨)
        allRoutePoints.clear()
        routeSteps.clear()
        announcedStepIndices.clear()
        waypointPoint = null
        waypointName = ""

        // UI 복귀
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
    //  8. 가상 주행
    // ============================================================

    private fun startMockNavigation() {
        mockNavJob?.cancel()
        mockNavJob = lifecycleScope.launch(Dispatchers.Main) {
            val points = allRoutePoints.toList()
            for (pt in points) {
                if (!isNavigating) break
                currentLocation = pt
                updateMyLocationMarker()
                tMapView.setCenterPoint(pt.longitude, pt.latitude)

                // 🌟 여기서 통합 로직 호출!
                checkNavigationProgress(pt)

                delay(MOCK_SPEED_MS)
            }
        }
    }


    // ============================================================
    //  9. 실제 GPS 주행
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

                    // 🌟 실제 GPS 위치를 받아서 똑같이 통합 로직 호출!
                    checkNavigationProgress(newLoc)
                }
            }
        }
    }


    // ============================================================
    //  10. 경유지
    // ============================================================

    // 경유지 추가 버튼 클릭 시 호출
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

    //검색된 POI를 경유지로 설정하고 경로 재탐색
    private fun addWaypointAndReroute(item: PoiItem) {
        val lat = item.noorLat.toDoubleOrNull() ?: return
        val lon = item.noorLon.toDoubleOrNull() ?: return
        waypointPoint = TMapPoint(lat, lon)
        waypointName = item.name

        // 지도에 경유지 마커 표시
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

        // 현재 위치에서 목적지까지, 경유지를 포함하여 다시 탐색
        if (currentLocation != null && destinationPoint != null) {
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
    }

    // 경유지 삭제 로직
    private fun removeWaypointAndReroute() {
        waypointPoint = null
        waypointName = ""
        tMapView.removeMarkerItem("waypoint")

        // UI 원상복구
        binding.tvWaypointInfo.text = "경유지 없음"
        binding.btnAddWaypoint.visibility = View.VISIBLE
        binding.btnRemoveWaypoint.visibility = View.GONE

        if (currentLocation != null && destinationPoint != null) {
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
    }


    // ============================================================
    //  11. 랜드마크 + 자연어 안내
    // ============================================================
/*
    private suspend fun searchNearbyPOI(point: TMapPoint): String? = withContext(Dispatchers.IO) {
        try {
            val resp = RetrofitClient.tmapService.searchAroundPOI(
                appKey = BuildConfig.TMAP_APP_KEY,
                centerLat = point.latitude.toString(),
                centerLon = point.longitude.toString(),
                radius = "1",
                count = 5, // 5개 정도 가져와서 제일 이상한 건 거르기 위함
                categories = "편의점;커피전문점;패스트푸드;은행"
            )
            resp.body()?.searchPoiInfo?.pois?.poiList?.firstOrNull {
                !it.name.contains("소화전") && !it.name.contains("공중전화") && !it.name.contains("정류소")
            }?.name
        } catch (_: Exception) { null }
    }

    private fun buildSmartMessage(step: RouteStep, landmark: String?): String {
        val dir = when (step.turnType) {
            12 -> "좌회전"; 13 -> "우회전"; 14 -> "유턴"
            16 -> "8시 방향"; 17 -> "10시 방향"; 18 -> "2시 방향"; 19 -> "4시 방향"
            211,212,213,214,215,216,217 -> "횡단보도"; 233 -> "직진"
            else -> step.description
        }
        if (dir == "직진") return step.description.replace("이동","직진하세요").replace("m","미터")
        return if (landmark != null) when (dir) {
            "좌회전" -> "전방 ${landmark}을 끼고 왼쪽으로 도세요"
            "우회전" -> "전방 ${landmark}을 끼고 오른쪽으로 도세요"
            "횡단보도" -> "${landmark} 근처 횡단보도를 건너주세요"
            "유턴" -> "${landmark} 앞에서 유턴하세요"
            else -> "${landmark} 방향으로 가세요"
        } else "약 30미터 앞에서 ${dir}하세요"
    }
*/
    // 주변 랜드마크 찾기
    private suspend fun searchNearbyPOIForTurn(point: TMapPoint): String = withContext(Dispatchers.IO) {
        try {
            val resp = RetrofitClient.tmapService.searchAroundPOI(
                appKey = BuildConfig.TMAP_APP_KEY,
                version = 1,
                centerLat = point.latitude.toString(),
                centerLon = point.longitude.toString(),
                radius = "1", // 반경 (1 = 약 300m 내외)
                count = 5,
                categories = "편의점;커피전문점;은행;패스트푸드;지하철역" // 가장 눈에 띄는 시설들
            )

            val poiList = resp.body()?.searchPoiInfo?.pois?.poiList
            if (poiList.isNullOrEmpty()) {
                return@withContext "특징적인 랜드마크 없음"
            }

            // 쓰레기 데이터(공중전화, 소화전 등) 제외하고 이름만 쉼표로 연결해서 3개만 추출
            poiList.filter { !it.name.contains("소화전") && !it.name.contains("공중전화") }
                .take(3)
                .joinToString(", ") { it.name }

        } catch (e: Exception) {
            "랜드마크 검색 실패"
        }
    }


    // ============================================================
    //  12. TTS
    // ============================================================

    private fun speakTTS(text: String) {
        if (isTtsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    private fun speakTTSWithCooldown(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastTTSTime >= TTS_COOLDOWN) { lastTTSTime = now; speakTTS(text) }
    }


    // ============================================================
    //  13. 유틸
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
    //  14. AI 카메라 & Gemini 연동 로직
    // ============================================================

    private fun startAiCameraFlow() {
        binding.fabAiGuide.visibility = View.GONE
        binding.cvAiCameraLayout.visibility = View.VISIBLE
        binding.ivSelectedImage.visibility = View.GONE
        binding.viewFinder.visibility = View.VISIBLE

        // 멘트 변경
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
                val responseText = GeminiHelper.analyzeImage(bitmap, step)?.trim()

                withContext(Dispatchers.Main) {
                    binding.progressBarAi.visibility = View.GONE
                    binding.cvAiCameraLayout.visibility = View.GONE

                    // 예외 처리: AI가 인식에 실패해서 "0"을 반환했거나 결과가 없을 때
                    if (responseText == null || responseText == "0" || responseText.isEmpty()) {
                        val fallbackMessage = step.description // TMap의 기본 깔끔한 멘트

                        binding.tvNavInstruction.text = fallbackMessage
                        speakTTS(fallbackMessage)
                        Toast.makeText(this@TmapsActivity, "특징을 찾기 어려워 기본 안내를 제공합니다.", Toast.LENGTH_SHORT).show()
                    }
                    // AI가 랜드마크를 찾아서 문장을 만들기 성공 시
                    else {
                        binding.tvNavInstruction.text = "🤖 $responseText"
                        speakTTS(responseText)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarAi.visibility = View.GONE
                    binding.cvAiCameraLayout.visibility = View.GONE

                    // 통신 에러가 났을 때 그냥 기본 안내 제공
                    val fallbackMessage = step.description
                    binding.tvNavInstruction.text = fallbackMessage
                    speakTTS(fallbackMessage)
                    Toast.makeText(this@TmapsActivity, "AI 연결 오류로 기본 안내를 제공합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ImageProxy -> 640x480 Bitmap 최적화 함수
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

    // --- 가상 테스트(Mock)용 갤러리 로직 ---
    private val galleryForAiLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                autoCaptureJob?.cancel() // 자동 촬영 취소
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

    private fun openGalleryForAiTest() {
        galleryForAiLauncher.launch("image/*")
    }

    private fun checkNavigationProgress(currentLoc: TMapPoint) {
        // 1. 목적지 잔여 거리 계산 및 도착 체크
        destinationPoint?.let { dest ->
            val dist = calculateDistance(currentLoc, dest)
            binding.tvNavRemainInfo.text = String.format("목적지까지: %.0fm", dist)

            if (dist <= 15f) {
                speakTTSWithCooldown("목적지 부근에 도착했습니다. 안내를 종료합니다.")
                stopNavigation()
                return
            }
        }

        // 2. 분기점 AI 안내 로직
        if (upcomingSteps.isNotEmpty()) {
            val next = upcomingSteps.first()
            val distToTurn = calculateDistance(currentLoc, next.coordinate)

            // ① 회전 50m 전: AI 카메라 버튼 등장
            if (distToTurn in 30f..50f && binding.fabAiGuide.visibility == View.GONE) {
                currentStepForAI = next
                binding.fabAiGuide.visibility = View.VISIBLE
            }

            // ② 회전 30m 이내: 랜드마크 기반 AI 텍스트 안내 제공
            if (distToTurn <= 30f && !announcedStepIndices.contains(next.pointIndex)) {
                announcedStepIndices.add(next.pointIndex)
                val originalMessage = next.description

                lifecycleScope.launch {
                    val landmarks = searchNearbyPOIForTurn(next.coordinate)
                    val enhancedMessage = if (landmarks == "특징적인 랜드마크 없음" || landmarks == "랜드마크 검색 실패") {
                        originalMessage
                    } else {
                        GeminiHelper.enhanceNavigationText(originalMessage, landmarks)
                    }

                    binding.tvNavInstruction.text = enhancedMessage
                    speakTTSWithCooldown(enhancedMessage)
                }

                upcomingSteps.removeAt(0)
                binding.fabAiGuide.visibility = View.GONE
            }
        }
    }
}