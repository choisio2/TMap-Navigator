package com.aivy.navigator

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
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
import android.util.Xml
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.*
import com.skt.Tmap.TMapMarkerItem
import com.skt.Tmap.TMapPoint
import com.skt.Tmap.TMapPolyLine
import com.skt.Tmap.TMapView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.xmlpull.v1.XmlPullParser
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

// Database Imports
import com.aivy.navigator.data.database.AppDatabase
import com.aivy.navigator.data.database.SavedCourseEntity
import com.aivy.navigator.data.database.WorkoutRecordEntity

// ==========================================
// 기본 데이터 모델
// ==========================================

data class CourseInfo(val resId: Int, val name: String)

// 앱 내 정적 코스 리스트
val ALL_AVAILABLE_COURSES = listOf(
    CourseInfo(R.raw.dangdang_run, "🐕 광화문 댕댕이런"),
    CourseInfo(R.raw.dolphin_run, "🐬 광교 돌고래런"),
    CourseInfo(R.raw.seoul_half_marathon, "🏃 서울 하프 마라톤"),
    CourseInfo(R.raw.rudolph_run, "🦌 루돌프런"),
    CourseInfo(R.raw.seoul_half_marathon, "🏃 MBN 서울 마라톤")
)

// ==========================================
// ViewModel 및 상태 관리 클래스
// ==========================================

enum class RunState { INIT, COUNTDOWN, RUNNING, PAUSED, STOPPED, FINISHED }

class RunningViewModel(application: Application) : AndroidViewModel(application) {
    var runState by mutableStateOf(RunState.INIT)
    var countdown by mutableStateOf(3)
    var timeElapsed by mutableStateOf(0L)
    var distance by mutableStateOf(0.0)

    val pathPoints = mutableStateListOf<TMapPoint>()
    val importedRoute = mutableStateListOf<TMapPoint>()

    // 구간별 스플릿 타임 계산용 변수
    val kmSplits = mutableStateListOf<Long>()
    private var lastSplitTime = 0L
    private var currentKmTarget = 1
    var lastWorkoutRecord by mutableStateOf<WorkoutRecordEntity?>(null)

    private var timerJob: Job? = null
    private var lastLocation: Location? = null

    // Room DB 및 DAO 초기화
    private val db = AppDatabase.getDatabase(application)
    private val runningDao = db.runningDao()

    val savedCoursesFlow = runningDao.getAllSavedCourses()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    var showAllCoursesDialog by mutableStateOf(false)
    var showSavedCoursesDialog by mutableStateOf(false)

