package com.aivy.navigator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aivy.navigator.ui.components.AivyPage
import com.aivy.navigator.ui.components.AivyPanel
import com.aivy.navigator.ui.components.AivySectionLabel
import com.aivy.navigator.ui.components.AivyStatusChip
import com.aivy.navigator.ui.model.AivyMockData
import com.aivy.navigator.ui.model.HomeActivity
import com.aivy.navigator.ui.model.HomeInsight
import com.aivy.navigator.ui.model.HomeShortcut
import com.aivy.navigator.ui.model.HomeUiState
import com.aivy.navigator.ui.navigation.AivyDestination
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private data class ShortcutVisual(
    val icon: ImageVector,
    val bg: Color,
    val fg: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState = AivyMockData.homeState(),
    onNavigate: (AivyDestination) -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    var showBanner by rememberSaveable { mutableStateOf(true) }
    var previewShortcut by remember { mutableStateOf<HomeShortcut?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }

    AivyPage {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = AivySpace.Xl),
            verticalArrangement = Arrangement.spacedBy(AivySpace.Md),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AivySpace.Page, vertical = AivySpace.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AivyColors.Text3,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.Bluetooth, contentDescription = "Bluetooth", tint = AivyColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Outlined.Wifi, contentDescription = "WiFi", tint = AivyColors.Positive, modifier = Modifier.size(16.dp))
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AivySpace.Page),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AivyColors.PrimaryLight,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("A", color = AivyColors.Primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(AivySpace.Sm))
                    Column {
                        Text("AIVY", style = MaterialTheme.typography.displayLarge, color = AivyColors.Primary)
                        Text("Companion", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
                    }
                }
            }

            if (showBanner) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AivySpace.Page),
                        color = AivyColors.WarningLight,
                        shape = RoundedCornerShape(AivyRadius.Md),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AivySpace.Md, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = AivyColors.Warning,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.bannerText,
                                style = MaterialTheme.typography.bodySmall,
                                color = AivyColors.Text1,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "배너 닫기",
                                tint = AivyColors.Text4,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { showBanner = false },
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AivySpace.Page),
                    color = AivyColors.Primary,
                    shape = RoundedCornerShape(AivyRadius.Md),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AivySpace.Md, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (state.connected) AivyColors.Positive else Color.White.copy(alpha = 0.35f)),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(state.deviceName, style = MaterialTheme.typography.titleMedium, color = AivyColors.Surface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.connected) "연결됨" else "연결 안됨",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        BatteryGauge(percent = state.batteryPercent)
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = AivySpace.Page)) {
                    AivySectionLabel(label = "바로가기")
                    Spacer(modifier = Modifier.height(AivySpace.Sm))
                    state.shortcuts.chunked(4).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm)) {
                            rowItems.forEach { shortcut ->
                                ShortcutCard(
                                    item = shortcut,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (shortcut.destination in setOf(
                                                AivyDestination.Navigate,
                                                AivyDestination.Translate,
                                                AivyDestination.Memory,
                                                AivyDestination.Settings,
                                            )
                                        ) {
                                            onNavigate(shortcut.destination)
                                        } else {
                                            previewShortcut = shortcut
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(AivySpace.Sm))
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(start = AivySpace.Page)) {
                    AivySectionLabel(label = "AIVY 인사이트")
                    Spacer(modifier = Modifier.height(AivySpace.Sm))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm)) {
                        items(state.insights) { insight ->
                            InsightCard(insight)
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = AivySpace.Page)) {
                    AivySectionLabel(label = "오늘")
                    Spacer(modifier = Modifier.height(AivySpace.Sm))
                    state.activities.forEach { activity ->
                        ActivityTimelineItem(activity)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        if (previewShortcut != null) {
            val shortcut = previewShortcut ?: return@AivyPage
            val visual = shortcutVisual(shortcut.destination)

            ModalBottomSheet(onDismissRequest = { previewShortcut = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AivySpace.Page, vertical = AivySpace.Sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = visual.bg,
                            shape = RoundedCornerShape(AivyRadius.Md),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(visual.icon, contentDescription = null, tint = visual.fg)
                            }
                        }
                        Spacer(modifier = Modifier.width(AivySpace.Sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(shortcut.title, style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1)
                            Text(shortcut.subtitle, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text3)
                        }
                    }

                    Spacer(modifier = Modifier.height(AivySpace.Md))
                    shortcutPreviewLines(shortcut.destination).forEach { line ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 6.dp)) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(AivyColors.Accent),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(line, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text2)
                        }
                    }

                    Spacer(modifier = Modifier.height(AivySpace.Sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                onNavigate(shortcut.destination)
                                previewShortcut = null
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("바로가기 열기")
                        }
                        TextButton(onClick = { previewShortcut = null }) {
                            Text("닫기")
                        }
                    }
                    Spacer(modifier = Modifier.height(AivySpace.Md))
                }
            }
        }
    }
}

@Composable
private fun BatteryGauge(percent: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .width(27.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Transparent)
                .padding(1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AivyColors.Positive),
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .width(2.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.4f)),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text("$percent%", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.88f))
    }
}

