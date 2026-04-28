package com.aivy.navigator

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aivy.navigator.data.database.AppDatabase
import com.aivy.navigator.data.database.WorkoutRecordEntity
import com.aivy.navigator.ui.components.AivyPanel
import com.aivy.navigator.ui.components.AivySectionLabel
import com.aivy.navigator.ui.components.AivyStatusChip
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace
import com.aivy.navigator.ui.theme.AivyTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class RunningReadyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val runningDao = db.runningDao()

    val allWorkouts = runningDao.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

class RunningReadyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AivyTheme {
                val viewModel: RunningReadyViewModel = viewModel()
                RunningReadyScreen(viewModel = viewModel, onBackClick = { finish() })
            }
        }
    }
}

@Composable
fun RunningReadyScreen(viewModel: RunningReadyViewModel, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val workouts by viewModel.allWorkouts.collectAsState()
    val totalDistance = workouts.sumOf { it.distance }
    val workoutCount = workouts.size
    val totalTimeSeconds = workouts.sumOf { it.timeElapsed }
    val latestWorkout = workouts.firstOrNull()
    val readiness = readinessFrom(latestWorkout)
    var goalKm by rememberSaveable { mutableIntStateOf(5) }
    var goalMinutes by rememberSaveable { mutableIntStateOf(30) }
    var selectedCourse by rememberSaveable { mutableStateOf("자유 러닝") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AivyColors.Background),
        contentPadding = PaddingValues(
            start = AivySpace.Page,
            top = AivySpace.Xl,
            end = AivySpace.Page,
            bottom = AivySpace.Xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(AivySpace.Lg),
    ) {
        item {
            RunningHeader(onBackClick = onBackClick)
        }

        item {
            PrescriptionCard(
                readiness = readiness,
                goalKm = goalKm,
                goalMinutes = goalMinutes,
                selectedCourse = selectedCourse,
                totalDistance = totalDistance,
                onStartClick = {
                    context.startActivity(Intent(context, RunningActivity::class.java))
                },
            )
        }

        item {
            SessionControls(
                goalKm = goalKm,
                goalMinutes = goalMinutes,
                selectedCourse = selectedCourse,
                onGoalCycle = {
                    when (goalKm) {
                        3 -> {
                            goalKm = 5
                            goalMinutes = 30
                        }
                        5 -> {
                            goalKm = 10
                            goalMinutes = 60
                        }
                        else -> {
                            goalKm = 3
                            goalMinutes = 20
                        }
                    }
                },
                onCourseCycle = {
                    selectedCourse = when (selectedCourse) {
                        "자유 러닝" -> "서울 하프마라톤"
                        "서울 하프마라톤" -> "광교 돌고래런"
                        else -> "자유 러닝"
                    }
                },
            )
        }

        item {
            TrainingProgressCard(
                distance = totalDistance,
                count = workoutCount,
                timeSec = totalTimeSeconds,
            )
        }

        item {
            EnvironmentCard()
        }

        item {
            WorkoutHistorySection(workouts)
        }
    }
}

@Composable
private fun RunningHeader(onBackClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = AivyColors.Text3)
        }
        Surface(
            color = AivyColors.PositiveLight,
            shape = RoundedCornerShape(AivyRadius.Md),
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DirectionsRun, contentDescription = null, tint = AivyColors.Positive)
            }
        }
        Spacer(modifier = Modifier.width(AivySpace.Md))
        Column {
            Text("러닝", style = MaterialTheme.typography.displayLarge, color = AivyColors.Primary)
            Text("오늘 몸 상태에 맞춰 가볍게 시작하세요", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
        }
    }
}

