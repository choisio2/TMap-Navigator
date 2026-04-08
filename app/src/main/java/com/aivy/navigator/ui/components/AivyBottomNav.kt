package com.aivy.navigator.ui.components

import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aivy.navigator.ui.navigation.AivyDestination
import com.aivy.navigator.ui.theme.AivyColors

@Composable
fun AivyBottomNav(
    currentRoute: String?,
    onTabSelected: (AivyDestination) -> Unit,
) {
    NavigationBar(
        containerColor = AivyColors.Surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(width = 1.dp, color = AivyColors.Border.copy(alpha = 0.7f)),
    ) {
        AivyDestination.primaryTabs.forEach { destination ->
            val selected = destination.route == currentRoute
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(destination) },
                icon = {
                    Icon(
                        imageVector = iconFor(destination),
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(text = destination.label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AivyColors.Primary,
                    selectedTextColor = AivyColors.Primary,
                    indicatorColor = AivyColors.AccentLight,
                    unselectedIconColor = AivyColors.Text4,
                    unselectedTextColor = AivyColors.Text4,
                ),
            )
        }
    }
}

private fun iconFor(destination: AivyDestination): ImageVector = when (destination) {
    AivyDestination.Home -> Icons.Outlined.Home
    AivyDestination.Navigate -> Icons.Outlined.Map
    AivyDestination.Translate -> Icons.Outlined.Translate
    AivyDestination.Memory -> Icons.Outlined.Memory
    AivyDestination.Settings -> Icons.Outlined.Settings
    else -> Icons.Outlined.Home
}
