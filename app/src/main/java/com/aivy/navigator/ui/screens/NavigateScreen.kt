package com.aivy.navigator.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aivy.navigator.ui.components.AivyPage
import com.aivy.navigator.ui.components.AivyPanelStrong
import com.aivy.navigator.ui.components.AivyStatusChip
import com.aivy.navigator.ui.model.AivyMockData
import com.aivy.navigator.ui.model.NavigatePanelState
import com.aivy.navigator.ui.model.NavigateRouteOption
import com.aivy.navigator.ui.navigation.MapBridgeAction
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace

@Composable
fun NavigateScreen(
    mapBridgeAction: MapBridgeAction,
) {
    val mock = AivyMockData.navigateState()

    var query by rememberSaveable { mutableStateOf(mock.query) }
    var panelState by rememberSaveable { mutableStateOf(mock.panelState) }
    var selectedRouteId by rememberSaveable { mutableStateOf<String?>(mock.selectedRouteId) }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    var parkingSaved by rememberSaveable { mutableStateOf(false) }

    val panelMinHeight by animateDpAsState(
        targetValue = when (panelState) {
            NavigatePanelState.Search -> 270.dp
            NavigatePanelState.RouteSelect -> 420.dp
            NavigatePanelState.Navigating -> 320.dp
            NavigatePanelState.Arrived -> 340.dp
        },
        animationSpec = tween(durationMillis = 250),
        label = "navigatePanelHeight",
    )

    AivyPage {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(AivyColors.PrimaryLight),
            ) {
                MapPlaceholder(state = panelState)

                if (panelState == NavigatePanelState.Navigating) {
                    Surface(
                        color = AivyColors.Surface,
                        shape = RoundedCornerShape(AivyRadius.Md),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AivyColors.Positive),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "길안내 중",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AivyColors.Primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${mock.remainingDistance} · ${mock.remainingTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AivyColors.Text3,
                            )
                        }
                    }
                }
            }

            Surface(
                color = AivyColors.Surface,
                shape = RoundedCornerShape(topStart = AivyRadius.Xl, topEnd = AivyRadius.Xl),
                shadowElevation = 10.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = panelMinHeight)
                        .padding(horizontal = AivySpace.Page, vertical = AivySpace.Lg),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AivyColors.Border),
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    when (panelState) {
                        NavigatePanelState.Search -> {
                            Text(
                                text = "어디로 갈까요?",
                                style = MaterialTheme.typography.titleLarge,
                                color = AivyColors.Primary,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                placeholder = { Text("목적지를 검색하세요") },
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickQueryChip("근처 카페") { query = "근처 카페" }
                                QuickQueryChip("약국") { query = "약국" }
                                QuickQueryChip("지하철역") { query = "지하철역" }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { mapBridgeAction.openTMap() }, modifier = Modifier.weight(1f)) {
                                    Text("TMap 열기")
                                }
                                TextButton(onClick = { mapBridgeAction.openNaverMap() }, modifier = Modifier.weight(1f)) {
                                    Text("Naver 열기")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { panelState = NavigatePanelState.RouteSelect },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = query.isNotBlank(),
                            ) {
                                Text("경로 검색")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "또는 AIVY 기기에 목적지를 말하세요",
                                style = MaterialTheme.typography.labelSmall,
                                color = AivyColors.Text4,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }

                        NavigatePanelState.RouteSelect -> {
                            Text(
                                text = query.ifBlank { "서강대학교 정문" },
                                style = MaterialTheme.typography.titleMedium,
                                color = AivyColors.Primary,
                            )
                            Text(
                                text = "${mock.routes.size}개 경로",
                                style = MaterialTheme.typography.bodySmall,
                                color = AivyColors.Text3,
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(250.dp),
                            ) {
                                items(mock.routes) { route ->
                                    RouteOptionCard(
                                        route = route,
                                        selected = selectedRouteId == route.id,
                                        frequent = route.id == mock.routes.firstOrNull()?.id,
                                        onSelect = { selectedRouteId = route.id },
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    panelState = NavigatePanelState.Navigating
                                    isPaused = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedRouteId != null,
                            ) {
                                Text("길안내 시작")
                            }
                        }

                        NavigatePanelState.Navigating -> {
                            if (isPaused) {
                                Surface(
                                    color = AivyColors.WarningLight,
                                    shape = RoundedCornerShape(AivyRadius.Md),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "일시정지됨",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AivyColors.Warning,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "이동 감지 시 자동 재개",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AivyColors.Text3,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        TextButton(onClick = { isPaused = false }) {
                                            Text("재개")
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            Surface(
                                color = AivyColors.Primary,
                                shape = RoundedCornerShape(AivyRadius.Lg),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(AivyRadius.Md),
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.Map, contentDescription = null, tint = Color.White)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("120m 후 우회전", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                        Text("다음 안내까지 40초", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                NavStat("남은 거리", mock.remainingDistance)
                                NavStat("도착 예정", mock.remainingTime)
                                NavStat("전체", "1.2km")
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = { isPaused = !isPaused },
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, if (isPaused) AivyColors.Positive else AivyColors.Warning, RoundedCornerShape(AivyRadius.Md)),
                                ) {
                                    Text(if (isPaused) "재개" else "일시정지")
                                }
                                TextButton(
                                    onClick = { mapBridgeAction.openTMap() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Md)),
                                ) {
                                    Text("실제 지도")
                                }
                                Button(
                                    onClick = { panelState = NavigatePanelState.Arrived },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("종료")
                                }
                            }
                        }

                        NavigatePanelState.Arrived -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    color = AivyColors.PositiveLight,
                                    shape = CircleShape,
                                    modifier = Modifier.size(52.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Place, contentDescription = null, tint = AivyColors.Positive)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("도착했습니다", style = MaterialTheme.typography.titleLarge, color = AivyColors.Primary)
                                Text(
                                    text = query.ifBlank { "서강대학교 정문" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AivyColors.Text3,
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = AivyColors.BackgroundAlt,
                                    shape = RoundedCornerShape(AivyRadius.Lg),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        NavStat("거리", "1.2km")
                                        NavStat("시간", "15분")
                                        NavStat("평균", "4.8km/h")
                                    }
                                }

                                if (parkingSaved) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AivyStatusChip(
                                        text = "주차 위치가 저장되었습니다",
                                        container = AivyColors.PositiveLight,
                                        content = AivyColors.Positive,
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        onClick = { parkingSaved = !parkingSaved },
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Md)),
                                    ) {
                                        Text(if (parkingSaved) "저장 취소" else "주차 저장")
                                    }
                                    TextButton(
                                        onClick = { mapBridgeAction.openNaverMap() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Md)),
                                    ) {
                                        Text("지도 보기")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        panelState = NavigatePanelState.Search
                                        selectedRouteId = null
                                        query = ""
                                        parkingSaved = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("새 경로 찾기")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickQueryChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        color = AivyColors.BackgroundAlt,
        shape = RoundedCornerShape(AivyRadius.Lg),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AivyColors.Text2,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun RouteOptionCard(
    route: NavigateRouteOption,
    selected: Boolean,
    frequent: Boolean,
    onSelect: () -> Unit,
) {
    val walkRoute = route.id != "transit"
    Surface(
        color = if (selected) AivyColors.AccentLight else AivyColors.Surface,
        shape = RoundedCornerShape(AivyRadius.Md),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) AivyColors.Accent else AivyColors.Border,
                shape = RoundedCornerShape(AivyRadius.Md),
            )
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (walkRoute) AivyColors.PositiveLight else AivyColors.AccentLight,
                shape = RoundedCornerShape(AivyRadius.Sm),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (walkRoute) Icons.AutoMirrored.Outlined.DirectionsWalk else Icons.Outlined.Map,
                        contentDescription = null,
                        tint = if (walkRoute) AivyColors.Positive else AivyColors.Accent,
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(route.label, style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text1, fontWeight = FontWeight.SemiBold)
                    if (frequent) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AivyStatusChip(
                            text = "자주 이용",
                            container = AivyColors.PositiveLight,
                            content = AivyColors.Positive,
                        )
                    }
                }
                Text(route.detail, style = MaterialTheme.typography.labelSmall, color = AivyColors.Text3)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(route.duration, style = MaterialTheme.typography.titleMedium, color = AivyColors.Primary)
                Text(route.distance, style = MaterialTheme.typography.labelSmall, color = AivyColors.Text3)
            }
        }
    }
}

