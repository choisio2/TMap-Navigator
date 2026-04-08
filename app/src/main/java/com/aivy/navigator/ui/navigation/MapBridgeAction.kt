package com.aivy.navigator.ui.navigation

import android.app.Activity
import android.content.Intent
import com.aivy.navigator.NaverMapsActivity
import com.aivy.navigator.TmapsActivity

interface MapBridgeAction {
    fun openTMap()
    fun openNaverMap()
}

class AndroidMapBridgeAction(
    private val activity: Activity,
) : MapBridgeAction {
    override fun openTMap() {
        activity.startActivity(Intent(activity, TmapsActivity::class.java))
    }

    override fun openNaverMap() {
        activity.startActivity(Intent(activity, NaverMapsActivity::class.java))
    }
}