    // 북마크 상태 변경
    fun toggleBookmark(course: CourseInfo, isSaved: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = SavedCourseEntity(course.resId, course.name)
            if (isSaved) {
                runningDao.deleteCourse(entity)
            } else {
                runningDao.insertCourse(entity)
            }
        }
    }

    // gpx 경로 데이터 파싱 및 로드
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

    // 러닝 카운트다운하고 시작
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

            val context = getApplication<Application>()
            val serviceIntent = Intent(context, RunningService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
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

    // 러닝 종료 및 기록 저장
    fun stopRunning() {
        timerJob?.cancel()

        val context = getApplication<Application>()
        val serviceIntent = Intent(context, RunningService::class.java)
        context.stopService(serviceIntent)

        viewModelScope.launch(Dispatchers.IO) {
            // 이전 세션 기록 조회
            lastWorkoutRecord = runningDao.getLastWorkout()

            // 신규 기록 저장
            val calories = calculateCalories()
            val newRecord = WorkoutRecordEntity(
                distance = distance,
                timeElapsed = timeElapsed,
                calories = calories,
                paceStr = calculatePace(),
                splitsCsv = kmSplits.joinToString(",")
            )
            runningDao.insertWorkout(newRecord)

            withContext(Dispatchers.Main) {
                runState = RunState.FINISHED
            }
        }
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

    // 위치 업데이트 및 거리 계산
    fun updateLocation(newLoc: Location) {
        if (runState != RunState.RUNNING) return
        val newPoint = TMapPoint(newLoc.latitude, newLoc.longitude)
        pathPoints.add(newPoint)

        lastLocation?.let { last ->
            val distMeters = last.distanceTo(newLoc)
            distance += (distMeters / 1000.0)

            // 지정된 km 구간 도달 시 스플릿 타임 기록
            if (distance >= currentKmTarget) {
                val splitDuration = timeElapsed - lastSplitTime
                kmSplits.add(splitDuration)
                lastSplitTime = timeElapsed
                currentKmTarget++
            }
        }
        lastLocation = newLoc
    }

    // 현재 페이스 계산
    fun calculatePace(): String {
        if (distance == 0.0 || timeElapsed == 0L) return "0'00\""
        val totalMinutes = (timeElapsed / 60.0) / distance
        val minutes = totalMinutes.toInt()
        val secs = ((totalMinutes - minutes) * 60).toInt()
        return String.format("%d'%02d\"", minutes, secs)
    }

    // 성인 평균 체중(65kg) 기준 칼로리 소모량 계산
    // TODO: 추후에 개인 정보 받아서 계산하는 로직으로 수정하기
    fun calculateCalories(): Int {
        return (65 * distance * 1.036).roundToInt()
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}



class RunningActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var myLocationBaseBitmap: Bitmap? = null
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var currentAzimuth = 0f
    private var lastMarkerAzimuth = 0f
    private var currentLocation: TMapPoint? = null
    private var tMapViewInstance: TMapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        myLocationBaseBitmap = getBitmapFromVectorDrawable(R.drawable.ic_my_location_dot)

        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                tMapViewInstance?.let { fetchCurrentLocation(it) }
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )

        setContent {
            val viewModel: RunningViewModel = viewModel()

            val tMapView = remember {
                TMapView(this@RunningActivity).apply {
                    tMapViewInstance = this
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

            // 센서 이벤트를 통한 디바이스 방향 측정
            DisposableEffect(Unit) {
                val sensorEventListener = object : SensorEventListener {
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
                                updateMyLocationMarker(tMapView)
                            }
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                accelerometer?.let { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI) }
                magnetometer?.let { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI) }

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
                onStopGps = { fusedLocationClient.removeLocationUpdates(locationCallback) },
                onMyLocationClick = { fetchCurrentLocation(tMapView) }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation(tMapView: TMapView) {
        // 캐시된 최근 위치를 우선적으로 적용하여 초기 지도 로딩 최적화
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                currentLocation = TMapPoint(loc.latitude, loc.longitude)
                tMapView.setCenterPoint(loc.longitude, loc.latitude)
                updateMyLocationMarker(tMapView)
            }
        }

        // 정확한 현재 위치를 백그라운드에서 재요청하여 위치 보정
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLocation = TMapPoint(loc.latitude, loc.longitude)
                    tMapView.setCenterPoint(loc.longitude, loc.latitude)
                    updateMyLocationMarker(tMapView)
                }
            }
    }

    // 마커 아이콘 회전 및 UI 업데이트
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
        tMapView.postInvalidate() // 지도 강제 갱신
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
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

// ==========================================
// Compose UI
// ==========================================

