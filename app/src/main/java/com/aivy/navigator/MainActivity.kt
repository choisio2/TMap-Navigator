package com.aivy.navigator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aivy.navigator.ui.AivyApp
import com.aivy.navigator.ui.navigation.AndroidMapBridgeAction
import com.aivy.navigator.ui.theme.AivyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AivyTheme {
                AivyApp(
                    mapBridgeAction = AndroidMapBridgeAction(this),
                )
            }
        }
    }
}
