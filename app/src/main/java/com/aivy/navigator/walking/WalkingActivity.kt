package com.aivy.navigator.walking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.aivy.navigator.BuildConfig
import com.aivy.navigator.R
import com.aivy.navigator.database.AppDatabase
import com.aivy.navigator.database.entity.WalkingRecordEntity
import com.google.android.gms.location.*
import com.skt.Tmap.TMapMarkerItem
import com.skt.Tmap.TMapPoint
import com.skt.Tmap.TMapPolyLine
import com.skt.Tmap.TMapView
import kotlinx.coroutines.*
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor
import android.content.BroadcastReceiver
import android.content.IntentFilter

// ==========================================
// ViewModel
// ==========================================

enum class WalkState { INIT, COUNTDOWN, WALKING, PAUSED, FINISHED }

class WalkingViewModel(application: Application) : AndroidViewModel(application) {
    var walkState by mutableStateOf(WalkState.INIT)
    var countdown by mutableStateOf(3)
    var timeElapsed by mutableStateOf(0L)
    var distance by mutableStateOf(0.0)

    // 걸음 수 관련 상태
    var steps by mutableStateOf(0)
    private var lastSensorSteps = -1

    val pathPoints = mutableStateListOf<TMapPoint>()
    private var lastLocation: Location? = null
    private var timerJob: Job? = null

    private val db = AppDatabase.getDatabase(application)
    private val walkingDao = db.walkingDao()

    fun startCountdown(onStart: () -> Unit) {
        walkState = WalkState.COUNTDOWN
        viewModelScope.launch {
            for (i in 3 downTo 1) {
                countdown = i
                delay(1000)
            }
            countdown = 0
            walkState = WalkState.WALKING
            onStart()
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (walkState == WalkState.WALKING) {
                delay(1000)
                timeElapsed++
            }
        }
    }

    fun pauseWalking() {
        walkState = WalkState.PAUSED
        timerJob?.cancel()
    }

    fun resumeWalking() {
        walkState = WalkState.WALKING
        startTimer()
    }

    fun stopWalking() {
        timerJob?.cancel()

        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            if (timeElapsed < 10) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "10초 미만의 기록은 저장되지 않습니다.", Toast.LENGTH_SHORT).show()
                    walkState = WalkState.FINISHED
                }
                return@launch
            }

            // 워킹 엔티티에 저장
            val newRecord = WalkingRecordEntity(
                totalDistance = distance,
                totalTimeElapsed = timeElapsed,
                paceStr = calculatePace(),
                calories = calculateCalories(context),
                steps = steps,
                averageSpeed = calculateAverageSpeed()
            )
            walkingDao.insertWalk(newRecord)

            withContext(Dispatchers.Main) {
                walkState = WalkState.FINISHED
            }
        }
    }


    fun updateLocation(newLoc: Location) {
        if (walkState != WalkState.WALKING) return

        lastLocation?.let { last ->
            val distMeters = last.distanceTo(newLoc)
            if (distMeters >= 3.0) {
                distance += (distMeters / 1000.0)
                pathPoints.add(TMapPoint(newLoc.latitude, newLoc.longitude))
                lastLocation = newLoc
            }
        } ?: run {
            pathPoints.add(TMapPoint(newLoc.latitude, newLoc.longitude))
            lastLocation = newLoc
        }
    }


    fun updateSteps(sensorSteps: Int) {
        // 처음 센서 값을 받아왔을 때 초기화
        if (lastSensorSteps == -1) {
            lastSensorSteps = sensorSteps
        }

        // 이전 센서 값과 현재 센서 값의 차이를 구함
        val delta = sensorSteps - lastSensorSteps
        lastSensorSteps = sensorSteps // 최신 값 갱신

        // 사용자가 실제로 WALKING일 때만 걸음 수를 누적함
        if (walkState == WalkState.WALKING) {
            steps += delta
        }
    }

    fun calculatePace(): String {
        if (distance == 0.0 || timeElapsed == 0L) return "0'00\""
        val totalMinutes = (timeElapsed / 60.0) / distance
        val minutes = totalMinutes.toInt()
        val secs = ((totalMinutes - minutes) * 60).toInt()
        return String.format("%d'%02d\"", minutes, secs)
    }

    fun calculateAverageSpeed(): Double {
        if (timeElapsed == 0L) return 0.0
        val hours = timeElapsed / 3600.0
        return distance / hours
    }

    fun calculateCalories(context: Context): Int {
        if (distance <= 0.0 || timeElapsed <= 0L) return 0
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val weightKg = sharedPref.getFloat("user_weight", 65f).toDouble()
        val durationHour = timeElapsed / 3600.0
        val met = 3.5
        return (met * weightKg * durationHour).roundToInt()
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}

