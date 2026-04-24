package com.aivy.navigator

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Color as AndroidColor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.*
import com.skt.Tmap.TMapMarkerItem
import com.skt.Tmap.TMapPoint
import com.skt.Tmap.TMapPolyLine
import com.skt.Tmap.TMapView
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.abs
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List

// 러닝 상태를 정의하는 Enum
enum class RunState { INIT, COUNTDOWN, RUNNING, PAUSED, STOPPED }

// ViewModel (데이터 및 상태 관리)
class RunningViewModel : ViewModel() {
    var runState by mutableStateOf(RunState.INIT)
    var countdown by mutableStateOf(3)
    var timeElapsed by mutableStateOf(0L)
    var distance by mutableStateOf(0.0) // km

    val pathPoints = mutableStateListOf<TMapPoint>()

    private var timerJob: Job? = null
    private var lastLocation: Location? = null

    // gpx 파일 넣기
    val importedRoute = mutableStateListOf<TMapPoint>()

    fun loadGpxRoute(context: Context, resId: Int, tMapView: TMapView) {
        viewModelScope.launch(Dispatchers.IO) {
            val points = mutableListOf<TMapPoint>()
            try {
                val parser = Xml.newPullParser()
                val inputStream = context.resources.openRawResource(resId)
                parser.setInput(inputStream, null)
                var eventType = parser.eventType

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "trkpt") {
                        val lat = parser.getAttributeValue(null, "lat").toDouble()
                        val lon = parser.getAttributeValue(null, "lon").toDouble()
                        points.add(TMapPoint(lat, lon))
                    }
                    eventType = parser.next()
                }
                inputStream.close()

                withContext(Dispatchers.Main) {
                    importedRoute.clear()
                    importedRoute.addAll(points)

                    // 루트를 불러오면 시작점으로 시점 자동 이동
                    if (points.isNotEmpty()) {
                        tMapView.setCenterPoint(points[0].longitude, points[0].latitude)
                        Toast.makeText(context, "루트를 성공적으로 불러왔습니다", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "루트 파일을 읽는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startCountdown(onCountdownFinish: () -> Unit) {
        runState = RunState.COUNTDOWN
        viewModelScope.launch {
            for (i in 3 downTo 1) {
                countdown = i
                delay(1000)
            }
            countdown = 0
            runState = RunState.RUNNING
            onCountdownFinish()
            startTimer()
        }
    }

    fun pauseRunning() {
        runState = RunState.PAUSED
        timerJob?.cancel()
    }

    fun resumeRunning() {
        runState = RunState.RUNNING
        startTimer()
    }

    fun stopRunning() {
        runState = RunState.STOPPED
        timerJob?.cancel()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (runState == RunState.RUNNING) {
                delay(1000)
                timeElapsed++
            }
        }
    }

    fun updateLocation(newLoc: Location) {
        if (runState != RunState.RUNNING) return
        val newPoint = TMapPoint(newLoc.latitude, newLoc.longitude)
        pathPoints.add(newPoint)

        lastLocation?.let { last ->
            val distMeters = last.distanceTo(newLoc)
            distance += (distMeters / 1000.0)
        }
        lastLocation = newLoc
    }

    fun calculatePace(): String {
        if (distance == 0.0 || timeElapsed == 0L) return "0'00\""
        val totalMinutes = (timeElapsed / 60.0) / distance
        val minutes = totalMinutes.toInt()
        val secs = ((totalMinutes - minutes) * 60).toInt()
        return String.format("%d'%02d\"", minutes, secs)
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}

// 3. 메인 Activity
class RunningActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // 마커 및 센서 관련 변수
    private var myLocationBaseBitmap: Bitmap? = null
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var currentAzimuth = 0f
    private var lastMarkerAzimuth = 0f
    private var currentLocation: TMapPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 센서 초기화 및 마커 이미지 캐싱
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        myLocationBaseBitmap = getBitmapFromVectorDrawable(R.drawable.ic_my_location_dot)

        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

        setContent {
            val viewModel: RunningViewModel = viewModel()

            // TMap 뷰 초기화 -> 키 인증 성공 시 현재 위치 바로 찾기
            val tMapView = remember {
                TMapView(this@RunningActivity).apply {
                    setSKTMapApiKey(BuildConfig.TMAP_APP_KEY)
                    zoomLevel = 17
                    setOnApiKeyListener(object : TMapView.OnApiKeyListenerCallback {
                        override fun SKTMapApikeySucceed() {
                            runOnUiThread { fetchCurrentLocation(this@apply) }
                        }
                        override fun SKTMapApikeyFailed(msg: String?) {}
                    })
                }
            }

            // 센서 및 GPS 콜백을 위한 Compose 생명주기 관리
            DisposableEffect(Unit) {
                // 센서 리스너 (방향 마커 회전용)
                val sensorEventListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) gravity.indices.forEach { gravity[it] = event.values[it] }
                        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) geomagnetic.indices.forEach { geomagnetic[it] = event.values[it] }

                        val R = FloatArray(9)
                        val I = FloatArray(9)
                        if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                            val orientation = FloatArray(3)
                            SensorManager.getOrientation(R, orientation)
                            currentAzimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360) % 360

                            if (abs(currentAzimuth - lastMarkerAzimuth) > 5f) {
                                lastMarkerAzimuth = currentAzimuth
                                updateMyLocationMarker(tMapView)
                            }
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                accelerometer?.let { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI) }
                magnetometer?.let { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI) }

                // GPS 콜백 (위치 이동용)
                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let { loc ->
                            currentLocation = TMapPoint(loc.latitude, loc.longitude)
                            tMapView.setCenterPoint(loc.longitude, loc.latitude)
                            updateMyLocationMarker(tMapView)
                            viewModel.updateLocation(loc)
                        }
                    }
                }

                onDispose {
                    sensorManager.unregisterListener(sensorEventListener)
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                }
            }

            RunningScreen(
                viewModel = viewModel,
                tMapView = tMapView,
                onStartGps = { startLocationUpdates() },
                onStopGps = { fusedLocationClient.removeLocationUpdates(locationCallback) }
            )
        }
    }

    // 앱 켜자마자 내 위치 한 번 잡아주는 함수
    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation(tMapView: TMapView) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLocation = TMapPoint(loc.latitude, loc.longitude)
                    tMapView.setCenterPoint(loc.longitude, loc.latitude)
                    updateMyLocationMarker(tMapView)
                }
            }
    }

    // 내 위치 마커 그리기 & 회전 업데이트 함수
    private fun updateMyLocationMarker(tMapView: TMapView) {
        val loc = currentLocation ?: return
        tMapView.removeMarkerItem("myLocation")

        val base = myLocationBaseBitmap ?: return
        val matrix = Matrix().apply { postRotate(currentAzimuth) }
        val rotated = Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)

        val marker = TMapMarkerItem().apply {
            tMapPoint = loc
            name = "내 위치"
            icon = rotated
            setPosition(0.5f, 0.5f)
        }
        tMapView.addMarkerItem("myLocation", marker)
        tMapView.postInvalidate()
    }

    private fun getBitmapFromVectorDrawable(drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, drawableId)!!
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN
            isTtsReady = true
        }
    }

    override fun onDestroy() {
        tts.stop(); tts.shutdown()
        super.onDestroy()
    }
}

