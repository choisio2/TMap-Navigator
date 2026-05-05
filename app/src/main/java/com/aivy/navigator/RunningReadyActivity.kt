package com.aivy.navigator

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*

import com.aivy.navigator.data.database.AppDatabase
import com.aivy.navigator.data.database.WorkoutRecordEntity

class RunningReadyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val runningDao = db.runningDao()

    val allWorkouts = runningDao.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteWorkout(record: WorkoutRecordEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runningDao.deleteWorkout(record)
        }
    }
}

class RunningReadyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: RunningReadyViewModel = viewModel()
            var selectedRecord by remember { mutableStateOf<WorkoutRecordEntity?>(null) }

            if (selectedRecord != null) {
                BackHandler { selectedRecord = null }
                WorkoutDetailScreen(
                    record = selectedRecord!!,
                    onBackClick = { selectedRecord = null }
                )
            } else {
                RunningReadyScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onRecordClick = { record -> selectedRecord = record }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningReadyScreen(
    viewModel: RunningReadyViewModel,
    onBackClick: () -> Unit,
    onRecordClick: (WorkoutRecordEntity) -> Unit
) {
    val context = LocalContext.current
    val workouts by viewModel.allWorkouts.collectAsState()

    // 현재 연도와 월 가져오기
    val currentCalendar = Calendar.getInstance()
    var selectedYear by remember { mutableStateOf(currentCalendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(currentCalendar.get(Calendar.MONTH) + 1) }

    // 드롭다운 메뉴 열림/닫힘 상태
    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }

    // 사용자가 운동했던 년/월 목록만 추출해서 동적 드롭다운 리스트 생성
    val availableYears = remember(workouts) {
        val years = workouts.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR)
        }.distinct().sortedDescending()
        if (years.isEmpty()) listOf(currentCalendar.get(Calendar.YEAR)) else years
    }

    val availableMonths = remember(workouts, selectedYear) {
        val months = workouts.filter {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR) == selectedYear
        }.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.MONTH) + 1
        }.distinct().sortedDescending()

        if (months.isEmpty()) listOf(currentCalendar.get(Calendar.MONTH) + 1) else months
    }

    LaunchedEffect(selectedYear, availableMonths) {
        if (!availableMonths.contains(selectedMonth) && availableMonths.isNotEmpty()) {
            selectedMonth = availableMonths.first()
        }
    }

    // 선택된 연도와 월에 맞게 데이터 필터링
    val filteredWorkouts by remember {
        derivedStateOf {
            workouts.filter { record ->
                val cal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                cal.get(Calendar.YEAR) == selectedYear && (cal.get(Calendar.MONTH) + 1) == selectedMonth
            }
        }
    }

    // 필터링된 데이터들만 계산
    val totalDistance = filteredWorkouts.sumOf { it.distance }
    val workoutCount = filteredWorkouts.size
    val totalTimeSeconds = filteredWorkouts.sumOf { it.timeElapsed }

    // 키 몸무게 정보 저장 관리
    var showProfileDialog by remember { mutableStateOf(false) }
    val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    var userHeight by remember { mutableStateOf(sharedPref.getFloat("user_height", 170f).toString()) }
    var userWeight by remember { mutableStateOf(sharedPref.getFloat("user_weight", 65f).toString()) }

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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFE8F5E9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🚶", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("운동", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            },
            actions = {
                // 우측 상단 내 키/몸무게 수정 아이콘 추가
                IconButton(onClick = { showProfileDialog = true }) {
                    Icon(Icons.Default.Person, contentDescription = "프로필 수정", tint = Color.Black)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF7F8FA)),
            navigationIcon = {}
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            TodayWorkoutCard(onStartClick = {
                val intent = Intent(context, RunningActivity::class.java)
                context.startActivity(intent)
            })

            //드롭다운 ui
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { yearExpanded = true }
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("${selectedYear}년", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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

                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { monthExpanded = true }
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("${selectedMonth}월", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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
            }

            MonthlyStatsRow(totalDistance, workoutCount, totalTimeSeconds, selectedMonth)

            WorkoutHistorySection(filteredWorkouts, viewModel, onRecordClick)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 키/몸무게를 수정할 수 있는 다이얼로그
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("프로필 설정", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = userHeight,
                        onValueChange = { userHeight = it },
                        label = { Text("키 (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = userWeight,
                        onValueChange = { userWeight = it },
                        label = { Text("몸무게 (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    sharedPref.edit()
                        .putFloat("user_height", userHeight.toFloatOrNull() ?: 170f)
                        .putFloat("user_weight", userWeight.toFloatOrNull() ?: 65f)
                        .apply()
                    showProfileDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))) {
                    Text("저장", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) { Text("취소", color = Color.Gray) }
            },
            containerColor = Color.White
        )
    }
}