// ==========================================
// Activity
// ==========================================

class WalkingActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var walkUpdateReceiver: BroadcastReceiver

    private var currentLocation: TMapPoint? = null
    private var tMapViewInstance: TMapView? = null
    private var myLocationBaseBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        myLocationBaseBitmap = getBitmapFromVectorDrawable(R.drawable.ic_my_location_dot)

        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (locationGranted) {
                tMapViewInstance?.let { fetchCurrentLocation(it) }
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (permissions[Manifest.permission.ACTIVITY_RECOGNITION] != true) {
                    Toast.makeText(this, "신체 활동 권한이 없어 걸음 수가 측정되지 않습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())

        setContent {
            val viewModel: WalkingViewModel = viewModel()

            // BroadcastReceiver 등록 로직 - 서비스에서 보내는 실시간 데이터 수신
            DisposableEffect(Unit) {
                walkUpdateReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        when (intent?.action) {
                            WalkingService.ACTION_WALK_LOCATION -> {
                                val lat = intent.getDoubleExtra(WalkingService.EXTRA_LATITUDE, 0.0)
                                val lon = intent.getDoubleExtra(WalkingService.EXTRA_LONGITUDE, 0.0)
                                if (lat != 0.0 && lon != 0.0) {
                                    val loc = Location("").apply { latitude = lat; longitude = lon }
                                    currentLocation = TMapPoint(lat, lon)
                                    tMapViewInstance?.let { view ->
                                        view.setCenterPoint(lon, lat)
                                        updateMyLocationMarker(view)
                                    }
                                    viewModel.updateLocation(loc)
                                }
                            }
                            WalkingService.ACTION_WALK_STEPS -> {
                                val steps = intent.getIntExtra(WalkingService.EXTRA_STEPS, -1)
                                if (steps != -1) {
                                    viewModel.updateSteps(steps)
                                }
                            }
                        }
                    }
                }

                val filter = IntentFilter().apply {
                    addAction(WalkingService.ACTION_WALK_LOCATION)
                    addAction(WalkingService.ACTION_WALK_STEPS)
                }
                ContextCompat.registerReceiver(
                    this@WalkingActivity,
                    walkUpdateReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                onDispose {
                    unregisterReceiver(walkUpdateReceiver)
                }
            }

            val tMapView = remember {
                TMapView(this@WalkingActivity).apply {
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

            WalkingScreen(
                viewModel = viewModel,
                tMapView = tMapView,
                onStartGps = { startWalkingService() },
                onStopGps = { stopWalkingService() },
                onMyLocationClick = { fetchCurrentLocation(tMapView) }
            )
        }
    }

    // 포그라운드 서비스 시작/종료
    private fun startWalkingService() {
        val serviceIntent = Intent(this, WalkingService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
    private fun stopWalkingService() {
        val serviceIntent = Intent(this, WalkingService::class.java)
        stopService(serviceIntent)
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation(tMapView: TMapView) {
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                currentLocation = TMapPoint(loc.latitude, loc.longitude)
                tMapView.setCenterPoint(loc.longitude, loc.latitude)
                updateMyLocationMarker(tMapView)
            }
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLocation = TMapPoint(loc.latitude, loc.longitude)
                    tMapView.setCenterPoint(loc.longitude, loc.latitude)
                    updateMyLocationMarker(tMapView)
                }
            }
    }

    private fun updateMyLocationMarker(tMapView: TMapView) {
        val loc = currentLocation ?: return
        tMapView.removeMarkerItem("myLocation")
        val base = myLocationBaseBitmap ?: return

        val marker = TMapMarkerItem().apply {
            tMapPoint = loc
            name = "내 위치"
            icon = base
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
}

// ==========================================
// Compose UI
// ==========================================

@Composable
fun WalkingScreen(
    viewModel: WalkingViewModel,
    tMapView: TMapView,
    onStartGps: () -> Unit,
    onStopGps: () -> Unit,
    onMyLocationClick: () -> Unit
) {
    val context = LocalContext.current

    if (viewModel.walkState == WalkState.FINISHED) {
        WalkingSummaryScreen(
            viewModel = viewModel,
            onGoHome = {
                (context as? ComponentActivity)?.finish()
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { tMapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.removeAllTMapPolyLine()
                if (viewModel.pathPoints.size >= 2) {
                    val myLine = TMapPolyLine().apply {
                        lineColor = AndroidColor.parseColor("#FF4CAF50")
                        lineWidth = 15f
                    }
                    viewModel.pathPoints.forEach { myLine.addLinePoint(it) }
                    view.addTMapPolyLine("walk_path", myLine)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .padding(top = 40.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                WalkDataItem("걸음 수", "${viewModel.steps}", modifier = Modifier.weight(1f))
                WalkDataItem("거리", String.format("%.2f km", viewModel.distance), modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                WalkDataItem("시간", formatSeconds(viewModel.timeElapsed), modifier = Modifier.weight(1f))
                WalkDataItem("페이스", viewModel.calculatePace(), modifier = Modifier.weight(1f))
            }
        }

        FloatingActionButton(
            onClick = onMyLocationClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 120.dp),
            containerColor = Color.White,
            contentColor = Color(0xFF4CAF50),
            shape = CircleShape
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "내 위치", modifier = Modifier.size(28.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            when (viewModel.walkState) {
                WalkState.INIT -> {
                    Button(
                        onClick = { viewModel.startCountdown(onStartGps) },
                        modifier = Modifier.size(width = 150.dp, height = 60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("시작", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                }
                WalkState.WALKING -> {
                    Button(
                        onClick = { viewModel.pauseWalking() },
                        modifier = Modifier.size(width = 150.dp, height = 60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) { Text("일시정지", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                WalkState.PAUSED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { viewModel.resumeWalking() },
                            modifier = Modifier.size(width = 120.dp, height = 60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) { Text("재개", fontSize = 18.sp) }

                        Button(
                            onClick = {
                                viewModel.stopWalking()
                                onStopGps()
                            },
                            modifier = Modifier.size(width = 120.dp, height = 60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                        ) { Text("종료", fontSize = 18.sp) }
                    }
                }
                else -> {}
            }
        }

        if (viewModel.walkState == WalkState.COUNTDOWN) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = viewModel.countdown.toString(), fontSize = 180.sp, color = Color.White, fontWeight = FontWeight.Black)
            }
        }
    }
}

// 상단 4분할 데이터 아이템
@Composable
fun WalkDataItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
    }
}

// ==========================================
// 워킹 완료 요약 화면
// ==========================================
@Composable
fun WalkingSummaryScreen(viewModel: WalkingViewModel, onGoHome: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF7F8FA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier.size(70.dp).background(Color(0xFFE8F5E9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("워킹 완료!", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black)
            Spacer(modifier = Modifier.height(30.dp))
        }

        // 6개 데이터 요약 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // 첫 번째 줄 (거리, 시간, 페이스)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryItem("거리", String.format("%.2f", viewModel.distance), "km")
                        SummaryItem("시간", formatSeconds(viewModel.timeElapsed), "")
                        SummaryItem("페이스", viewModel.calculatePace(), "")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    // 두 번째 줄 (칼로리, 걸음 수, 평균 속도)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryItem("칼로리", viewModel.calculateCalories(context).toString(), "kcal")
                        SummaryItem("걸음 수", viewModel.steps.toString(), "걸음")
                        SummaryItem("평균 속도", String.format("%.1f", viewModel.calculateAverageSpeed()), "km/h")
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }

        item {
            Button(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(12.dp)
            ) { Text("홈으로", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// 요약 화면 데이터 아이템
@Composable
fun SummaryItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp)) {
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        if (unit.isNotEmpty()) {
            Text(text = unit, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

// 시간 포맷 변환 함수
fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}