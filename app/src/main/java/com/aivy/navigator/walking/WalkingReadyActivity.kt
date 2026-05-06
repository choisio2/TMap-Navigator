package com.aivy.navigator.walking

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aivy.navigator.database.AppDatabase
import com.aivy.navigator.database.entity.DailyStepEntity
import com.aivy.navigator.database.entity.WalkingRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// ViewModel
// ==========================================
class WalkingReadyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val walkingDao = db.walkingDao()
    private val dailyStepDao = db.dailyStepDao()

    // 상세 운동 기록 리스트
    val allWalks = walkingDao.getAllWalks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allDailySteps = dailyStepDao.getAllDailySteps()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedDateSteps = MutableStateFlow(0)
    val selectedDateSteps: StateFlow<Int> = _selectedDateSteps

    fun deleteWalk(record: WalkingRecordEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // walkingDao.deleteWalk(record)
        }
    }

    fun loadStepsForDate(year: Int, month: Int, day: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = dailyStepDao.getDailyStep(year, month, day)
            _selectedDateSteps.value = record?.steps ?: 0
        }
    }
}

// ==========================================
// Activity
// ==========================================
class WalkingReadyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: WalkingReadyViewModel = viewModel()
            var selectedRecord by remember { mutableStateOf<WalkingRecordEntity?>(null) }

            if (selectedRecord != null) {
                BackHandler { selectedRecord = null }
                WalkingDetailScreen(
                    record = selectedRecord!!,
                    onBackClick = { selectedRecord = null }
                )
            } else {
                WalkingReadyScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onRecordClick = { record -> selectedRecord = record }
                )
            }
        }
    }
}