@Composable
private fun PrescriptionCard(
    readiness: ReadinessUi,
    goalKm: Int,
    goalMinutes: Int,
    selectedCourse: String,
    totalDistance: Double,
    onStartClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AivyColors.Primary,
        shape = RoundedCornerShape(AivyRadius.Xl),
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(AivySpace.Card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.White.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(54.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("A", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.width(AivySpace.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text("오늘의 러닝 처방", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.68f))
                    Text("이지런 ${goalMinutes}분", style = MaterialTheme.typography.displayLarge, color = Color.White)
                }
                AivyStatusChip(readiness.label, readiness.container, readiness.content)
            }

            Spacer(modifier = Modifier.height(AivySpace.Lg))
            Text(
                text = readiness.reason(totalDistance),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.86f),
            )
            Spacer(modifier = Modifier.height(AivySpace.Md))

            Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                PrescriptionMetric("목표 거리", "${goalKm}km", Modifier.weight(1f))
                PrescriptionMetric("예상 시간", "${goalMinutes}분", Modifier.weight(1f))
                PrescriptionMetric("코스", selectedCourse, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(AivySpace.Lg))
            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AivyColors.Primary),
                shape = RoundedCornerShape(AivyRadius.Lg),
            ) {
                Text("러닝 시작", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PrescriptionMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(76.dp),
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(AivyRadius.Md),
    ) {
        Column(
            modifier = Modifier.padding(AivySpace.Sm),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.58f), maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SessionControls(
    goalKm: Int,
    goalMinutes: Int,
    selectedCourse: String,
    onGoalCycle: () -> Unit,
    onCourseCycle: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
        SecondaryActionCard(
            icon = Icons.Outlined.Tune,
            label = "목표 설정",
            value = "${goalKm}km · ${goalMinutes}분",
            onClick = onGoalCycle,
            modifier = Modifier.weight(1f),
        )
        SecondaryActionCard(
            icon = Icons.Outlined.Route,
            label = "코스 선택",
            value = selectedCourse,
            onClick = onCourseCycle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SecondaryActionCard(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        shape = RoundedCornerShape(AivyRadius.Lg),
        border = BorderStroke(1.dp, AivyColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AivyColors.Surface),
        contentPadding = PaddingValues(AivySpace.Md),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = AivyColors.AccentLight, shape = RoundedCornerShape(AivyRadius.Md), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = AivyColors.Accent, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(AivySpace.Sm))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4, maxLines = 1)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun TrainingProgressCard(distance: Double, count: Int, timeSec: Long) {
    val weeklyTarget = 25.0
    val monthlyTarget = 80.0
    val weekFraction = (distance / weeklyTarget).toFloat().coerceIn(0f, 1f)
    val monthFraction = (distance / monthlyTarget).toFloat().coerceIn(0f, 1f)
    val hours = timeSec / 3600
    val minutes = (timeSec % 3600) / 60

    AivyPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("목표 대비 진행", style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1)
                Text("무리하지 않는 누적 거리 관리", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
            }
            Surface(color = AivyColors.PositiveLight, shape = RoundedCornerShape(AivyRadius.Lg)) {
                Text(
                    text = "${(weekFraction * 100).roundToInt()}%",
                    modifier = Modifier.padding(horizontal = AivySpace.Sm, vertical = AivySpace.Xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = AivyColors.Positive,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        ProgressLine("이번 주", distance, weeklyTarget, weekFraction, AivyColors.Primary)
        ProgressLine("이번 달", distance, monthlyTarget, monthFraction, AivyColors.Positive)
        Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
            SmallStat("운동 횟수", "${count}회", Icons.Outlined.CalendarMonth, Modifier.weight(1f))
            SmallStat("누적 시간", "${hours}h ${minutes}m", Icons.Outlined.Speed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProgressLine(label: String, current: Double, target: Double, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(AivySpace.Xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text2, modifier = Modifier.weight(1f))
            Text(
                text = "${String.format("%.1f", current)}/${target.toInt()}km",
                style = MaterialTheme.typography.bodySmall,
                color = AivyColors.Text4,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = AivyColors.BackgroundAlt,
        )
    }
}

@Composable
private fun SmallStat(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = AivyColors.BackgroundAlt,
        shape = RoundedCornerShape(AivyRadius.Md),
    ) {
        Row(
            modifier = Modifier.padding(AivySpace.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = AivyColors.Text3, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(AivySpace.Xs))
            Column {
                Text(label, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text1)
            }
        }
    }
}

@Composable
fun EnvironmentCard() {
    AivyPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("러닝 환경", style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1, modifier = Modifier.weight(1f))
            AivyStatusChip("실외 러닝 적합", AivyColors.AccentLight, AivyColors.Accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
            OutlinedBadge("기온", "14°C", Modifier.weight(1f))
            OutlinedBadge("미세먼지", "좋음", Modifier.weight(1f))
            OutlinedBadge("습도", "52%", Modifier.weight(1f))
        }
    }
}

@Composable
fun WorkoutHistorySection(workouts: List<WorkoutRecordEntity>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AivySectionLabel("최근 기록")
        Spacer(modifier = Modifier.height(AivySpace.Sm))

        if (workouts.isEmpty()) {
            AivyPanel {
                Text("아직 저장된 러닝 기록이 없습니다.", style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text3)
                Text("첫 기록을 만들면 AIVY가 다음 러닝 강도를 추천합니다.", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
            }
        } else {
            workouts.take(5).forEachIndexed { index, record ->
                WorkoutRecordRow(record = record, previous = workouts.getOrNull(index + 1))
                Spacer(modifier = Modifier.height(AivySpace.Sm))
            }
        }
    }
}

@Composable
private fun WorkoutRecordRow(record: WorkoutRecordEntity, previous: WorkoutRecordEntity?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AivyColors.Surface,
        shape = RoundedCornerShape(AivyRadius.Lg),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = AivySpace.Md, vertical = AivySpace.Md)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dateStr = SimpleDateFormat("MM/dd (E)", Locale.KOREAN).format(Date(record.timestamp))
            val mins = record.timeElapsed / 60
            val secs = record.timeElapsed % 60
            val hint = recoveryHint(record, previous)

            Surface(color = AivyColors.PositiveLight, shape = RoundedCornerShape(AivyRadius.Md), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, tint = AivyColors.Positive)
                }
            }
            Spacer(modifier = Modifier.width(AivySpace.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(dateStr, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4, maxLines = 1)
                Text(
                    text = "${String.format("%.1f", record.distance)}km · ${record.paceStr}",
                    style = MaterialTheme.typography.titleMedium,
                    color = AivyColors.Text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$hint · ${String.format("%02d:%02d", mins, secs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AivyColors.Text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun OutlinedBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = AivyColors.Surface,
        shape = RoundedCornerShape(AivyRadius.Lg),
        border = BorderStroke(1.dp, AivyColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AivySpace.Sm, vertical = AivySpace.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4, maxLines = 1)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text1, maxLines = 1)
        }
    }
}

private data class ReadinessUi(
    val label: String,
    val container: Color,
    val content: Color,
    val reason: (Double) -> String,
)

private fun readinessFrom(latestWorkout: WorkoutRecordEntity?): ReadinessUi {
    if (latestWorkout == null) {
        return ReadinessUi(
            label = "첫 러닝",
            container = AivyColors.AccentLight,
            content = AivyColors.Accent,
        ) { "기록이 아직 없어 20~30분 가벼운 러닝으로 기준 페이스를 만들어요." }
    }

    return when {
        latestWorkout.distance >= 8.0 -> ReadinessUi(
            label = "회복 권장",
            container = AivyColors.WarningLight,
            content = AivyColors.Warning,
        ) { "최근 긴 거리를 뛰었으니 오늘은 강도를 낮추고 호흡 리듬을 회복하세요." }
        latestWorkout.paceStr.startsWith("0") -> ReadinessUi(
            label = "가볍게",
            container = AivyColors.AccentLight,
            content = AivyColors.Accent,
        ) { "최근 기록이 짧아 오늘은 편한 페이스로 움직임을 쌓는 것이 좋아요." }
        else -> ReadinessUi(
            label = "보통",
            container = AivyColors.PositiveLight,
            content = AivyColors.Positive,
        ) { total -> "이번 달 ${String.format("%.1f", total)}km 누적 중입니다. 오늘은 일정한 페이스 유지에 집중하세요." }
    }
}

private fun recoveryHint(record: WorkoutRecordEntity, previous: WorkoutRecordEntity?): String {
    if (previous == null) return "기준 기록"
    val diff = record.distance - previous.distance
    return when {
        diff >= 1.0 -> "거리 증가"
        diff <= -1.0 -> "회복 러닝"
        else -> "페이스 유지"
    }
}
