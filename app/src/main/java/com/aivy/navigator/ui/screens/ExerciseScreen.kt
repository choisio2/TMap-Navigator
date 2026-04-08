package com.aivy.navigator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aivy.navigator.ui.components.AivyPage
import com.aivy.navigator.ui.components.AivyPanel
import com.aivy.navigator.ui.components.AivySectionLabel
import com.aivy.navigator.ui.components.AivyStatusChip
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace
import java.util.Locale
import kotlin.math.sin
import kotlinx.coroutines.delay

private enum class ExercisePhase { Ready, Active, Coaching, Complete }

private enum class ExerciseType(
    val activeLabel: String,
    val completeLabel: String,
) {
    Running("러닝 중", "러닝"),
    Walking("걷기 중", "걷기"),
}

private data class RecentSession(
    val id: String,
    val date: String,
    val distance: String,
    val time: String,
    val pace: String,
)

private data class Split(
    val km: Int,
    val pace: String,
    val seconds: Int,
)

private data class Readiness(
    val score: Int,
    val status: String,
    val guidance: String,
    val factors: List<String>,
)

private data class TodayWorkout(
    val label: String,
    val goal: String,
    val goalDistance: String,
    val aiChangedFrom: String?,
    val aiChangedReason: String?,
)

private data class WeeklyVolume(
    val currentKm: Float,
    val targetKm: Float,
    val impactLoad: Int,
    val impactStatus: String,
)

private data class EnvironmentChip(
    val emoji: String,
    val label: String,
    val value: String,
)

private data class RacePrep(
    val name: String,
    val expectedRecord: String,
    val dDay: Int,
)

private val RECENT_SESSIONS = listOf(
    RecentSession("1", "3/23 (일)", "5.2km", "28:34", "5'29\""),
    RecentSession("2", "3/21 (금)", "3.1km", "18:45", "6'03\""),
    RecentSession("3", "3/18 (화)", "7.0km", "38:12", "5'27\""),
)

private val SPLITS = listOf(
    Split(1, "5'24\"", 324),
    Split(2, "5'18\"", 318),
    Split(3, "5'31\"", 331),
    Split(4, "5'45\"", 345),
    Split(5, "5'22\"", 322),
)

private val COACHING_MESSAGES = listOf(
    "목을 조금 더 들어보세요 — 자세가 앞으로 기울어지고 있어요",
    "페이스가 안정적이에요. 이대로 유지해보세요!",
    "호흡이 빨라지고 있어요. 코로 깊게 들이쉬어 보세요",
)

private val READINESS = Readiness(
    score = 78,
    status = "보통",
    guidance = "오늘은 무리 없는 강도로 리듬을 유지해요.",
    factors = listOf("수면 6시간", "회의 3개"),
)

private val TODAY_WORKOUT = TodayWorkout(
    label = "이지런",
    goal = "30분",
    goalDistance = "5km",
    aiChangedFrom = "인터벌",
    aiChangedReason = "수면 시간이 평소보다 부족해 강도를 조정했어요.",
)

private val WEEKLY_VOLUME = WeeklyVolume(
    currentKm = 18.5f,
    targetKm = 25f,
    impactLoad = 82,
    impactStatus = "normal",
)

private val RACE_PREP = RacePrep(
    name = "서울 하프마라톤",
    expectedRecord = "1:52:00 예상",
    dDay = 45,
)

