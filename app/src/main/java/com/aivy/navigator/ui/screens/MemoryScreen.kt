package com.aivy.navigator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.aivy.navigator.ui.components.AivyStatusChip
import com.aivy.navigator.ui.model.AivyMockData
import com.aivy.navigator.ui.model.MemoryEntry
import com.aivy.navigator.ui.model.MemoryUiState
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace

@Composable
fun MemoryScreen(
    state: MemoryUiState = AivyMockData.memoryState(),
) {
    var selectedCategory by rememberSaveable { mutableStateOf(state.selectedCategory) }
    var selectedEntry by remember { mutableStateOf<MemoryEntry?>(null) }

    val visibleEntries = state.entries.filter { selectedCategory == "전체" || it.category == selectedCategory }

    AivyPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AivySpace.Page),
        ) {
            Text(
                text = "기억",
                style = MaterialTheme.typography.titleLarge,
                color = AivyColors.Primary,
                modifier = Modifier.padding(vertical = AivySpace.Md),
            )

            AivySectionLabel(label = "카테고리")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AivySpace.Sm, bottom = AivySpace.Md),
                horizontalArrangement = Arrangement.spacedBy(AivySpace.Sm),
            ) {
                state.categories.forEach { category ->
                    Surface(
                        color = if (selectedCategory == category) AivyColors.Primary else AivyColors.BackgroundAlt,
                        shape = RoundedCornerShape(AivyRadius.Lg),
                        modifier = Modifier.clickable { selectedCategory = category },
                    ) {
                        Text(
                            text = category,
                            color = if (selectedCategory == category) AivyColors.Surface else AivyColors.Text3,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = AivySpace.Md, vertical = AivySpace.Xs),
                        )
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(AivySpace.Sm)) {
                items(visibleEntries) { entry ->
                    AivyPanel(modifier = Modifier.clickable { selectedEntry = entry }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AivyStatusChip(
                                text = entry.category,
                                container = AivyColors.AccentLight,
                                content = AivyColors.Accent,
                            )
                            Spacer(modifier = Modifier.width(AivySpace.Sm))
                            Text(entry.createdAt, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text4)
                            Spacer(modifier = Modifier.weight(1f))
                            AivyStatusChip(
                                text = entry.status,
                                container = statusContainer(entry.status),
                                content = statusText(entry.status),
                            )
                        }
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, color = AivyColors.Text1)
                        Text(
                            entry.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = AivyColors.Text3,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        selectedEntry?.let { entry ->
            AlertDialog(
                onDismissRequest = { selectedEntry = null },
                title = {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, color = AivyColors.Primary)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AivySpace.Sm)) {
                        Text("카테고리: " + entry.category, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text3)
                        Text("생성일: " + entry.createdAt, style = MaterialTheme.typography.bodySmall, color = AivyColors.Text3)
                        Text(entry.summary, style = MaterialTheme.typography.bodyLarge, color = AivyColors.Text1)
                    }
                },
                confirmButton = {
                    Button(onClick = { selectedEntry = null }) {
                        Text("닫기")
                    }
                },
            )
        }
    }
}

private fun statusContainer(status: String) = when (status) {
    "동기화됨" -> AivyColors.PositiveLight
    "검토 필요" -> AivyColors.WarningLight
    else -> AivyColors.BackgroundAlt
}

private fun statusText(status: String) = when (status) {
    "동기화됨" -> AivyColors.Positive
    "검토 필요" -> AivyColors.Warning
    else -> AivyColors.Text3
}
