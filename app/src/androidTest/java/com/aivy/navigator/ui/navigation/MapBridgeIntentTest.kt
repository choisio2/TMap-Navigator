package com.aivy.navigator.ui.navigation

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.Intents.intended
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aivy.navigator.MainActivity
import com.aivy.navigator.NaverMapsActivity
import com.aivy.navigator.TmapsActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapBridgeIntentTest {
    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun openTMap_launchesTMapActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AndroidMapBridgeAction(activity).openTMap()
            }
        }

        intended(hasComponent(TmapsActivity::class.java.name))
    }

    @Test
    fun openNaverMap_launchesNaverActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AndroidMapBridgeAction(activity).openNaverMap()
            }
        }

        intended(hasComponent(NaverMapsActivity::class.java.name))
    }
}
