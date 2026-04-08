package com.aivy.navigator.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aivy.navigator.ui.components.AivyPage
import com.aivy.navigator.ui.model.AivyMockData
import com.aivy.navigator.ui.model.TranslateMessage
import com.aivy.navigator.ui.model.TranslateUiMode
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace
import kotlinx.coroutines.delay

@Composable
fun TranslateScreen() {
    val seed = AivyMockData.translateState()

    var mode by rememberSaveable { mutableStateOf(seed.mode) }
    var currentOriginal by rememberSaveable { mutableStateOf(seed.currentOriginal) }
    var currentTranslated by rememberSaveable { mutableStateOf(seed.currentTranslated) }
    var showToast by rememberSaveable { mutableStateOf(false) }
    var turnCount by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(mode) {
        when (mode) {
            TranslateUiMode.Listening -> {
                currentOriginal = ""
                currentTranslated = ""
                delay(800)
                currentOriginal = "すみません..."
                delay(700)
                currentOriginal = "すみません、この近くに..."
                delay(900)
                currentOriginal = "すみません、この近くに駅はありますか？"
                currentTranslated = "실례합니다, 이 근처에 역이 있나요?"
                turnCount += 1
            }

            TranslateUiMode.Speaking -> {
                currentOriginal = ""
                currentTranslated = ""
                delay(700)
                currentOriginal = "네, 저쪽으로..."
                delay(1_100)
                currentOriginal = "네, 저쪽으로 200미터 가시면 됩니다."
                currentTranslated = "はい、あちらに200メートル行けば着きます。"
                turnCount += 1
            }

            TranslateUiMode.Log -> {
                showToast = true
                delay(2_300)
                showToast = false
            }

            else -> Unit
        }
    }

    if (mode == TranslateUiMode.ShowCard) {
        ShowCardMode(
            translated = currentTranslated.ifBlank { "はい、あちらに200メートル行けば着きます。" },
            original = currentOriginal.ifBlank { "네, 저쪽으로 200미터 가시면 됩니다." },
            onClose = { mode = TranslateUiMode.Idle },
        )
        return
    }

    if (mode == TranslateUiMode.Log) {
        LogMode(
            messages = seed.messages,
            showToast = showToast,
            onBack = { mode = TranslateUiMode.Idle },
        )
        return
    }

    AivyPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AivySpace.Page),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AivySpace.Lg, bottom = AivySpace.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "번역",
                    style = MaterialTheme.typography.titleLarge,
                    color = AivyColors.Primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = AivyColors.BackgroundAlt,
                    shape = RoundedCornerShape(AivyRadius.Sm),
                    modifier = Modifier
                        .clip(RoundedCornerShape(AivyRadius.Sm))
                        .border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Sm)),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AivyRadius.Sm))
                            .background(AivyColors.BackgroundAlt)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable { mode = TranslateUiMode.Log },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = AivyColors.Text3,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "기록",
                            color = AivyColors.Text3,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AivySpace.Sm, bottom = AivySpace.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                LangPill(
                    code = "KO",
                    name = "한국어",
                    active = mode == TranslateUiMode.Speaking,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Outlined.SwapHoriz,
                    contentDescription = null,
                    tint = AivyColors.Text4,
                )
                Spacer(modifier = Modifier.width(10.dp))
                LangPill(
                    code = "JA",
                    name = "日本語",
                    active = mode == TranslateUiMode.Listening,
                )
            }

            if (mode == TranslateUiMode.Idle) {
                Surface(
                    color = AivyColors.Surface,
                    shape = RoundedCornerShape(AivyRadius.Lg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, AivyColors.Accent, RoundedCornerShape(AivyRadius.Lg)),
                ) {
                    Column(modifier = Modifier.padding(AivySpace.Lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = AivyColors.Accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "현재 위치: 도쿄, 일본",
                                style = MaterialTheme.typography.bodySmall,
                                color = AivyColors.Text2,
                            )
                        }
                        Spacer(modifier = Modifier.height(AivySpace.Sm))
                        Text(
                            text = "지난번 설정(KO ↔ JA)으로 시작할까요?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AivyColors.Text1,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { mode = TranslateUiMode.Listening },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("이 설정으로 시작")
                            }
                            Surface(
                                color = AivyColors.Background,
                                shape = RoundedCornerShape(AivyRadius.Sm),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, AivyColors.Border, RoundedCornerShape(AivyRadius.Sm)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "다른 언어 선택",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AivyColors.Text3,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (mode == TranslateUiMode.Idle) AivySpace.Md else AivySpace.Xl))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (mode) {
                    TranslateUiMode.Idle -> IdleContent()
                    TranslateUiMode.Listening -> ActiveContent(
                        title = "듣는 중 · 日本語",
                        titleColor = AivyColors.Accent,
                        original = currentOriginal,
                        translated = currentTranslated,
                        turnCount = turnCount,
                        ringColor = AivyColors.Accent,
                        centerColor = AivyColors.AccentLight,
                        iconTint = AivyColors.Accent,
                    )

                    TranslateUiMode.Speaking -> ActiveContent(
                        title = "내 차례 · 한국어",
                        titleColor = AivyColors.Primary,
                        original = currentOriginal,
                        translated = currentTranslated,
                        turnCount = turnCount,
                        ringColor = AivyColors.Primary,
                        centerColor = AivyColors.PrimaryLight,
                        iconTint = AivyColors.Primary,
                    )

                    else -> Unit
                }
            }

            if (currentTranslated.isNotBlank() && mode in setOf(TranslateUiMode.Listening, TranslateUiMode.Speaking)) {
                Surface(
                    color = AivyColors.BackgroundAlt,
                    shape = RoundedCornerShape(AivyRadius.Md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AivySpace.Sm),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .clickable { mode = TranslateUiMode.ShowCard },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = AivyColors.Text2,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "상대방에게 보여주기",
                            style = MaterialTheme.typography.bodySmall,
                            color = AivyColors.Text2,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ListenSpeakButton(
                    label = if (mode == TranslateUiMode.Listening) "듣는 중..." else "듣기",
                    caption = "상대 언어",
                    active = mode == TranslateUiMode.Listening,
                    activeColor = AivyColors.Accent,
                    inactiveColor = AivyColors.AccentLight,
                    inactiveTextColor = AivyColors.Accent,
                    onClick = {
                        if (mode == TranslateUiMode.Listening) {
                            mode = TranslateUiMode.Idle
                            currentOriginal = ""
                            currentTranslated = ""
                        } else {
                            mode = TranslateUiMode.Listening
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                ListenSpeakButton(
                    label = if (mode == TranslateUiMode.Speaking) "번역 중..." else "말하기",
                    caption = "내 언어",
                    active = mode == TranslateUiMode.Speaking,
                    activeColor = AivyColors.Primary,
                    inactiveColor = AivyColors.PrimaryLight,
                    inactiveTextColor = AivyColors.Primary,
                    onClick = {
                        if (mode == TranslateUiMode.Speaking) {
                            mode = TranslateUiMode.Idle
                            currentOriginal = ""
                            currentTranslated = ""
                        } else {
                            mode = TranslateUiMode.Speaking
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(AivySpace.Lg))
        }
    }
}

@Composable
private fun ShowCardMode(
    translated: String,
    original: String,
    onClose: () -> Unit,
) {
    AivyPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AivySpace.Page),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = translated,
                style = MaterialTheme.typography.titleLarge,
                color = AivyColors.Primary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(AivySpace.Md))
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(2.dp)
                    .background(AivyColors.Border),
            )
            Spacer(modifier = Modifier.height(AivySpace.Md))
            Text(
                text = original,
                style = MaterialTheme.typography.titleMedium,
                color = AivyColors.Text4,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(AivySpace.Xl))
            Surface(
                color = AivyColors.BackgroundAlt,
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = AivyColors.Primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("닫기")
            }
        }
    }
}

