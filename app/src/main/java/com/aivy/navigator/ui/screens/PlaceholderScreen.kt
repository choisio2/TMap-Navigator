package com.aivy.navigator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.aivy.navigator.ui.components.AivyPage
import com.aivy.navigator.ui.theme.AivyColors
import com.aivy.navigator.ui.theme.AivySpace

@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    ctaLabel: String = "이전으로",
    onClickCta: (() -> Unit)? = null,
) {
    AivyPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AivySpace.Page),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = AivyColors.Accent,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = AivyColors.Primary,
                modifier = Modifier.padding(top = AivySpace.Md),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = AivyColors.Text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = AivySpace.Sm),
            )

            if (onClickCta != null) {
                Button(
                    onClick = onClickCta,
                    modifier = Modifier.padding(top = AivySpace.Xl),
                ) {
                    Text(text = ctaLabel)
                }
            }
        }
    }
}