private val ENVIRONMENT_CHIPS = listOf(
    EnvironmentChip("🌡️", "기온", "14°C"),
    EnvironmentChip("🌫️", "미세먼지", "좋음"),
    EnvironmentChip("💧", "습도", "52%"),
    EnvironmentChip("☀️", "UV", "3"),
    EnvironmentChip("🌬️", "바람", "3.2m/s"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    var phase by rememberSaveable { mutableStateOf(ExercisePhase.Ready) }
    var elapsedSeconds by rememberSaveable { mutableIntStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var goalKm by rememberSaveable { mutableIntStateOf(5) }
    var exerciseType by rememberSaveable { mutableStateOf(ExerciseType.Running) }
    var coachMessage by rememberSaveable { mutableStateOf(COACHING_MESSAGES.first()) }
    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    var showSavedToast by rememberSaveable { mutableStateOf(false) }
    var lastCoachingTrigger by rememberSaveable { mutableIntStateOf(-1) }
    var showSessionSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showRacePrepSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(phase, paused) {
        if (phase != ExercisePhase.Active && phase != ExercisePhase.Coaching) return@LaunchedEffect
        if (paused) return@LaunchedEffect
        while (true) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }

    LaunchedEffect(elapsedSeconds, phase, paused) {
        if (phase != ExercisePhase.Active || paused) return@LaunchedEffect
        if (elapsedSeconds <= 0 || elapsedSeconds % 30 != 0) return@LaunchedEffect
        if (elapsedSeconds == lastCoachingTrigger) return@LaunchedEffect
        lastCoachingTrigger = elapsedSeconds
        coachMessage = COACHING_MESSAGES[(elapsedSeconds / 30) % COACHING_MESSAGES.size]
        phase = ExercisePhase.Coaching
    }

    LaunchedEffect(phase) {
        if (phase == ExercisePhase.Coaching) {
            delay(3_000)
            phase = ExercisePhase.Active
        }
        if (phase == ExercisePhase.Complete) {
            showSavedToast = true
            delay(2_000)
            showSavedToast = false
        }
    }

    val liveDistance = String.format(Locale.US, "%.2f", elapsedSeconds * 0.003f)
    val livePace = if (elapsedSeconds > 10) "5'30\"" else "--'--\""
    val liveCalories = (elapsedSeconds * 0.23f).toInt()
    val liveBpm = 138 + (sin(elapsedSeconds * 0.1f) * 8f).toInt()

    AivyPage {
        Box(modifier = Modifier.fillMaxSize()) {
            when (phase) {
                ExercisePhase.Ready -> ReadyScreen(
                    goalKm = goalKm,
                    onGoalChanged = { goalKm = it },
                    onOpenSessionSettings = { showSessionSettingsSheet = true },
                    onOpenRacePrep = { showRacePrepSheet = true },
                    onBack = onBack,
                    onStartRunning = {
                        exerciseType = ExerciseType.Running
                        elapsedSeconds = 0
                        paused = false
                        lastCoachingTrigger = -1
                        phase = ExercisePhase.Active
                    },
                    onStartWalking = {
                        exerciseType = ExerciseType.Walking
                        elapsedSeconds = 0
                        paused = false
                        lastCoachingTrigger = -1
                        phase = ExercisePhase.Active
                    },
                )

                ExercisePhase.Active,
                ExercisePhase.Coaching,
                -> ActiveScreen(
                    exerciseType = exerciseType,
                    elapsedSeconds = elapsedSeconds,
                    goalKm = goalKm,
                    liveDistance = liveDistance,
                    livePace = livePace,
                    liveCalories = liveCalories,
                    liveBpm = liveBpm,
                    paused = paused,
                    onTogglePause = { paused = !paused },
                    onStop = { phase = ExercisePhase.Complete },
                    coachingMode = phase == ExercisePhase.Coaching,
                    coachMessage = coachMessage,
                )

                ExercisePhase.Complete -> CompleteScreen(
                    exerciseType = exerciseType,
                    goalKm = goalKm,
                    onHome = onHome,
                    onShare = { showShareSheet = true },
                )
            }

            if (showSavedToast) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AivySpace.Page),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    AivyStatusChip(
                        text = "운동 기록이 저장되었습니다",
                        container = AivyColors.Positive,
                        content = Color.White,
                    )
                }
            }
        }
    }

    if (showSessionSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSessionSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page, vertical = AivySpace.Sm),
            ) {
                Text(
                    text = "러닝 세션 설정",
                    style = MaterialTheme.typography.titleMedium,
                    color = AivyColors.Primary,
                )
                Spacer(modifier = Modifier.height(AivySpace.Sm))
                Text("목표 거리", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(3, 5, 10).forEach { km ->
                        Surface(
                            color = if (goalKm == km) AivyColors.Primary else AivyColors.BackgroundAlt,
                            shape = RoundedCornerShape(AivyRadius.Md),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { goalKm = km },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${km}km",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (goalKm == km) Color.White else AivyColors.Text2,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AivySpace.Md))
                Button(
                    onClick = { showSessionSettingsSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("적용")
                }
                Spacer(modifier = Modifier.height(AivySpace.Lg))
            }
        }
    }

    if (showRacePrepSheet) {
        ModalBottomSheet(onDismissRequest = { showRacePrepSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page, vertical = AivySpace.Sm),
            ) {
                Text(
                    text = "레이스 준비 상세",
                    style = MaterialTheme.typography.titleMedium,
                    color = AivyColors.Primary,
                )
                Spacer(modifier = Modifier.height(AivySpace.Sm))
                AivyPanel {
                    Text(RACE_PREP.name, style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text1, fontWeight = FontWeight.Bold)
                    Text(RACE_PREP.expectedRecord, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text3)
                    AivyStatusChip("D-${RACE_PREP.dDay}", AivyColors.PrimaryLight, AivyColors.Primary)
                }
                Spacer(modifier = Modifier.height(AivySpace.Md))
                Text(
                    text = "다음 단계에서 세부 페이스 전략과 주간 계획을 연결합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AivyColors.Text3,
                )
                Spacer(modifier = Modifier.height(AivySpace.Lg))
            }
        }
    }

    if (showShareSheet) {
        ModalBottomSheet(onDismissRequest = { showShareSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page, vertical = AivySpace.Sm),
            ) {
                Text(
                    text = "운동 기록 공유",
                    style = MaterialTheme.typography.titleMedium,
                    color = AivyColors.Primary,
                )
                Spacer(modifier = Modifier.height(AivySpace.Sm))
                ShareOptionRow(
                    icon = Icons.Outlined.FileDownload,
                    label = "기록 이미지 저장",
                    description = "운동 요약을 이미지로 저장",
                    onClick = { showShareSheet = false },
                )
                ShareOptionRow(
                    icon = Icons.AutoMirrored.Outlined.Message,
                    label = "메시지로 전송",
                    description = "카카오톡, 인스타 스토리 등",
                    onClick = { showShareSheet = false },
                )
                ShareOptionRow(
                    icon = Icons.Outlined.ContentCopy,
                    label = "링크 복사",
                    description = "클립보드에 복사합니다",
                    onClick = { showShareSheet = false },
                )
                Spacer(modifier = Modifier.height(AivySpace.Lg))
            }
        }
    }
}

