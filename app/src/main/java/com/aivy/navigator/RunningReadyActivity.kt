package com.aivy.navigator

import android.app.Application
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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

// 러닝 준비 화면의 데이터 상태 관리
class RunningReadyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val runningDao = db.runningDao()

    // DB에서 모든 러닝 기록을 최신순으로 가져오는 플로우
    val allWorkouts = runningDao.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteWorkout(record: WorkoutRecordEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runningDao.deleteWorkout(record)
        }
    }
}

// 메인 액티비티
class RunningReadyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: RunningReadyViewModel = viewModel()
            // 상세 화면으로 이동하기 위한 상태 변수
            var selectedRecord by remember { mutableStateOf<WorkoutRecordEntity?>(null) }

            if (selectedRecord != null) {
                // 뒤로가기 버튼
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

// 메인 화면 UI 구성 컴포넌트
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningReadyScreen(
    viewModel: RunningReadyViewModel,
    onBackClick: () -> Unit,
    onRecordClick: (WorkoutRecordEntity) -> Unit
) {
    val context = LocalContext.current
    val workouts by viewModel.allWorkouts.collectAsState()

    val totalDistance = workouts.sumOf { it.distance }
    val workoutCount = workouts.size
    val totalTimeSeconds = workouts.sumOf { it.timeElapsed }

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
                        modifier = Modifier.clickable { onBackClick() }.padding(end = 12.dp)
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

            MonthlyStatsRow(totalDistance, workoutCount, totalTimeSeconds)

            EnvironmentCard()

            WorkoutHistorySection(workouts, viewModel, onRecordClick)

            Spacer(modifier = Modifier.height(32.dp))
        }
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
fun MonthlyStatsRow(distance: Double, count: Int, timeSec: Long) {
    val hours = timeSec / 3600
    val minutes = (timeSec % 3600) / 60

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatBox("이번 달 거리", String.format("%.1fkm", distance), Modifier.weight(1f))
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
fun EnvironmentCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("러닝 환경", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                BadgeUI("실외 러닝 적합", Color(0xFFE3F2FD), Color(0xFF1976D2))
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedBadge("🌡️ 기온 14°C")
                OutlinedBadge("🌫️ 미세먼지 좋음")
                OutlinedBadge("💧 습도 52%")
            }
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
                        .clickable { onRecordClick(record) }, // 클릭 시 상세 화면 이동
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

                        // 더보기 메뉴 (삭제 기능)
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
    // 날짜 포맷
    val fullDateStr = SimpleDateFormat("yyyy. M. d. - HH:mm", Locale.KOREAN).format(Date(record.timestamp))
    // 요일 포맷
    val amPm = SimpleDateFormat("a", Locale.KOREAN).format(Date(record.timestamp))
    val dayStr = SimpleDateFormat("EEEE", Locale.KOREAN).format(Date(record.timestamp))
    val titleStr = "$dayStr $amPm 러닝"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 상단 뒤로가기
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
            // 타이틀 영역
            Text(fullDateStr, color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(titleStr, fontSize = 26.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(32.dp))

            // 메인 거리 데이터
            Text(
                text = String.format("%.2f", record.distance),
                fontSize = 90.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-2).sp
            )
            Text("킬로미터", fontSize = 16.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(40.dp))

            // 요약 수치 영역 (페이스, 시간, 칼로리)
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

            // 스플릿 데이터
            Text("구간", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text("Km", modifier = Modifier.weight(1f), color = Color.Gray, fontSize = 14.sp)
                Text("평균 페이스", modifier = Modifier.weight(2f), color = Color.Gray, fontSize = 14.sp)
            }

            // CSV로 저장된 스플릿 타임 파싱 및 출력
            val splits = record.splitsCsv.split(",").filter { it.isNotBlank() }
            if (splits.isEmpty()) {
                Text("구간 데이터가 없습니다.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                splits.forEachIndexed { index, splitTimeStr ->
                    val splitSecs = splitTimeStr.toLongOrNull() ?: 0L
                    val splitMins = splitSecs / 60
                    val splitRemainSecs = splitSecs % 60
                    val splitPace = String.format("%d'%02d\"", splitMins, splitRemainSecs)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.weight(2f)) {
                            Text(
                                text = splitPace,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp)) // 하단 여백
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

// 뱃지 UI 컴포넌트들
@Composable
fun BadgeUI(text: String, bgColor: Color, textColor: Color) {
    Box(modifier = Modifier.background(bgColor, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 12.sp, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OutlinedBadge(text: String) {
    Box(modifier = Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, color = Color.DarkGray)
    }
}