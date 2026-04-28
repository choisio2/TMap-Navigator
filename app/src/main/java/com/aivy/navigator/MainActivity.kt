package com.aivy.navigator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aivy.navigator.ui.components.AivyPage
import com.aivy.navigator.ui.components.AivySectionLabel
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace
import com.aivy.navigator.ui.theme.AivyTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AivyTheme {
                HomeScreen(
                    onOpenTmap = { startActivity(Intent(this, TmapsActivity::class.java)) },
                    onOpenRunning = { startActivity(Intent(this, RunningReadyActivity::class.java)) },
                    onOpenCamera = { startActivity(Intent(this, CameraActivity::class.java)) },
                )
            }
        }
    }
}

private data class HomeShortcut(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconContainer: Color,
    val iconTint: Color,
    val onClick: () -> Unit,
)

@Composable
private fun HomeScreen(
    onOpenTmap: () -> Unit,
    onOpenRunning: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    val shortcuts = listOf(
        HomeShortcut("길안내", "목적지 검색", Icons.Outlined.Map, AivyColors.AccentLight, AivyColors.Accent, onOpenTmap),
        HomeShortcut("러닝", "운동 시작", Icons.Outlined.DirectionsRun, AivyColors.PositiveLight, AivyColors.Positive, onOpenRunning),
        HomeShortcut("AI 카메라", "사진 분석", Icons.Outlined.CameraAlt, AivyColors.IconBackground, AivyColors.Primary, onOpenCamera),
    )

    AivyPage {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AivySpace.Page,
                top = AivySpace.Xl,
                end = AivySpace.Page,
                bottom = AivySpace.Xl,
            ),
            verticalArrangement = Arrangement.spacedBy(AivySpace.Lg),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = now,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AivyColors.Text3,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.Bluetooth, contentDescription = "Bluetooth", tint = AivyColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(AivySpace.Sm))
                    Icon(Icons.Outlined.Wifi, contentDescription = "WiFi", tint = AivyColors.Positive, modifier = Modifier.size(18.dp))
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(AivyRadius.Md),
                        color = AivyColors.PrimaryLight,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("A", color = AivyColors.Primary, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Spacer(modifier = Modifier.width(AivySpace.Md))
                    Column {
                        Text("AIVY", style = MaterialTheme.typography.displayLarge, color = AivyColors.Primary)
                        Text("Navigator", style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text4)
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AivyColors.WarningLight,
                    shape = RoundedCornerShape(AivyRadius.Md),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AivySpace.Md, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = AivyColors.Warning, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(AivySpace.Sm))
                        Text(
                            text = "TMap 경로 안내와 러닝 기록을 AIVY 스타일로 사용할 수 있어요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AivyColors.Text1,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AivyColors.Primary,
                    shape = RoundedCornerShape(AivyRadius.Md),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AivySpace.Md, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AivyColors.Positive),
                        )
                        Spacer(modifier = Modifier.width(AivySpace.Sm))
                        Text("AIVY-Pro", style = MaterialTheme.typography.titleMedium, color = AivyColors.Surface)
                        Spacer(modifier = Modifier.width(AivySpace.Sm))
                        Text("연결됨", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.weight(1f))
                        Text("82%", style = MaterialTheme.typography.titleMedium, color = AivyColors.Surface)
                    }
                }
            }

            item {
                Column {
                    AivySectionLabel("바로가기")
                    Spacer(modifier = Modifier.height(AivySpace.Sm))
                    shortcuts.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm), modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { shortcut ->
                                ShortcutCard(shortcut = shortcut, modifier = Modifier.weight(1f))
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(AivySpace.Sm))
                    }
                }
            }

            items(
                listOf(
                    "오늘의 길안내 기록을 확인하고 다음 목적지를 빠르게 검색하세요.",
                    "이번 달 러닝 거리와 최근 운동 기록을 한 화면에서 확인하세요.",
                ),
            ) { insight ->
                InsightCard(text = insight)
            }
        }
    }
}

@Composable
private fun ShortcutCard(shortcut: HomeShortcut, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .height(138.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { shortcut.onClick() },
        color = AivyColors.Surface,
        shape = RoundedCornerShape(AivyRadius.Lg),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(AivySpace.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                color = shortcut.iconContainer,
                shape = RoundedCornerShape(AivyRadius.Md),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(shortcut.icon, contentDescription = null, tint = shortcut.iconTint, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.height(AivySpace.Md))
            Text(shortcut.title, style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1, maxLines = 1)
            Text(shortcut.subtitle, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4, maxLines = 1)
        }
    }
}

@Composable
private fun InsightCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AivyColors.Surface,
        shape = RoundedCornerShape(AivyRadius.Lg),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(AivySpace.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = AivyColors.AccentLight, shape = RoundedCornerShape(AivyRadius.Md), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("A", color = AivyColors.Accent, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(modifier = Modifier.width(AivySpace.Md))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = AivyColors.Text2)
        }
    }
}