@Composable
private fun ReadyScreen(
    goalKm: Int,
    onGoalChanged: (Int) -> Unit,
    onOpenSessionSettings: () -> Unit,
    onOpenRacePrep: () -> Unit,
    onBack: () -> Unit,
    onStartRunning: () -> Unit,
    onStartWalking: () -> Unit,
) {
    val readinessVisual = readinessVisual(READINESS.status)
    val aiHeadline = if (TODAY_WORKOUT.aiChangedFrom != null) {
        "${TODAY_WORKOUT.aiChangedFrom} 대신 ${TODAY_WORKOUT.label} 권장"
    } else {
        "${TODAY_WORKOUT.label} ${TODAY_WORKOUT.goal} 진행 권장"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AivySpace.Xl),
        verticalArrangement = Arrangement.spacedBy(AivySpace.Md),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page, vertical = AivySpace.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "뒤로",
                        tint = AivyColors.Text3,
                    )
                }
                Surface(
                    color = AivyColors.PositiveLight,
                    shape = RoundedCornerShape(AivyRadius.Md),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = AivyColors.Positive)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "운동",
                    style = MaterialTheme.typography.titleLarge,
                    color = AivyColors.Primary,
                )
            }
        }

        item {
            Surface(
                color = AivyColors.Surface,
                shape = RoundedCornerShape(AivyRadius.Xl),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page)
                    .border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Xl)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AivySpace.Md, vertical = AivySpace.Md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        color = AivyColors.PrimaryLight,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "A",
                                style = MaterialTheme.typography.titleMedium,
                                color = AivyColors.Primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AivyStatusChip("AI 코치", AivyColors.BackgroundAlt, AivyColors.Text3)
                            Spacer(modifier = Modifier.width(6.dp))
                            AivyStatusChip(
                                text = "컨디션 ${READINESS.status}",
                                container = readinessVisual.bg,
                                content = readinessVisual.fg,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = aiHeadline,
                            style = MaterialTheme.typography.titleMedium,
                            color = AivyColors.Text1,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = TODAY_WORKOUT.aiChangedReason ?: READINESS.guidance,
                            style = MaterialTheme.typography.bodySmall,
                            color = AivyColors.Text3,
                        )
                    }
                }
            }
        }

        item {
            Surface(
                color = AivyColors.PrimaryLight.copy(alpha = 0.7f),
                shape = RoundedCornerShape(AivyRadius.Xl),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page)
                    .border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Xl)),
            ) {
                Column(modifier = Modifier.padding(AivySpace.Card)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("오늘의 워크아웃", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
                        Spacer(modifier = Modifier.weight(1f))
                        AivyStatusChip(
                            text = READINESS.status,
                            container = readinessVisual.bg,
                            content = readinessVisual.fg,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = TODAY_WORKOUT.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = AivyColors.Text1,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "목표 ${TODAY_WORKOUT.goal} · ${TODAY_WORKOUT.goalDistance}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AivyColors.Text2,
                    )
                    if (TODAY_WORKOUT.aiChangedFrom != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AivyStatusChip(
                            text = "${TODAY_WORKOUT.aiChangedFrom}에서 ${TODAY_WORKOUT.label}으로 조정",
                            container = AivyColors.Surface,
                            content = AivyColors.Text3,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onStartRunning,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("러닝 시작")
                        }
                        Surface(
                            color = AivyColors.Surface,
                            shape = RoundedCornerShape(AivyRadius.Lg),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Lg))
                                .clickable(onClick = onOpenSessionSettings),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "세션 설정",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AivyColors.Primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    if (READINESS.factors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = READINESS.factors.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = AivyColors.Text4,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AivyPanel(modifier = Modifier.weight(1f)) {
                    Text("주간 진행", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
                    ProgressLine(
                        label = "거리",
                        progressText = "${WEEKLY_VOLUME.currentKm}/${WEEKLY_VOLUME.targetKm}km",
                        ratio = WEEKLY_VOLUME.currentKm / WEEKLY_VOLUME.targetKm,
                        activeColor = AivyColors.Primary,
                    )
                    ProgressLine(
                        label = "운동 부하",
                        progressText = "${WEEKLY_VOLUME.impactLoad}%",
                        ratio = WEEKLY_VOLUME.impactLoad / 100f,
                        activeColor = if (WEEKLY_VOLUME.impactStatus == "normal") AivyColors.Positive else AivyColors.Warning,
                    )
                }

                Surface(
                    color = AivyColors.Surface,
                    shape = RoundedCornerShape(AivyRadius.Xl),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Xl))
                        .clickable(onClick = onOpenRacePrep),
                ) {
                    Column(modifier = Modifier.padding(AivySpace.Card)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("레이스 준비", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = AivyColors.Text4, modifier = Modifier.size(15.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = RACE_PREP.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AivyColors.Text1,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(RACE_PREP.expectedRecord, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text3)
                        Spacer(modifier = Modifier.height(10.dp))
                        AivyStatusChip("D-${RACE_PREP.dDay}", AivyColors.PrimaryLight, AivyColors.Primary)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactStat("이번 달 거리", "32.5km", Modifier.weight(1f))
                CompactStat("운동 횟수", "8회", Modifier.weight(1f))
                CompactStat("총 시간", "3h 24m", Modifier.weight(1f))
            }
        }

        item {
            AivyPanel(modifier = Modifier.padding(horizontal = AivySpace.Page)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("러닝 환경", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text2, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    AivyStatusChip("실외 러닝 적합", AivyColors.AccentLight, AivyColors.Accent)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ENVIRONMENT_CHIPS) { item ->
                        Surface(
                            color = AivyColors.Surface,
                            shape = RoundedCornerShape(AivyRadius.Lg),
                            modifier = Modifier.border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Lg)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.emoji, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(item.label, style = MaterialTheme.typography.labelSmall, color = AivyColors.Text4)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(item.value, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text1, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = AivySpace.Page)) {
                AivySectionLabel("최근 기록")
                Spacer(modifier = Modifier.height(AivySpace.Sm))
                RECENT_SESSIONS.forEach { session ->
                    RecentSessionRow(session)
                    Spacer(modifier = Modifier.height(AivySpace.Sm))
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = AivySpace.Page)) {
                AivySectionLabel("목표 거리")
                Spacer(modifier = Modifier.height(AivySpace.Sm))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(3, 5, 10).forEach { km ->
                        Surface(
                            color = if (goalKm == km) AivyColors.Primary else AivyColors.BackgroundAlt,
                            shape = RoundedCornerShape(AivyRadius.Md),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onGoalChanged(km) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${km}km",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (goalKm == km) Color.White else AivyColors.Text2,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                color = AivyColors.Primary,
                shape = RoundedCornerShape(AivyRadius.Lg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page)
                    .clickable(onClick = onStartWalking),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "걷기 시작",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveScreen(
    exerciseType: ExerciseType,
    elapsedSeconds: Int,
    goalKm: Int,
    liveDistance: String,
    livePace: String,
    liveCalories: Int,
    liveBpm: Int,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    coachingMode: Boolean,
    coachMessage: String,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AivySpace.Page, vertical = AivySpace.Lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = AivyColors.Accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = exerciseType.activeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = AivyColors.Accent,
                )
            }

            Spacer(modifier = Modifier.height(AivySpace.Sm))
            Text(
                text = formatTime(elapsedSeconds),
                style = MaterialTheme.typography.displayLarge,
                color = AivyColors.Text1,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = "목표 ${goalKm}km",
                style = MaterialTheme.typography.labelSmall,
                color = AivyColors.Text4,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(AivySpace.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactStat("현재 페이스", livePace, Modifier.weight(1f), unit = "min/km")
                CompactStat("거리", liveDistance, Modifier.weight(1f), unit = "km")
                CompactStat("칼로리", "$liveCalories", Modifier.weight(1f), unit = "kcal")
            }

            Spacer(modifier = Modifier.height(AivySpace.Md))
            MapPreviewCard()

            Spacer(modifier = Modifier.height(AivySpace.Md))
            AivyPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Favorite,
                        contentDescription = null,
                        tint = AivyColors.Danger,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$liveBpm BPM",
                        style = MaterialTheme.typography.titleMedium,
                        color = AivyColors.Text1,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AivyStatusChip("Zone 3", AivyColors.WarningLight, AivyColors.Warning)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = if (paused) AivyColors.Accent else AivyColors.BackgroundAlt,
                    shape = RoundedCornerShape(AivyRadius.Lg),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onTogglePause),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (paused) "재개" else "일시정지",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (paused) Color.White else AivyColors.Text2,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Surface(
                    color = AivyColors.Danger,
                    shape = RoundedCornerShape(AivyRadius.Lg),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onStop),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "운동 종료",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (coachingMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = AivyColors.Surface,
                    shape = RoundedCornerShape(AivyRadius.Xl),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AivySpace.Page),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AivySpace.Lg, vertical = AivySpace.Xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            color = AivyColors.PositiveLight,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = AivyColors.Positive)
                            }
                        }
                        Spacer(modifier = Modifier.height(AivySpace.Md))
                        Text(
                            text = coachMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AivyColors.Text1,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(AivySpace.Sm))
                        Text(
                            text = "3초 후 자동으로 닫힙니다",
                            style = MaterialTheme.typography.labelSmall,
                            color = AivyColors.Text4,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompleteScreen(
    exerciseType: ExerciseType,
    goalKm: Int,
    onHome: () -> Unit,
    onShare: () -> Unit,
) {
    val maxSplitSeconds = SPLITS.maxOf { it.seconds }.coerceAtLeast(1)
    val fastestSplit = SPLITS.minOf { it.seconds }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AivySpace.Md),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page, vertical = AivySpace.Md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = AivyColors.PositiveLight,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = AivyColors.Positive)
                    }
                }
                Spacer(modifier = Modifier.height(AivySpace.Sm))
                Text("운동 완료!", style = MaterialTheme.typography.titleLarge, color = AivyColors.Text1)
                Text(
                    "${exerciseType.completeLabel} | 목표 ${goalKm}km",
                    style = MaterialTheme.typography.bodySmall,
                    color = AivyColors.Text3,
                )
            }
        }

        item {
            AivyPanel(modifier = Modifier.padding(horizontal = AivySpace.Page)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    SummaryMetric("거리", "5.2", "km")
                    SummaryMetric("시간", "27:48")
                    SummaryMetric("평균", "5'21\"")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    SummaryMetric("칼로리", "387", "kcal")
                    SummaryMetric("케이던스", "172")
                    SummaryMetric("심박", "146", "BPM")
                }
            }
        }

        item {
            AivyPanel(modifier = Modifier.padding(horizontal = AivySpace.Page)) {
                AivySectionLabel("지난 세션 대비")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CompareChip("페이스", "-7초", AivyColors.Accent, Modifier.weight(1f))
                    CompareChip("거리", "+0.3km", AivyColors.Positive, Modifier.weight(1f))
                    CompareChip("칼로리", "+42kcal", AivyColors.Positive, Modifier.weight(1f))
                }
            }
        }

        item {
            AivyPanel(modifier = Modifier.padding(horizontal = AivySpace.Page)) {
                AivySectionLabel("km별 스플릿")
                SPLITS.forEach { split ->
                    val ratio = split.seconds.toFloat() / maxSplitSeconds.toFloat()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${split.km}km", style = MaterialTheme.typography.labelSmall, color = AivyColors.Text3, modifier = Modifier.width(30.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .clip(RoundedCornerShape(AivyRadius.Sm))
                                .background(AivyColors.BackgroundAlt),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(AivyRadius.Sm))
                                    .background(if (split.seconds == fastestSplit) AivyColors.Positive else AivyColors.Accent.copy(alpha = 0.7f)),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            split.pace,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (split.seconds == fastestSplit) AivyColors.Positive else AivyColors.Text2,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AivySpace.Page),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onHome, modifier = Modifier.weight(1f)) {
                    Text("홈으로")
                }
                Surface(
                    color = AivyColors.Surface,
                    shape = RoundedCornerShape(AivyRadius.Lg),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Lg))
                        .clickable(onClick = onShare),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = AivyColors.Text2, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("공유", style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text2, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(AivySpace.Xl))
        }
    }
}

