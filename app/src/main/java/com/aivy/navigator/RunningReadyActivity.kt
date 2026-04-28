package com.aivy.navigator

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*

// 데이터베이스 관련 패키지 임포트
import com.aivy.navigator.data.database.AppDatabase
import com.aivy.navigator.data.database.WorkoutRecordEntity

// 러닝 준비 화면의 데이터 상태 관리
class RunningReadyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val runningDao = db.runningDao()

    // DB에서 모든 러닝 기록을 최신순으로 가져오는 플로우
    val allWorkouts = runningDao.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

// 메인 액티비티
class RunningReadyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: RunningReadyViewModel = viewModel()
            RunningReadyScreen(viewModel = viewModel, onBackClick = { finish() })
        }
    }
}

// 메인 화면 UI 구성 컴포넌트
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningReadyScreen(viewModel: RunningReadyViewModel, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val workouts by viewModel.allWorkouts.collectAsState()

    // 전체 누적 기록 및 통계 데이터 산출 (추후 월별 데이터 필터링 로직 추가 필요)
    val totalDistance = workouts.sumOf { it.distance }
    val workoutCount = workouts.size
    val totalTimeSeconds = workouts.sumOf { it.timeElapsed }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
    ) {
        // 상단 네비게이션 앱바
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

        // 메인 콘텐츠 스크롤 영역
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

            WorkoutHistorySection(workouts)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// 하위 UI 컴포넌트
// 오늘의 워크아웃 및 시작 버튼 카드
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

// 월별 통계 수치 요약 로우
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

// 개별 통계 수치 표시 박스
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

// 날씨 정보 표시
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

            // TODO: 크롤링으로 실시간 정보 가져오기
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedBadge("🌡️ 기온 14°C")
                OutlinedBadge("🌫️ 미세먼지 좋음")
                OutlinedBadge("💧 습도 52%")
            }
        }
    }
}

// 전체 러닝 기록 리스트 출력 섹션
@Composable
fun WorkoutHistorySection(workouts: List<WorkoutRecordEntity>) {
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { /* 상세 기록 화면 연동 필요 */ },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dateStr = SimpleDateFormat("MM/dd (E) HH:mm", Locale.KOREAN).format(Date(record.timestamp))
                        val mins = record.timeElapsed / 60
                        val secs = record.timeElapsed % 60
                        val timeStr = String.format("%02d:%02d", mins, secs)

                        Text(dateStr, fontSize = 14.sp, color = Color.DarkGray)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(String.format("%.1fkm", record.distance), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(timeStr, fontSize = 16.sp, color = Color.Gray)
                            Text(record.paceStr, fontSize = 16.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 상태 텍스트용 채워진 뱃지 UI 컴포넌트
@Composable
fun BadgeUI(text: String, bgColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 12.sp, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

// 테두리 형태의 뱃지 UI 컴포넌트
@Composable
fun OutlinedBadge(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, color = Color.DarkGray)
    }
}