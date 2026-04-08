package com.aivy.navigator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.aivy.navigator.ui.components.AivyPage
import com.aivy.navigator.ui.components.AivyPanel
import com.aivy.navigator.ui.components.AivySectionLabel
import com.aivy.navigator.ui.model.AivyMockData
import com.aivy.navigator.ui.navigation.AivyDestination
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace

enum class SettingsSheet {
    Tts,
    Language,
    Translation,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    onNavigate: (AivyDestination) -> Unit,
) {
    val defaults = AivyMockData.settingsState()

    var autoConnect by rememberSaveable { mutableStateOf(defaults.autoConnect) }
    var cloudSync by rememberSaveable { mutableStateOf(defaults.cloudSync) }
    var safetyFilter by rememberSaveable { mutableStateOf(defaults.safetyFilter) }
    var fallDetection by rememberSaveable { mutableStateOf(defaults.fallDetection) }
    var showAdvanced by rememberSaveable { mutableStateOf(defaults.showAdvanced) }
    var ttsSpeed by rememberSaveable { mutableIntStateOf(defaults.ttsSpeed) }
    var appLanguage by rememberSaveable { mutableStateOf(defaults.appLanguage) }
    var targetLanguage by rememberSaveable { mutableStateOf(defaults.translationTarget) }

    var activeSheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    AivyPage {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AivySpace.Page),
            verticalArrangement = Arrangement.spacedBy(AivySpace.Md),
        ) {
            item {
                Text(
                    text = "설정",
                    style = MaterialTheme.typography.titleLarge,
                    color = AivyColors.Primary,
                    modifier = Modifier.padding(top = AivySpace.Md),
                )
            }

            item {
                AivySectionLabel("기기")
                AivyPanel {
                    SettingRow(
                        label = "연결된 기기",
                        value = "AIVY-Pro",
                        onClick = { onNavigate(AivyDestination.Pairing) },
                    )
                    HorizontalDivider(color = AivyColors.Border.copy(alpha = 0.6f))
                    SettingSwitchRow(
                        label = "자동 연결",
                        description = "기기가 근처에 있을 때 자동 재연결",
                        checked = autoConnect,
                        onCheckedChange = { autoConnect = it },
                    )
                }
            }

            item {
                AivySectionLabel("오디오")
                AivyPanel {
                    SettingRow(
                        label = "음성 속도",
                        value = speedLabel(ttsSpeed),
                        onClick = { activeSheet = SettingsSheet.Tts },
                    )
                    HorizontalDivider(color = AivyColors.Border.copy(alpha = 0.6f))
                    SettingRow(
                        label = "앱 언어",
                        value = appLanguage,
                        onClick = { activeSheet = SettingsSheet.Language },
                    )
                    HorizontalDivider(color = AivyColors.Border.copy(alpha = 0.6f))
                    SettingRow(
                        label = "번역 대상 언어",
                        value = targetLanguage,
                        onClick = { activeSheet = SettingsSheet.Translation },
                    )
                }
            }

            item {
                AivySectionLabel("안전")
                AivyPanel {
                    SettingSwitchRow(
                        label = "안전 필터",
                        description = "유해 콘텐츠 사전 차단",
                        checked = safetyFilter,
                        onCheckedChange = { safetyFilter = it },
                    )
                    HorizontalDivider(color = AivyColors.Border.copy(alpha = 0.6f))
                    SettingSwitchRow(
                        label = "낙상 감지",
                        description = "위험 상황 자동 감지",
                        checked = fallDetection,
                        onCheckedChange = { fallDetection = it },
                    )
                }
            }

            item {
                AivySectionLabel("메모리")
                AivyPanel {
                    SettingSwitchRow(
                        label = "클라우드 동기화",
                        description = "기기 간 기록 동기화",
                        checked = cloudSync,
                        onCheckedChange = { cloudSync = it },
                    )
                    HorizontalDivider(color = AivyColors.Border.copy(alpha = 0.6f))
                    SettingRow(
                        label = "데이터 전체 삭제",
                        value = "삭제",
                        valueColor = AivyColors.Danger,
                        onClick = { confirmDelete = true },
                    )
                }
            }

            item {
                Surface(
                    color = AivyColors.BackgroundAlt,
                    shape = RoundedCornerShape(AivyRadius.Md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced },
                ) {
                    Text(
                        text = if (showAdvanced) "고급 설정 숨기기" else "고급 설정 보기",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AivyColors.Text2,
                        modifier = Modifier.padding(AivySpace.Md),
                    )
                }
            }

            if (showAdvanced) {
                item {
                    AivySectionLabel("고급")
                    AivyPanel {
                        SettingRow(label = "OCR", value = "설정", onClick = { onNavigate(AivyDestination.Ocr) })
                        HorizontalDivider(color = AivyColors.Border.copy(alpha = 0.6f))
                        SettingRow(label = "갤러리", value = "열기", onClick = { onNavigate(AivyDestination.Gallery) })
                        HorizontalDivider(color = AivyColors.Border.copy(alpha = 0.6f))
                        SettingRow(label = "미팅", value = "열기", onClick = { onNavigate(AivyDestination.Meeting) })
                        HorizontalDivider(color = AivyColors.Border.copy(alpha = 0.6f))
                        SettingRow(label = "운동", value = "열기", onClick = { onNavigate(AivyDestination.Exercise) })
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(AivySpace.Xl)) }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("정말 삭제할까요?", color = AivyColors.Primary) },
                text = { Text("모든 저장된 기억이 삭제됩니다.", color = AivyColors.Text3) },
                confirmButton = {
                    Button(onClick = { confirmDelete = false }) {
                        Text("삭제")
                    }
                },
                dismissButton = {
                    Button(onClick = { confirmDelete = false }) {
                        Text("취소")
                    }
                },
            )
        }

        if (activeSheet != null) {
            ModalBottomSheet(onDismissRequest = { activeSheet = null }) {
                when (activeSheet) {
                    SettingsSheet.Tts -> OptionSheet(
                        title = "음성 속도",
                        options = listOf("느리게", "보통", "빠르게"),
                        selected = speedLabel(ttsSpeed),
                        onSelect = {
                            ttsSpeed = when (it) {
                                "느리게" -> 25
                                "빠르게" -> 75
                                else -> 50
                            }
                            activeSheet = null
                        },
                    )

                    SettingsSheet.Language -> OptionSheet(
                        title = "앱 언어",
                        options = listOf("한국어", "English", "日本語"),
                        selected = appLanguage,
                        onSelect = {
                            appLanguage = it
                            activeSheet = null
                        },
                    )

                    SettingsSheet.Translation -> OptionSheet(
                        title = "번역 대상 언어",
                        options = listOf("자동", "영어", "일본어"),
                        selected = targetLanguage,
                        onSelect = {
                            targetLanguage = it
                            activeSheet = null
                        },
                    )

                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun OptionSheet(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AivySpace.Page, vertical = AivySpace.Sm),
        verticalArrangement = Arrangement.spacedBy(AivySpace.Xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AivyColors.Primary)
        options.forEach { option ->
            Surface(
                color = if (option == selected) AivyColors.AccentLight else AivyColors.Surface,
                shape = RoundedCornerShape(AivyRadius.Md),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) },
            ) {
                Text(
                    text = option,
                    color = if (option == selected) AivyColors.Accent else AivyColors.Text1,
                    modifier = Modifier.padding(horizontal = AivySpace.Md, vertical = AivySpace.Md),
                )
            }
        }
        Spacer(modifier = Modifier.height(AivySpace.Lg))
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = AivyColors.Text2,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AivySpace.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = AivyColors.Text1,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AivySpace.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = AivyColors.Text1)
            Text(description, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text3)
        }
        Spacer(modifier = Modifier.width(AivySpace.Sm))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun speedLabel(speed: Int): String = when {
    speed < 40 -> "느리게"
    speed > 60 -> "빠르게"
    else -> "보통"
}
