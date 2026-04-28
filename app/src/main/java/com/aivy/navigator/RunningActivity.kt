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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aivy.navigator.ui.components.AivyStatusChip
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace
import com.aivy.navigator.ui.theme.AivyTheme
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
            AivyTheme {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningScreen(
    viewModel: RunningViewModel,
    tMapView: TMapView,
    onStartGps: () -> Unit,
    onStopGps: () -> Unit,
    onMyLocationClick: () -> Unit
) {
    val context = LocalContext.current
    var showCourseSheet by remember { mutableStateOf(false) }
    val coachingMessage = rememberRunCoachingMessage(viewModel)

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

        LiveRunDashboard(
            viewModel = viewModel,
            coachingMessage = coachingMessage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AivySpace.Page, vertical = AivySpace.Lg)
                .align(Alignment.TopCenter),
        )

        // 우측 부가 기능 메뉴
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 210.dp, end = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                Button(
                    onClick = { showCourseSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Surface),
                    shape = RoundedCornerShape(AivyRadius.Lg),
                    elevation = ButtonDefaults.elevatedButtonElevation(6.dp)
                ) {
                    Text("코스", color = AivyColors.Primary, fontWeight = FontWeight.Bold)
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
            contentColor = AivyColors.Accent,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "내 위치로 이동", modifier = Modifier.size(28.dp))
        }

        RunControlDock(
            runState = viewModel.runState,
            coachingMessage = coachingMessage,
            onStart = { viewModel.startCountdown(onStartGps) },
            onPause = { viewModel.pauseRunning() },
            onResume = { viewModel.resumeRunning() },
            onStop = {
                viewModel.stopRunning()
                onStopGps()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = AivySpace.Page, vertical = AivySpace.Xl),
        )

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

    if (showCourseSheet) {
        CourseActionSheet(
            onDismiss = { showCourseSheet = false },
            onOpenAllCourses = {
                showCourseSheet = false
                viewModel.showAllCoursesDialog = true
            },
            onOpenSavedCourses = {
                showCourseSheet = false
                viewModel.showSavedCoursesDialog = true
            },
        )
    }

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

@Composable
private fun LiveRunDashboard(
    viewModel: RunningViewModel,
    coachingMessage: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = AivyColors.Surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(AivyRadius.Xl),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(AivySpace.Card)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AivyStatusChip(
                    text = runStateLabel(viewModel.runState),
                    container = runStateContainer(viewModel.runState),
                    content = runStateContent(viewModel.runState),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text("AIVY Run Coach", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
            }

            Spacer(modifier = Modifier.height(AivySpace.Sm))
            Text("거리", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
            Text(
                text = String.format(Locale.US, "%.2f", viewModel.distance),
                fontSize = 62.sp,
                lineHeight = 66.sp,
                fontWeight = FontWeight.Black,
                color = AivyColors.Text1,
            )
            Text("km", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)

            Spacer(modifier = Modifier.height(AivySpace.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                LiveMetricTile("시간", formatSeconds(viewModel.timeElapsed), Modifier.weight(1f))
                LiveMetricTile("현재 페이스", viewModel.calculatePace(), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(AivySpace.Sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                LiveMetricTile("평균 페이스", viewModel.calculatePace(), Modifier.weight(1f))
                LiveMetricTile("칼로리", "${viewModel.calculateCalories()} kcal", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(AivySpace.Md))
            Surface(
                color = AivyColors.AccentLight,
                shape = RoundedCornerShape(AivyRadius.Md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = coachingMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AivyColors.Primary,
                    modifier = Modifier.padding(horizontal = AivySpace.Md, vertical = AivySpace.Sm),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LiveMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = AivyColors.BackgroundAlt,
        shape = RoundedCornerShape(AivyRadius.Md),
    ) {
        Column(modifier = Modifier.padding(AivySpace.Sm)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1, maxLines = 1)
        }
    }
}

@Composable
private fun RunControlDock(
    runState: RunState,
    coachingMessage: String,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AivyColors.Surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(AivyRadius.Xl),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(AivySpace.Card)) {
            Text("코칭", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
            Text(
                text = coachingMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = AivyColors.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(AivySpace.Md))

            when (runState) {
                RunState.INIT -> {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Primary),
                        shape = RoundedCornerShape(AivyRadius.Lg),
                    ) { Text("러닝 시작", style = MaterialTheme.typography.titleMedium) }
                }
                RunState.RUNNING -> {
                    Button(
                        onClick = onPause,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Warning),
                        shape = RoundedCornerShape(AivyRadius.Lg),
                    ) { Text("일시정지", style = MaterialTheme.typography.titleMedium) }
                }
                RunState.PAUSED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onResume,
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Positive),
                            shape = RoundedCornerShape(AivyRadius.Lg),
                        ) { Text("재개", style = MaterialTheme.typography.titleMedium) }
                        Button(
                            onClick = onStop,
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Danger),
                            shape = RoundedCornerShape(AivyRadius.Lg),
                        ) { Text("종료", style = MaterialTheme.typography.titleMedium) }
                    }
                }
                RunState.STOPPED -> {
                    Button(
                        onClick = onStop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Text3),
                        shape = RoundedCornerShape(AivyRadius.Lg),
                    ) { Text("러닝 완료", style = MaterialTheme.typography.titleMedium) }
                }
                RunState.COUNTDOWN,
                RunState.FINISHED,
                -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseActionSheet(
    onDismiss: () -> Unit,
    onOpenAllCourses: () -> Unit,
    onOpenSavedCourses: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AivySpace.Page, vertical = AivySpace.Sm),
            verticalArrangement = Arrangement.spacedBy(AivySpace.Sm),
        ) {
            Text("코스 불러오기", style = MaterialTheme.typography.titleLarge, color = AivyColors.Primary)
            Text("추천 코스나 저장한 GPX 코스를 지도 위에 불러옵니다.", style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text3)
            Spacer(modifier = Modifier.height(AivySpace.Sm))
            Button(
                onClick = onOpenAllCourses,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Primary),
                shape = RoundedCornerShape(AivyRadius.Lg),
            ) {
                Text("새로운 코스 가져오기", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = onOpenSavedCourses,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(AivyRadius.Lg),
            ) {
                Text("저장한 코스 불러오기", style = MaterialTheme.typography.titleMedium, color = AivyColors.Primary)
            }
            Spacer(modifier = Modifier.height(AivySpace.Xl))
        }
    }
}