@Composable
private fun ShortcutCard(
    item: HomeShortcut,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val visual = shortcutVisual(item.destination)

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = AivyColors.Surface,
        shape = RoundedCornerShape(AivyRadius.Md),
        shadowElevation = 1.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
        ) {
            Surface(
                color = visual.bg,
                shape = RoundedCornerShape(AivyRadius.Sm),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.fg,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = AivyColors.Text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = AivyColors.Text4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InsightCard(insight: HomeInsight) {
    AivyPanel(modifier = Modifier.width(272.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = AivyColors.AccentLight,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = AivyColors.Accent, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(insight.title, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text2, modifier = Modifier.weight(1f))
            if (insight.metric != null) {
                AivyStatusChip(
                    text = insight.metric,
                    container = AivyColors.AccentLight,
                    content = AivyColors.Accent,
                )
            }
        }
        Text(
            text = insight.description,
            style = MaterialTheme.typography.bodyMedium,
            color = AivyColors.Text2,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActivityTimelineItem(activity: HomeActivity) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = activity.time,
            style = MaterialTheme.typography.labelSmall,
            color = AivyColors.Text3,
            modifier = Modifier
                .width(42.dp)
                .padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.width(AivySpace.Sm))

        Surface(
            color = AivyColors.Surface,
            shape = RoundedCornerShape(AivyRadius.Md),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val visual = activityVisual(activity.type)
                Surface(
                    color = visual.bg,
                    shape = RoundedCornerShape(AivyRadius.Sm),
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(visual.icon, contentDescription = null, tint = visual.fg, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(activity.summary, style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text1, fontWeight = FontWeight.SemiBold)
                    Text(
                        activity.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = AivyColors.Text4,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun shortcutVisual(destination: AivyDestination): ShortcutVisual = when (destination) {
    AivyDestination.Navigate -> ShortcutVisual(Icons.Outlined.Map, AivyColors.AccentLight, AivyColors.Accent)
    AivyDestination.Translate -> ShortcutVisual(Icons.Outlined.Translate, AivyColors.PositiveLight, AivyColors.Positive)
    AivyDestination.Memory -> ShortcutVisual(Icons.Outlined.Memory, AivyColors.WarningLight, AivyColors.Warning)
    AivyDestination.Settings -> ShortcutVisual(Icons.Outlined.Settings, AivyColors.BackgroundAlt, AivyColors.Text3)
    AivyDestination.Ocr -> ShortcutVisual(Icons.Outlined.History, AivyColors.BackgroundAlt, AivyColors.Primary)
    AivyDestination.Gallery -> ShortcutVisual(Icons.Outlined.Place, AivyColors.AccentLight, AivyColors.Accent)
    AivyDestination.Meeting -> ShortcutVisual(Icons.Outlined.AutoAwesome, AivyColors.WarningLight, AivyColors.Warning)
    AivyDestination.Exercise -> ShortcutVisual(Icons.AutoMirrored.Outlined.DirectionsWalk, AivyColors.PositiveLight, AivyColors.Positive)
    AivyDestination.Onboarding -> ShortcutVisual(Icons.Outlined.AutoAwesome, AivyColors.PrimaryLight, AivyColors.Primary)
    AivyDestination.Pairing -> ShortcutVisual(Icons.Outlined.Settings, AivyColors.BackgroundAlt, AivyColors.Text3)
    AivyDestination.Home -> ShortcutVisual(Icons.Outlined.AutoAwesome, AivyColors.PrimaryLight, AivyColors.Primary)
}

private fun activityVisual(type: String): ShortcutVisual = when (type) {
    "번역" -> ShortcutVisual(Icons.Outlined.Translate, AivyColors.PositiveLight, AivyColors.Positive)
    "길안내" -> ShortcutVisual(Icons.Outlined.Map, AivyColors.AccentLight, AivyColors.Accent)
    else -> ShortcutVisual(Icons.Outlined.AutoAwesome, AivyColors.BackgroundAlt, AivyColors.Text2)
}

private fun shortcutPreviewLines(destination: AivyDestination): List<String> = when (destination) {
    AivyDestination.Ocr -> listOf("문서/표지판 스캔 결과를 기억에 저장", "OCR 종료 후 홈으로 복귀")
    AivyDestination.Gallery -> listOf("최근 기억 카드 중심으로 탐색", "카테고리별 필터를 제공")
    AivyDestination.Meeting -> listOf("미팅 요약과 액션 아이템 정리", "대화 기록과 연계")
    AivyDestination.Exercise -> listOf("러닝 세션 요약과 캘린더 흐름", "운동 패턴 인사이트 반영")
    AivyDestination.Onboarding -> listOf("초기 설정과 안내 플로우", "홈 진입 전 핵심 기능 소개")
    AivyDestination.Pairing -> listOf("블루투스/와이파이 연동 상태", "기기 연결 문제 해결 도우미")
    else -> listOf("선택한 기능으로 이동합니다")
}