@Composable
fun RunningScreen(
    viewModel: RunningViewModel,
    tMapView: TMapView,
    onStartGps: () -> Unit,
    onStopGps: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showRouteMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // 지도 영역
        AndroidView(
            factory = { tMapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.removeAllTMapPolyLine()

                if (viewModel.importedRoute.isNotEmpty()) {
                    val guideLine = TMapPolyLine().apply {
                        lineColor = AndroidColor.parseColor("#802196F3")
                        lineWidth = 15f
                    }
                    viewModel.importedRoute.forEach { guideLine.addLinePoint(it) }
                    view.addTMapPolyLine("guide_route", guideLine)
                }

                if (viewModel.pathPoints.size >= 2) {
                    val myLine = TMapPolyLine().apply {
                        lineColor = AndroidColor.parseColor("#DE6F3F")
                        lineWidth = 15f
                    }
                    viewModel.pathPoints.forEach { myLine.addLinePoint(it) }
                    view.addTMapPolyLine("run_path", myLine)
                }
            }
        )

        // 상단 데이터 대시보드
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .padding(top = 40.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "러닝 거리", fontSize = 14.sp, color = Color.Gray)
            Text(text = String.format("%.2f km", viewModel.distance), fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color(0xFF212121))

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RunningDataItem("시간", formatSeconds(viewModel.timeElapsed))
                RunningDataItem("페이스", viewModel.calculatePace())
            }
        }

        // 우측 루트 가져오기 버튼
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 210.dp, end = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            // 버튼과 드롭다운을 하나의 Box로 묶기
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                Button(
                    onClick = { showRouteMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    elevation = ButtonDefaults.elevatedButtonElevation(6.dp)
                ) {
                    Text("📍 루트 가져오기", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                DropdownMenu(
                    expanded = showRouteMenu,
                    onDismissRequest = { showRouteMenu = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    DropdownMenuItem(
                        text = { Text("🐕 광화문 댕댕이런", fontWeight = FontWeight.Bold) },
                        onClick = {
                            showRouteMenu = false
                            viewModel.loadGpxRoute(context, R.raw.dangdang_run, tMapView)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🐬 광교 돌고래런", fontWeight = FontWeight.Bold) },
                        onClick = {
                            showRouteMenu = false
                            viewModel.loadGpxRoute(context, R.raw.dolphin_run, tMapView)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🏃 서울 하프 마라톤", fontWeight = FontWeight.Bold) },
                        onClick = {
                            showRouteMenu = false
                            viewModel.loadGpxRoute(context, R.raw.seoul_half_marathon, tMapView)
                        }
                    )
                }
            }
        }

        // 하단 컨트롤 Box
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            when (viewModel.runState) {
                RunState.INIT -> {
                    Button(
                        onClick = { viewModel.startCountdown(onStartGps) },
                        modifier = Modifier.size(width = 150.dp, height = 60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("시작", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                }
                RunState.RUNNING -> {
                    Button(
                        onClick = { viewModel.pauseRunning() },
                        modifier = Modifier.size(width = 150.dp, height = 60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) { Text("일시정지", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                RunState.PAUSED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { viewModel.resumeRunning() },
                            modifier = Modifier.size(width = 120.dp, height = 60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) { Text("재개", fontSize = 18.sp) }

                        Button(
                            onClick = {
                                viewModel.stopRunning()
                                onStopGps()
                            },
                            modifier = Modifier.size(width = 120.dp, height = 60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                        ) { Text("종료", fontSize = 18.sp) }
                    }
                }
                RunState.STOPPED -> {
                    Button(
                        onClick = { /* 완료 처리 */ },
                        modifier = Modifier.size(width = 200.dp, height = 60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) { Text("러닝 완료", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                else -> {}
            }
        }

        // 카운트다운 박스
        if (viewModel.runState == RunState.COUNTDOWN) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = viewModel.countdown.toString(), fontSize = 180.sp, color = Color.White, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun RunningDataItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 14.sp, color = Color.Gray)
        Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
    }
}

fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}