@Composable
private fun rememberRunCoachingMessage(viewModel: RunningViewModel): String {
    return when (viewModel.runState) {
        RunState.INIT -> "오늘은 5km 이지런으로 시작하세요. 첫 1km는 천천히 몸을 여세요."
        RunState.COUNTDOWN -> "곧 출발합니다. 어깨 힘을 빼고 시선은 정면에 두세요."
        RunState.RUNNING -> when {
            viewModel.distance < 1.0 -> "초반 페이스를 낮게 유지하세요. 호흡이 안정되면 자연스럽게 올립니다."
            viewModel.distance < 3.0 -> "페이스 안정적입니다. 다음 1km도 같은 리듬을 유지하세요."
            else -> "후반 구간입니다. 보폭보다 케이던스를 일정하게 유지하세요."
        }
        RunState.PAUSED -> "일시정지 중입니다. 호흡이 돌아오면 재개하고, 무리하면 종료하세요."
        RunState.STOPPED -> "러닝이 정지되었습니다. 기록을 저장하려면 종료를 눌러주세요."
        RunState.FINISHED -> "운동 완료. 회복을 위해 5분 정도 천천히 걸어주세요."
    }
}

private fun runStateLabel(runState: RunState): String = when (runState) {
    RunState.INIT -> "준비"
    RunState.COUNTDOWN -> "카운트다운"
    RunState.RUNNING -> "러닝 중"
    RunState.PAUSED -> "일시정지"
    RunState.STOPPED -> "정지"
    RunState.FINISHED -> "완료"
}

private fun runStateContainer(runState: RunState): Color = when (runState) {
    RunState.RUNNING -> AivyColors.PositiveLight
    RunState.PAUSED,
    RunState.COUNTDOWN,
    -> AivyColors.WarningLight
    RunState.STOPPED -> AivyColors.DangerLight
    else -> AivyColors.AccentLight
}

private fun runStateContent(runState: RunState): Color = when (runState) {
    RunState.RUNNING -> AivyColors.Positive
    RunState.PAUSED,
    RunState.COUNTDOWN,
    -> AivyColors.Warning
    RunState.STOPPED -> AivyColors.Danger
    else -> AivyColors.Accent
}

// 다이얼로그 내 개별 코스 항목 UI
@Composable
fun CourseListItem(courseName: String, isSaved: Boolean, onItemClick: () -> Unit, onBookmarkClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onItemClick() }
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
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = AivyColors.Text1)
    }
}

