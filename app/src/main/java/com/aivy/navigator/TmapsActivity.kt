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
    private var isTtsReady = false  // TTS 초기화 완료 플래그

    // -- 안내 제어 --
    private var lastTTSTime = 0L
    private val TTS_COOLDOWN = 2000L
    private val announcedStepIndices = mutableSetOf<Int>()
    private val MOCK_SPEED_MS = 500L

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

        setupTMapView()
        setupSearch()
        setupRouteButton()
        setupLocationCallback()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancelRoutePreview.setOnClickListener { cancelRoutePreview() }

        binding.btnAddWaypoint.setOnClickListener { onAddWaypointClicked() }
        binding.btnRemoveWaypoint.setOnClickListener { removeWaypointAndReroute() }
    }

    override fun onDestroy() {
        if (isTtsReady) { tts.stop(); tts.shutdown() }
        if (isNavigating && this::locationCallback.isInitialized) {
            try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN; tts.setSpeechRate(0.85f);tts.setPitch(1.0f); isTtsReady = true
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
                // 중심점을 이미지 중앙(0.5, 0.5)으로 설정
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

                        // 예쁜 리스트를 만들어주는 커스텀 어댑터
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

                                // 항목별 위아래 여백을 주어 쾌적하게
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
        destinationPoint = TMapPoint(lat, lon); destinationName = item.name

        tMapView.removeAllTMapPolyLine()
        binding.cardRouteInfo.visibility = View.GONE
        tMapView.removeMarkerItem("destination")
        tMapView.removeMarkerItem("waypoint")

        tMapView.addMarkerItem("destination", TMapMarkerItem().apply {
            tMapPoint = destinationPoint; name = item.name
        })
        tMapView.setCenterPoint(lon, lat); tMapView.zoomLevel = 15; tMapView.postInvalidate()
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
            lineColor = Color.parseColor("#215CF3") // 산뜻한 머티리얼 블루
            lineWidth = 11f                          // 15f에서 8f로 얇고 깔끔하게
            outLineColor = Color.parseColor("#1976D2") // 약간 짙은 테두리 색상
            outLineWidth = 2f                       // 테두리 두께 추가로 입체감 부여
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
        isNavigating = true
        announcedStepIndices.clear()
        binding.searchCard.visibility = View.GONE
        binding.cardRouteInfo.visibility = View.GONE
        binding.navTopCard.visibility = View.VISIBLE
        binding.navBottomCard.visibility = View.VISIBLE
        speakTTS("경로 안내를 시작합니다.")
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
        // 가상주행 루프 즉시 탈출
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

        // 5) 데이터 초기화 (isWaypointMode 삭제됨)
        allRoutePoints.clear()
        routeSteps.clear()
        announcedStepIndices.clear()
        waypointPoint = null
        waypointName = ""

        // UI 복귀
        binding.tvWaypointInfo.text = "경유지 없음"
        binding.btnAddWaypoint.visibility = View.VISIBLE
        binding.btnRemoveWaypoint.visibility = View.GONE

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
            val upcoming = routeSteps.toMutableList()
            upcoming.removeAll { s ->
                s.description.contains("이동") && !s.description.contains("회전")
                        && !s.description.contains("좌") && !s.description.contains("우")
                        && !s.description.contains("m")
            }

            val points = allRoutePoints.toList()  // 스냅샷 복사
            for (pt in points) {
                if (!isNavigating) break
                currentLocation = pt
                updateMyLocationMarker()
                tMapView.setCenterPoint(pt.longitude, pt.latitude)

                // ⭐ 변경됨: TMap이 하나의 경로로 묶어주었으므로, 최종 목적지까지의 거리만 계산하면 됩니다!
                destinationPoint?.let {
                    val dist = calculateDistance(currentLocation!!, it)
                    binding.tvNavRemainInfo.text = String.format("목적지까지: %.0fm", dist)

                    // 목적지에 15m 이내로 접근하면 안내 종료
                    if (dist <= 15f) {
                        speakTTSWithCooldown("목적지 부근에 도착했습니다. 안내를 종료합니다.")
                        stopNavigation()
                        return@launch
                    }
                }

                // 분기점 안내
                if (upcoming.isNotEmpty()) {
                    val next = upcoming.first()
                    if (calculateDistance(currentLocation!!, next.coordinate) <= 30f
                        && !announcedStepIndices.contains(next.pointIndex)) {
                        announcedStepIndices.add(next.pointIndex)

                        // 주변 랜드마크 검색 및 안내 멘트 생성
                        val msg = buildSmartMessage(next, searchNearbyPOI(next.coordinate))
                        binding.tvNavInstruction.text = msg
                        speakTTSWithCooldown(msg)
                        upcoming.removeAt(0)
                    }
                }
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
                    currentLocation = TMapPoint(loc.latitude, loc.longitude)
                    updateMyLocationMarker()
                    tMapView.setCenterPoint(loc.longitude, loc.latitude)
                }
            }
        }
    }

    // ============================================================
    //  10. 경유지
    // ============================================================

    /** 경유지 추가 버튼 클릭 시 호출 */
    private fun onAddWaypointClicked() {
        val et = EditText(this).apply {
            hint = "경유지 검색 (예: 편의점, 카페)"
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

    /** 검색된 POI를 경유지로 설정하고 경로 재탐색 */
    private fun addWaypointAndReroute(item: PoiItem) {
        val lat = item.noorLat.toDoubleOrNull() ?: return
        val lon = item.noorLon.toDoubleOrNull() ?: return
        waypointPoint = TMapPoint(lat, lon)
        waypointName = item.name

        // 지도에 경유지 마커 표시
        tMapView.removeMarkerItem("waypoint")
        tMapView.addMarkerItem("waypoint", TMapMarkerItem().apply {
            tMapPoint = waypointPoint
            name = "경유지: ${item.name}"
        })

        // UI 업데이트 (XML에 해당 ID들이 있어야 함)
        binding.tvWaypointInfo.text = "경유지: ${item.name}"
        binding.btnAddWaypoint.visibility = View.GONE
        binding.btnRemoveWaypoint.visibility = View.VISIBLE

        speakTTS("${item.name}을 경유지로 추가했습니다.")

        // 현재 위치에서 목적지까지, 경유지를 포함하여 다시 탐색
        if (currentLocation != null && destinationPoint != null) {
            findPedestrianRoute(currentLocation!!, destinationPoint!!)
        }
    }

    /** 경유지 삭제 로직 */
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
}