// ==========================================
// 메인 대시보드 화면
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkingReadyScreen(
    viewModel: WalkingReadyViewModel,
    onBackClick: () -> Unit,
    onRecordClick: (WalkingRecordEntity) -> Unit
) {
    val context = LocalContext.current

    val walks by viewModel.allWalks.collectAsState()
    val dailyStepsList by viewModel.allDailySteps.collectAsState() // 막대그래프를 위한 전체 일별 기록

    // 백그라운드 만보기 데이터 연동
    val prefs = context.getSharedPreferences("pedometer_prefs", Context.MODE_PRIVATE)
    var todaySteps by remember { mutableIntStateOf(prefs.getInt("today_total_steps", 0)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "today_total_steps") todaySteps = sharedPreferences.getInt(key, 0)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var weekOffset by remember { mutableIntStateOf(0) }

    // 드롭다운 및 필터링 상태 변수
    val currentCalendar = Calendar.getInstance()
    var selectedYear by remember { mutableStateOf(currentCalendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(currentCalendar.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableStateOf(currentCalendar.get(Calendar.DAY_OF_MONTH)) }

    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    var dayExpanded by remember { mutableStateOf(false) }

    val availableYears = remember(walks) {
        val years = walks.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR)
        }.distinct().sortedDescending()
        if (years.isEmpty()) listOf(currentCalendar.get(Calendar.YEAR)) else years
    }

    val availableMonths = remember(walks, selectedYear) {
        val months = walks.filter {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR) == selectedYear
        }.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.MONTH) + 1
        }.distinct().sortedDescending()
        if (months.isEmpty()) listOf(currentCalendar.get(Calendar.MONTH) + 1) else months
    }

    val availableDays = remember(walks, selectedYear, selectedMonth) {
        val days = walks.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            cal.get(Calendar.YEAR) == selectedYear && (cal.get(Calendar.MONTH) + 1) == selectedMonth
        }.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.DAY_OF_MONTH)
        }.distinct().sortedDescending()
        if (days.isEmpty()) listOf(currentCalendar.get(Calendar.DAY_OF_MONTH)) else days
    }

    LaunchedEffect(selectedYear, selectedMonth, availableDays) {
        if (!availableMonths.contains(selectedMonth) && availableMonths.isNotEmpty()) {
            selectedMonth = availableMonths.first()
        }
        if (!availableDays.contains(selectedDay) && availableDays.isNotEmpty()) {
            selectedDay = availableDays.first()
        }
    }

    LaunchedEffect(selectedYear, selectedMonth, selectedDay) {
        viewModel.loadStepsForDate(selectedYear, selectedMonth, selectedDay)
    }

    val selectedDateDbSteps by viewModel.selectedDateSteps.collectAsState()

    val isToday = (selectedYear == currentCalendar.get(Calendar.YEAR) &&
            selectedMonth == currentCalendar.get(Calendar.MONTH) + 1 &&
            selectedDay == currentCalendar.get(Calendar.DAY_OF_MONTH))
    val displayDailyTotalSteps = if (isToday) todaySteps else selectedDateDbSteps

    val filteredWalks by remember {
        derivedStateOf {
            walks.filter { record ->
                val cal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                cal.get(Calendar.YEAR) == selectedYear &&
                        (cal.get(Calendar.MONTH) + 1) == selectedMonth &&
                        cal.get(Calendar.DAY_OF_MONTH) == selectedDay
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기",
                        modifier = Modifier
                            .clickable { onBackClick() }
                            .padding(end = 12.dp)
                    )
                    Text("워킹", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            navigationIcon = {}
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 오늘 누적
            TodayWalkingCard(
                todaySteps = todaySteps,
                onStartClick = {
                    val intent = Intent(context, WalkingActivity::class.java)
                    context.startActivity(intent)
                }
            )

            WeeklyStepChartSection(
                dailyWalks = dailyStepsList,
                todaySteps = todaySteps,
                weekOffset = weekOffset,
                onWeekChange = { weekOffset += it }
            )

            // 년/월/일 필터링 드롭다운 UI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 연도 드롭다운
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { yearExpanded = true }
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Text("${selectedYear}년", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "연도 선택", tint = Color.DarkGray)
                    }

                    DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }, modifier = Modifier.background(Color.White)) {
                        availableYears.forEach { year ->
                            DropdownMenuItem(
                                text = { Text("${year}년", fontWeight = if (year == selectedYear) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    selectedYear = year
                                    yearExpanded = false
                                }
                            )
                        }
                    }
                }

                // 월 드롭다운
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { monthExpanded = true }
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Text("${selectedMonth}월", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "월 선택", tint = Color.DarkGray)
                    }

                    DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }, modifier = Modifier.background(Color.White)) {
                        availableMonths.forEach { month ->
                            DropdownMenuItem(
                                text = { Text("${month}월", fontWeight = if (month == selectedMonth) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    selectedMonth = month
                                    monthExpanded = false
                                }
                            )
                        }
                    }
                }

                // 일 드롭다운
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dayExpanded = true }
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Text("${selectedDay}일", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "일 선택", tint = Color.DarkGray)
                    }

                    DropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }, modifier = Modifier.background(Color.White)) {
                        availableDays.forEach { day ->
                            DropdownMenuItem(
                                text = { Text("${day}일", fontWeight = if (day == selectedDay) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    selectedDay = day
                                    dayExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedYear}년 ${selectedMonth}월 ${selectedDay}일 누적 걸음", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    Text(String.format("%,d 걸음", displayDailyTotalSteps), fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF1565C0))
                }
            }

            // 필터링된 운동 상세 기록 리스트
            WalkingHistorySection(walks = filteredWalks, onRecordClick = onRecordClick)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// 누적 걸음수를 포함한 시작 카드
@Composable
fun TodayWalkingCard(todaySteps: Int, onStartClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("오늘 하루 누적 걸음", fontSize = 14.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format("%,d", todaySteps),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "걸음",
                    fontSize = 18.sp,
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("걷기 운동 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ==========================================
// 주간 걸음 수 막대그래프 컴포넌트
// ==========================================
@Composable
fun WeeklyStepChartSection(
    dailyWalks: List<DailyStepEntity>,
    todaySteps: Int,
    weekOffset: Int,
    onWeekChange: (Int) -> Unit
) {
    val weeklySteps = IntArray(7) { 0 }
    val days = listOf("월", "화", "수", "목", "금", "토", "일")

    val startCalendar = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        add(Calendar.WEEK_OF_YEAR, weekOffset)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    val startDateStr = SimpleDateFormat("MM.dd", Locale.getDefault()).format(startCalendar.time)

    val endCalendar = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        add(Calendar.WEEK_OF_YEAR, weekOffset)
        set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    }
    val endDateStr = SimpleDateFormat("MM.dd", Locale.getDefault()).format(endCalendar.time)

    for (i in 0..6) {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            add(Calendar.WEEK_OF_YEAR, weekOffset)
            set(Calendar.DAY_OF_WEEK, if (i == 6) Calendar.SUNDAY else i + 2)
        }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)

        val record = dailyWalks.find { it.year == y && it.month == m && it.day == d }
        weeklySteps[i] = record?.steps ?: 0
    }

    if (weekOffset == 0) {
        val currentCal = Calendar.getInstance()
        val todayDayOfWeek = currentCal.get(Calendar.DAY_OF_WEEK)
        val todayIndex = if (todayDayOfWeek == Calendar.SUNDAY) 6 else todayDayOfWeek - 2

        if (todaySteps > weeklySteps[todayIndex]) {
            weeklySteps[todayIndex] = todaySteps
        }
    }

    val totalStepsThisWeek = weeklySteps.sum()
    val daysPassed = if (weekOffset < 0) 7 else {
        val todayIndex = if (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 6 else Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2
        todayIndex + 1
    }
    val averageSteps = if (daysPassed > 0) totalStepsThisWeek / daysPassed else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("일주일 동안", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("하루 평균 ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(String.format("%,d걸음", averageSteps), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF00838F))
                Text(" 걸었어요", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onWeekChange(-1) },
                    modifier = Modifier.background(Color(0xFF00838F), RoundedCornerShape(4.dp)).size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "이전 주", tint = Color.White)
                }

                Text("$startDateStr ~ $endDateStr", fontSize = 16.sp, fontWeight = FontWeight.Bold)

                IconButton(
                    onClick = { onWeekChange(1) },
                    modifier = Modifier.background(Color(0xFF00838F), RoundedCornerShape(4.dp)).size(32.dp),
                    enabled = weekOffset < 0
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "다음 주", tint = if(weekOffset < 0) Color.White else Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(Color(0xFF00838F), CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text("걸음 수", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text("---- 8000걸음", fontSize = 12.sp, color = Color.LightGray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            val targetSteps = 8000
            val maxSteps = maxOf(targetSteps, weeklySteps.maxOrNull() ?: 0)

            Column(modifier = Modifier.fillMaxWidth()) {

                Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    val targetFraction = 1f - (targetSteps.toFloat() / maxSteps.toFloat())
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val yPos = size.height * targetFraction
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(0f, yPos),
                            end = Offset(size.width, yPos),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeklySteps.forEach { steps ->
                            val fraction = if (maxSteps > 0) (steps.toFloat() / maxSteps.toFloat()) else 0f
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .fillMaxHeight(fraction)
                                    .background(Color(0xFF00838F), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    days.forEach { day ->
                        Text(
                            text = day,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.width(16.dp), // 막대기와 동일한 너비를 주어 정확히 가운데 정렬
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 걷기 기록 리스트 컴포넌트
// ==========================================
@Composable
fun WalkingHistorySection(walks: List<WalkingRecordEntity>, onRecordClick: (WalkingRecordEntity) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "걷기 상세 기록",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        if (walks.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "해당 일에 저장된 걷기 상세 기록이 없습니다.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            walks.forEach { record ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onRecordClick(record) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dateStr = SimpleDateFormat("MM/dd (E) HH:mm", Locale.KOREAN).format(Date(record.timestamp))
                        val mins = record.totalTimeElapsed / 60
                        val secs = record.totalTimeElapsed % 60
                        val timeStr = String.format("%02d:%02d", mins, secs)

                        Column {
                            Text(dateStr, fontSize = 14.sp, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(String.format("%.2f km", record.totalDistance), fontSize = 24.sp, fontWeight = FontWeight.Black)
                                Text("${record.steps} 걸음", fontSize = 18.sp, color = Color(0xFF00838F), fontWeight = FontWeight.Bold)
                                Text(timeStr, fontSize = 18.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 걷기 기록 상세 화면
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkingDetailScreen(record: WalkingRecordEntity, onBackClick: () -> Unit) {
    val fullDateStr = SimpleDateFormat("yyyy. M. d. - HH:mm", Locale.KOREAN).format(Date(record.timestamp))
    val dayStr = SimpleDateFormat("EEEE", Locale.KOREAN).format(Date(record.timestamp))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        TopAppBar(
            title = { Text("걷기 상세 기록", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FA)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                val mins = record.totalTimeElapsed / 60
                val secs = record.totalTimeElapsed % 60
                val timeStr = String.format("%02d:%02d", mins, secs)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    DetailStatBox("거리", String.format("%.2f", record.totalDistance), "km", Modifier.weight(1f))
                    DetailStatBox("시간", timeStr, "", Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(40.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    DetailStatBox("페이스", record.paceStr, "", Modifier.weight(1f))
                    DetailStatBox("걸음 수", String.format("%,d", record.steps), "걸음", Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(40.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    DetailStatBox("칼로리", "${record.calories}", "kcal", Modifier.weight(1f))
                    DetailStatBox("평균 속도", String.format("%.1f", record.averageSpeed), "km/h", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DetailStatBox(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, fontSize = 16.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF212121))

            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}