@Composable
private fun ShareOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AivyRadius.Sm))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = AivyColors.BackgroundAlt,
            shape = RoundedCornerShape(AivyRadius.Sm),
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = AivyColors.Text2, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text1)
            Text(description, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
        }
    }
}

@Composable
private fun CompactStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
) {
    Surface(
        color = AivyColors.Surface,
        shape = RoundedCornerShape(AivyRadius.Lg),
        modifier = modifier.border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Lg)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AivyColors.Text3)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = AivyColors.Text1,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            if (unit.isNotBlank()) {
                Text(unit, style = MaterialTheme.typography.labelSmall, color = AivyColors.Text4)
            }
        }
    }
}

@Composable
private fun RecentSessionRow(session: RecentSession) {
    Surface(
        color = AivyColors.Surface,
        shape = RoundedCornerShape(AivyRadius.Md),
        modifier = Modifier.fillMaxWidth().border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Md)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AivySpace.Md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(session.date, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text3, modifier = Modifier.weight(1f))
            Text(session.distance, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text1, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(10.dp))
            Text(session.time, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text3, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.width(10.dp))
            Text(session.pace, style = MaterialTheme.typography.bodySmall, color = AivyColors.Accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MapPreviewCard() {
    Surface(
        color = AivyColors.PrimaryLight,
        shape = RoundedCornerShape(AivyRadius.Lg),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(size.width * 0.06f, size.height * 0.72f)
                    cubicTo(
                        size.width * 0.22f,
                        size.height * 0.52f,
                        size.width * 0.26f,
                        size.height * 0.20f,
                        size.width * 0.44f,
                        size.height * 0.40f,
                    )
                    cubicTo(
                        size.width * 0.66f,
                        size.height * 0.64f,
                        size.width * 0.78f,
                        size.height * 0.20f,
                        size.width * 0.94f,
                        size.height * 0.30f,
                    )
                }
                drawPath(
                    path = path,
                    color = AivyColors.Accent,
                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                )
                drawCircle(
                    color = AivyColors.Accent,
                    radius = 8f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.94f, size.height * 0.30f),
                )
            }
            Text(
                text = "경로 미리보기",
                style = MaterialTheme.typography.labelSmall,
                color = AivyColors.Text3,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(AivyRadius.Sm))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    unit: String = "",
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AivyColors.Text3)
        Text(value, style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1, fontFamily = FontFamily.Monospace)
        if (unit.isNotBlank()) {
            Text(unit, style = MaterialTheme.typography.labelSmall, color = AivyColors.Text4)
        }
    }
}