@Composable
fun RunningScreen(
    viewModel: RunningViewModel,
    tMapView: TMapView,
    onStartGps: () -> Unit,
    onStopGps: () -> Unit,
    onMyLocationClick: () -> Unit
) {
    val context = LocalContext.current
    var showMainMenu by remember { mutableStateOf(false) }

    // 운동 완료 -> 데이터 요약 화면으로 전환 -> 러닝홈으로
    if (viewModel.runState == RunState.FINISHED) {
        WorkoutSummaryScreen(
            viewModel = viewModel,
            onGoHome = {
                val intent = Intent(context, RunningReadyActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                (context as? ComponentActivity)?.finish()
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 지도 영역
        AndroidView(
            factory = { tMapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.removeAllTMapPolyLine()

                // 가이드 경로 
                if (viewModel.importedRoute.isNotEmpty()) {
                    val guideLine = TMapPolyLine().apply {
                        lineColor = AndroidColor.parseColor("#802196F3")
                        lineWidth = 15f
                    }
                    viewModel.importedRoute.forEach { guideLine.addLinePoint(it) }
                    view.addTMapPolyLine("guide_route", guideLine)
                }

                // 사용자가 이동한 경로
                if (viewModel.pathPoints.size >= 2) {
                    val myLine = TMapPolyLine().apply {
                        lineColor = AndroidColor.parseColor("#FF1976D2") // 초록색으로 변경
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

        // 우측 부가 기능 메뉴
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 210.dp, end = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                Button(
                    onClick = { showMainMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    elevation = ButtonDefaults.elevatedButtonElevation(6.dp)
                ) {
                    Text("➕ 더 많은 기능", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                DropdownMenu(
                    expanded = showMainMenu,
                    onDismissRequest = { showMainMenu = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    DropdownMenuItem(
                        text = { Text("🌍 새로운 코스 가져오기", fontWeight = FontWeight.Bold) },
                        onClick = {
                            showMainMenu = false
                            viewModel.showAllCoursesDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("♥️ 저장한 코스 불러오기", fontWeight = FontWeight.Bold) },
                        onClick = {
                            showMainMenu = false
                            viewModel.showSavedCoursesDialog = true
                        }
                    )
                }
            }
        }

        // 내 위치 다시 잡기 플로팅 버튼
        FloatingActionButton(
            onClick = onMyLocationClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 40.dp),
            containerColor = Color.White,
            contentColor = Color(0xFF1976D2),
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "내 위치로 이동", modifier = Modifier.size(28.dp))
        }

        // 하단 컨트롤 박스
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
                        onClick = { /* 추후 완료 후속 처리 구성 가능 */ },
                        modifier = Modifier.size(width = 200.dp, height = 60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) { Text("러닝 완료", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                else -> {}
            }
        }

        // 카운트다운 영역
        if (viewModel.runState == RunState.COUNTDOWN) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = viewModel.countdown.toString(), fontSize = 180.sp, color = Color.White, fontWeight = FontWeight.Black)
            }
        }
    }

    // ==========================================
    // 팝업 다이얼로그
    // ==========================================
    val savedCourses by viewModel.savedCoursesFlow.collectAsState()
    val savedIds = savedCourses.map { it.resId }

    // 새로운 코스 목록 가져오기 다이얼로그
    if (viewModel.showAllCoursesDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showAllCoursesDialog = false },
            title = { Text("새로운 코스 가져오기", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(ALL_AVAILABLE_COURSES) { course ->
                        val isSaved = savedIds.contains(course.resId)
                        CourseListItem(
                            courseName = course.name,
                            isSaved = isSaved,
                            onItemClick = {
                                viewModel.loadGpxRoute(context, course.resId, tMapView)
                                viewModel.showAllCoursesDialog = false
                            },
                            onBookmarkClick = { viewModel.toggleBookmark(course, isSaved) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.showAllCoursesDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    // 저장한 코스 불러오기 다이얼로그
    if (viewModel.showSavedCoursesDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showSavedCoursesDialog = false },
            title = { Text("저장한 코스 불러오기", fontWeight = FontWeight.Bold) },
            text = {
                if (savedCourses.isEmpty()) {
                    Text("저장된 코스가 없습니다.\n새로운 코스에서 하트 아이콘을 눌러주세요!", color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(savedCourses) { savedCourse ->
                            val course = CourseInfo(savedCourse.resId, savedCourse.name)
                            CourseListItem(
                                courseName = course.name,
                                isSaved = true,
                                onItemClick = {
                                    viewModel.loadGpxRoute(context, course.resId, tMapView)
                                    viewModel.showSavedCoursesDialog = false
                                },
                                onBookmarkClick = { viewModel.toggleBookmark(course, true) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.showSavedCoursesDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }
}

// 다이얼로그 내 개별 코스 항목 UI
@Composable
fun CourseListItem(courseName: String, isSaved: Boolean, onItemClick: () -> Unit, onBookmarkClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = courseName,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp
        )
        IconButton(onClick = onBookmarkClick) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Bookmark",
                tint = if (isSaved) Color(0xFFE91E63) else Color.LightGray
            )
        }
    }
}

// 상단 대시보드 데이터 아이템 컴포넌트
@Composable
fun RunningDataItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 14.sp, color = Color.Gray)
        Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
    }
}

// ==========================================
// 운동 결과 요약 화면
// ==========================================
@Composable
fun WorkoutSummaryScreen(viewModel: RunningViewModel, onGoHome: () -> Unit) {
    val lastSession = viewModel.lastWorkoutRecord

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF7F8FA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 타이틀 및 아이콘
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier.size(70.dp).background(Color(0xFFE8F5E9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("운동 완료!", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black)
            Spacer(modifier = Modifier.height(30.dp))
        }

        // 통계 요약 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        SummaryItem("거리", String.format("%.2f", viewModel.distance), "km")
                        SummaryItem("시간", formatSeconds(viewModel.timeElapsed), "")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        SummaryItem("평균", viewModel.calculatePace(), "")
                        SummaryItem("칼로리", viewModel.calculateCalories().toString(), "kcal")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 지난 세션 대비 비교 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("지난 세션 대비", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (lastSession == null) {
                        Text("지난 기록이 없습니다.", fontSize = 14.sp, color = Color.DarkGray)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val distDiff = viewModel.distance - lastSession.distance
                            val calDiff = viewModel.calculateCalories() - lastSession.calories

                            DiffBadge("거리", String.format("%+.1fkm", distDiff), if(distDiff>=0) Color(0xFF4CAF50) else Color(0xFF1976D2))
                            DiffBadge("칼로리", String.format("%+dkcal", calDiff), if(calDiff>=0) Color(0xFF4CAF50) else Color(0xFF1976D2))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // km별 스플릿 타임 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("km별 스플릿", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (viewModel.kmSplits.isEmpty()) {
                        Text("1km 미만의 기록입니다.", fontSize = 14.sp, color = Color.DarkGray)
                    } else {
                        val maxTime = viewModel.kmSplits.maxOrNull() ?: 1L
                        viewModel.kmSplits.forEachIndexed { index, timeSec ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}km", fontSize = 14.sp, modifier = Modifier.width(40.dp))

                                Box(modifier = Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE3F2FD))) {
                                    val fraction = (timeSec.toFloat() / maxTime).coerceIn(0f, 1f)
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color(0xFF64B5F6)))
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                val mins = timeSec / 60
                                val secs = timeSec % 60
                                Text(String.format("%d'%02d\"", mins, secs), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // 하단 이동 버튼
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onGoHome,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF102841)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("홈으로", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

                // TODO: 공유기능 구현
                OutlinedButton(
                    onClick = { /* 공유 기능 연동 가능 */ },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("공유", fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// 통계 수치 표시용 공통 컴포넌트
@Composable
fun SummaryItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        if (unit.isNotEmpty()) {
            Text(text = unit, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

// 이전 세션 기록과 증감 수치 표시용 뱃지 컴포넌트
@Composable
fun DiffBadge(label: String, diffStr: String, diffColor: Color) {
    Column(
        modifier = Modifier.background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
        Text(text = diffStr, fontSize = 16.sp, color = diffColor, fontWeight = FontWeight.Bold)
    }
}

// 초 단위를 시:분:초 포맷으로 변환하는 함수
fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}