// ==========================================
// 운동 결과 요약 화면
// ==========================================
@Composable
fun WorkoutSummaryScreen(viewModel: RunningViewModel, onGoHome: () -> Unit) {
    val lastSession = viewModel.lastWorkoutRecord
    val distanceText = String.format(Locale.US, "%.2f", viewModel.distance)
    val timeText = formatSeconds(viewModel.timeElapsed)
    val paceText = viewModel.calculatePace()
    val caloriesText = viewModel.calculateCalories().toString()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AivyColors.Background)
            .padding(AivySpace.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AivySpace.Lg),
    ) {
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(AivyColors.PositiveLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AivyColors.Positive, modifier = Modifier.size(34.dp))
            }
            Spacer(modifier = Modifier.height(AivySpace.Md))
            Text("운동 완료", style = MaterialTheme.typography.displayLarge, color = AivyColors.Primary)
            Text(
                text = completionHeadline(viewModel.distance, viewModel.timeElapsed),
                style = MaterialTheme.typography.bodyMedium,
                color = AivyColors.Text3,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                    SummaryMetricCard("거리", distanceText, "km", AivyColors.Primary, Modifier.weight(1f))
                    SummaryMetricCard("시간", timeText, "", AivyColors.Accent, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                    SummaryMetricCard("평균 페이스", paceText, "/km", AivyColors.Positive, Modifier.weight(1f))
                    SummaryMetricCard("칼로리", caloriesText, "kcal", AivyColors.Warning, Modifier.weight(1f))
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AivyColors.Surface,
                shape = RoundedCornerShape(AivyRadius.Xl),
                shadowElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(AivySpace.Card), verticalArrangement = Arrangement.spacedBy(AivySpace.Sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("지난 세션 대비", style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1, modifier = Modifier.weight(1f))
                        AivyStatusChip(compareStatus(viewModel.distance, lastSession), AivyColors.AccentLight, AivyColors.Accent)
                    }

                    if (lastSession == null) {
                        Text("비교할 지난 기록이 없습니다. 이번 기록이 다음 코칭 기준이 됩니다.", style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text3)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                            DiffBadge("거리", String.format(Locale.US, "%+.1fkm", viewModel.distance - lastSession.distance), if (viewModel.distance >= lastSession.distance) AivyColors.Positive else AivyColors.Accent, Modifier.weight(1f))
                            DiffBadge("시간", formatSignedSeconds(viewModel.timeElapsed - lastSession.timeElapsed), if (viewModel.timeElapsed >= lastSession.timeElapsed) AivyColors.Positive else AivyColors.Accent, Modifier.weight(1f))
                            DiffBadge("페이스", paceText, AivyColors.Primary, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AivyColors.Surface,
                shape = RoundedCornerShape(AivyRadius.Xl),
                shadowElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(AivySpace.Card)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("스플릿 분석", style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1)
                            Text("막대가 길수록 시간이 오래 걸린 구간입니다.", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
                        }
                        AivyStatusChip("${viewModel.kmSplits.size}개 구간", AivyColors.BackgroundAlt, AivyColors.Text3)
                    }
                    Spacer(modifier = Modifier.height(AivySpace.Md))

                    if (viewModel.kmSplits.isEmpty()) {
                        Text("1km 미만의 기록입니다.", style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text3)
                    } else {
                        val maxTime = viewModel.kmSplits.maxOrNull() ?: 1L
                        viewModel.kmSplits.forEachIndexed { index, timeSec ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}km", fontSize = 14.sp, modifier = Modifier.width(40.dp))

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AivyColors.AccentLight),
                                ) {
                                    val fraction = (timeSec.toFloat() / maxTime).coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction)
                                            .background(if (timeSec == maxTime) AivyColors.Warning else AivyColors.Accent),
                                    )
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
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm)) {
                Button(
                    onClick = onGoHome,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AivyColors.Primary),
                    shape = RoundedCornerShape(AivyRadius.Lg)
                ) { Text("러닝 홈으로", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

                OutlinedButton(
                    onClick = { /* 공유 기능 연동 가능 */ },
                    enabled = false,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(AivyRadius.Lg)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("공유 준비 중", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SummaryMetricCard(label: String, value: String, unit: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(118.dp),
        color = AivyColors.Surface,
        shape = RoundedCornerShape(AivyRadius.Lg),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(AivySpace.Md),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1)
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
                }
            }
        }
    }
}

@Composable
fun DiffBadge(label: String, diffStr: String, diffColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(AivyColors.BackgroundAlt, RoundedCornerShape(AivyRadius.Md))
            .padding(horizontal = AivySpace.Sm, vertical = AivySpace.Sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
        Text(text = diffStr, style = MaterialTheme.typography.bodyMedium, color = diffColor, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun completionHeadline(distance: Double, seconds: Long): String {
    if (distance <= 0.0 || seconds <= 0L) return "첫 기준 기록이 저장되었습니다."
    return "${String.format(Locale.US, "%.1f", distance)}km를 완료했습니다. 다음 러닝은 같은 리듬으로 시작하세요."
}

private fun compareStatus(distance: Double, lastSession: WorkoutRecordEntity?): String {
    if (lastSession == null) return "기준 생성"
    return if (distance >= lastSession.distance) "거리 증가" else "회복 러닝"
}

private fun formatSignedSeconds(seconds: Long): String {
    val sign = if (seconds >= 0) "+" else "-"
    val absSeconds = kotlin.math.abs(seconds)
    val minutes = absSeconds / 60
    val rest = absSeconds % 60
    return "$sign${minutes}:${rest.toString().padStart(2, '0')}"
}

// 초 단위를 시:분:초 포맷으로 변환하는 함수
fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