@Composable
private fun CompareChip(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AivyColors.BackgroundAlt,
        shape = RoundedCornerShape(AivyRadius.Md),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AivyColors.Text3)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

private data class ReadinessVisual(
    val bg: Color,
    val fg: Color,
)

@Composable
private fun ProgressLine(
    label: String,
    progressText: String,
    ratio: Float,
    activeColor: Color,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AivyColors.Text3)
            Spacer(modifier = Modifier.weight(1f))
            Text(progressText, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text1, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(AivyRadius.Lg))
                .background(AivyColors.BackgroundAlt),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(AivyRadius.Lg))
                    .background(activeColor),
            )
        }
    }
}

private fun readinessVisual(status: String): ReadinessVisual = when (status) {
    "좋음" -> ReadinessVisual(bg = Color(0xFFE6F6EE), fg = Color(0xFF0F8A5F))
    "낮음" -> ReadinessVisual(bg = Color(0xFFFFECEB), fg = Color(0xFFD14343))
    "휴식" -> ReadinessVisual(bg = Color(0xFFEEF2FF), fg = Color(0xFF4F46E5))
    else -> ReadinessVisual(bg = Color(0xFFFFF5E8), fg = Color(0xFFC36A11))
}

private fun formatTime(totalSeconds: Int): String {
    val hour = totalSeconds / 3600
    val minute = (totalSeconds % 3600) / 60
    val second = totalSeconds % 60
    val mm = minute.toString().padStart(2, '0')
    val ss = second.toString().padStart(2, '0')
    return if (hour > 0) "$hour:$mm:$ss" else "$mm:$ss"
}