@Composable
private fun NavStat(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = AivyColors.Primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AivyColors.Text4)
    }
}

@Composable
private fun MapPlaceholder(
    state: NavigatePanelState,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grid = 48.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = AivyColors.Primary.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                )
                x += grid
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = AivyColors.Primary.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                )
                y += grid
            }

            if (state == NavigatePanelState.RouteSelect || state == NavigatePanelState.Navigating) {
                val path = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.88f)
                    cubicTo(
                        size.width * 0.22f,
                        size.height * 0.66f,
                        size.width * 0.38f,
                        size.height * 0.62f,
                        size.width * 0.48f,
                        size.height * 0.48f,
                    )
                    cubicTo(
                        size.width * 0.62f,
                        size.height * 0.36f,
                        size.width * 0.78f,
                        size.height * 0.30f,
                        size.width * 0.90f,
                        size.height * 0.18f,
                    )
                }

                drawPath(
                    path = path,
                    color = AivyColors.Accent.copy(alpha = if (state == NavigatePanelState.RouteSelect) 0.6f else 0.85f),
                    style = Stroke(width = 8f, cap = StrokeCap.Round),
                )
            }
        }

        when (state) {
            NavigatePanelState.Search -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(AivyRadius.Md))
                        .background(Color.White.copy(alpha = 0.75f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Place, contentDescription = null, tint = AivyColors.Accent, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("현재 위치", style = MaterialTheme.typography.labelSmall, color = AivyColors.Accent)
                }
            }

            NavigatePanelState.Arrived -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(AivyColors.Positive)
                            .border(3.dp, Color.White, CircleShape),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AivyStatusChip("도착", AivyColors.PositiveLight, AivyColors.Positive)
                }
            }

            else -> Unit
        }
    }
}