@Composable
private fun LogMode(
    messages: List<TranslateMessage>,
    showToast: Boolean,
    onBack: () -> Unit,
) {
    AivyPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AivySpace.Page),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AivySpace.Lg, bottom = AivySpace.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                        tint = AivyColors.Text4,
                    )
                }
                Text(
                    text = "대화 기록",
                    style = MaterialTheme.typography.titleLarge,
                    color = AivyColors.Primary,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AivySpace.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LangPill("KO", "한국어", active = false)
                Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = AivyColors.Text4)
                LangPill("JA", "日本語", active = false)
                Spacer(modifier = Modifier.weight(1f))
                Text("${messages.size}턴", style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AivySpace.Sm),
            ) {
                items(messages) { item ->
                    LogBubble(item = item)
                }
            }

            Spacer(modifier = Modifier.height(AivySpace.Md))
            Surface(
                color = AivyColors.Surface,
                shape = RoundedCornerShape(AivyRadius.Md),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, AivyColors.Accent, RoundedCornerShape(AivyRadius.Md)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        tint = AivyColors.Accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "텍스트 내보내기",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AivyColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(AivySpace.Lg))
        }

        if (showToast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AivySpace.Page),
                contentAlignment = Alignment.TopCenter,
            ) {
                Surface(
                    shape = RoundedCornerShape(AivyRadius.Md),
                    color = AivyColors.Positive,
                ) {
                    Text(
                        text = "대화 기록이 저장되었습니다 ✓",
                        color = AivyColors.Surface,
                        modifier = Modifier.padding(horizontal = AivySpace.Lg, vertical = AivySpace.Sm),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = AivyColors.BackgroundAlt,
            shape = CircleShape,
            modifier = Modifier.size(84.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = AivyColors.Text4,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "번역 준비 완료",
            style = MaterialTheme.typography.titleMedium,
            color = AivyColors.Text4,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "아래 버튼을 탭하거나 AIVY에 말하세요",
            style = MaterialTheme.typography.bodySmall,
            color = AivyColors.Text4,
        )
    }
}

@Composable
private fun ActiveContent(
    title: String,
    titleColor: Color,
    original: String,
    translated: String,
    turnCount: Int,
    ringColor: Color,
    centerColor: Color,
    iconTint: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PulseMic(ringColor = ringColor, centerColor = centerColor, iconTint = iconTint)
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))

        val blinkTransition = rememberInfiniteTransition(label = "typingBlink")
        val blinkAlpha by blinkTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(650, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "typingBlinkAlpha",
        )

        Text(
            text = original.ifBlank { "..." },
            style = MaterialTheme.typography.titleMedium,
            color = AivyColors.Text1,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (translated.isBlank()) {
            Text(
                text = "|",
                style = MaterialTheme.typography.titleMedium,
                color = AivyColors.Text3,
                modifier = Modifier.alpha(blinkAlpha),
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            if (turnCount >= 2) {
                Surface(
                    color = AivyColors.AccentLight,
                    shape = RoundedCornerShape(AivyRadius.Lg),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = AivyColors.Accent,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "이전 대화 맥락 참조 중",
                            style = MaterialTheme.typography.labelSmall,
                            color = AivyColors.Accent,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = "→ $translated",
                style = MaterialTheme.typography.bodyMedium,
                color = AivyColors.Accent,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PulseMic(
    ringColor: Color,
    centerColor: Color,
    iconTint: Color,
) {
    val transition = rememberInfiniteTransition(label = "pulseMic")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_200, easing = LinearEasing),
        ),
        label = "pulseProgress",
    )

    Box(
        modifier = Modifier.size(172.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            for (index in 0..2) {
                val offset = (progress + index * 0.28f) % 1f
                val radius = 32.dp.toPx() + offset * 64.dp.toPx()
                val alpha = (0.22f * (1f - offset)).coerceAtLeast(0f)
                drawCircle(
                    color = ringColor.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Surface(
            color = centerColor,
            shape = CircleShape,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun LogBubble(item: TranslateMessage) {
    val mine = item.speaker == "나"
    Column(
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "${if (mine) "나" else "상대"} · ${item.timestamp}",
            style = MaterialTheme.typography.labelSmall,
            color = AivyColors.Text4,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Surface(
            color = if (mine) AivyColors.Primary else AivyColors.BackgroundAlt,
            shape = RoundedCornerShape(
                topStart = AivyRadius.Lg,
                topEnd = AivyRadius.Lg,
                bottomStart = if (mine) AivyRadius.Lg else 4.dp,
                bottomEnd = if (mine) 4.dp else AivyRadius.Lg,
            ),
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Column(modifier = Modifier.padding(horizontal = AivySpace.Md, vertical = 10.dp)) {
                Text(
                    text = if (mine) item.translated else item.original,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mine) Color.White else AivyColors.Text1,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (mine) item.original else item.translated,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mine) Color.White.copy(alpha = 0.62f) else AivyColors.Text4,
                )
            }
        }
    }
}

@Composable
private fun LangPill(
    code: String,
    name: String,
    active: Boolean,
) {
    Surface(
        color = if (active) AivyColors.Primary else AivyColors.BackgroundAlt,
        shape = RoundedCornerShape(AivyRadius.Lg),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Color.White.copy(alpha = 0.7f) else AivyColors.Text4,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) Color.White else AivyColors.Text3,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ListenSpeakButton(
    label: String,
    caption: String,
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    inactiveTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (active) activeColor else inactiveColor,
        shape = RoundedCornerShape(AivyRadius.Lg),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (active) activeColor else inactiveColor)
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(AivyRadius.Lg))
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) Color.White else inactiveTextColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Color.White.copy(alpha = 0.7f) else inactiveTextColor.copy(alpha = 0.8f),
            )
        }
    }
}