@Composable
fun TodayWorkoutCard(onStartClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEDF2F7)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("오늘의 워크아웃", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "오늘의 러닝을 시작해볼까요?",
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF102841)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("러닝 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MonthlyStatsRow(distance: Double, count: Int, timeSec: Long, selectedMonth: Int) {
    val hours = timeSec / 3600
    val minutes = (timeSec % 3600) / 60

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatBox("${selectedMonth}월 총 거리", String.format("%.1fkm", distance), Modifier.weight(1f))
        StatBox("운동 횟수", "${count}회", Modifier.weight(1f))
        StatBox("총 시간", "${hours}h ${minutes}m", Modifier.weight(1f))
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun WorkoutHistorySection(
    workouts: List<WorkoutRecordEntity>,
    viewModel: RunningReadyViewModel,
    onRecordClick: (WorkoutRecordEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "전체 러닝 기록",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        if (workouts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "아직 저장된 러닝 기록이 없습니다.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            workouts.forEach { record ->
                var showMenu by remember { mutableStateOf(false) }

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
                            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dateStr = SimpleDateFormat("MM/dd (E) HH:mm", Locale.KOREAN).format(Date(record.timestamp))
                        val mins = record.timeElapsed / 60
                        val secs = record.timeElapsed % 60
                        val timeStr = String.format("%02d:%02d", mins, secs)

                        Column(modifier = Modifier.weight(1f)) {
                            Text(dateStr, fontSize = 14.sp, color = Color.DarkGray)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(String.format("%.2f km", record.distance), fontSize = 24.sp, fontWeight = FontWeight.Black)
                                Text(timeStr, fontSize = 18.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                                Text(record.paceStr, fontSize = 18.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                            }
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "더보기", tint = Color.Gray)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("삭제하기", color = Color.Red) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.deleteWorkout(record)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(record: WorkoutRecordEntity, onBackClick: () -> Unit) {
    val fullDateStr = SimpleDateFormat("yyyy. M. d. - HH:mm", Locale.KOREAN).format(Date(record.timestamp))
    val amPm = SimpleDateFormat("a", Locale.KOREAN).format(Date(record.timestamp))
    val dayStr = SimpleDateFormat("EEEE", Locale.KOREAN).format(Date(record.timestamp))
    val titleStr = "$dayStr $amPm 러닝"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(fullDateStr, color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(titleStr, fontSize = 26.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = String.format("%.2f", record.distance),
                fontSize = 90.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-2).sp
            )
            Text("킬로미터", fontSize = 16.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(40.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val mins = record.timeElapsed / 60
                val secs = record.timeElapsed % 60
                val timeStr = String.format("%02d:%02d", mins, secs)

                DetailStatItem("페이스", record.paceStr)
                DetailStatItem("시간", timeStr)
                DetailStatItem("칼로리", "${record.calories}")
            }

            Spacer(modifier = Modifier.height(40.dp))
            HorizontalDivider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(32.dp))

            Text("구간", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)) {
                Text("Km", modifier = Modifier.weight(0.5f), color = Color.Gray, fontSize = 14.sp)
                Text("평균 페이스", modifier = Modifier.weight(2.5f), color = Color.Gray, fontSize = 14.sp)
            }

            val splits = record.splitsCsv.split(",").filter { it.isNotBlank() }

            if (splits.isEmpty()) {
                Text("구간 데이터가 없습니다.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                val paceDataList = splits.mapIndexed { index, splitTimeStr ->
                    val splitSecs = splitTimeStr.toLongOrNull() ?: 0L
                    val remainder = record.distance - index
                    val isLast = index == splits.size - 1

                    // 마지막 구간이 1km 미만일 경우 자투리 거리를 소수점 둘째 자리까지 추출
                    val splitDist = if (isLast && remainder > 0.0) {
                        Math.round(remainder * 100) / 100.0
                    } else {
                        1.0
                    }

                    val paceSecsPerKm = if (splitDist > 0) (splitSecs / splitDist).toLong() else 0L
                    Pair(splitDist, paceSecsPerKm)
                }

                // 페이스 막대 그래프 차이 극대화
                val maxPace = paceDataList.maxOfOrNull { it.second } ?: 1L
                val minPace = paceDataList.minOfOrNull { it.second } ?: 0L
                val paceRange = (maxPace - minPace).coerceAtLeast(1L)

                paceDataList.forEachIndexed { index, (splitDist, paceSecs) ->
                    val splitMins = paceSecs / 60
                    val splitRemainSecs = paceSecs % 60
                    val splitPaceStr = String.format("%d'%02d\"", splitMins, splitRemainSecs)

                    // 거리가 1km 미만이어도 소수점으로 출력
                    val distanceLabel = if (splitDist < 1.0) String.format("%.2f", splitDist) else "${index + 1}"

                    // 가장 빠른 페이스라도 최소 20%의 바 길이를 갖도록 보정 (0.2f + 0.8f * 비율)
                    val fraction = 0.2f + 0.8f * ((paceSecs - minPace).toFloat() / paceRange.toFloat())

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(distanceLabel, modifier = Modifier.weight(0.5f), fontSize = 18.sp, fontWeight = FontWeight.Bold)

                        Box(modifier = Modifier.weight(2.5f), contentAlignment = Alignment.CenterStart) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction) // 동적으로 계산된 비율 적용
                                    .height(36.dp)
                                    .background(Color(0xFFF0F0F0), RoundedCornerShape(4.dp))
                            )
                            Text(
                                text = splitPaceStr,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun DetailStatItem(label: String, value: String) {
    Column {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 14.sp, color = Color.Gray)
    }
}