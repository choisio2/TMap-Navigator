package com.aivy.navigator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivyElevation
import com.aivy.navigator.ui.theme.AivyRadius
import com.aivy.navigator.ui.theme.AivySpace

@Composable
fun AivyPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AivyColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 430.dp)
                .align(Alignment.TopCenter)
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun AivyPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AivyColors.Border.copy(alpha = 0.85f), RoundedCornerShape(AivyRadius.Xl)),
        shape = RoundedCornerShape(AivyRadius.Xl),
        tonalElevation = 0.dp,
        shadowElevation = AivyElevation.Sm,
        color = AivyColors.Surface,
    ) {
        Column(
            modifier = Modifier.padding(AivySpace.Card),
            verticalArrangement = Arrangement.spacedBy(AivySpace.Sm),
            content = content,
        )
    }
}

@Composable
fun AivyPanelStrong(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AivyColors.Border.copy(alpha = 0.95f), RoundedCornerShape(AivyRadius.Xl)),
        shape = RoundedCornerShape(AivyRadius.Xl),
        tonalElevation = 0.dp,
        shadowElevation = AivyElevation.Md,
        color = AivyColors.Surface,
    ) {
        Column(
            modifier = Modifier.padding(AivySpace.Card),
            verticalArrangement = Arrangement.spacedBy(AivySpace.Sm),
            content = content,
        )
    }
}

@Composable
fun AivySectionLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = AivyColors.Text4,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun AivyStatusChip(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = container,
        shape = RoundedCornerShape(AivyRadius.Lg),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = content,
            modifier = Modifier.padding(horizontal = AivySpace.Sm, vertical = AivySpace.Xs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
